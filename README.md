# YixinDroid

PC Yixin-Board GUI를 안드로이드로 옮기는 프로젝트. 전체 설계는
`../test-yixin/docs/안드로이드_앱_아키텍처_계획.md` 참고.

이 저장소는 현재 **P0(스캐폴드) + P1(엔진 통신 모듈) + P2(보드 UI·분석·파서 확장)
+ P3(3수/5수 랭킹 대시보드) + P6(분석 표시 완성)** 까지 구현되어 있다.

> 전체 로드맵은 `test-yixin/docs/안드로이드_전체기능_이식_계획.md`(v2)를 따른다.
> Yixin.exe의 설정 67개·DB 기능·대국 기능 대부분은 아직 미구현이며 P4·P5·P7~P11에 있다.

**P6 핵심**: 엔진 핸드셰이크에 `info show_detail 3` + `yxshowinfo`가 없으면 Rapfi가
`INFO PV/DEPTH/EVAL/WINRATE/BESTLINE`을 보내지 않아 분석이 화면에 전혀 안 나온다.
또 `INFO rule`·`thread_num`·`hash_size` 등을 보내지 않으면 엔진이 자기 config
기본값(프리스타일)으로 돌아 **PC Yixin과 결과가 달라진다** — `EngineParams`가
`test-yixin/settings.txt` 기본값(자유 렌주·4스레드·8192MB·멀티PV 3)을 그대로 전송한다.

> ⚠ 이 코드는 **Android SDK가 없는 환경에서 작성**되어 여기서 컴파일 검증은 하지
> 못했다. Android Studio(Ladybug 이상, JDK 17)에서 열어 Gradle sync 후 빌드하는
> 것을 전제로 정확하게 작성했다. 첫 sync 시 Studio가 Gradle 래퍼(jar/gradlew)를
> 채워준다(또는 `gradle wrapper --gradle-version 8.9`).

## 무엇이 들어있나 (P1 범위)

- **엔진 통신 모듈** — engine.exe(투명 TCP 릴레이)를 대체. `rapfi-server`
  (`100.111.248.44:5050`)에 직접 접속해 piskvork를 그대로 주고받는다.
  - `EngineConnection` — 소켓 + 리더/라이터 코루틴 + 연결 상태기계
  - `EngineCommand` / `EngineResponse` / `ResponseParser` — 프로토콜 (순수 함수)
  - `CoordMapper` — 좌표 변환 단일 진원 (flip-Y 함정 격리)
  - `EngineRepository(Impl)` — 파싱 스트림 + 원시 콘솔 미러
  - `EngineService` — 세션 유지용 Foreground Service
- **연결 화면**(`feature/connection`) — 서버 IP/포트 입력, 연결/해제, 상태 칩,
  **piskvork 원시 콘솔**(양방향), 수동 명령 입력. 실서버 왕복·좌표 검증용.
- **앱 셸** — Material 3 다크 테마, 하단 4탭 내비게이션(보드/익스플로러/랭킹은
  P2+ 플레이스홀더).
- **테스트**(`app/src/test`) — 파서·좌표·명령 직렬화 단위테스트 + 로컬 소켓
  서버를 띄워 `EngineConnection` 왕복을 검증하는 통합테스트.

## 요구 사항

1. Android Studio + Android SDK (compileSdk 35), JDK 17
2. 기기/에뮬레이터에 **Tailscale 앱 설치 + 로그인 + 연결** (시스템 VPN)
3. `rapfi-server` 노드가 온라인이어야 함 (온디맨드 운용)

## P1 검증(실서버) 절차

1. 기기에서 Tailscale 켜기 → `rapfi-server` 도달 확인.
2. 앱 실행 → **연결** 탭 → host `100.111.248.44`(프리필), port `5050`(프리필) → **연결**.
3. 콘솔에 서버의 초기 로드 메시지 + `START 15` 전송(`»`) + `OK` 수신 확인,
   상태 칩이 **준비됨**으로.
4. 명령 입력에 `ABOUT` → 엔진 정보 라인 확인. `TURN 7,7` → 좌표 응답(`x,y`)이
   **PC GUI에서 같은 국면과 일치하는지** 대조(좌표계 검증, 계획 §2.5).
5. 콘솔에서 관찰한 실제 INFO/PV 라인 문법으로 P2에서 `ResponseParser`를 확장.

## 구조 메모

P1은 **단일 `:app` 모듈**이되, 패키지가 계획서의 모듈 경계를 그대로 반영한다
(`core/`, `domain/`, `data/`, `feature/`). 빌드가 확인되면 P2에서 기계적으로
Gradle 모듈로 분리한다(계획 §1.3). 단일 모듈로 시작한 이유는 SDK 없는 환경에서
다중 모듈 Gradle 배선을 검증할 수 없어 위험을 줄이기 위함.

## 라이선스 주의

- 배포는 **개인 사이드로드** 전용.
- rank5(순수 계산)만 번들 가능. 오프닝 익스플로러 데이터(`freq_data.json`/팩)는
  **RenjuNet 파생 → 재배포·스토어 배포 금지**. P4에서 사용자 기기 반입 방식으로 구현.

## P2 (구현됨)

- **보드 UI**: `core/designsystem/component/GomokuBoard` — 반응형 15×15 Canvas
  (격자·화점·좌표·번호 돌·마지막 수 링·PV 고스트·금수 X·베스트 마커), 탭→착수.
- **보드 화면**(`feature/board`): 평가바(흑 승률)·수순 카운트·무르기/초기화·
  분석 토글·멀티PV 스테퍼·PV 목록(탭하면 해당 수순을 보드에 고스트로 미리보기).
- **파서 확장(문법 포팅)**: 데스크톱 `iochannelout_watch` 문법을 그대로 포팅.
  - **좌표 = `y,x`(행,열)** 로 정정(P1의 `x,y` 가정은 오류였음, `CoordMapper` 수정).
  - `INFO PV <idx>…INFO PV DONE` 블록 → `SearchAggregator` 가 `PvSnapshot` 조립
    (DEPTH·EVAL(±M/수)·WINRATE·BESTLINE·NUMPV), `MESSAGE REALTIME BEST/POS/…`,
    `FORBID`(yyxx*·`.`), 더블 좌표 착수.
  - 분석 흐름: `EngineRepository.analyze(position, params)` → `yxboard`+stones+
    `yxnbest N`, 취소 시 `YXSTOP`. `forbidden()` 도 추가.
- 테스트 추가: 좌표(y,x)·명령 직렬화·응답 파서(EVAL/WINRATE/BESTLINE/FORBID/
  REALTIME)·`SearchAggregator`(PV 블록 조립·승률 흑 기준 변환).

> ⚠ **좌표 재검증 필수**: P2에서 좌표를 `y,x`로 바꿨다. 실서버 왕복으로 착수/PV가
> PC GUI와 같은 자리에 찍히는지 반드시 확인할 것(계획 §2.5).

## P3 (구현됨) — 랭킹 대시보드

`Rankings` 탭 = 상단 2탭(3수/5수) + 공용 필터 바텀시트.

- **번들 데이터**: `assets/rank5.db.gz`(gzip 4.1MB → 해제 17MB, 206,470행) —
  이론 5수 전수 랭킹. **순수 계산(RenjuNet 무관) → 번들 가능**. 첫 실행 시
  내부 저장소로 해제·복사, 읽기 전용 SQLite로 오픈(`Rank5Database`, Room 미사용:
  읽기 전용·마이그레이션 없음, prepackaged 식별해시 함정 회피).
- **26주형(정적)**: `Opening26` — 이름(한/로마자/약칭)·대표 모양·直/間·`classify()`
  분류기를 `mo_opening26`/`freq35.py`에서 순수 포팅. 데이터 없이도 항상 표시.
- **실전 데이터(freq)**: `freq_data.json`은 **RenjuNet 파생 → 번들 금지**. 사용자가
  SAF(문서 선택)로 **기기에 반입**(`FreqStore`, 영구 권한 취득·재시작 시 자동 로드).
  절대 내보내지/공유하지 않음.
- **분석 엔진**: `FreqAnalyzer`(순수) = `freq35.rankings` 포팅. 선수/룰 필터 →
  3수 26주형 승·무·패 집계 / 5수 실전 빈도 랭킹 / 이론순위별 실전수.
- **UI**: 26주형 카드 그리드(미니보드+3색 결과막대), 5수 리스트(이론순/실전순 전환,
  9×9/7×7 범위, 수순 검색, 경우의 수 그룹 32/16/8/4 분포 막대), 선수 자동완성·룰 칩.

> 주형 라벨은 **rank5.db의 권위값**을 사용(3수=게임 o3, 5수=rank5 opening). 한 5수
> 모양은 여러 착수 순서로 도달 가능하므로 `classify(대표수순)`은 대표게임 기준일 뿐 —
> 미매칭(비표준) 모양의 폴백에만 사용.

라이선스: rank5만 번들, freq는 사용자 반입·비공유(§라이선스 주의 그대로).

## 빌드 & 검증 (Android Studio)

빌드/검증 절차는 `../test-yixin/docs/안드로이드_앱_아키텍처_계획.md` §11 에 정리.
요약: ① Android Studio에서 폴더 열기 → Gradle sync → ▶ Run, ② P3는 서버 없이
바로 `Rankings` 탭에서 이론 랭킹 확인, ③ freq 임포트로 실전 빈도 확인,
④ (P1/P2) Tailscale on + `Connect` 탭으로 실서버 왕복·좌표(y,x) 검증.

## 다음 (P4/P5)

P4 오프닝 익스플로러(PackReader + 국면키 교차검증), P5 수순 탐색기(MoVarSet 포팅).
그리고 실서버 캡처로 realtime INFO 문법 최종 확인 + 워치독·자동 재연결.
