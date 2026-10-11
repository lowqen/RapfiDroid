<div align="center">

# RapfiDroid

**진짜 엔진이 폰 안에서 도는 오목·렌주 보드.**
**서버도, 계정도 필요 없고, 오프라인에서 동작합니다.**

[![Build](https://github.com/lowqen/RapfiDroid/actions/workflows/build.yml/badge.svg)](https://github.com/lowqen/RapfiDroid/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/lowqen/RapfiDroid?include_prereleases&sort=semver)](https://github.com/lowqen/RapfiDroid/releases/latest)
[![License](https://img.shields.io/badge/license-GPL--3.0--or--later-blue)](LICENSES.md)
[![Android](https://img.shields.io/badge/Android-8.0%2B%20arm64-3DDC84)](https://github.com/lowqen/RapfiDroid/releases/latest)

[내려받기](#내려받기) · [기능](#기능) · [원리](#엔진이-폰에서-도는-방법) · [빌드](#소스에서-빌드하기) · [English](README.md)

</div>

## 이게 뭔가요

강한 오목 엔진은 전부 데스크톱 프로그램입니다. PC 와 콘솔을 전제하고, 보통은
말을 걸 상대 기계 한 대를 더 요구합니다. RapfiDroid 는 공개된 것 중 가장 강한
축에 드는 엔진인 [Rapfi](https://github.com/dhbloo/rapfi) 를 arm64 안드로이드로
크로스 컴파일해서, 앱 안에서 **진짜 프로세스로** 돌립니다. 보드도, 분석도, 금수
표시도, 국면 데이터베이스도 비행기 모드에서 그대로 동작합니다. 어디에도 열려
있는 포트가 없습니다.

인터페이스는 데스크톱 [Yixin-Board](https://github.com/dhbloo/Yixin-Board) 연구
환경을 이식한 것입니다. 그래서 모바일용으로 줄인 일부가 아니라 엔진 파라미터와
연구 도구가 통째로 건너왔습니다. 데스크톱 설정 67개가 전부 있고, PC 에서와 똑같은
방식으로 엔진을 구동합니다.

## 내려받기

[최신 릴리스](https://github.com/lowqen/RapfiDroid/releases/latest)에서 APK 를
받으세요. 안드로이드가 「출처를 알 수 없는 앱」 설치를 한 번 물어봅니다.

스토어 없이 업데이트 알림을 받으려면
[Obtainium](https://github.com/ImranR98/Obtainium) 을 설치하고 이 저장소 주소를
추가하면 됩니다. 새 릴리스마다 알림이 오고 한 번 눌러 갱신됩니다.

**필요 사양**

* 안드로이드 8.0 (API 26) 이상
* **arm64** 기기. 구형 armeabi·x86 기기에서는 엔진이 실행되지 않습니다.
* 저장공간 90 MB 정도. 신경망 가중치가 APK 안에 있어 첫 실행에 받을 것이 없습니다.

한국어 · 영어 · 중국어(간체)를 지원합니다.

## 기능

### 두고 분석하기

* 앱을 열면 엔진이 저절로 뜨고, 보드는 **[분석] · [대국]** 두 탭입니다. 탭마다 판이
  따로라 분석에서 늘어놓은 수가 대국 기보에 섞이지 않고, 두 판 모두 자동 저장됩니다
* 분석: 최선수 · 승률 · 평가 · 깊이(세부 수치는 펼쳐서), 멈춘 뒤에도 직전 결과가
  남고, 분석 시간 · 후보 수 · «정지하면 최선수 두기»는 접힌 설정에
* 대국: 흑/백 · 시간 제어(칩 + 직접 입력) · 노드로 정하는 레벨 5단 · 룰셋,
  무제한이면 「지금 두기」
* 판 위 값은 동그라미 없이 **글자만**(파랑 → 초록 → 빨강), 판 옆 세로 평가 바
* 국면 카드: 주형 이름 사슬과 흑 5수 유불리, 수 등급, DB 값 · 주석, 기보 메뉴
  (열기 · .psq/.sav 저장 · RapfiWeb 링크 복사 · 이미지)
* 렌주 금수 실시간 표시. 삼삼·사사·장련을 **엔진이 직접** 판정합니다(근사치가 아닙니다)
* 프리스타일·표준·렌주 규칙, 각각 전용 신경망
* 합법 착점 전부를 엔진 판단으로 색칠하는 보드 값 오버레이
* 보드 이미지 저장, 데스크톱과 같은 텍스트 형식의 국면 복사·붙여넣기, 되돌리기와
  분기 이동

### 연구하기

* **게임 리뷰.** 끝난 판을 한 수씩 되짚으며 평가가 실제로 뒤집힌 지점을 엔진이
  짚어 줍니다.
* **오프닝 익스플로러.** 눌러서 두는 판 위에 다음 수의 유불리 모양과 판 수를 그리고,
  1~4수 이름 249개, 흑 5수 유불리 11등급(모양 1,889개), 환원 수순을 보여 줍니다.
* **랭킹.** 26주형과 直·間 필터, 그리고 5수 모양 빈도.
* **국면 증명.** 평가만이 아니라 증명을 시키고, 깊이 예산을 직접 정합니다.
* **수순 탐색기**와 원시 프로토콜 명령을 넣는 엔진 콘솔.

### 정석

* **공식 커리큘럼.** 코스 › 레슨, 레슨마다 살펴보기(트리 · 해설 · 좋은 수 · 나쁜 수 ·
  함정 · 응징)와 학습(수순을 직접 맞히기, 힌트 · 점수 · 진도). RapfiWeb 이 싣는 그 파일을
  사이트에서 받아 오므로 새 레슨이 앱 업데이트 없이 들어오고, 레슨 링크
  (`rapfiweb.pages.dev/joseki?lesson=…`)는 앱에서 열립니다.
* **내 정석.** 판이나 `h8 i9 j10` 으로 수를 넣고, 갈래 · 평가 기호 · 마크다운 메모를
  달고, 지금 보드 국면으로 찾습니다(대칭 포함). RapfiWeb 과 같은 JSON 으로 주고받습니다.

### 배운 것을 남기기

* 엔진이 배운 국면을 폰 안의 데이터베이스 파일로 남기고 다음에 켤 때 이어 씁니다.
  앱이 엔진을 내리기 전에 저장 명령을 먼저 보내므로, 강제 종료로 한 세션을 통째로
  잃는 일이 없습니다.
* 기보 불러오기·내보내기, 그리고 데스크톱이 쓰는 `settings.txt`·`settings_dev.txt`
  파일을 그대로 주고받기.

## 엔진이 폰에서 도는 방법

당연하지 않은 부분이라 분명히 적어 둡니다.

안드로이드 10 부터 앱이 실행할 수 있는 파일은 **패키지 매니저가 네이티브 라이브러리
디렉터리로 추출해 둔 것**뿐이고, 그것도 이름이 `lib*.so` 여야 합니다. 그래서 엔진은
`libengine.so` 라는 이름의 평범한 PIE 실행 파일로 빌드되고, 패키지 매니저가 실제로
추출하도록 레거시 jniLibs 패키징으로 담기며, 데스크톱과 똑같은 piskvork 프로토콜을
stdin·stdout 으로 주고받는 **별도 프로세스**로 뜹니다.

JNI 가 아니라 별도 프로세스인 이유는 넷이고, 넷 다 실제로 물립니다.

1. Rapfi 는 트랜스포지션 테이블 할당에 실패하면 `exit` 를 부릅니다. 앱 프로세스
   안이라면 스택 트레이스 없는 크래시입니다.
2. 엔진이 작업 디렉터리를 바꿉니다. 호스트에 그 짓을 하는 라이브러리는 무관한
   코드를 깨뜨립니다.
3. 탐색은 앱을 풀지 않고 취소할 수 있어야 합니다.
4. GPL-3.0 코드와 BSD-2-Clause 인 이 앱 사이의 경계가 깨끗해집니다.

빌드는 믿는 게 아니라 검증합니다. upstream 이 `-ffp-contract=off` 로 컴파일하기
때문에 Rapfi 의 탐색 결과는 컴파일된 명령어 집합과 무관하고, 따라서 `BENCH` 가
폰에서도 x86 호스트와 같은 해시 `e240ab16` 을 찍어야 합니다. 빌드 스크립트는
aarch64 가 아니거나, `libc++_shared` 에 의존하거나, 저 플래그를 하나라도 잃은
바이너리를 거부합니다.
[tools/build_engine_android.sh](tools/build_engine_android.sh) 를 보세요.

## 실전 통계

오프닝 익스플로러와 랭킹의 실전 승률은 대국 데이터베이스가 있어야 채워집니다.
그 데이터베이스는 [RenjuNet](https://www.renju.net/game/) 이고, 라이선스가 오프라인
개인 사용은 허용하되 그 내용물 **또는 그로부터 파생된 무엇이든** 웹사이트나 온라인
시스템에 올리는 것을 금지합니다.

그래서 앱은 그 자료를 동봉하지도, 호스팅하지도, 받아 오지도 않습니다. 대신 변환
파이프라인 전체를 싣고 **기기 안에서** 팩을 만듭니다.

> 설정 ▸ **대국 데이터 추가** ▸ renju.net 열기 ▸ 내려받은 XML 고르기 ▸ 만들기

파일은 사용자가 공식 출처에서 직접 받고, 변환은 사용자 폰이 하고, 결과는 기기를
벗어나지 않습니다. 라이선스가 허용하는 유일한 경로이고, 대신 눌러 주는 다운로드
버튼이 없는 이유이기도 합니다.

변환기는 RapfiWeb 의 고속 경로를 이식한 것입니다. 43MB XML 을 바이트 그대로 흘려
읽어 대국을 정수 열에 쌓고, 모든 접두 국면을 **64비트 증분 Zobrist 정규 해시**(대칭
8종마다 XOR 한 번)로 세어 기수 정렬 한 번으로 통계를 만듭니다. 예전의 문자열 키
두 번 훑기가 몇 분 걸리던 것이 몇 초가 됩니다. 결과는 팩 파일 하나입니다. 해시가
같으면 정규 국면 키도 같다는 것을 무작위 국면과 모든 대칭에서 시험이 확인합니다.

## 서버 모드

원래 구조는 PC 의 Rapfi 와 네트워크로 대화하는 것이었고, 폰이 도저히 못 당하는
경우를 위해 그 모드도 남아 있습니다. 아주 큰 오프닝 데이터베이스나, 몇 시간씩
걸어 두는 분석 같은 것입니다.

**기본은 꺼져 있고 고급 설정 뒤에 있습니다.** 아래의 커리큘럼 파일 말고는, 직접 켜고
주소를 넣기 전에는 이 앱의 어떤 부분도 네트워크에 접속하지 않습니다.

### 저절로 하는 요청 하나

정석 탭은 공식 커리큘럼을 `https://rapfiweb.pages.dev/joseki/curriculum.json` 에서
받습니다. RapfiWeb 사이트가 보여 주는 바로 그 파일이라, 새 레슨이 앱 업데이트 없이
들어옵니다. 정적 파일 하나를 받는 요청이고 사용자 · 기보 · 기기에 관한 정보는 아무것도
보내지 않으며, 바뀌지 않았으면 빈 응답 하나로 끝납니다. **설정 ▸ 정석 커리큘럼 자동
업데이트**로 끌 수 있고, 꺼도 새로 고침 단추와 레슨 링크는 누를 때 받습니다. 그 밖의
모든 것은, 폰에 이미 있는 커리큘럼까지 포함해, 비행기 모드에서 그대로 동작합니다.

## 소스에서 빌드하기

```bash
git clone https://github.com/lowqen/RapfiDroid.git
cd RapfiDroid
./gradlew testDebugUnitTest assembleDebug
```

Android Studio Ladybug 이상, JDK 17, compileSdk 35. 저장소에 `libengine.so` 가
들어 있어서 클론 직후 바로 빌드되고 실행됩니다. 엔진을 직접 다시 빌드하려면
[tools/build_engine_android.sh](tools/build_engine_android.sh) 가 NDK 설치부터
결과 바이너리 검증까지 전부 하고,
[app/src/main/jniLibs/ENGINE_VERSION.txt](app/src/main/jniLibs/ENGINE_VERSION.txt)
에 커밋된 바이너리가 어느 엔진 커밋에서 나왔는지 적혀 있습니다.

유닛 테스트는 push 마다 돕니다. 엔진 문법, 국면 키, 팩 리더·라이터, 설정 코덱,
오프닝 표를 덮고 있고, 정규 키 같은 것을 리팩터링해도 되는 이유가 바로 이것입니다.

### 구조

```
app/src/main/java/dev/gomoku/rapfidroid/
  core/         보드 모델, 국면 키, 설정 표, 디자인 시스템
  data/         엔진 전송 계층, 스토어, 팩·DB 입출력
  domain/       엔진 세션, RIF 파싱, 집계, 팩 쓰기
  feature/      보드, 연구, DB, 연결, 설정, 첫 실행
tools/          엔진 크로스 컴파일과 검증
fdroid/         F-Droid 메타데이터와 빌드 레시피
docs/           배포, 라이선스 정리, 릴리스 노트
```

## 릴리스

태그를 밀면 CI 가 나머지를 합니다. 나쁜 APK 를 내보내느니 릴리스를 실패시키는
게이트가 둘 있습니다. 하나는 RenjuNet 파생 자료가 섞인 빌드를 거부하고, 다른
하나는 엔진이 들어 있고 안드로이드가 실제로 실행할 수 있는 형태로 담겼는지
확인합니다. [docs/publishing.md](docs/publishing.md) 를 보세요.

## 라이선스

RapfiDroid 자체 소스는 **BSD-2-Clause** 입니다. 배포되는 APK 에는 Rapfi 엔진이
함께 들어가고 그것이 **GPL-3.0-or-later** 이므로, 패키지 전체는
GPL-3.0-or-later 로 배포됩니다.

릴리스 APK 에 들어간 엔진의 대응 소스는 **https://github.com/lowqen/rapfi** 의
`android` 브랜치입니다. upstream 과의 차이는 안드로이드 빌드를 위한
`Rapfi/CMakeLists.txt` 의 블록 두 개뿐입니다.

전체 서드파티 고지는 [LICENSES.md](LICENSES.md) 에 있고, GPL-3.0 과 BSD-2-Clause
전문은 앱 안 설정 ▸ 정보 ▸ 라이선스 에 동봉돼 있습니다.

이 앱은 구글 플레이에 올릴 수 없습니다. 선택이 아니라 GPL-3.0 6조·10조와 플레이
배포 약관이 충돌한 결과입니다.

## 감사

* **[dhbloo](https://github.com/dhbloo)** 님. 이 앱을 쓸 만하게 만들어 주는 엔진
  [Rapfi](https://github.com/dhbloo/rapfi) 와, 이 인터페이스가 따라간
  [Yixin-Board](https://github.com/dhbloo/Yixin-Board) fork.
* **Kai Sun** 님. 원본 [Yixin-Board](https://github.com/accreator/Yixin-Board),
  BSD-2-Clause, 저작권 2009 ~ 2017.
* **[rapfi-networks](https://github.com/dhbloo/rapfi-networks)** 신경망 가중치,
  CC0 1.0.
* **[RenjuNet](https://www.renju.net/)** 사용자가 자기 통계를 만드는 근거가 되는
  대국 데이터베이스.

이 중 누구에게도 허락을 받을 필요가 없었고, 받지도 않았습니다. 저 라이선스들이
요구하는 것은 고지와 소스이며, 둘 다 위에 있습니다.
