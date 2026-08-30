# 원저작자 연락 — 초안과 사전 확인

작성 2026-08-30. 배포 준비(`../LICENSES.md`)의 부속 문서.

## 0. 먼저 알아야 할 것 — 앱 스토어 예외는 받기 어렵다

Rapfi 의 `AUTHORS` 가 이렇게 적고 있다:

> As Rapfi adopts some of **Stockfish's** code, we acknowledge all authors of Stockfish

실제로 소스에도 남아 있다 — `core/platform.cpp` 의 큰 페이지 할당이
"Adapted from Stockfish" 주석과 함께 PR 링크까지 달고 있다.

그래서 «GPLv3 §7 추가 허가(앱 스토어 예외)»를 받으려면 **모든 저작권자**의 동의가
필요한데, 그 명단이 Rapfi 기여자 6명에서 끝나지 않는다:

| 누구 | 인원 | 현실성 |
|---|---|---|
| Rapfi 기여자 | 6명 (`AUTHORS`) | 연락 가능 |
| **Stockfish 기여자** | 수백 명 | 사실상 불가능 |

게다가 Stockfish 팀은 앱 스토어 예외에 **원칙적으로 반대**해 왔고(ChessBase 건
등 GPL 집행 전력이 있다), 그 입장이 바뀔 이유가 없다.

**결론**: dhbloo 가 흔쾌히 동의하더라도 그가 Stockfish 파생 부분까지 재라이선스할
권한이 없다. Play 스토어에 **엔진을 넣은 채로** 올리는 길은 사실상 닫혀 있다고
보는 것이 정확하다.

**그러면 어디로 가는가** — GPL 앱의 본래 서식지다:

1. **F-Droid** (권장). 소스에서 직접 빌드하므로 GPL 준수가 구조적으로 보장된다.
2. **GitHub Releases + Obtainium** — 자동 업데이트까지 된다.
3. Play 를 꼭 써야 한다면 **엔진 없는 빌드**(서버 모드 전용)뿐인데, 지금 제품
   방향과 정반대다.

## 1. 그래도 보낼 가치가 있는 메일

목적을 «예외 요청»에서 **«알림 + 질문»** 으로 바꾸면 보낼 값어치가 충분하다.
포팅 사실을 알리는 것은 예의이고, 스토어 가능성은 그가 가장 잘 아는 사람이며,
답이 «안 된다» 여도 우리는 F-Droid 로 가면 된다.

**보내는 것: 링크. APK 가 아니다.**
첨부한 APK 는 대부분의 메일 서버가 막고, 그가 평가에 필요한 것도 아니다.
필요하면 릴리스 링크를 눌러 받으면 된다.

### 1-1. dhbloo (Rapfi) — GitHub Issue 또는 이메일

> **Subject:** Rapfi on Android — a GPL-compliant port, and a licensing question
>
> Hello,
>
> I've built an Android app that runs Rapfi on the phone itself. It's a port of
> the Yixin-Board GUI (Kai Sun's, BSD-2) with Rapfi cross-compiled for arm64-v8a
> and run as a separate process over the piskvork protocol, exactly as the
> desktop GUI drives it. The NNUE weights are your rapfi-networks (CC0). It
> works: the on-device build reproduces the reference bench signature
> (`e240ab16`), so the phone and a desktop build pick the same move given the
> same nodes.
>
> Two things I want to be straight about.
>
> **1. GPL compliance.** The APK carries `libengine.so`, so I treat it as
> conveying Rapfi. The full GPL-3.0 text ships inside the APK and is readable
> from the app; the corresponding source — your tree with two CMake changes for
> Android (bionic has no libpthread; Android only executes `lib*.so` out of the
> native library directory) — is public at <FORK URL>, branch `android`, and the
> exact commit is recorded in the APK. The app's own code is BSD-2, so there is
> no incompatibility either way. If anything there falls short of what you'd
> expect, tell me and I'll fix it.
>
> **2. A question about app stores.** I'd like to know whether you think a
> Google Play release is possible at all. My reading is that it is not: Play's
> distribution terms are the usual GPLv3 §6/§10 problem, and even with your
> permission the Stockfish-derived parts of Rapfi (noted in AUTHORS, and in
> `core/platform.cpp`) would still need the Stockfish authors' agreement, which
> nobody can realistically obtain. So I'm planning to distribute through F-Droid
> and GitHub releases instead, which avoids the question entirely. I'm asking
> because you know this codebase and I would rather be corrected now than after
> a listing.
>
> Either way — thank you for Rapfi and for keeping the ARM/NEON path a
> first-class target. That is the only reason this was a weekend of work and not
> a rewrite.
>
> Repository: <APP REPO URL>
> Engine fork: <FORK URL> (branch `android`)
> Third-party notices: <LICENSES.md URL>
>
> Best regards,
> <이름>

### 1-2. Kai Sun / accreator (Yixin-Board) — 선택, 예의

BSD-2 라 **의무는 없다.** 다만 GUI 가 그의 작업이고 메일 주소가 README 에
공개돼 있으니(`sunkaicn@gmail.com`), 한 통 보내 두면 나중에 이름 문제로 곤란해질
일이 줄어든다.

> **Subject:** Android port of Yixin-Board
>
> Hello,
>
> I've ported the Yixin-Board interface to Android — the settings layout, the
> extended piskvork protocol, the position keys and the opening frame keys all
> follow your `main.c`, via dhbloo's GTK3 fork. The engine it drives is Rapfi
> rather than Yixin.
>
> The BSD-2 notice and your copyright are reproduced in the app and in the
> repository, as the licence asks. I'm writing only to let you know it exists,
> and to ask one thing: the app is currently called **RapfiDroid**. If you would
> rather the name did not travel to a program that doesn't run Yixin, say so and
> I'll change it.
>
> Repository: <APP REPO URL>
>
> Thank you for the interface — it has been the reference for every screen.
>
> Best regards,
> <이름>

## 2. 보내기 전 채울 것

- [ ] `<FORK URL>` — `github.com/lowqen/rapfi` 를 **공개**로 만들고 `android` 브랜치 push
- [ ] `<APP REPO URL>` — `github.com/lowqen/RapfiDroid`
- [ ] `<LICENSES.md URL>`
- [ ] 첨부 없음 확인 (APK 를 붙이지 않는다)

> ⚠ 이 문서는 법률 자문이 아니다. GPL 해석에 확신이 필요하면 변호사에게 물을 것.
> 여기 적힌 것은 라이선스 원문과 프로젝트 파일에서 읽어낸 사실과 그 위에서의 판단이다.
