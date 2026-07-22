# YixinDroid

PC Yixin-Board GUI를 안드로이드로 옮기는 프로젝트. 전체 설계는
`../test-yixin/docs/안드로이드_앱_아키텍처_계획.md` 참고.

이 저장소는 현재 **P0(스캐폴드) + P1(엔진 통신 모듈)** 까지 구현되어 있다.

> ⚠ 이 코드는 **Android SDK가 없는 환경에서 작성**되어 여기서 컴파일 검증은 하지
> 못했다. Android Studio(Ladybug 이상, JDK 17)에서 열어 Gradle sync 후 빌드하는
> 것을 전제로 정확하게 작성했다. 첫 sync 시 Studio가 Gradle 래퍼(jar/gradlew)를
> 채워준다(또는 `gradle wrapper --gradle-version 8.9`).

## 무엇이 들어있나 (P1 범위)

- **엔진 통신 모듈** — engine.exe(투명 TCP 릴레이)를 대체. `rapfi-server`
  (`100.111.248.44:7669`)에 직접 접속해 piskvork를 그대로 주고받는다.
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
2. 앱 실행 → **연결** 탭 → host `100.111.248.44`(프리필), port `7669` → **연결**.
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

## 다음 (P2)

15×15 보드 Composable + 분석 스트림(평가바·멀티PV·금수), 실서버 캡처 기반
`SearchInfo` 파싱, 워치독·자동 재연결.
