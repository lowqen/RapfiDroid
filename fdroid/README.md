# fdroid/ — F-Droid 등재용 메타데이터

여기 있는 두 파일은 **우리 저장소가 아니라 `fdroiddata` 저장소에 들어간다.**
버전 관리를 여기서 하는 이유는 하나다 — `versionCode` 와 엔진 커밋을
`app/build.gradle.kts`·`ENGINE_VERSION.txt` 와 **같이** 올려야 하기 때문이다.
따로 두면 반드시 어긋난다.

| 여기 | fdroiddata 안의 자리 |
|---|---|
| `metadata/dev.gomoku.rapfidroid.yml` | `metadata/dev.gomoku.rapfidroid.yml` |
| `srclibs/Rapfi.yml` | `srclibs/Rapfi.yml` |

## 왜 srclib 이 필요한가

F-Droid 는 **사전 빌드된 바이너리를 신뢰하지 않는다.** 그것이 정말 그 소스에서
나왔는지 증명할 수 없기 때문이다. 우리 저장소에는 `libengine.so` 가 커밋돼
있으므로(클론 직후 `gradlew` 만으로 빌드되게 하려고), 레시피가 그것을 **지우고
같은 커밋의 엔진을 소스에서 다시 빌드한다** — `rm:` + `srclibs:` + `build:`.

엔진 패치는 `Rapfi/CMakeLists.txt` **한 파일**뿐이라(실행 파일 이름을 `lib*.so`
로, bionic 에는 libpthread 없음) 리뷰어가 upstream 과의 차이를 몇 초에 확인할 수
있다. 이것이 등재 심사에서 가장 값이 나가는 지점이다.

## 확인 방법

```bash
git clone https://gitlab.com/<나>/fdroiddata.git && cd fdroiddata
cp <이 폴더>/metadata/dev.gomoku.rapfidroid.yml metadata/
cp <이 폴더>/srclibs/Rapfi.yml srclibs/
fdroid lint dev.gomoku.rapfidroid
fdroid build -v -l dev.gomoku.rapfidroid
```

`fdroid build -l` 이 실제로 통과해야 한다. 여기가 이 등재의 **유일하게 까다로운
부분**이다. 자주 걸리는 곳:

- **NDK 버전.** `ndk: r27c` 가 빌드서버에 없으면 있는 것으로 낮춘다. 낮춰도
  탐색 결과는 같아야 한다 — upstream `CMakeLists.txt` 가 `-ffp-contract=off` 를
  걸어 두어 부동소수 축약이 컴파일러·ISA 마다 달라지지 않기 때문이다.
  **확인**: 빌드된 엔진에 `BENCH` 를 넣어 `Hash: e240ab16` 이 나오면 같은 엔진이다.
- **`$$NDK$$`·`$$Rapfi$$`** 는 fdroidserver 가 절대 경로로 바꿔 준다.
  `build:` 명령은 `subdir`(= `app/`) 에서 돌고, `rm:` 경로는 저장소 루트 기준이다.
  둘의 기준이 다르다 — 이 레시피가 그렇게 쓰여 있다.
- **가중치 39MB**(`app/src/main/assets/engine/*.bin.lz4`, `model220723.bin`) 는
  CC0 1.0 데이터라 안티피처가 아니다. 다만 저장소 비대를 지적받으면 이것도
  `Networks` 저장소에서 빌드 단계에 받아 오도록 바꿀 수 있다.

## 서명

공식 저장소의 APK 는 **F-Droid 키**로 서명된다. GitHub Releases 의 APK 와 서명이
다르므로 **둘 사이를 오갈 때는 재설치**가 필요하고, 그때 기기 DB 와 설정이
사라진다. 사용자에게 한 곳을 정해 주는 편이 낫다 —
「F-Droid 를 쓰면 F-Droid 로, 아니면 Obtainium 으로」.

## 막히면

공식 등재가 길어지면 **자체 F-Droid 저장소**(`fdroid` 도구로 만드는 개인 repo)를
먼저 띄워도 된다. 심사가 없고 사용자는 URL 만 추가하면 된다. 공식 등재는 그 뒤에
해도 늦지 않다.
