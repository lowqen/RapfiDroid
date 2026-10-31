# Security

## Reporting a vulnerability

Please report privately, not in a public issue:
**[Report a vulnerability](https://github.com/lowqen/Zenyth/security/advisories/new)**
(GitHub's private vulnerability reporting; only the maintainer sees it).

Say what is affected, how to reproduce it, and what an attacker gains. A
crashing input (a link, a game file, a settings file) is welcome as an
attachment. This is a one-person project, so replies are best effort; a fix
for anything real goes out in the next release, and you are credited in its
notes unless you would rather not be.

## Supported versions

Only the [latest release](https://github.com/lowqen/Zenyth/releases/latest)
gets fixes. Android does not let an app update itself, so a fix reaches you
when you install that release (Obtainium does it for you).

## What is in scope

Zenyth has no account, no server of its own and no payments, and its source is
public, so there is little on the phone to steal. What matters:

* **The update path.** Anything that could get an APK signed with the release
  key, or a release published, without the maintainer: the signing key, this
  repository's settings and its GitHub Actions workflows.
* **Input from outside.** Links any web page or app can open the app with
  (`rapfiweb.pages.dev/board?p=…`, `…/joseki?lesson=…`), files you open (game
  records, settings, joseki JSON, `.rif`), the joseki curriculum fetched from
  Zenyth Web, and the replies of a server engine. A crash from any of these is
  a bug worth reporting; anything more is a vulnerability.
* **The bundled engine.** The Rapfi binaries are rebuilt from source in CI and
  compared with the committed ones (`.github/workflows/engine-repro.yml`). A
  binary that does not match its source is in scope.

Not in scope: an attacker who already controls your phone (root, an installed
accessibility service); the server-engine link when you point it outside
Tailscale or a private network — it is plain TCP and the app warns you; and
denial of service by a server engine you chose to connect to.

## Checking what you installed

Every release APK is signed with the key whose certificate SHA-256 is

```
F8:30:FF:50:9B:96:CB:A8:01:3E:B0:0D:1B:DB:C2:F4:FE:EF:41:C9:35:03:D5:06:B3:C9:DA:65:22:A9:B9:2E
```

See [Verify your download](README.md#verify-your-download) for three ways to
check it. F-Droid builds are signed by F-Droid's own key.

---

## 한국어 요약

* **취약점 제보는 공개 이슈가 아니라 [비공개 제보](https://github.com/lowqen/Zenyth/security/advisories/new)로.**
  무엇이 영향을 받는지, 재현 방법, 공격자가 얻는 것을 적어 주세요. 1인 프로젝트라 답은 최선을 다하는
  수준이고, 실제 문제는 다음 릴리스에서 고치며 원하면 릴리스 노트에 이름을 올립니다.
* **지원 버전**은 최신 릴리스 하나입니다.
* **중요하게 보는 것**: 업데이트 경로(서명 키 · 저장소 설정 · GitHub Actions), 밖에서 들어오는 입력(링크 ·
  여는 파일 · 커리큘럼 · 서버 엔진 응답 — 이것들로 앱이 죽는 것도 제보 대상), 동봉 엔진이 소스와 다른 경우.
* **범위 밖**: 이미 폰을 장악한 공격자, Tailscale · 사설망 밖으로 연결한 서버 엔진 링크(평문 TCP 이고
  앱이 경고합니다), 직접 고른 서버 엔진이 일으키는 서비스 거부.
* 서명 인증서 SHA-256 은 위 값이고, 확인 방법은 [README](README.ko.md#받은-apk-확인하기)에 있습니다.
