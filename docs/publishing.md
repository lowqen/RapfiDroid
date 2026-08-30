# 배포 — GitHub Releases + F-Droid

Play 스토어는 쓰지 않는다(이유는 `../LICENSES.md` §3, `licence-outreach.md` §0).
GPL 앱의 본래 서식지 둘로 간다. **누구의 허락도 필요 없다** — GPL 은 배포를
막지 않고 소스 공개와 고지를 요구할 뿐이며, 그건 이미 갖췄다.

---

## 0. 전제 — ✅ 충족됨 (2026-08-30 확인)

`https://github.com/lowqen/rapfi` 가 **공개**이고, `android` 브랜치 끝이
`8d16fc2c…` 로 `app/src/main/jniLibs/ENGINE_VERSION.txt` 의 `upstream` 과 같다.
이 저장소가 비공개이거나 커밋이 어긋나면 APK 배포는 **GPL 위반**이다.
엔진을 다시 빌드할 때마다 이 두 값을 같이 맞출 것.

---

## 1. 서명 키 — 한 번 만들고 절대 잃어버리지 않는다

같은 키로 서명된 APK 만 «업데이트» 로 설치된다. 키를 잃으면 사용자는 앱을 지우고
다시 깔아야 하고, 그때 **기기 DB 와 설정이 사라진다.** F-Droid 는 자체 키로
서명하지만(§3), GitHub Releases 용 APK 는 우리 키를 쓴다.

> 이 워크스테이션에는 JDK·안드로이드 SDK 가 없다(2026-08-30 확인). 그래서 릴리스
> 빌드는 **CI 에서** 하고, 키 생성만 JDK 가 있는 곳(엔진을 빌드한 lightning.ai 등)
> 에서 한 번 한다.

```bash
keytool -genkeypair -v -keystore rapfidroid.jks -alias rapfidroid \
        -keyalg RSA -keysize 2048 -validity 10000
```

만든 키를 **Actions 시크릿 4개**로 넣는다(로컬 저장소에는 두지 않는다):

```bash
gh secret set SIGNING_KEYSTORE          --repo lowqen/RapfiDroid < <(base64 -w0 rapfidroid.jks)
gh secret set SIGNING_STORE_PASSWORD    --repo lowqen/RapfiDroid
gh secret set SIGNING_KEY_ALIAS         --repo lowqen/RapfiDroid   # rapfidroid
gh secret set SIGNING_KEY_PASSWORD      --repo lowqen/RapfiDroid
```

> **백업**: `.jks` 와 비밀번호를 오프라인 두 곳에. 저장소에는 절대 넣지 않는다
> (루트 `.gitignore` 가 `*.jks` 를 막고 있지만 믿고 방심하지 말 것).
> 로컬에서 직접 빌드하고 싶으면 `keystore.properties`(gitignore 됨)를 만들면 된다 —
> 형식은 `app/build.gradle.kts` 주석에 있다.

---

## 2. GitHub Releases — 태그 하나가 전부

### 2-1. 절차

```bash
# 1) 버전을 올린다 (app/build.gradle.kts)
#      versionCode = 14      <- F-Droid 는 이 값으로 업데이트를 판단한다. 반드시 증가.
#      versionName = "0.14.0"
# 2) 릴리스 노트를 쓴다 — 파일 이름이 태그와 묶여 있다
#      docs/release-notes-0.14.0.md
# 3) 태그를 민다
git tag -a v0.14.0 -m "RapfiDroid v0.14.0"
git push origin v0.14.0
```

`.github/workflows/release.yml` 이 나머지를 한다: 유닛 테스트 → 시크릿에서 키
복원 → `assembleRelease` → **키 파기** → 두 게이트 → 릴리스 생성·APK 첨부.

### 2-2. 두 게이트 (사람이 잊어도 CI 는 안 잊는다)

| 게이트 | 무엇을 막나 |
|---|---|
| RenjuNet 파생물 0건 | 팩·`freq_data.json`·`.rif` 가 APK 에 섞여 나가는 것. 그 자료는 **온라인에 올릴 수 없다** — 릴리스는 온라인이다 |
| 엔진 동봉·압축 확인 | `libengine.so` 가 빠지거나 **압축되지 않은 채** 들어가는 것. 후자면 패키지 매니저가 추출하지 않아 exec 할 경로가 없고, 증상은 사용자 폰에서만 나타난다 |

### 2-3. 자동 업데이트 — Obtainium

사용자에게 안내할 한 줄: **Obtainium** 을 설치하고 저장소 URL
`https://github.com/lowqen/RapfiDroid` 를 추가하면, 새 릴리스가 올라올 때마다
알림이 오고 한 번 눌러 갱신된다. 스토어 없이 스토어 경험을 얻는 표준적인 방법이다.

---

## 3. F-Droid

메타데이터와 절차는 **`../fdroid/README.md`** 에 있다(파일 두 개와, 커밋된
`libengine.so` 를 소스 빌드로 대체하는 레시피).

요약: F-Droid 는 우리가 준 APK 를 배포하지 않고 **저장소를 받아 자기 서버에서
직접 빌드한다.** 그래서 GPL 준수가 구조적으로 보장되고, 그래서 사전 빌드된
엔진 바이너리를 지우고 다시 빌드하는 단계가 필요하다. `fdroid build -l` 을
로컬에서 통과시키는 것이 이 등재의 유일하게 까다로운 부분이다.

---

## 4. 릴리스 체크리스트

- [x] `lowqen/rapfi` 공개 · `android` 브랜치 · `ENGINE_VERSION.txt` 커밋과 일치
- [ ] 서명 키 생성 + Actions 시크릿 4개 (§1)
- [ ] `versionCode`·`versionName` 을 올렸다
- [ ] `docs/release-notes-<버전>.md` 를 썼다 (없으면 CI 가 멈춘다)
- [ ] 태그 push → 워크플로 초록
- [ ] 릴리스 페이지에 엔진 소스 링크 + `LICENSES.md` 링크가 보인다
- [ ] 키스토어·`keystore.properties` 가 커밋에 없다
