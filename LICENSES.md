# 서드파티 고지 (THIRD-PARTY NOTICES)

이 파일은 **배포되는 APK 안에 무엇이 들어 있고, 그것을 남에게 건넬 때 무엇을 해야
하는지**를 적는다. 배포 전 점검표는 §6.

한 줄 결론부터: **원저작자에게 허락을 받아야 하는 구성요소는 하나도 없다.** 전부
허락이 아니라 *고지*와 *소스 공개*를 요구하는 라이선스다. 다만 그 두 가지는 **반드시**
해야 하고, 하나(RenjuNet 자료)는 애초에 배포에 실을 수 없다.

---

## 1. 이 앱에 들어 있는 것

| 구성요소 | 출처 | 라이선스 | 배포 시 의무 |
|---|---|---|---|
| **RapfiDroid** (이 저장소의 Kotlin 코드) | 이 저장소 | BSD 2-Clause (`LICENSE`) | — |
| **Yixin-Board 로부터의 이식분** (프로토콜·국면 키·오프닝 프레임 키·설정 표 등) | [accreator/Yixin-Board](https://github.com/accreator/Yixin-Board) → [dhbloo/Yixin-Board](https://github.com/dhbloo/Yixin-Board) | **BSD 2-Clause**, © 2009–2017 Kai Sun | 저작권 고지 + 무보증 조항을 **바이너리에 동반되는 문서에 재현** |
| **Rapfi 엔진** `jniLibs/arm64-v8a/libengine.so` | [dhbloo/rapfi](https://github.com/dhbloo/rapfi) | **GPL-3.0-or-later** | 라이선스 전문 동봉 + **대응 소스 제공** (§3) |
| **NNUE 가중치** `assets/engine/*.bin.lz4`, `model220723.bin` | [dhbloo/rapfi-networks](https://github.com/dhbloo/rapfi-networks) | **CC0 1.0** (공유 저작물) | 없음 (출처 표기는 예의) |
| **엔진 설정 템플릿** `assets/engine/config.toml` | Rapfi 배포본의 설정 예시에서 파생 | GPL-3.0 (Rapfi 일부로 취급) | §3 과 함께 |
| **흑 5수 유불리 등급** `assets/opening_evals.txt` | Renju Atlas | **CC0 1.0** | 없음 |
| **오프닝 명칭 표** `assets/opening_names.txt` | 이 프로젝트에서 작성 | BSD 2-Clause (이 앱과 동일) | — |
| **리포트 템플릿** `assets/report_*.html` | 이 프로젝트에서 작성 | BSD 2-Clause | — |
| AndroidX · Jetpack Compose · Kotlin · Hilt/Dagger · kotlinx-coroutines · kotlinx-serialization | Google / JetBrains | **Apache-2.0** | 고지 (본 파일) |
| Okio | Square | **Apache-2.0** | 고지 (본 파일) |

앱 안에서도 볼 수 있다: **설정 ▸ ⓘ ▸ 라이선스 전문**. GPL-3.0 과 BSD 2-Clause
전문이 `assets/licenses/` 로 APK 에 동봉된다.

---

## 2. Yixin-Board (BSD 2-Clause) — 허락 불필요, 고지 필수

이 앱은 `Yixin-Board` 의 `main.c` 를 **오라클 삼아 이식**했다 — 엔진 프로토콜,
좌표계, `web_poskey`, 오프닝 프레임 키, 설정 67개의 배치가 그렇다. BSD 2-Clause 는
수정·재배포·상용 이용을 모두 허용하며 요구하는 것은 둘뿐이다:

1. 소스 배포 시 저작권 고지 유지 → `LICENSE` 에 Kai Sun 의 저작권을 적어 두었다.
2. **바이너리 배포 시** 저작권 고지와 무보증 조항을 *동반 문서에 재현* →
   이 파일과 `assets/licenses/BSD-2-Clause-Yixin-Board.txt`, 그리고 앱 안의
   라이선스 화면이 그 역할을 한다.

> 원저작자 연락은 **의무가 아니다.** 다만 dhbloo 의 fork 가 Rapfi 지원을 더한 판이고
> 이 앱이 그 판을 따라갔으므로, 공개 시 README 에서 두 저장소를 모두 링크해 두었다.

**Yixin 엔진 자체(`Yixin.exe`)는 이 앱에 들어 있지 않다** — 비공개 소프트웨어이고,
앱이 쓰는 엔진은 Rapfi 다. 이름이 겹치는 것뿐이다.

---

## 3. Rapfi (GPL-3.0) — 여기가 유일하게 "반드시 해야 하는" 항목

APK 가 `libengine.so` 를 담고 있으므로, APK 를 남에게 주는 순간 GPL 프로그램을
**배포(convey)** 하는 것이 된다. 의무는 셋이다.

1. **라이선스 전문을 함께 준다.** → `assets/licenses/GPL-3.0.txt` 로 APK 안에 있고
   앱에서 읽을 수 있다.
2. **대응 소스를 제공한다.** 우리는 upstream 을 그대로 쓰지 않고 CMake 를 두 군데
   고쳤으므로(안드로이드 링크·출력 이름), 그 수정본이 소스다:
   - 저장소: `https://github.com/lowqen/rapfi` 브랜치 `android`
   - **이 저장소는 반드시 공개여야 한다.** 비공개면 GPL 위반이다.
   - 빌드 재현 절차: `RapfiDroid/tools/build_engine_android.sh`
   - 정확한 커밋은 `app/src/main/jniLibs/ENGINE_VERSION.txt` 에 적혀 있다.
3. **저작권 고지를 지운 채 배포하지 않는다.** 소스는 손대지 않았다.

### 앱 전체가 GPL 이 되는가?

**되지 않지만, 되어도 문제가 없다.** 두 가지 이유가 겹쳐서 안전하다.

- 엔진은 **별도 프로세스**로 돌고 파이프로 텍스트(piskvork)만 주고받는다. 링크가
  아니므로 파생 저작물이 아니라는 것이 통상의 해석이고, APK 는 GPL §5 가 명시적으로
  허용하는 **집합체(aggregation)** 다.
- 설령 누군가 APK 를 하나의 결합 저작물로 본다 해도, 이 앱은 **BSD 2-Clause** —
  GPL 과 호환되는 허용적 라이선스다. 결합 저작물이 GPLv3 가 될 뿐 충돌하지 않는다.

> **이 안전판은 구조에 달려 있다.** 엔진을 JNI 로 앱 프로세스 안에 넣는 순간 첫 번째
> 근거가 사라진다. 그렇게 바꾸려면 앱을 GPLv3 로 낼 각오를 함께 해야 한다.

### 앱 스토어 (Google Play) — 주의

GPLv3 §6(설치 정보)·§10(추가 제한 금지)과 Play 배포 계약이 충돌한다는 것이 오래된
쟁점이다(VLC 가 Play 에서 내려갔던 그 문제). **사이드로드·GitHub Releases·F-Droid
는 문제가 없다.** Play 에 올리려면 셋 중 하나가 필요하다:

- 엔진을 뺀 빌드를 Play 용으로 따로 내거나,
- ~~Rapfi 저작권자에게 추가 허가를 받거나~~ — **사실상 불가능하다.** Rapfi 는
  Stockfish 코드를 일부 채용했다고 `AUTHORS` 에 밝히고 있어(그리고 실제로
  `core/platform.cpp` 에 그 주석이 있다), 예외를 주려면 Stockfish 기여자
  수백 명의 동의가 필요하다. 자세한 것은 `docs/licence-outreach.md`.
- 법률 자문을 받는다.

---

## 4. RenjuNet 자료 — 실을 수 없다

오프닝 익스플로러 팩(`renju_stats.pack` / `renju_games.pack`)과 랭킹의 실전 빈도
(`freq_data.json`)는 RenjuNet 대국 DB 파생물이다. 원문(DB 파일 머리)은 이렇다:

> It is allowed to use this database for non-commercial purposes in the forms of
> **OFFLINE databases only**.
> It is **forbidden** to use any contents of this database **or its modifications
> in any website or ONLINE system**.

읽는 방식은 하나뿐이다:

- ✅ 사용자가 자기 기기에서 오프라인으로 쓰는 것 — 허용.
- ❌ 우리가 팩을 **어딘가에 올려 두고 받게 하는 것** — 파생물을 온라인 시스템에
  올리는 것이라 **금지**. GitHub Releases 도 웹이다. APK 에 넣어 배포하는 것도
  같은 이유로 피한다.

그래서 이 앱은 그 자료를 **번들하지도, 서버에서 내려주지도 않는다.** 사용자가
renju.net 에서 직접 받아 자기 기기로 반입한다. 데이터가 없어도 앱은 동작한다
(26주형·오프닝 이름·흑5수 유불리는 번들 자료로 나온다).

---

## 5. 상표·이름

"Yixin" 은 Kai Sun 의 엔진 이름이다. 앱 이름 `RapfiDroid` 는 그 GUI 의 이식이라는
사실에서 왔지만, **앱에 Yixin 엔진은 들어 있지 않다.** 혼동을 피하려면 스토어
설명과 README 에 "Rapfi 엔진 내장, Yixin-Board GUI 를 이식" 이라고 적어 두는 편이
낫고, 원저작자가 이름 사용에 이의를 제기하면 바꿀 수 있게 준비해 둔다(법적 의무가
아니라 예의와 위험 관리의 문제다).

---

## 6. 배포 전 점검표

- [ ] `https://github.com/lowqen/rapfi` (브랜치 `android`) **공개** — GPL 대응 소스
- [ ] `ENGINE_VERSION.txt` 의 upstream 커밋이 그 브랜치에 실제로 있는지 확인
- [ ] 릴리스 노트에 GPL 소스 링크와 이 파일 링크
- [ ] APK 에 RenjuNet 파생 파일이 없는지 확인:
      `unzip -l app-release.apk | grep -iE "renju_(stats|games)|freq_data"` → **0건**
- [ ] 앱 안 라이선스 화면이 열리는지 (설정 ▸ ⓘ ▸ 라이선스 전문)
- [ ] Play 스토어에 올릴 생각이면 §3 의 주의를 먼저 해결
- [ ] 키스토어·`keystore.properties` 가 커밋에 없는지
