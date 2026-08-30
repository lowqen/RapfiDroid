## RapfiDroid 0.14.0

**The first public release.** A gomoku and renju board with the
[Rapfi](https://github.com/dhbloo/rapfi) engine compiled for arm64 Android and
running as a real process inside the app. Play, analyse, review and study with
the phone in airplane mode. There is no server, no account, and nothing listening
on a port.

### Highlights

* **The engine is on your phone.** Freestyle, standard and renju, each with its
  own neural network, all four networks bundled so nothing is downloaded on first
  run. Live principal variation, depth, evaluation bar and win rate.
* **Renju forbidden points judged by the engine itself.** Double three, double
  four and overline, applied by the same code that plays, not by an
  approximation in the interface.
* **A position database that stays on the device.** The engine writes what it
  learns to `rapfi.db` and picks it up next session. The app sends the save
  command before shutting the engine down, so a session is never lost to a forced
  kill.
* **The desktop study environment, ported.** Game review, opening explorer with
  249 named openings and an eleven grade evaluation of Black's fifth move across
  1,889 shapes, the 26 classical opening rankings, position proving, a move
  sequence explorer, an engine console, and all 67
  [Yixin-Board](https://github.com/dhbloo/Yixin-Board) settings driving the engine
  exactly as they do on a PC.
* **Server mode is still available and off by default.** It lives behind an
  advanced switch for the cases a phone cannot win: a very large opening database,
  or an analysis left running for hours. Nothing here touches the network until
  you turn it on and type an address.

### Install

Download `RapfiDroid-0.14.0.apk` below. Android will ask once for permission to
install from an unknown source.

* Android 8.0 (API 26) or newer
* **arm64 devices only.** The engine will not run on armeabi or x86.
* About 90 MB of storage
* English and Korean interface

For update notifications without a store, install
[Obtainium](https://github.com/ImranR98/Obtainium) and add
`https://github.com/lowqen/RapfiDroid`.

### Optional: real game statistics

Win rates from real tournament play need the [RenjuNet](https://www.renju.net/game/)
database. Its licence permits offline personal use and forbids its contents, or
anything derived from them, in any website or online system. So the data is not
bundled, not hosted and not fetched. The app converts it on the device instead:

> Settings ▸ **Add game data** ▸ open renju.net ▸ pick the XML file you
> downloaded ▸ Build

Build it once and it stays on the phone. Nothing produced from it leaves the
device. This is the only path the licence allows, and it is why there is no
button that fetches it for you.

### Known limits

* arm64 only.
* Building the game data from a large database takes real time and memory. It
  continues with the screen off, but it can fail on a low end device.
* This app cannot be published on Google Play. That follows from GPL-3.0 section
  6 and section 10 against the Play distribution terms, and it is why the release
  lives here and on F-Droid.

### Licence and source

The engine is **Rapfi, GPL-3.0-or-later**. The corresponding source for the
engine in this APK is **https://github.com/lowqen/rapfi**, branch `android`,
upstream `8d16fc2c`. The only change from upstream is two blocks in
`Rapfi/CMakeLists.txt` for the Android build.

The app itself is BSD-2-Clause and its interface is a port of Yixin-Board
(BSD-2-Clause, copyright 2009 to 2017 Kai Sun). The network weights and the
opening evaluation grades are CC0 1.0. Full notices:
[LICENSES.md](https://github.com/lowqen/RapfiDroid/blob/main/LICENSES.md), and
inside the app under Settings ▸ About ▸ Licences.


## 한국어

**첫 공개 릴리스입니다.** [Rapfi](https://github.com/dhbloo/rapfi) 엔진을 arm64
안드로이드로 컴파일해서 앱 안에서 **진짜 프로세스로** 돌리는 오목·렌주 보드입니다.
비행기 모드로 두고, 분석하고, 복기하고, 연구할 수 있습니다. 서버도 계정도 없고,
어디에도 열려 있는 포트가 없습니다.

### 무엇이 들어 있나

* **엔진이 폰 안에 있습니다.** 프리스타일·표준·렌주 각각 전용 신경망을 쓰고, 네
  가중치가 전부 동봉돼 있어 첫 실행에 받을 것이 없습니다. 최선 수순·깊이·평가바·
  승률이 실시간으로 갱신됩니다.
* **금수를 엔진이 직접 판정합니다.** 삼삼·사사·장련을 인터페이스의 근사치가 아니라
  실제로 두는 그 코드가 판단합니다.
* **기기에 남는 국면 데이터베이스.** 엔진이 배운 것을 `rapfi.db` 에 쓰고 다음
  세션에 이어 씁니다. 앱이 엔진을 내리기 전에 저장 명령을 먼저 보내므로 강제
  종료로 한 세션을 통째로 잃지 않습니다.
* **데스크톱 연구 환경을 그대로.** 게임 리뷰, 1~4수 이름 249개와 흑 5수 유불리
  11등급(모양 1,889개)을 담은 오프닝 익스플로러, 26주형 랭킹, 국면 증명, 수순
  탐색기, 엔진 콘솔, 그리고 PC 에서와 똑같이 엔진을 구동하는
  [Yixin-Board](https://github.com/dhbloo/Yixin-Board) 설정 67개.
* **서버 모드도 그대로 있고 기본은 꺼져 있습니다.** 아주 큰 오프닝 DB 나 몇 시간짜리
  분석처럼 폰이 못 당하는 경우를 위해 고급 설정 뒤에 있습니다. 직접 켜고 주소를
  넣기 전에는 네트워크에 접속하지 않습니다.

### 설치

아래 `RapfiDroid-0.14.0.apk` 를 받으세요. 「출처를 알 수 없는 앱」 설치를 한 번
허용해야 합니다.

* 안드로이드 8.0 (API 26) 이상
* **arm64 기기 전용.** armeabi·x86 에서는 엔진이 실행되지 않습니다.
* 저장공간 90 MB 정도
* 한국어·영어 지원

스토어 없이 업데이트 알림을 받으려면
[Obtainium](https://github.com/ImranR98/Obtainium) 에
`https://github.com/lowqen/RapfiDroid` 을 추가하세요.

### 선택: 실전 통계

실전 승률은 [RenjuNet](https://www.renju.net/game/) 대국 DB 가 있어야 채워집니다.
그 라이선스는 오프라인 개인 사용은 허용하되 내용물 또는 그로부터 파생된 무엇이든
웹사이트·온라인 시스템에 올리는 것을 금지합니다. 그래서 동봉하지도, 호스팅하지도,
받아 오지도 않습니다. 대신 앱이 기기에서 변환합니다.

> 설정 ▸ **대국 데이터 추가** ▸ renju.net 열기 ▸ 내려받은 XML 고르기 ▸ 만들기

한 번 만들면 기기에 남고, 거기서 나온 것은 기기를 벗어나지 않습니다. 라이선스가
허용하는 유일한 경로이고, 대신 눌러 주는 버튼이 없는 이유입니다.

### 알려진 제약

* arm64 전용입니다.
* 큰 DB 로 대국 데이터를 만들면 시간과 메모리를 꽤 씁니다. 화면을 꺼도 이어지지만
  저사양 기기에서는 실패할 수 있습니다.
* 구글 플레이에는 올릴 수 없습니다. GPL-3.0 6조·10조와 플레이 배포 약관이 충돌한
  결과이고, 그래서 배포처가 여기와 F-Droid 입니다.

### 라이선스와 소스

엔진은 **Rapfi, GPL-3.0-or-later** 입니다. 이 APK 에 들어간 엔진의 대응 소스는
**https://github.com/lowqen/rapfi** 의 `android` 브랜치, upstream `8d16fc2c`
입니다. upstream 과의 차이는 안드로이드 빌드를 위한 `Rapfi/CMakeLists.txt` 의
블록 두 개뿐입니다.

앱 본체는 BSD-2-Clause 이고 인터페이스는 Yixin-Board(BSD-2-Clause, 저작권
2009 ~ 2017 Kai Sun) 이식본입니다. 신경망 가중치와 오프닝 유불리 등급은 CC0 1.0
입니다. 전체 고지는
[LICENSES.md](https://github.com/lowqen/RapfiDroid/blob/main/LICENSES.md) 와
앱 안 설정 ▸ 정보 ▸ 라이선스 에 있습니다.
