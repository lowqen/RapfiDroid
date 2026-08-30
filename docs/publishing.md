# 배포 — GitHub Releases + F-Droid

Play 스토어는 쓰지 않는다(이유는 `../LICENSES.md` §3, `licence-outreach.md` §0).
GPL 앱의 본래 서식지 둘로 간다. **누구의 허락도 필요 없다** — GPL 은 배포를
막지 않고 소스 공개와 고지를 요구할 뿐이며, 그건 이미 갖췄다.

---

## 0. 먼저 — 이것부터 안 하면 나머지가 전부 위반이다

```bash
# 빌드 머신에서, 패치된 엔진 fork 를 공개로 올린다
cd ~/rapfi
gh auth login
gh repo create lowqen/rapfi --public --source=. --remote=fork
git push -u fork android
```

- `app/src/main/jniLibs/ENGINE_VERSION.txt` 의 `upstream` 커밋이 그 브랜치에
  실제로 있어야 한다.
- 이 저장소가 비공개이거나 없으면 APK 배포는 **GPL 위반**이다.

---

## 1. 서명 키 — 한 번 만들고 절대 잃어버리지 않는다

같은 키로 서명된 APK 만 «업데이트» 로 설치된다. 키를 잃으면 사용자는 앱을
지우고 다시 깔아야 하고, 그때 기기 DB 와 설정이 사라진다. F-Droid 는 자체 키로
서명하지만(§3), GitHub Releases 용 APK 는 우리 키를 쓴다.

```bash
keytool -genkeypair -v -keystore rapfidroid.jks -alias rapfidroid \
        -keyalg RSA -keysize 2048 -validity 10000
```

`keystore.properties`(gitignore 되어 있다):

```properties
storeFile=/절대/경로/rapfidroid.jks
storePassword=...
keyAlias=rapfidroid
keyPassword=...
```

> **백업**: `.jks` 와 비밀번호를 오프라인 두 곳에. 저장소에는 절대 넣지 않는다
> (루트 `.gitignore` 가 `*.jks` 를 막고 있지만 믿고 방심하지 말 것).

---

## 2. GitHub Releases — 오늘 바로 할 수 있는 것

### 2-1. 릴리스 APK 만들기

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat clean testDebugUnitTest assembleRelease
```

산출물: `app/build/outputs/apk/release/app-release.apk`

### 2-2. 내보내기 전 점검 (RenjuNet 자료가 섞이지 않았는지)

```bash
unzip -l app/build/outputs/apk/release/app-release.apk \
  | grep -iE "renju_(stats|games)|freq_data" || echo "OK - RenjuNet 파생물 없음"
```

**0건이어야 한다.** 있으면 배포하지 않는다 — 그 자료는 온라인에 올릴 수 없다.

### 2-3. 태그와 릴리스

```bash
git tag -a v0.14.0 -m "RapfiDroid v0.14.0"
git push origin v0.14.0
gh release create v0.14.0 app/build/outputs/apk/release/app-release.apk \
  --title "RapfiDroid v0.14.0" --notes-file docs/release-notes-0.14.0.md
```

릴리스 노트에 **반드시** 넣을 것:

```markdown
엔진: Rapfi (GPL-3.0). 대응 소스: https://github.com/lowqen/rapfi (브랜치 `android`)
서드파티 고지: https://github.com/lowqen/RapfiDroid/blob/main/LICENSES.md
```

### 2-4. 자동 업데이트 — Obtainium

사용자에게 안내할 한 줄: **Obtainium** 을 설치하고 저장소 URL
`https://github.com/lowqen/RapfiDroid` 를 추가하면, 새 릴리스가 올라올 때마다
알림이 오고 한 번 눌러 갱신된다. 스토어 없이 스토어 경험을 얻는 표준적인 방법이다.

### 2-5. (선택) CI 가 릴리스까지

`.github/workflows/build.yml` 은 디버그 APK 만 만든다. 태그를 밀면 릴리스가
나가게 하려면 워크플로를 하나 더 두고, 키스토어는 **base64 로 인코딩해 Actions
시크릿**(`SIGNING_KEYSTORE`, `SIGNING_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`)에
넣는다. 키를 CI 에 두는 것이 불안하면 로컬에서 서명해 올리는 §2-3 으로 충분하다.

---

## 3. F-Droid — 소스에서 빌드되는 진짜 스토어

F-Droid 는 우리가 준 APK 를 배포하지 않는다. **저장소를 받아 자기 서버에서
직접 빌드한다.** 그래서 GPL 준수가 구조적으로 보장되고, 그래서 준비물이 있다.

### 3-1. 통과해야 하는 조건

| 조건 | 우리 상태 |
|---|---|
| 소스 공개 · OSI 라이선스 | ✅ BSD-2 + GPL-3.0 |
| **바이너리가 저장소에 없을 것** | ⚠ **문제** — `jniLibs/arm64-v8a/libengine.so` 가 커밋돼 있다 |
| 독점 의존성 없음 | ✅ (Google Play Services 등 미사용) |
| 재현 가능한 빌드 | ✅ Gradle 표준 |
| 안티 기능(광고·추적) 없음 | ✅ 네트워크는 서버 모드에서만, 기본 꺼짐 |

### 3-2. 커밋된 엔진 바이너리 — 세 가지 길

F-Droid 는 사전 빌드된 바이너리를 싫어한다(그게 소스에서 나온 것인지 증명할 수
없으므로). 선택지:

1. **F-Droid 빌드 레시피가 엔진도 빌드하게 한다** (정공법, 권장).
   `metadata` 의 `sudo`/`build` 단계에서 NDK 를 받아 fork 를 클론하고
   `tools/build_engine_android.sh` 와 같은 절차로 `libengine.so` 를 만든 뒤
   `jniLibs` 에 넣는다. 우리 스크립트가 이미 그 절차를 한 파일로 갖고 있다는
   점이 여기서 값을 한다.
2. **가중치 40MB 는 별도로 받게 한다** — F-Droid 는 큰 바이너리 에셋도 꺼린다.
   CC0 라 법적 문제는 없지만 저장소 비대는 지적받을 수 있다. 필요하면
   빌드 단계에서 `rapfi-networks` 를 클론해 넣는다.
3. 위가 어려우면 **자체 F-Droid 저장소**(`fdroid` 도구로 만든 개인 repo)를
   운영한다. 사용자가 URL 을 추가하면 되고 심사가 없다. 공식 등재보다 빠르다.

### 3-3. 등재 절차 (공식)

```bash
# 1) fdroiddata 를 fork
git clone https://gitlab.com/<나>/fdroiddata.git && cd fdroiddata
# 2) 메타데이터 작성
#    metadata/dev.gomoku.rapfidroid.yml
# 3) 로컬 검증
fdroid lint dev.gomoku.rapfidroid
fdroid build -v -l dev.gomoku.rapfidroid
# 4) merge request
```

`metadata/dev.gomoku.rapfidroid.yml` 뼈대:

```yaml
Categories:
  - Games
License: BSD-2-Clause
AuthorName: lowqen
SourceCode: https://github.com/lowqen/RapfiDroid
IssueTracker: https://github.com/lowqen/RapfiDroid/issues

AutoName: RapfiDroid
Summary: On-device gomoku/renju engine with analysis
Description: |-
    A gomoku and renju board with the Rapfi engine running on the device
    itself — no server and no internet. Analysis, opening names, forbidden
    points, game review and a position database.

    The engine is Rapfi (GPL-3.0); this app is a port of the Yixin-Board
    interface (BSD-2-Clause, (c) 2009-2017 Kai Sun). Real-game statistics are
    optional and are built on the device from a RenjuNet database the user
    downloads themselves — that data is never bundled or transmitted.

RepoType: git
Repo: https://github.com/lowqen/RapfiDroid.git

Builds:
  - versionName: 0.14.0
    versionCode: 14
    commit: v0.14.0
    subdir: app
    gradle:
      - yes
    # 엔진은 소스에서 빌드한다 (§3-2 1안). NDK 버전은 ENGINE_VERSION.txt 와 맞춘다.
    srclibs:
      - rapfi@android
    prebuild:
      - rm -f src/main/jniLibs/arm64-v8a/libengine.so

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 0.14.0
CurrentVersionCode: 14
```

> 위 `Builds` 는 뼈대다. 엔진 빌드 단계는 `fdroid build -l` 로 실제 통과시키며
> 다듬어야 하고, 그게 이 등재의 **유일하게 까다로운 부분**이다. 막히면 §3-2 3안
> (자체 저장소)으로 먼저 배포하고 공식 등재는 나중에 해도 된다.

### 3-4. 서명 — F-Droid 가 자기 키로 한다

공식 저장소의 APK 는 **F-Droid 키**로 서명된다. GitHub Releases 의 APK 와
서명이 다르므로 **둘 사이를 오갈 때는 재설치**가 필요하다. 사용자에게 한 곳을
정해 주는 편이 낫다 — 「F-Droid 를 쓰는 사람은 F-Droid 로, 아니면 Obtainium 으로」.

---

## 4. 릴리스 체크리스트

- [ ] `lowqen/rapfi` 공개 · `android` 브랜치 · `ENGINE_VERSION.txt` 커밋과 일치
- [ ] `gradlew testDebugUnitTest assembleRelease` 통과
- [ ] APK 에 RenjuNet 파생물 0건 (§2-2)
- [ ] 앱 안 라이선스 화면이 GPL/BSD 전문을 띄운다
- [ ] 릴리스 노트에 엔진 소스 링크 + `LICENSES.md` 링크
- [ ] `versionCode` 를 올렸다 (F-Droid 는 이걸로 업데이트를 판단한다)
- [ ] 키스토어·`keystore.properties` 가 커밋에 없다
