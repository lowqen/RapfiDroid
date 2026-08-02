# YixinDroid

PC Yixin-Board GUI를 안드로이드로 옮기는 프로젝트. 전체 설계·로드맵·검증 절차는
`../test-yixin/docs/YixinDroid_이식_계획.md` 참고(단일 진원).

이 저장소는 현재 **P0(스캐폴드) + P1(엔진 통신 모듈) + P2(보드 UI·분석·파서 확장)
+ P3(3수/5수 랭킹 대시보드) + P6(분석 표시 완성) + P4(설정 67개) + P7(데이터베이스 전체)
+ 보드 툴바 + P5(대국 기능) + P8(기보 I/O·리뷰·리포트·큐) + P8b(국면 증명)
+ P9(오프닝 익스플로러·수순 탐색기) + P10(엔진 운용 도구·콘솔 명령어)
+ P11(외관·입출력·안정화)** 까지 구현되어 있다 — 계획한 단계를 전부 마쳤다.

> 목표였던 **Yixin.exe의 모든 설정·기능 이식**은 P11로 마무리됐다. 남은 것은
> 기기·실서버에서의 사용자 확인(계획서 §6)이다.

**P4 핵심**: 데스크톱이 쓰는 `settings.txt`(47줄) + `settings_dev.txt`(20줄) = **67개
설정 전부**를 `DesktopSettings` 선언표 하나로 모델링한다. 표의 위치가 곧 줄 번호이고,
그 표가 파일 코덱·설정 화면·`INFO` 전송을 모두 구동하므로 어느 한쪽만 빠질 수 없다.
설정 화면에서 PC의 두 파일을 **그대로 불러오기/내보내기**할 수 있다(SAF).
단위 함정 3건은 `AppSettings.toEngineParams()`가 한곳에서 흡수한다 —
시간 제한은 파일이 초·엔진이 ms, 수당 증가는 양쪽 ms, 해시는 MB→KB(`<<10`).

**P6 핵심**: 엔진 핸드셰이크에 `info show_detail 3` + `yxshowinfo`가 없으면 Rapfi가
`INFO PV/DEPTH/EVAL/WINRATE/BESTLINE`을 보내지 않아 분석이 화면에 전혀 안 나온다.
또 `INFO rule`·`thread_num`·`hash_size` 등을 보내지 않으면 엔진이 자기 config
기본값(프리스타일)으로 돌아 **PC Yixin과 결과가 달라진다** — `EngineParams`가
`test-yixin/settings.txt` 기본값(자유 렌주·4스레드·8192MB·멀티PV 3)을 그대로 전송한다.

> P0~P4는 Android SDK가 없는 환경에서 작성돼 컴파일 검증 없이 커밋됐다. **P7부터는
> 로컬 SDK + Android Studio JBR로 `testDebugUnitTest`·`assembleDebug`를 실제로 돌려
> 확인한다**(현재 478 테스트 통과, APK 빌드 성공). 기기에서의 화면·실서버 동작 확인은
> 여전히 사용자 몫이다.

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
- **앱 셸** — Material 3 테마(다크/라이트는 설정 27행), 하단 6탭 내비게이션
  (보드/익스플로러/랭킹/DB/설정/연결).
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
  **RenjuNet 파생 → 재배포·스토어 배포 금지**. P3에서 사용자 기기 반입(SAF)으로 구현했고
  앱은 그 데이터를 절대 내보내지 않는다(설정 내보내기는 `settings*.txt`뿐).

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

- **번들 데이터**: `assets/rank5.db.bin`(gzip 4.1MB → 해제 18MB, 206,470행; AGP가
  `*.gz`를 자동 해제해 버리므로 중립 확장자) —
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

## 빌드 & 검증

빌드/검증 절차는 `../test-yixin/docs/YixinDroid_이식_계획.md` §6 에 정리. 커밋 전
로컬 확인은 명령줄로 끝난다:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --offline testDebugUnitTest assembleDebug
```

요약: ① Android Studio에서 폴더 열기 → Gradle sync → ▶ Run, ② P3는 서버 없이
바로 `Rankings` 탭에서 이론 랭킹 확인, ③ freq 임포트로 실전 빈도 확인,
④ (P1/P2) Tailscale on + `Connect` 탭으로 실서버 왕복·좌표(y,x) 검증.

## P4 (구현됨) — 설정 67개

- **선언표**: `core/model/DesktopSettings` = 47 + 20 항목. `line = 표 위치`,
  `build()`가 47/20을 `require`로 검증. 항목마다 파일·줄·주석(원문 그대로)·라벨·
  카테고리·편집기·`INFO` 키·주의사항을 갖는다.
- **모델**: `AppSettings`(@Serializable, 67 필드, 선언 순서 = 파일 순서),
  `toEngineParams()`가 단위 변환과 규칙 환원(오프닝 룰 3~6 → 엔진 0/1/2)을 담당.
- **코덱**: `domain/settings/SettingsCodec` — 데스크톱 텍스트 왕복. 위치 기반 파싱을
  그대로 재현하고 **예약 슬롯(dev 9행)·일회성 표시(dev 20행)도 읽고 그대로 쓴다**.
- **영속**: `SettingsStore`(DataStore, JSON 한 덩어리 → 원자적 갱신·마이그레이션 불필요),
  `SettingsRepository`가 앱 전체의 단일 진원.
- **엔진 반영**: 설정이 바뀌면 **달라진 `INFO`만** 재전송, 규칙·보드크기 변경 시
  핸드셰이크 재수행. `usedatabase`/`database_readonly`/`nbestsym` 추가,
  `hash autoclear`는 탐색 직전 `yxhashclear`. 엔진이 알려주는
  `MAX_THREAD_NUM`/`MAX_HASH_SIZE`로 스레드·해시 상한을 조인다.
- **화면**: `feature/settings` — 검색·카테고리 필터·행마다 출처(`settings.txt 37행`,
  `INFO thread_num`)·PC 기본값과 다른 항목 표시, SAF로 두 파일 불러오기/내보내기.
- **테스트**: `SettingsCodecTest`(14) — 배포된 두 파일을 바이트 단위로 임베드해 고정.

## P7 (구현됨) — 데이터베이스 전체

`DB` 탭 + 보드 화면 DB 패널. 데이터베이스는 **원격 엔진 옆의 `rapfi.db`** 이므로 모든
연산이 소켓 명령이고, 파일 경로는 **서버 경로**다.

- **명령**: `DbPositionCommand`(head + `y,x` 줄들 + `done`)로 위치 기반 명령 전부 —
  조회(`yxquerydatabaseallt/one/text`), 편집(`yxedittextdatabase`·`yxeditlabeldatabase`·
  `yxedittvddatabase 1/2/4`·최선수 표시), 삭제(`yxdeletedatabaseone`,
  `yxdeletedatabaseall` 12변형). 파일 연산은 head + 경로(`yxsetdatabase`·`yxdbmerge`·
  `yxdbsplit`·`yxlibtodb`·`yxdbtolib`·`yxdbtotxt(all)`·`yxtxttodb`·`yxdbtopos`),
  단일 줄은 `yxsavedatabase`·`yxdbcheck`·`yxdbfix`.
- **셀 값**: `boardtag`의 1~4자 big-endian 패킹 int를 디코딩(`w39`→`W39`, 수순 0 → `W*`),
  `showBoardText`가 켜지면 사용자 라벨 우선. 분석 중에는 분석 태그가 이긴다.
- **국면 값**: `evalbar_update_from_db` 포팅 — 최선 자식값 = 국면값. 엔진 값이 없을 때
  평가바를 이 값으로 채운다.
- **조회 페어링**: `dbqueryseq`/`dbdoneseq` 그대로 — 늦게 도착한 이전 국면의 응답이
  국면값(메이트 부호)을 뒤집지 못한다.
- **자동 저장**: 엔진이 Ready(유휴)일 때만, 1분 틱으로 간격을 세어 즉시 반영.
- **가드**: 미연결 / DB 꺼짐 / 읽기 전용은 클라이언트에서 거부하고 이유를 표시한다.
  **일괄 삭제·분할은 기본 잠금**(DB 화면 스위치로 해제), 삭제 확인은 settings.txt 35행.
- **테스트**: `DatabaseProtocolTest`(31) + `DatabaseRepositoryTest`(11).

## 보드 툴바 (구현됨) — 2026-07-27

보드 아래 아이콘 툴바. 버튼 하나 = 데스크톱 콘솔 명령 하나(`main.c execute_command`).

- **수순 이동** « ‹ › » = `undo all`·`undo one`·`redo one`·`redo all`. 데스크톱처럼
  전체 기보를 들고 커서만 옮기므로 **되돌린 수가 살아 있고**, 저장돼 있던 그 수를 다시
  놓으면 뒤 수순도 유지된다(main.c:2182). 규칙은 `core/model/MoveCursor`에 분리·테스트.
- **엔진** ▶/■ = `thinking start/stop`. ■는 균형점 탐색도 `YXSTOP`으로 끊는다.
- **균형점** ⚖ = `balance1 [n]` / `balance2 [n]` → `yxbalanceone|two <bias>`. 답으로 온
  좌표(두 수면 둘 다)를 그대로 판에 놓고, 그 사이 국면이 바뀌었으면 버린다.
- **모양 대칭** ⇄ = `rotate 90/180/270` + `flip`. 단 `flip /`는 데스크톱에서 사실상
  180° 회전이라, 앱은 **진짜 역대각 대칭**을 넣어 8개 대칭을 모두 낸다(나머지는 동일).
- **수 이동** ✥ = `move ^v<>`. 판 밖으로 나가는 수가 하나라도 있으면 전부 이동하지 않는다.
- **이미지 저장** 🖼 = 1440px PNG를 SAF로 저장(권한 불필요). 화면과 같은 프레임을
  `drawBoard`로 오프스크린 렌더.
- **국면 문자열** ⓘ = `getpos`/`putpos` + 클립보드 왕복(`h8i9…`, 두 자리 행 그리디 파싱).
- **보드 확대**: 보드만 full-bleed + 좌표 여백 1칸→0.75칸 + 승률 그래프를 툴바 아래로.
  여백을 줄였으므로 **그리기와 탭 판정이 `BoardGeometry` 하나**를 공유하고 전 교점 왕복을
  테스트한다. 획 두께는 `step` 비례(PNG 확대 시 선이 실 같아지지 않게).
- **테스트**: `BoardTransformTest`·`MoveCursorTest`·`BoardGeometryTest` +
  `EngineCommandTest`의 balance 2건 → 전체 **125개** 통과.

## P5 (구현됨) — 대국 기능 — 2026-07-28

데스크톱의 대국 로직 세 곳(`on_button_press_windowmain` = 탭의 의미,
`iochannelout_watch` = 엔진 응답 적용, 스왑 대화상자 4종)을 `GameRepository`
**하나**(`@Singleton`)로 모았다. 탭 이동 시 `BoardViewModel`이 정리되므로 보드·수순·
금수·대국 상태의 소유권을 화면 밖에 두어야 진행 중인 판이 살아남는다.

- **승패 판정** `Referee` — 마지막 수에서 4축을 세어 `k == 5 || (k > 5 && rule != 1)`.
  **장목은 표준 오목에서만 무효**이고, 렌주의 장목 금지는 금수(`FORBID`)로 따로 온다.
  판이 꽉 차면 무승부.
- **규칙 7종** — 프리스타일/표준/자유 렌주는 바로 두고, 오프닝 규칙은 각각:
  RIF(3수까지 사람이 직접) · Swap2(`yxswap2step1~3` + 3지선다) ·
  Soosorv-8(`yxsoosorvstep1~6`, `MOVE4`의 N만큼 5수 후보를 놓아야 진행) ·
  첫 수 교환(메뉴에 없고 `settings.txt`로만 켜지는 것까지 동일).
  **`MESSAGE SWAP2/SOOSORV`의 좌표만 공백 구분 `y x`**라 파서가 분기한다.
- **시계** `GameClock` — 타이머 4개(컴퓨터/사람 × 턴/매치) + 증분 2개. 엔진에 보내는
  값은 `timeoutmatch - timercomputermatch + timercomputerincrement`(진행 중인 턴은
  빼지 않는다). 표시는 `showClock`(설정 28행), 시간 초과 경고는 `checkTimeout`(31행).
- **컴퓨터 담당·무승부·기권·금수 토글** — 보드 아래 대국 패널. 담당 전환은 설정
  4·5행과 같은 비트마스크이고, 스왑이 성립하면 그 자리에서 뒤집힌다.
- 데스크톱이 모달로 막는 자리는 전부 `GamePrompt` 상태로 올려 비동기로 처리한다
  (색 교체 시점이 모달 종료→프롬프트 표시로 한 칸 앞당겨진 것이 유일한 의도적 차이).
- **범위 밖**: Yamaguchi·Handicap은 데스크톱에도 코드가 없다(문자열 자원만 존재).
- **테스트**: `RefereeTest`(11)·`GameClockTest`(9)·`GameProtocolTest`(14)·
  `GameRepositoryTest`(16, 가짜 엔진에 실제 파서를 물려 서버 라인 그대로 주입)
  → 전체 **175개** 통과.

## P8 (구현됨) — 기보 I/O · 게임 리뷰 · 리포트 · 분석 큐 — 2026-07-28

하단 탭에 **리뷰**가 생겼다. 리뷰는 `ReviewRepository`(싱글턴)가 돌리고 화면은 상태만
그린다 — 리뷰는 화면보다 오래 살아야 하기 때문이다.

- **기보 파일** `GameFile` — `.psq`(`열,행,시간` 1-based, `-1` 종료) · `.sav`(크기·크기·수·
  `행 열`) · `.pos`(수 바이트 + `열*15+행`). 데스크톱 `load_game_file`과 같은 관용도로,
  판 밖이나 중복된 수가 나오면 거기서 줄이 끝난다. 저장은 `.sav`(이름이 `.psq`면 `.psq`).
- **수 등급** `MoveGrader` — `classify_moves` 전체 이식: 10등급, 프리셋 3종(0.6/1.0/1.5배),
  명수·훌륭한 수 판정(2순위와의 격차), 놓친 승리, **필패 국면의 저항력 등급**
  (`kept = -ma-(-mb-1)`), 코멘트, 정확도 `100·exp(-dWR·100/40)`.
- **리뷰 파이프라인** — 국면마다 `INFO time_left` → `START` → `YXBOARD` → **`YXNBEST 2`**.
  PV0이 국면 값과 최선수, PV0−PV1 격차가 "유일한 수" 신호다. 예산은 초/고정 깊이 2종이고
  끝나면 `set_level`로 사용자 한계를 되돌린다. 이미 승부가 난 국면은 탐색 없이 1.0/0.0,
  오프닝 1~5수는 건너뛴다(settings_dev 19행). 워치독은 `yxstop` 후 같은 국면을 재전송하고
  그때 나오는 최선수는 `isneedomit`과 같은 카운터로 삼킨다.
- **리포트** — 정확도·등급 분포·최악의 수·수순 표(등급/dWR/승률/최선/코멘트). 행을 누르면
  보드가 그 국면으로 간다. 내보내기는 CSV(영문 고정) · MD(한국어) · **HTML**: 데스크톱
  페이지의 정적 셸을 `assets/report_{head,body,tail}.html`로 추출해 같은 `GAME` 객체를
  주입하므로 PC와 같은 페이지가 나온다.
- **분석 큐** — 여러 기보를 차례로 리뷰하고, 못 읽거나 빈 파일은 표시 후 건너뛴다.
- **배지** — 리뷰가 끝나면 돌 오른쪽 위에 등급 배지(settings_dev 4행). 보드의 수순이
  리뷰한 줄의 접두일 때만 붙는다.
- 데스크톱과 다른 점 2가지(의도적): ① 리뷰가 **보드를 되감지 않고** 자체 사본으로 돈다
  ② 자동 저장 위치가 앱 전용 외부 폴더(`Android/data/…/files/reports`)다.
- **테스트**: `GameFileTest`(13)·`MoveQualityTest`(22)·`ReportFormatsTest`(9)·
  `ReviewRepositoryTest`(15) → 전체 **233개** 통과(P8 시점).

## P8b (구현됨) — 국면 증명 (Prove) — 2026-07-30

«리뷰» 탭에 **국면 증명** 카드가 생겼다(데스크톱도 Analysis 메뉴에서 게임 리뷰 옆에 있다).
차례인 쪽이 이기는지를 AND/OR 탐색으로 증명하고, 결론(메이트)을 **엔진의 데이터베이스에
기록**한다 — 이 쓰기 때문에 P8에서 분리한 단계다.

- **순수 탐색기** `ProveTree` — 노드 관리·`prove_cost` 우선순위·`prove_propagate`·
  `prove_or_widen`·`prove_expand`·기록 큐. 코루틴도 IO도 없어서 탐색 순서와 결론 전파를
  엔진 없이 전부 테스트할 수 있다. 잘못된 전파는 곧 잘못된 DB 레코드이기 때문이다.
- **엔진 대화** `ProveRepositoryImpl` — 한 번에 한 명령만 미결로 둔다:
  `yxquerydatabaseone` → (레이블이 있으면 그대로 결론 / 없으면 `yxnbest k`(공격) ·
  `yxsearchdefend`(방어)) → 결론이 나오면 `yxedittvddatabase 7` 로 플러시 → 다음 노드.
- **DB 레이블 관점** — yixindb 레코드는 **그 국면으로 들어온 쪽** 기준이다. 그래서 차례인
  쪽이 이기는 국면은 `L`(76), 지는 국면은 `W`(87)로 쓴다. 읽을 때 main.c는 **소문자**
  `l`/`w`/`d`만 인식하고 나머지는 "기록 없음"으로 보아 탐색한다 — 비대칭이지만 그대로
  이식했다(대소문자를 임의로 넓히면 PC는 탐색하는데 앱은 DB를 믿어 결론이 갈릴 수 있다).
- **예산·우선순위** — 노드마다 초 또는 고정 깊이로 시작해 실패하면 2배(깊이는 +2), 상한에
  닿으면 그 노드를 포기하고 다음 공격 후보를 꺼낸다. PV에 보이는 메이트가 최우선(짧은 것부터),
  그다음 공격 승률이 높은 가지, **성립할 것 같은 방어는 조기 탐침 1회**(반증되면 그 가지의
  쉬운 증명이 전부 헛일이 되므로). 방어 승률 ≤2 %는 보류하고 나머지가 전부 패배로 판명된
  뒤 한 번에 검증한다(낙관적 가지치기).
- **보드 오버레이** — 탐색 중인 수순을 반투명 돌 + 플라이 번호 + 공격(주황)/방어(파랑) 링으로,
  루트 후보를 상태 마커(✓ 승 / ✗ 패 / ! 포기 / 남은 예산 / 대기)로 그린다. 보드 위 2줄
  배지는 `prove_badge_lines` 그대로.
- **보드 잠금** — 리뷰·증명이 도는 동안 보드 탭·수순 이동·초기화·분석 시작·균형점·대칭/이동을
  거부한다(main.c 2661·4524와 같다). 없으면 탭 한 번이 실행 중인 대화 사이에 `TURN`을
  끼워 넣는다. P8에서 빠져 있던 부분을 여기서 함께 채웠다.
- 데스크톱과 다른 점: ① 보드를 되감지 않고 증명 전용 사본으로 돈다 ② main.c는 최선수가 오면
  단계 확인 **전에** 워치독을 끄기 때문에 조회/기록 중 엉뚱한 최선수가 오면 멈추는데, 앱은
  그 응답을 기대하는 단계에서만 처리한다 ③ 초점 돌 깜빡임(0.5초 전체 재그리기) 대신 굵은 링.
- **테스트**: `ProveTreeTest`(44)·`ProveRepositoryTest`(20).

## P9 (구현됨) — 연구 도구: 오프닝 익스플로러 · 수순 탐색기 — 2026-07-31

데스크톱 분석 메뉴의 두 창을 **익스플로러 탭** 하나에 「오프닝」/「수순」으로 넣었다.
짝 도구다 — 익스플로러는 수순이 달라도 같은 배치면 합산하고, 수순 탐색기는 그 합쳐진
것을 되펼친다. 둘 다 보드를 따라가기만 하므로 시작·중지가 없다.

- **국면 키 = 4번째 구현** (`core/model/PosKey.kt`). 나머지 셋은 `main.c web_poskey()`,
  `Yixin-Board/tests/test_webkey.c`, `rifdb/rifkey.py`. `rifdb/rif_crosscheck.py`가
  앞의 둘을 대조해 합의한 값만 골든 벡터(`app/src/test/resources/poskey_golden.txt`,
  248케이스)에 쓰고, `PosKeyTest`가 그것을 읽는다 — 골든은 한 부뿐이고 스크립트가
  유일한 생산자라 사본이 어긋날 수 없다. 키뿐 아니라 **채택 대칭 t**도 대조한다:
  팩의 다음 수는 t로 보드 좌표로 되돌리므로, 키가 같아도 t가 다르면 통계는 맞는데
  엉뚱한 자리에 그려진다. 구현을 고쳤으면 `python rif_crosscheck.py --emit` 후 재실행.
- **수순 탐색기** (`core/model/MoveOrder.kt`, `moveorder.h` 포팅): 수순은 `b!·w!`로
  폭발해도 DAG 노드는 폭발하지 않는다 — 나열하지 않고 한 플라이씩 드릴다운한다.
  D4 8변형을 국면으로 중복 제거해 병합(환원)하고, 노드 예산은 데스크톱과 **같은
  300,000**을 쓴다(예산이 다르면 "정확한 집계 생략" 시점이 갈려 PC와 숫자가 어긋난다).
- **팩은 사용자 반입 전용**(RenjuNet 라이선스 = 비상업·오프라인). 두 파일을 한 번에
  고르면 매직으로 종류를 판별하고, 앱 전용 저장소로 복사한 뒤 mmap한다(33MB를 힙에
  올리지 않기 위해 — 데스크톱 `g_mapped_file_new`와 같은 이유). 번들·내보내기 없음.
- **테스트**: `PosKeyTest`(12)·`MoveOrderTest`(38)·`RjPacksTest`(15)·
  `ExplorerLookupTest`(12) → 전체 **375개** 통과. 수순 탐색기는 데스크톱 테스트와 같이
  **규칙을 독립 재구현한 브루트포스와 대조**한다(경우의 수는 눈으로 검증 불가).

## P10 (구현됨) — 엔진 운용 도구 · 콘솔 명령어 — 2026-07-31

데스크톱에 "엔진 운용 메뉴" 같은 건 없다. 실제로는 **콘솔 명령어 언어 하나**가 로그 창
입력줄, 커스텀 툴바 36개, 핫키 6개, 콜백 스크립트를 전부 구동한다 —
`function/toolbar*.txt` 자체가 명령 스크립트 파일이다. 그래서 버튼을 나열하는 대신
**언어를 파서로 이식**했다(`core/model/ConsoleCommand.kt`, 명령 44종). 화면의 버튼도
결국 같은 스크립트를 실행하므로 경로가 하나뿐이다.

- **접두 일치 순서가 곧 의미다**: `blockpath *` 는 `block *` 보다, `forbid undo` 는
  `forbid` 보다 먼저 시험해야 한다. 어기면 `blockpath h8h7` 이 `block` 으로 먹혀
  **조용히 엉뚱한 점을 차단**한다.
- **`bench`/`traceboard`/`tracesearch`/`reloadconfig` 는 콘솔 명령이 아니다** — 엔진
  명령이고, 데스크톱은 `command on` → 명령 → `command off` 로 감싼다. 앱도 같은
  스크립트를 쓴다(`ToolScripts`). **평가 모드 전환도 이 방식**이다
  (`reloadconfig config.toml` / `config_classical.toml`).
- 해시(지우기·사용량·저장/복원 — 경로는 **서버 경로**), 차단 `block`/`blockpath`(보드에 ✕),
  강제 금수 `yxforbid`(**좌표가 인라인**인 유일한 위치 명령), 국면 슬롯 10칸,
  `search from`, `searchdefend`, `nbest`, 균형점.
- `hash autoclear`/`block autoreset`/`blockpath autoreset` 은 이미 `settings.txt`
  25/32/33행이라 **P4 `AppSettings` 에 쓴다**(사본을 만들지 않는다). 자동 해제는
  데스크톱과 같이 **엔진의 착수가 실제로 도착했을 때만** 발화한다.
- **콜백**은 기존 코드를 건드리지 않고 관찰자로 붙였다: `INFO PV … DONE` 의 mate/승률로
  승·패·무(50%가 N회 연속)를 판정하고, 착수는 수번호로 세 스크립트 중 하나를 고른다.
- **테스트**: `ConsoleCommandTest`(21)·`BlockTargetsTest`(5) + `EngineCommandTest` 도구
  직렬화 5종 → 전체 **420개** 통과.

## 중간 점검에서 고친 것들

- **배지**: 데스크톱의 `wrhistory` 를 쓰는 함수는 `evalbar_set_black_winrate` 하나뿐이고
  호출자는 **엔진 응답과 DB 응답 둘**이다 — 리뷰 없이도 값이 도착하면 배지가 뜬다.
  앱도 수마다 `PositionRecord` 를 두고 양쪽이 채우며, **현재 수만** 그린다(main.c:2114).
- **팩 반입**: 리더는 문제가 없었다(배포된 실물 팩을 읽는 `RealPackSmokeTest` 로 확인).
  고른 문서를 **앞 4바이트만 훔쳐봐서** 판별한 것이 원인 — 콘텐츠 스트림의 짧은 읽기가
  "잘못된 파일"과 구분되지 않는다. 지금은 **복사한 뒤 팩으로 열어** 판별하고, 거부할 땐
  본 크기와 머리글을 알려 준다.
- **증명 표시**: 배지를 **보드 위**(왼쪽 위, PC와 같은 주황/파랑)로 옮기고 초점 돌을
  0.5초 heartbeat 로 깜박이게 했다. 증명 카드도 자기 보드를 띄운다 — 연구 탭에는
  보드가 없어서 "아무 일도 안 일어나는 것"처럼 보였다.
- **설정**: «고급 설정» 스위치. 평소 쓰는 27개만 기본 노출하고, 숨긴 항목도 검색하면
  나오며 파일 왕복은 67줄 전부 그대로다.
- **하단 탭 7 → 5**: 보드·연구·DB·엔진·설정. 리뷰/오프닝/수순/랭킹은 데스크톱이
  분석 메뉴 하나로 묶는 같은 활동이라 「연구」 탭의 서브 탭이 됐다.
- **랭킹 스크롤**: 정렬·필터를 바꾸면 목록이 중간에 멈춰 있었다. lazy 목록은 화면에
  있던 항목을 **키로 다시 찾아** 그 자리로 스크롤하고, 5수 탭은 키가 아예 사라져
  raw index 로 되돌아간다. 이제 내용이 바뀌면 맨 위로 되돌린다.

## P11 — 외관·입출력·안정화 (완료)

- **끊긴 링크가 세션의 끝이 아니게 했다.** engine.exe 는 같은 기계의 자식이지만 여기서는
  자는 라디오와 idle 흐름을 잊는 NAT 을 건너는 VPN 소켓이다. `LinkHealth` + 백오프
  재연결(2/4/8/16/32/60초), **사용자 의사로 게이트**(«연결 해제» 는 계속 해제),
  150초 침묵 뒤 `ABOUT` 생존 확인(콘솔에 안 찍는다), `SO_KEEPALIVE`.
  포그라운드 서비스는 **재연결 중이면 살아 있다** — 백오프 도중 내리면 지키려는 세션을
  스스로 끝낸다.
- **`function/` · `language/`**: `toolbar<n>.txt` / `hotkey<n>.txt` 를 main.c 의
  `fscanf`+`fgets` 그대로 읽고, 스크립트는 P10 명령어 언어로 실행한다. `.lng` 표는 앱 UI
  번역용이 아니라 **툴바가 저장하는 숫자 라벨 id** 용이다. SAF 로 Yixin 폴더 한 번.
- **핫키**: 키 입력 → 데스크톱 **콤보박스 위치**(파일이 저장하는 값). 액티비티에서 처리.
- **폰트 4종**: PC 글꼴 가족은 기기에 없다. 사용자가 조정한 것은 크기이고 그건 옮겨진다.
- **디버그 로그**: 값만 있고 아무것도 기록되지 않던 설정 — 이제 파일로 남고 내보낼 수 있다.
- **가로·태블릿**: 보드는 정사각형이라 한 열로는 양쪽을 낭비한다. 640dp 이상이면 보드와
  패널을 갈라 각각 스크롤한다.
- **스킨은 이식 대상이 아니었다**: 이 devbuild 의 스킨은 `piece.bmp`/`piece_dark.bmp`
  두 장뿐이고 다크 모드로만 갈린다(main.c:152/14330). 선택 설정이 없다.
- 결과적으로 **선언만 되고 아무 일도 하지 않던 설정이 사라졌다** — 남은 미사용 필드는
  예약 슬롯 2개뿐이다.

## 보드 화면 정리 (P11 후속)

데스크톱 배치를 그대로 옮긴 자리가 엄지와 6인치 화면에서 어긋난 곳들.

- **보드 밑 사용자 툴바가 보드 컨트롤과 중복이었다.** 데스크톱 툴바 기본값 6개는 곧
  이동·탐색인데, 앱에서는 그 6개가 이미 보드 밑 아이콘이다.
  `FunctionScripts.isBoardRowDuplicate()` 로 **보드 줄이 이미 하는 스크립트는 빼고**
  보여준다 — 기본값만 가져오면 줄 자체가 없고, `nbest 8` 같은 건 남는다.
- **Defend 를 보드에 올렸다.** `searchdefend` 는 `nbest` 와 시작 명령 한 줄만 다른
  분석이라(main.c:10883) `AnalyzeParams.defend` 하나로 끝난다 — 응답·태그·정지가 그대로다.
- **값 색을 파스텔로.** 채도 설정(39~43행)은 남기되 파스텔 대역의 **세기**로 쓴다. 판에
  값이 백 칸 깔리면 완전 채도는 노이즈가 되고 흰 글자를 잡아먹는다. 옅은 면 + 같은
  색상의 짙은 잉크 + 테두리 링(거의 흰 «호각» 칩을 나무판에서 떼어내는 건 그 링이다).
- **항상 보이는 줄에는 국면을 읽는 동안 누르는 것만**: 이동 4개와 탐색 토글 하나(가운데,
  제일 큼 — 데스크톱 기본 핫키도 F1 = `thinking toggle` 이고 시작/정지 쌍은 한쪽이 늘
  죽어 있다). 나머지는 «⋯» 뒤에 **글자로** 둔다. 분석 결과가 데이터베이스·대국보다
  위로 올라오고, 그 둘은 한 줄 요약을 단 접이식 패널이 됐다.

## 정지가 수를 두고, DB 값이 제때 보인다

두 증상의 원인은 하나였다 — **이 포트는 탐색이 언제 끝나는지 몰랐다.**

- `YXSTOP` 은 요청이고, 탐색이 끝난 시점은 엔진이 `y,x` 한 줄로 답할 때다(main.c:13930).
  정지하면서 분석 플로를 먼저 접었기 때문에 그 답을 버렸다. 이제 정지는 `yxstop` 만 보내고
  답이 오면 **그때까지의 최선수를 판에 둔다**(main.c:13957). 5초 무응답이면 UI 만 풀어 준다.
- 그래서 `isneedomit` 이 필요해졌다(main.c:4557): 분석 중 수를 두면 옛 탐색이 **떠나온
  국면의 수**로 답한다 — 그 하나는 삼킨다.
- **DB 질의는 탐색 중에 보내지 않는다.** Rapfi 는 탐색이 끝나야 답하므로 값이 한참 뒤에
  도착했다. `Thinking` 이면 미루고 엔진이 `Ready` 로 돌아오는 순간 보낸다. 탐색은 DB 에
  쓰기도 하므로 이 재질의는 필수다(main.c:13961).
- **분석 태그가 DB 값을 덮고 있었다.** 데스크톱은 `boardtag` 하나를 돌려쓰고
  `show_database()` 가 분석 %를 DB 값으로 덮어쓴다. 여기서는 두 map 이라 분석 쪽이 이겨
  **방금 분석한 점에서만** 값이 안 보였다. 분석 오버레이는 이제 탐색 중에만 그린다.
- `DbQueryPairing`: DONE 이 하나 유실되면 국면값이 영영 얼어붙던 카운터에 **응답 시한**을
  뒀다.

## 증명 깊이 예산 · 한국어와 영어

- **depth 는 OR 노드에서만 예산이다.** `yxnbest k` 의 k 는 막혀 있지만 AND 노드의
  `yxsearchdefend` 는 **모든 방어수**를 그 깊이로 평가하므로 비용이 방어수 개수만큼 곱해진다.
  시계를 열어 두면 워치독까지 가고, 워치독에 걸린 노드는 답을 받는 게 아니라 **버려진다**.
  깊이 모드도 초 상한을 리시로 빌려 두 모드가 같은 원리 위에 선다(main.c:9484).
- **언어는 한국어·영어 둘뿐.** 확인해 보니 앱은 한국어 전용이었고, APK 는 AndroidX 의
  모든 로케일을 싣고 있었으며, 툴바 라벨은 PC 설정이 가리키는 `.lng` 라 중국어·일본어까지
  들어올 수 있었다. `localeFilters` + `locales_config.xml` 로 둘만 선언하고, **보이는 화면
  652개 문자열**에 영어를 붙였다.
- 방식은 `tr(ko, en)` — main.c 의 `DT("English")` 와 같다. 번역을 쓰는 자리에서 하고 영어를
  소스에 남기므로, 쌍이 어긋날 수 없고 공유 용어는 `language/0.lng` 에서 가져와 PC 와 맞는다.
  플랫폼이 직접 읽는 것(런처 라벨·알림 채널)만 `strings.xml`.
- 로그·콘솔과 내보낸 리뷰 리포트는 한국어로 남는다 — 리포트는 PC 의 문서 형식 그대로다.
