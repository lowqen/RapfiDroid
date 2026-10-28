# UI 전면 개편 마스터플랜 — RapfiDroid × Mornye 벤치마크

> **읽는 법.** 2026-10-27 에 세운 계획서다. 엔진 최적화(`엔진-고도화-마스터플랜.md`)와 백그라운드
> 안정화를 마친 뒤, 앱의 UI/UX 를 전면 개편하기 위한 조사 · 갭 분석 · 단계 계획을 담는다.
> **승인 전에는 앱 코드를 고치지 않는다.** 4부의 각 Phase 는 승인된 뒤 하나씩 진행하고, 맨 끝
> 「실행 기록」에 결과를 쌓는다. 5부의 결정 여섯 개는 2026-10-27 에 확정됐다(전부 권장안대로).
>
> **출처.** Mornye `mornyeapp/Mornye@c90ec64`(2026-10-25) 의 `lib/theme/*`(README · `app_theme.dart` ·
> `app_tokens.dart` · `dynamic_color_wrapper.dart` · `app_motion.dart` · `material_expressive.dart` ·
> `mornye_theme.dart`), `lib/widgets/*`(`mornye_chrome.dart` · `app_bottom_sheet.dart` ·
> `settings_group.dart` · `app_content_card.dart` · `animation_utils.dart` · `mornye_bottom_bar.dart` ·
> `smoothed_progress.dart` · `scroll_edge_fade.dart`), `lib/providers/runtime_profile_provider.dart`.
> 이 저장소의 `core/designsystem/**`, `feature/**`, `navigation/**`, `MainActivity.kt`,
> `ThemeContrastTest.kt`, 그리고 RapfiWeb `.calm-design/DESIGN.md` · `docs/design-token-reconciliation.md`.
>
> **라이선스.** Mornye 는 GPL-3.0(+LGPL-3.0) 이고 이 앱은 BSD-2 다. **코드를 옮기지 않는다.**
> 가져오는 것은 기법 · 구조 · 수치 관례뿐이고, 모두 Compose 로 새로 쓴다.

## 결론 먼저

| | |
|---|---|
| Mornye 는 무엇인가 | **Flutter** 음악 플레이어(Compose 아님). 기법을 1:1 복사할 수 없고 Compose 로 번역해야 한다 |
| Mornye 에서 가장 값진 것 | ① 토큰을 `ThemeExtension` 하나로 모은 구조 ② 모든 모션을 «감속 모드면 원래 값» 게이트 뒤에 둔 것 ③ 기기 등급별로 유리 효과를 낮추는 3단계(flat · frosted · liquid) ④ 연결된 그룹(바깥 모서리만 둥글게, 행 사이 4dp) ⑤ 상태가 바뀌는 순간에만 쓰는 절제된 햅틱 |
| Mornye 에서 가져오지 않을 것 | iOS 흉내 2번째 스타일, 벽지 색을 앱 전체에 칠하는 Monet, 판 위의 블러 · 굴절 유리 |
| RapfiDroid 의 현재 수준 | 색 · 타이포 · 모양은 이미 **원칙 있는 시스템**(OKLCH 램프, 15슬롯 타입, 대비 시험)이다. 문제는 시스템이 아니라 **채택률과 구조**: 화면 쪽 `Spacing` 사용 0회 · 날 dp 382개, 탭 구현 4종, 세로 화면에서 판이 카드와 함께 스크롤되어 사라짐, 가로 화면에서 하단 바가 판 높이를 80dp 먹음, 햅틱 0 |
| 개편의 축 | **판은 고정하고 크롬을 움직인다.** 판 영역은 지금의 4대 모션 규칙을 그대로 지키고, 판 밖(시트 · 툴바 · 내비 · 목록)에만 스프링 · 시트 · 햅틱을 들인다 |
| 반드시 지킬 경계 | `data/**` · `domain/**` · `core/model/**` · `jniLibs/**`, ViewModel 공개 API, `Color.kt` 램프(RapfiWeb 골든 909값과 묶임), `drawBoard` 픽셀(PNG 내보내기와 공유), 탭 좌표 매핑 · 프루브 500ms 하트비트 |

## 상태판

| ID | 내용 | 상태 |
|---|---|---|
| P0 | 기반 정비 — 스크린샷 시험 · 토큰 채택 · 탭 단일화 · 햅틱/모션 인프라(시각 변화 없음) | **완료**(2026-10-27) — 실행 기록 P0 |
| P1 | 테마 시스템 — 토큰 확장 · AMOLED · (선택) 벽지 톤 · 테마 모드 3상태 | **완료**(2026-10-27) — 실행 기록 P1. 실기 확인 완료(2026-10-28, 사용자) |
| P2 | 셸 · 내비게이션 — 넓은 화면 레일 · 접히는 상단 바 · 스크롤 시 하단 바 숨김 | **완료**(2026-10-28) — 실행 기록 P2. 실기 확인 완료(2026-10-28, 사용자) |
| P3 | **보드 화면 재구성** — 고정 판 + 스냅 시트 · 떠 있는 이동 툴바 · 판 햅틱 | **완료**(2026-10-28) — 실행 기록 P3. 실기 확인 남음 |
| P4 | 컴포넌트 갱신 — 연결 그룹 · 시트화 · 목록 행 · 빈/로딩 상태 · 하드코딩 색 정리 | 계획 |
| P5 | 모션 · 햅틱 마감 — 스프링 프리셋 적용 · 목록 스태거 · 진행률 평활화 | 계획 |
| P6 | 검증 · 릴리스 — 실기(A16) NPS/프레임 · TalkBack · 글꼴 배율 · RapfiWeb 대조 | 계획 |

---

# 1부. Mornye 코드 분석

## 1.1 테마 시스템 — ColorScheme · 다이내믹 컬러 · AMOLED

- **두 스타일, 하나의 계약.** `AppThemeStyle { material, mornye }` 둘 다 `useMaterial3: true` 의
  `ThemeData` 를 만든다. iOS 풍 «Mornye» 스타일도 자기 색을 M3 `ColorScheme` 역할에 **매핑**할 뿐
  역할을 대체하지 않는다. 그래서 위젯은 `colorScheme`/`textTheme` 만 읽고 스타일을 모른다.
- **다이내믹 컬러(Monet).** `DynamicColorWrapper` 가 `DynamicColorBuilder` 아래에서 두 스타일을 모두
  만든다(스타일 전환 때 앱 전체가 재마운트되어 상태를 잃던 문제 때문). 동적 색이 켜져 있고 기기가
  주면 벽지 스킴의 **전 역할을 하나하나 복사**하고(`_toFlutterColorScheme`, fixed 역할까지),
  아니면 `ColorScheme.fromSeed(seed)`. Mornye 스타일은 «벽지 색과 독립»을 명시한다.
- **AMOLED.** 다크 스킴에 덧씌우는 방식(`_applyAmoledColors`): `surface`=검정,
  컨테이너 사다리 `#000 → #111111 → #161616 → #1F1F1F → #2A2A2A`. 주석: «M3 다크 톤 간격
  (low −2, high +5, highest +10)을 유지하되 톤 7 근처에 앵커해서, 그림자가 안 보이는 검정 위에서도
  메뉴가 떠 보이게». 앱 바는 검정 + `scrolledUnderElevation 0` + tint 투명, **내비 바만은
  `surfaceContainer` 층을 유지**해 검정 페이지와 분리한다.
- **고대비 존중.** `mornyeGlassContrastBlockedProvider` — 시스템이 고대비를 요청하면 장식적 반투명을
  끈다(사용자가 강제할 때만 예외).

## 1.2 타이포그래피 · Shape 토큰

- **토큰은 `ThemeExtension` 하나**(`AppTokens`): 반경 badge 6 · thumb 8 · cover 12 · control 16 ·
  card 20 · sheet 28, 간격 4 · 8 · 12 · 16 · 24, 최소 터치 48, 모션 150 · 250 · 400ms, 행 패딩
  (가로 20 · 세로 16 · 스위치 행 12 · 아이콘 간격 16 · 구분선 들여쓰기 56) 등 35개.
  `context.tokens` 로만 읽고 테마 밖(테스트 · 프리뷰)에서는 `AppTokens.standard` 로 떨어진다.
  데스크톱용 `AppTokens.desktop` 은 **바뀌는 값만** `copyWith` 로 덮는다(밀도 −2).
- **모양 규칙.** 행동(버튼)은 전부 Stadium(완전한 알약), 면(카드 20 · 다이얼로그/시트 28)은 토큰 반경.
  메뉴는 control 16, 메뉴 항목은 cover 12 반경으로 4dp 안쪽에.
- **타이포.** 번들 가변 글꼴(Google Sans Flex)에서 display/headline/title 만 `ROND 100`(둥근 끝),
  body/label 은 `ROND 0` — «긴 글과 작은 라벨은 각진 끝이 더 또렷하다». wght 축은 건드리지 않아
  `fontWeight` 가 계속 먹는다. iOS 스타일은 음수 자간(−0.08 ~ −0.24)을 쓴다.

## 1.3 컨테이너 · 레이아웃 · 반투명

- **카드.** 그림자 0, `surfaceContainerLow`, 반경 20. 누를 수 있는 카드는 M3E filled card 의
  눌림 피드백. iOS 스타일은 `onSurface` 를 α 0.10(다크)/0.07(라이트)로 깐 채움 + 반경 28.
- **연결 그룹**(`settings_group.dart` `connectedRowRadius`): 한 묶음의 **첫 행 위 · 끝 행 아래만 card
  반경, 행 사이는 4dp**, 구분선 대신 행 사이 틈. 넓은 화면은 `16 + 중앙 정렬 인셋`으로 모든 그룹 ·
  머리 · 안내 카드의 가장자리를 맞춘다.
- **유리.** `MornyeGlass` — 블러 시그마를 `[2, 3, 6, 16]` 네 단계로 **양자화해 필터 객체를 공유**
  (슬라이더 프레임마다 가우시안을 새로 만들지 않음). 틴트 불투명도 0.60(보통)/0.80(강), 다크는 ×0.82.
  **기기 등급**(`MornyeGlassLevel`): 저사양(arm32 · 저램) → `flat`(불투명), 그 외 `frosted`(블러 한 번),
  여유 있으면 `liquid`(굴절). 사용자 «항상 블러» 강제 스위치.
- **스크롤 가장자리 페이드**(`ScrollEdgeFade`): 더 스크롤할 내용이 있을 때만 끝단에 그라데이션.

## 1.4 컨트롤 · 내비게이션

- **하단 바.** M3E 내비 바(높이 72). 넓은 화면은 접힌 M3E 레일 + 목적지 사이를 스프링으로 이동하는
  알약 지시자. **크롬 최소화**(`MornyeChromeController`): 의도적인 세로 드래그가 누적 28px 를 넘으면
  하단 바를 접고, 맨 위로 돌아오면 편다. 가로 스크롤 · 프로그램 스크롤 · 바운스는 무시.
- **바텀시트**(`AppBottomSheet`): 손잡이 · 제목 · 높이 상한 · 키보드 인셋 · 안전 영역을 한 곳에서.
  긴 내용은 `DraggableScrollableSheet` + 스냅. 데스크톱에선 같은 내용을 **크기 등급(작/중/대)의
  가운데 다이얼로그**로. 시트 최대 폭 640.
- **세그먼트.** M3E 연결 버튼 그룹, 감속 모드면 정적 `SegmentedButton`.

## 1.5 모션 · 피드백

- **스프링을 커브로**(`SpringCurve`): M3E 스프링을 시뮬레이션해 **정착 시간을 계산**하고 그 길이의
  커브로 만든다 → 암시적 애니메이션이 물리 운동이 끝나는 순간 정확히 끝난다.
- **두 종류**: spatial(위치 · 크기 · 배율, 살짝 overshoot) / effects(불투명도 · 색, overshoot 없음).
  각각 fast · default · slow. 시트/메뉴 등장은 **임계 감쇠 slow spatial**(면이 자기 크기를 넘어
  뒤를 드러내지 않게), 닫힘 200ms.
- **게이트.** 모든 프리셋은 `materialExpressiveEnabled(context)`(= 감속 모드 아님)일 때만 스프링,
  아니면 **호출부가 준 원래 spec**. 모션은 늘 «대체값과 함께» 호출된다.
- **목록 스태거**: 40ms 간격, **최대 10개**까지만, 다시 스크롤해 올 때는 안 함.
- **진행률 평활화**(`SmoothedProgress`): 첫 값은 즉시, 단조 증가만 보간, 리셋 · 완료는 즉시 스냅 —
  폴링을 늘리지 않고 부드럽게.
- **모양 모핑**: 선택된 행이 알약으로 변형(spatial 스프링).
- **햅틱**(전수): `selectionClick` 8 · `mediumImpact` 9 · `lightImpact` 2 · `heavyImpact` 1.
  탭 전환, 빠른 스크롤러 잡기 시작, 슬라이더가 원점을 **넘는 순간**, 선택 모드 진입. 모든 터치가
  아니라 **상태 문턱**에서만 울린다.

## 1.6 이 앱에 맞지 않는 것

| Mornye 기법 | 안 가져오는 이유 |
|---|---|
| 두 번째(iOS) 스타일 | 유지비 2배. 이 앱의 정체성은 이미 «먹과 한지» 한 벌 |
| 앨범아트 · 벽지에서 뽑은 색으로 크롬 전체 칠하기 | 이 앱에서 **색은 값이다**(`Color.kt` 머리말). 초록=균형인 판 옆에 벽지 초록 버튼은 충돌 |
| 판 위 블러 · 굴절 | 판 위 숫자 칩 가독성 · 엔진이 코어 전부(최대 8 스레드)를 쓰는 동안 GPU/발열 비용 |
| Flutter 의 predictive back 끄기 | Flutter 버그 우회일 뿐. Compose 는 predictive back 을 켤 수 있다 |

---

# 2부. RapfiDroid 현재 UI

## 2.1 인벤토리

| 층 | 파일 | 요지 |
|---|---|---|
| 테마 | `theme/Theme.kt` | 라이트/다크 스킴 **30역할 전부** 채움. 다이내믹 컬러 **의도적으로 미제공**(주석에 근거). 다크 여부는 `settings.txt` 27행(데스크톱이 기준) |
| 색 | `Color.kt` · `Oklch.kt` | OKLCH 톤 램프 9개(Ink · Neutral · Sage · Clay · WoodNight · Signal · Result×2 …). hex 는 시드뿐. RapfiWeb 와 909값 골든 일치 |
| 타이포 | `Type.kt` | RapfiSans(Pretendard 서브셋 가변) 15슬롯, 음수/0 자간, `tabular()`(tnum) |
| 모양 | `Shape.kt` | 4 · 8 · 12 · 16 · 28 다섯 단계 |
| 간격 | `Spacing.kt` | 2 · 4 · 8 · 12 · 16 · 24 |
| 모션 | `Motion.kt` | 4대 규칙: 미끄러지지 않음 · 200ms 이하 · 실시간 숫자 보간 금지 · 프루브 하트비트 유지. 150/180/200ms tween |
| 판 | `GomokuBoard.kt` · `BoardSkin.kt` | Canvas 하나에 나무 · 격자 · 돌(그림자 + 방사 그라데이션 + 림) · 값 칩 · 프루브 · 배지. `drawBoard` 는 PNG 내보내기와 공유 |
| 컴포넌트 | `Calm.kt` · `AppChrome.kt` | CalmCard(흰 면 + 머리카락 테두리) · Disclosure(접힘 기억) · ChoiceChips · RapfiTopBar(52dp + 엔진 칩 + 테마 토글) · EngineChip · QuietSwitch(크로스페이드) · EmptyState |
| 셸 | `RapfiNavHost.kt` | Scaffold + **6탭** `NavigationBar`(보드 · 연구 · 정석 · DB · 엔진 · 설정), 엔진 탭에 상태 점 |
| 화면 | `feature/**` 약 12,000줄 | 보드(Screen · Chrome · Panels · Dialogs · UserToolbar), 연구(리뷰 · 익스플로러 · 랭킹 · 수순), 정석(커리큘럼 · 퀴즈 · 내 정석), DB, 엔진 연결 · 콘솔, 설정, 도구 |
| 시험 | `ThemeContrastTest.kt` | AA 대비 · 결과 3색 분리 · 두 판 스킨 가독성 · 다크 판이 화면에서 가장 밝지 않음 · 램프 단조성 |

## 2.2 개편 시 절대 건드리지 않을 경계선

**A. 손대지 않는 층(파일 단위)**

| 경계 | 이유 |
|---|---|
| `data/**`(특히 `engine/` — `EngineService` 포그라운드 서비스 · `LocalEngineTransport` 프로세스 · `MemoryGuard` · `ThermalMonitor` · `EngineIdleWatcher`), `data/rif/PackBuildService` | 백그라운드 안정화 결과물. UI 개편과 무관 |
| `domain/**`, `core/model/**` | 순수 로직 · 프로토콜 모델 |
| `app/src/main/jniLibs/**`, `tools/build_engine_android.sh` | 엔진 바이너리는 JNI 가 아니라 `libengine*.so` 실행 파일을 `ProcessBuilder` 로 띄운다 — UI 와 결합점 없음 |
| `AndroidManifest.xml` 의 서비스 · 권한 · 딥링크 intent-filter | 백그라운드 · 링크 열기 동작 |

**B. 손대되 계약은 지키는 층**

| 결합점 | 지켜야 할 계약 |
|---|---|
| ViewModel (`BoardViewModel` 938줄 등) | `uiState` 흐름과 `on*` 핸들러 시그니처 그대로. 새 UI 가 필요한 표시값은 **feature 쪽 순수 매퍼**로 파생(ViewModel 로직 수정 금지). 불가피하면 별도 승인 |
| `ShellViewModel` | 엔진 자동 시작 · `lessonLink` 이동 · `darkMode` 토글 의미 유지 |
| `GomokuBoard` 입력 | `rememberUpdatedState` 로 핸들러를 읽는 구조(분석 스트리밍 중 탭 유실 수정) · `BoardGeometry.cellAt` · 롱프레스는 있을 때만 |
| `drawBoard` | 화면과 PNG 내보내기(`renderBoardPng`)가 **같은 함수**. 화면 전용 연출은 기본값이 «연출 없음»인 파라미터로만 |
| 값 칩 사다리 `TagPalette` · `BoardSkin` · 프루브 500ms 하트비트(main.c:9206) | Yixin 사용자가 외운 극(極). 재설계 대상 아님 |
| `Color.kt` 램프(hue · chroma) | RapfiWeb `tokens.golden.json` 909값과 골든 시험으로 묶임. **역할 배정**만 바꿀 수 있다 |
| `ThemeContrastTest` | 새 스킴(AMOLED 등)은 이 시험을 통과해야 들어온다 — 시험을 **늘리지**, 약하게 하지 않는다 |
| `Destination.route`, `LocalUiPrefs` 의 Disclosure id, `settings.txt` 키 | 저장된 상태 · 링크 · 데스크톱 설정 호환 |
| `tr(ko, en, zh)` 문자열 · `semantics` 설명 | 3개 국어와 TalkBack |

**C. 성능 경계** — 분석 중 `BoardUiState` 는 초당 여러 번 갱신되고 엔진은 코어 전부를 쓴다(R3).
판 주변에 **상시 도는 애니메이션 · 블러 · 셰이더를 두지 않는다**. 판 영역의 재구성 범위를 넓히는
변경(예: 판을 감싸는 애니메이션 컨테이너)은 P6 의 NPS · 프레임 측정 대상이다.

## 2.3 현재 UI 의 문제 — 컴포넌트별

| 구분 | 문제 | 근거 |
|---|---|---|
| **보드(세로)** | 판 · 이동 바 · 카드가 **한 `verticalScroll`** 안에 있어, 카드를 읽으려 내리면 판이 화면 밖으로 사라진다. «판이 주인공» 원칙과 정면 충돌 | `BoardScreen.kt` 세로 분기 |
| **보드(가로)** | 판 높이 = `maxHeight − 16dp` 인데 하단 내비 바(≈80dp)와 상단 바(52dp)가 그 높이를 먹는다 | `RapfiNavHost` 의 단일 `NavigationBar`, 레일 없음 |
| 상단 바 | 보드 탭에서도 «보드» 제목이 한 줄을 차지(탭 이름 반복). 모든 화면 고정 52dp, 스크롤 반응 없음 | `RapfiTopBar` |
| 내비 | 6탭(M3 권장 3~5)을 기본 `NavigationBar` 로 — 라벨이 빽빽. 넓은 화면 대응 없음 | `Destinations.kt` |
| 탭/세그먼트 | 구현 4종: 보드 `ModeTabs`(자작 알약) · 정석/도구 `TabRow` · 연구 `ScrollableTabRow` · 랭킹 `SegmentedButton` | grep |
| 카드 | `ConnectionScreen` 만 Material `Card` 원본(다른 면 색), 나머지는 `FlatCard as Card`. CalmCard 는 `shapes.large`(16)인데 `Shape.kt` 문서는 카드=medium(12) | grep · `Calm.kt` |
| 토큰 채택 | `feature/**` 의 `Spacing` 사용 **0회**, 날 dp **382개**. `RoundedCornerShape(n.dp)` 직접 5곳, 평가 막대 3/6dp 임의 반경 | grep |
| 하드코딩 색 | `feature/**` 13곳 — 정석 퀴즈 `0xFF2E8B57` · `0xFFFF6B5E` · `0xFF6BE38A` · `0xFFF2E7D3`, 프루브/리뷰 배지 등. 다크 모드 · 대비 시험 밖 | `JosekiParts.kt` · `CurriculumScreens.kt` · `BoardDialogs.kt` |
| 다이얼로그 | 보드에만 `AlertDialog` 8개(균형 · 국면 문자열 · 판 글자 · 주석 · 스왑 …). 입력이 있는 것도 다이얼로그 | `BoardDialogs.kt` |
| 모션 | 앱 전체 `AnimatedVisibility` 3 · `Crossfade` 2 · `animateFloatAsState` 4. 누름 피드백은 리플뿐 | grep |
| 햅틱 | **0** | grep |
| 테마 | 라이트/다크 수동 토글만(시스템 따름 없음), AMOLED 없음 | `Theme.kt` · `ShellViewModel` |
| 검증 수단 | 대비 시험은 있으나 **스크린샷 시험 없음**, 로컬 Gradle 없음(빌드는 CI) | `build.yml` |

---

# 3부. 벤치마크 이식 · 갭 분석

## 3.1 오목 앱에 맞춘 세 원칙

1. **판은 움직이지 않는다.** 판 영역(나무 · 돌 · 평가 막대)은 현행 `Motion.kt` 4대 규칙을 그대로
   지킨다. Mornye 의 스프링 · 시트 · 최소화는 **판 밖 크롬**에만 쓴다.
2. **크롬은 판보다 조용하다.** 반투명 · 블러는 판과 겹치지 않는 곳에서만, 그리고 기기 등급 · 고대비 ·
   «분석 중» 여부로 꺼진다. 판 위에 떠 있는 면을 두지 않는다.
3. **색은 값, 모양과 움직임은 상태.** 색을 새로 쓰지 않고 상태를 알려야 할 때는 **모양 모핑 · 굵기 ·
   햅틱**으로 말한다(Mornye 의 선택 시 알약 모핑이 좋은 예).

## 3.2 1:1 매핑

| 오목 앱 대상 | Mornye 기법 | Compose 번역 | 판단 |
|---|---|---|---|
| 토큰 전반 | `AppTokens` ThemeExtension + `context.tokens` 폴백 | `RapfiTokens` data class + `staticCompositionLocalOf` (`RapfiTheme.tokens`), `Spacing`/`Shapes` 흡수, 넓은 화면용 `copy()` 변형 | **채택** |
| 다크 / AMOLED | 다크 위에 덧씌우는 검정 + 톤 사다리, 내비만 컨테이너 층 유지 | `Neutral` 램프의 낮은 톤(따뜻한 hue 유지)으로 `surface`=0 · 컨테이너 사다리 구성, `BoardSkin.Dark` 유지 | **채택**(옵션) |
| 다이내믹 컬러 | 벽지 스킴 전 역할 복사 | **크롬 톤만**: 벽지 primary 의 **hue 만** 가져와 `Ink`/`Neutral` 램프(채도 0.007~0.02)에 넣기. Sage · Clay · Signal · 결과색 · 판은 불변 | **조건부**(D1) |
| 고대비 | 고대비면 장식 끔 | `AccessibilityManager` 고대비 · 애니메이션 배율 0 감지 → 반투명 · 스프링 끔 | **채택** |
| 보드 화면(세로) | `DraggableScrollableSheet` + 스냅, 높이 상한 | 판 고정 + `BottomSheetScaffold`(또는 `AnchoredDraggable`) 스냅 3단: **피크**(판 아래 남은 높이, 최선수 · 승률 · 깊이 · 노드 요약 띠) / 절반 / 전체 | **채택 · 핵심** |
| 이동 바(\|< < ▶ > >\|) | 플로팅 컨트롤 · 미니플레이어 스와이프 | M3E `HorizontalFloatingToolbar` 형태의 알약 툴바(판 **아래**, 겹치지 않음), ▶ 는 FAB 형 주 행동. 툴바 가로 드래그 = 수순 스크럽(수마다 틱 햅틱) | **채택** |
| 분석/대국 전환 | M3E 연결 버튼 그룹 | `ButtonGroup`/연결 `ToggleButton`(가능 시) 또는 공용 `RapfiSegmented` 하나로 4종 탭 통일 | **채택** |
| 평가 막대 | (해당 없음) | 현행 유지. 반경을 토큰으로, 채움만 200ms(규칙 그대로) | 유지 |
| 상태 칩(엔진) | — | EngineChip 유지, 상태 전환 시 점 색은 effects 스프링(overshoot 없음), 탐색 중 점은 정지(상시 애니메이션 금지) | 소폭 |
| 하단 내비 | 72dp 바 · 넓은 화면 레일 · 스크롤 시 최소화 | 세로 폰: 바 유지(6탭, D3). **가로 · ≥600dp: `NavigationRail`**(판 높이 회수). 목록 화면만 28px 누적 스크롤 시 바 숨김, 보드 화면은 숨김 없음 | **채택** |
| 상단 바 | 접히는 큰 머리(120 → 접힘) | 목록 화면(설정 · DB · 랭킹): `LargeTopAppBar` + `exitUntilCollapsed`. 보드: 제목 없이 엔진 칩 · 기보 메뉴만 있는 낮은 바 | **채택** |
| 설정 | 연결 그룹(바깥만 둥글게, 행 사이 4dp), 넓은 화면 중앙 인셋 | `ConnectedGroup` 컴포넌트, 구분선 → 틈. CalmCard 원칙(면 위에 면 금지)과 합치 | **채택** |
| 다이얼로그 | 폰=시트, 데스크톱=크기 등급 다이얼로그 | 입력형(국면 문자열 · 판 글자 · 주석 · 균형)은 `ModalBottomSheet`, **결정형**(스왑 · 스왑2 · 다섯째 수 · 기권 확인)은 다이얼로그 유지 | **채택** |
| 기보 목록 · 랭킹 · DB 행 | 썸네일 반경 토큰, 행 패딩 토큰, 선택 행 알약 모핑 | `MiniBoard` 반경 = thumb 토큰, 선택 시 모양 모핑, 목록 스태거(최대 10) | **채택** |
| 진행률(팩 빌드 · 리뷰) | `SmoothedProgress` | 단조 증가만 보간 · 리셋 즉시 — **요약 값만**(규칙 3과 합치) | **채택** |
| 로딩 | M3E 모양 모핑 로딩 · 스켈레톤 | `LoadingIndicator`(가능 시) · 정적 스켈레톤(분석 중 시머 금지) | 부분 |
| 블러 유리 | 3단 등급 · 양자화 시그마 | 1차엔 **블러 없음**, 시트 스크림과 떠 있는 툴바에만 불투명 틴트. 블러는 P5 이후 실측으로 판단 | **보류**(D2) |
| 햅틱 | 문턱에서만 | `LocalHapticFeedback`: 착수 `SegmentTick`, 금수 · 둘 수 없음 `Reject`, 5목 · 승부 `Confirm`, 롱프레스 `LongPress`, 스크럽 `SegmentFrequentTick`, 토글 `ToggleOn/Off`. 엔진의 착수는 무진동. 설정에 끄기 | **채택** |
| 화면 전환 | FadeForwards | `QuietSwitch` 크로스페이드 유지 + predictive back 활성 | 유지 + 보강 |

---

# 4부. 단계별 실행 계획

> 공통 규칙. 각 Phase 는 **별도 커밋 묶음**으로, CI(`build.yml`: 단위 시험 + APK)가 녹색이어야 다음으로
> 간다. 화면을 바꾸는 Phase 는 P0 의 스크린샷 골든을 **의도적으로 갱신**하고 그 diff 를 실행 기록에 남긴다.

## P0. 기반 정비 — 보이는 변화 없음

| | |
|---|---|
| 대상 | `core/designsystem/**`, 테스트 소스, 빌드 스크립트 |
| 작업 | ① **스크린샷 시험 하네스**(Roborazzi + Robolectric — JVM 단위 시험이라 CI 에서 돈다). P0 의 대상은 **상태 없이 그릴 수 있는 것**: 판(분석 값 · 금수 · 최선수), 평가 막대, 이동 바, 단계 바, 카드 · 접힘 · 칩 · 엔진 칩 · 상단 바 · 빈 상태, 보드의 분석/국면/대국 카드 × 라이트/다크 × 글꼴 1.0/1.3. 화면 전체(보드 · 연구 · 정석 · DB · 엔진 · 설정)는 ViewModel 을 받으므로, 각 화면을 «상태 없는 본문 + ViewModel 연결»로 나누는 P2 ~ P4 에서 그 화면의 골든을 함께 추가한다 ② BOM `2026.09.00` 이 해석하는 material3 버전 확인 — **결과는 아래 «P0 확인: material3 1.4.0»** ③ `RapfiTokens` 도입(기존 `Spacing`/`Shapes` 값 그대로 이관 — 값 불변) ④ 공용 `RapfiSegmented` 추출 — P0 에서는 보드의 `ModeTabs` 만 옮긴다(외형 동일). 정석 · 도구 `TabRow`, 연구 `ScrollableTabRow`, 랭킹 `SegmentedButton` 을 바꾸면 **보이는 변화**라 P2(연구 · 정석) · P4(도구 · 랭킹)에서 옮긴다 ⑤ `ChromeMotion`(구현 이름 — 판에는 쓰지 않는다는 뜻을 이름에 담음. spatial/effects × fast/default/slow, **감속 모드면 호출부 대체값**) · `RapfiHaptics`(설정 게이트) API 만 추가, 아직 호출 안 함 ⑥ `ConnectionScreen` 의 원본 `Card` → `FlatCard`(콘솔 카드가 페이지와 같은 색이라 경계가 안 보이던 것 — 이 Phase 의 유일한 의도된 시각 변화) |
| 시각 변화 | 없음(④⑥ 은 미세 정렬 차이만) |
| 검증 | 스크린샷 골든 최초 기록, 이후 P0 커밋들의 diff ≈ 0. `ThemeContrastTest` 녹색. 의존성 추가는 F-Droid 빌드 조건(`fdroid/`) 확인 후 |

**P0 확인: material3 1.4.0** — `compose-bom-2026.09.00.pom` 은 `material3 1.4.0` · `ui 1.12.1` 을 고른다.
`material3-android-1.4.0.aar` 의 클래스를 직접 확인한 결과:

| API | 1.4.0 | 비고 |
|---|---|---|
| `MotionScheme`(`standard()` · `expressive()`, spatial/effects × fast/default/slow) · `MaterialTheme(motionScheme=)` | **있지만 `internal`** | 처음엔 클래스 파일에 실험 표식이 없어 «안정»으로 잘못 적었다. Kotlin `internal` 은 바이트코드 접근 플래그에 안 드러난다 — CI 컴파일(`Cannot access 'interface MotionScheme': it is internal`)과 `-sources.jar` 로 바로잡음. `ChromeMotion` 은 그 소스의 `ExpressiveMotionTokens` 값으로 `spring()` 을 직접 만든다 |
| `ShortNavigationBar` · `WideNavigationRail` | **있음 · public**(`-sources.jar` 로 확인) | P2 의 하단 바 · 레일 후보 |
| `ButtonGroup` · `ToggleButton` · `HorizontalFloatingToolbar` · `LoadingIndicator` · `MaterialExpressiveTheme` | **없음** | material3 1.5.0 에만(지금 beta02). P3 · P4 는 이 앱의 컴포넌트(`RapfiSegmented` 등)로 만들고, 1.5 가 안정되면 래퍼 안쪽만 바꾼다 |
| `HapticFeedbackType` `Confirm` · `Reject` · `SegmentTick` · `SegmentFrequentTick` · `ToggleOn/Off` · `LongPress` | 있음(ui 1.8+) | `RapfiHaptics` 가 쓴다 |

## P1. 테마 시스템

| | |
|---|---|
| 대상 | `Theme.kt` · `Color.kt`(역할만) · `RapfiTokens` · 설정 화면 «화면» 묶음 |
| 기법 | Mornye 1.1 · 1.2 |
| 작업 | ① 테마 모드 **3상태**(시스템 · 라이트 · 다크, D4) ② **AMOLED** 옵션 — `Neutral` 따뜻한 hue 로 검정 사다리, 내비만 컨테이너 층 유지, 판은 `BoardSkin.Dark` 그대로 ③ (D1 승인 시) **벽지 톤** — 크롬 램프 hue 만 벽지에서 ④ 토큰 확장: 반경 badge · thumb · control · card · sheet, 행 패딩 · 최소 터치 48, CalmCard 반경을 문서와 일치시킴 ⑤ 버튼 모양 규칙(행동=알약, 면=토큰) |
| 시각 변화 | AMOLED 에서 순검정 페이지 위 따뜻한 회색 카드, 벽지 톤에서 크롬 회색이 미세하게 벽지 쪽으로. 판 · 값 칩 · 결과색은 변화 없음 |
| 검증 | `ThemeContrastTest` 에 AMOLED 스킴 · 벽지 톤 극단 hue(0° · 120° · 240°) 케이스 추가, 결과색과 크롬 색의 ΔE 하한 시험. RapfiWeb 골든(램프)은 불변이어야 함 |

## P2. 셸 · 내비게이션

| | |
|---|---|
| 대상 | `RapfiNavHost.kt` · `AppChrome.kt`(`RapfiTopBar`) · `Destinations.kt` · `MainActivity` |
| 기법 | Mornye 1.4(레일 · 크롬 최소화 · 접히는 머리) |
| 작업 | ① 폭 ≥600dp 또는 가로 → `NavigationRail`(엔진 상태 점 유지), 세로 폰 → 하단 바 ② 보드 화면 상단 바: 제목 제거, 엔진 칩 · 테마 · 기보 메뉴만(높이 축소) ③ 목록 화면: 접히는 큰 상단 바 ④ 목록 화면만 스크롤 시 하단 바 숨김(누적 28px · 맨 위에서 복귀 · 가로/프로그램 스크롤 무시) ⑤ predictive back 활성(`enableOnBackInvokedCallback`) 및 시트 우선 닫힘 순서 확인 |
| 시각 변화 | 가로 화면에서 판이 **약 80dp 커짐**, 보드 세로 화면 상단 여백 감소, 설정/DB 는 큰 제목이 스크롤에 접힘 |
| 검증 | 스크린샷(폰 세로 · 가로 · 태블릿 840dp), 판 한 변 길이 수치 비교(전/후), 딥링크 `lessonLink` 이동 · 탭 상태 복원 수동 확인 |

## P3. 보드 화면 재구성 — 핵심

| | |
|---|---|
| 대상 | `BoardScreen.kt` · `BoardChrome.kt` · `BoardPanels.kt`(배치만) · `UserToolbar.kt` · `GomokuBoard.kt`(입력 콜백 래핑만) |
| 기법 | 스냅 시트 · 플로팅 툴바 · 연결 버튼 그룹 · 문턱 햅틱 |
| 작업 | ① **세로: 판 고정 + 스냅 시트**. 피크 = 판 아래 남은 높이에 «요약 띠»(최선수 좌표 · 승률 · 깊이 · 노드/초 — 모두 `tabular()`, 보간 없음). 끌어올리면 분석 · 그래프 · 국면 · 도구 카드. 판은 절대 가려지지 않는 높이까지만 피크 ② **이동 툴바**: 판 바로 아래 알약 툴바, ▶/⏸ 가 주 행동. 툴바 위 가로 드래그로 수순 스크럽 ③ 분석/대국 전환 = `RapfiSegmented`, «새 판»은 툴바 오버플로 또는 시트 머리로 ④ 햅틱 연결(착수 · 금수 · 승부 · 롱프레스 · 스크럽) — `GomokuBoard` 바깥에서 `onTap` 결과(BoardUiState 변화)를 보고 판단, 판 내부 코드는 불변 ⑤ 가로: 현행 2단 유지 + 레일(P2)로 판 확대, 오른쪽 열 머리에 세그먼트 |
| 하지 않는 것 | 돌 착수 애니메이션(`drawBoard` 가 PNG 와 공유, 규칙 «판은 움직이지 않는다»). 필요하면 P5 에서 «마지막 수 링 120ms 페이드» 하나만 별도 승인 |
| 시각 변화 | 세로 폰에서 카드를 읽는 동안에도 판이 늘 보임. 화면 하단이 «요약 띠 + 알약 툴바»로 정돈. 탭/버튼 줄이 하나로 |
| 검증 | 스크린샷(피크 · 절반 · 전체), **분석 스트리밍 중 탭 유실 0**(빠른 연속 탭 50회 수동), 실기 A16 에서 분석 30초 노드/초 전후 비교(±3% 이내 — 엔진 CI 판정 규칙 준용), 시트 드래그 중 프레임 드랍(`dumpsys gfxinfo` 95퍼센타일) |

## P4. 컴포넌트 갱신

| | |
|---|---|
| 대상 | `Calm.kt` · `SettingsScreen` · `DatabaseScreen` · `ConnectionScreen` · 연구 4탭 · 정석 · `BoardDialogs` |
| 작업 | ① `ConnectedGroup`(바깥 card 반경 · 행 사이 4dp · 구분선 → 틈)으로 설정 · 엔진 · DB 옵션 재구성 ② 입력형 다이얼로그 4개 → 시트(손잡이 · 제목 · 키보드 인셋 · 안전 영역을 공용 `RapfiSheet` 한 곳에서), 결정형은 다이얼로그 유지 ③ 목록 행(랭킹 · 리뷰 · 익스플로러 · 기보): 토큰 패딩, `MiniBoard` thumb 반경, 선택 행 알약 ④ 하드코딩 색 13곳 → 역할/`RapfiColors` 로(정석 퀴즈 정답/오답은 `positive`/`Signal` 계열) ⑤ 빈 상태 · 로딩 · 스낵바(떠 있는 형태, 토큰 반경) 통일 ⑥ `feature/**` 날 dp 를 토큰으로(레이아웃 리듬만 — 그리기 치수는 제외) |
| 시각 변화 | 설정이 «한 장짜리 카드 + 접힘»에서 «연결된 그룹»으로, 입력 상자가 아래에서 올라오는 시트로, 정석 퀴즈 표식이 다크에서도 읽힘 |
| 검증 | 스크린샷 전 화면, `ThemeContrastTest` 에 퀴즈 표식 색 추가, ko/en/zh 긴 문자열 줄바꿈 확인(글꼴 1.3) |

## P5. 모션 · 햅틱 마감

| | |
|---|---|
| 작업 | ① `RapfiMotion` 프리셋을 크롬에 적용: 시트 등장 = 임계 감쇠, 칩 · 버튼 = spatial fast, 색 · 불투명도 = effects ② 목록 스태거(40ms · 최대 10 · 재진입 시 없음) ③ 진행률 평활화(팩 빌드 · 리뷰 진행) ④ 선택 상태 모양 모핑 ⑤ 설정 «진동» 스위치 ⑥ (선택) 블러 실험 — 시트 스크림 한 곳, 기기 등급 · 분석 중이면 끔 |
| 지키는 것 | 판 영역 4대 규칙. 실시간 수치(깊이 · 노드 · 속도 · 시계)는 어떤 경우에도 보간하지 않음 |
| 검증 | 개발자 옵션 «애니메이션 끄기»에서 모든 전환이 즉시/크로스페이드로 떨어지는지, 햅틱 끄기 동작, A16 분석 중 NPS 재측정 |

## P6. 검증 · 릴리스

| | |
|---|---|
| 작업 | ① 실기(갤럭시 A16): 분석 5분 NPS · 발열 단계(T1) 전후 비교, 시트 · 스크롤 프레임 통계 ② TalkBack 전 화면 순회(시트 · 툴바 · 세그먼트의 role/selected) ③ 글꼴 배율 1.3 · 2.0 ④ RapfiWeb 대조 — `design-token-reconciliation.md` 에 «역할 배정 차이» 절 추가(D5) ⑤ 릴리스 노트 · 스크린샷 갱신 |
| 완료 조건 | CI 녹색, 스크린샷 골든 승인, NPS 하락 없음(판정 규칙 내), 접근성 체크리스트 통과 |

---

# 5부. 결정 — 2026-10-27 확정

모두 아래 «권장» 열대로 확정됐다. D4 는 AMOLED 를 다크의 선택 사항으로 포함한다.

| ID | 질문 | 확정 |
|---|---|---|
| D1 | 다이내믹 컬러 | **크롬 톤만, 기본 꺼짐**. 벽지 hue 를 `Ink`/`Neutral` 저채도 램프에만 — 의미 색 · 판은 불변. 전면 Monet 은 비권장(`Theme.kt` 의 기존 판단 유지) |
| D2 | 블러 · 반투명 유리 | **1차 출시엔 없음**. 불투명 틴트만. 블러는 P5 에서 실측 후 시트 스크림 한 곳만 |
| D3 | 하단 탭 6개 | **6 유지**(RapfiWeb 보존 계약 «레일 6»), 가로/넓은 화면은 `NavigationRail` 로 판 높이 회수 |
| D4 | 테마 모드 | **시스템 · 라이트 · 다크 3상태 + AMOLED 옵션**. 데스크톱 `settings.txt` 를 들여온 경우는 그 값을 그대로(현 동작) |
| D5 | RapfiWeb 동기화 | 시트 · 레일 · 햅틱은 **안드로이드 고유로 갈라짐을 허용**하고, 색 램프 · 토큰 값만 계속 일치. 차이는 문서로 기록 |
| D6 | 모션 규칙 개정 | `Motion.kt` «미끄러지지 않는다 · 200ms 이하»를 **판 영역 규칙**으로 좁히고, 크롬(시트 · 툴바 · 내비)은 스프링 허용. 감속 모드면 전부 현행 값 |
| (부) | 스크린샷 시험 의존성 | **승인.** Roborazzi(Apache-2.0) · Robolectric(MIT) — `testImplementation`, 그리고 `ui-test-manifest` 는 `debugImplementation`(디버그 APK 에 시험용 액티비티 하나). 릴리스 APK · F-Droid 빌드에는 들어가지 않는다 |

# 6부. 리스크

| 리스크 | 대응 |
|---|---|
| 시트가 판을 가림 / 피크 높이가 기기마다 다름 | 피크 = `화면 높이 − 판 − 툴바` 로 계산, 최소 높이 미만이면 요약 띠만 |
| M3 Expressive 컴포넌트가 1.4.0 에 없음(P0 확인) | 내비 · 레일은 1.4.0 public API 로, 모션은 `ChromeMotion`(토큰 값 직접). 버튼 그룹 · 플로팅 툴바 · 로딩은 이 앱 컴포넌트로 만들고, 1.5 안정 후 래퍼 안쪽만 교체 |
| UI 재구성 범위 확대로 분석 중 재구성 비용 증가 → NPS · 발열 | 요약 띠 · 툴바를 `BoardUiState` 의 필요한 필드만 읽는 작은 컴포저블로 분리, P3 · P6 에서 NPS 측정 |
| 로컬 Gradle 없음 | 모든 판정은 CI(단위 시험 + 스크린샷) + 실기. 메모리 «성능 판정은 CI» 원칙 준용 |
| 다이얼로그 → 시트 전환 시 키보드 · 뒤로가기 순서 | 공용 `RapfiSheet` 한 곳에서 IME 인셋 · predictive back 처리 |
| GPL 코드 혼입 | Mornye 코드는 참조만, 붙여 넣지 않는다. 수치(반경 · 간격 등)는 일반 관례 수준으로만 차용 |

# 실행 기록

## P0 — 2026-10-27 완료

| 항목 | 결과 | 커밋 |
|---|---|---|
| ① 스크린샷 하네스 | Roborazzi 1.76.0 + Robolectric 4.17(native graphics, SDK 35, `ko-rKR`, 420dp xhdpi). Gradle 플러그인 없이 `-Pscreenshots=record\|verify\|compare` → 시험 시스템 속성. `screenshots.yml`: push 마다 verify, 수동 실행으로 record | `9aa6b26` · `99340c4` · `7007bcb` |
| 골든 | 26장 = 7 대상 × 라이트/다크 × 글꼴 1.0/1.3(판은 1.0 만 — 판 글자는 `Paint` 라 글꼴 배율을 안 탄다). 리팩터 **전** 코드(`99340c4`)에서 CI 러너가 기록(run 40520019661) | `1cd38e3` |
| ② material3 확인 | 1.4.0. `MotionScheme` 는 **internal**, `ShortNavigationBar` · `WideNavigationRail` 은 public, 버튼 그룹 · 플로팅 툴바 · 로딩은 1.5 전용(위 «P0 확인» 표) | `8eea2a3` · `172be4b` |
| ③ `RapfiTokens` | 모서리 · 간격 · 화면 패딩 · 터치 크기를 한 값으로. `Shapes` · `Spacing` · `ScreenPadding` 은 거기서 만든다. 값 불변(`TokensTest`) | `3f17f19` |
| ④ `RapfiSegmented` | 보드 `ModeTabs` 를 그대로 옮김. 나머지 탭 3종은 P2 · P4 | `3f17f19` |
| ⑤ `ChromeMotion` · `RapfiHaptics` | 스프링 6종(1.4.0 `ExpressiveMotionTokens` 값) + 시트용 임계 감쇠, 감속 모드면 호출부 대체값. 햅틱 6어휘(`HapticsTest`). 아직 호출처 없음 | `3f17f19` · `172be4b` |
| ⑥ 콘솔 카드 | Material `Card`(페이지색) → `FlatCard`. 이 Phase 의 유일한 의도된 시각 변화(골든 대상 밖) | `e83e3d8` |
| **판정** | P0 리팩터 뒤 verify 가 시험을 **실제로 실행**해 26장 전부 통과(run 40522216391). 음성 대조 — 골든 한 장을 바꾼 임시 브랜치 — 는 정확히 `evalBar[light_100]` 하나로 실패(run 40522230502). 임시 브랜치는 지움 | — |

배운 것:

- **실험 표식이 없다고 public 은 아니다.** Kotlin `internal` 은 클래스 파일 접근 플래그에 안 나온다. API 판정은 `-sources.jar` 로 한다.
- **골든은 Gradle 입력이 아니다.** 처음 만든 음성 대조는 `testDebugUnitTest FROM-CACHE` 로 «통과»했다 — 아무것도 비교하지 않은 것. 스크린샷 모드에서는 캐시 · up-to-date 를 끈다(`7007bcb`). verify 가 녹색이면 로그에서 `Task :app:testDebugUnitTest` 뒤에 `FROM-CACHE` 가 없는지 본다.
- **아티팩트 다운로드가 계정 전송량 한도(HTTP 503)에 걸릴 수 있다.** 재시도로 받았다. 반복되면 record 워크플로가 골든을 브랜치로 커밋하는 방식으로 바꾼다.

## P1 — 2026-10-27 완료

| 항목 | 결과 | 커밋 |
|---|---|---|
| ① 테마 모드 3상태(D4) | 설정 «테마»: 기기 설정 · 라이트 · 다크. 라이트/다크는 지금처럼 settings.txt 27행에 쓰고(내보낸 파일에 남음), «기기 설정»만 앱 설정(`theme.followSystem`, 기본 꺼짐 — 업데이트로 누구의 테마도 바뀌지 않게). **PC 설정 파일을 들여오면 꺼진다.** 상단 바 해/달 버튼은 «화면에 보이는 것»의 반대를 고르고 «기기 설정»을 끈다. `ShellViewModel` · `SettingsViewModel` 은 손대지 않음(화면 쪽에서 처리) | `6017ac1` |
| ② AMOLED | 다크 위에 덧씌움: 페이지 톤 0, 컨테이너 5 · 7 · 12 · 17(Mornye 의 `#111` · `#161616` · `#1F1F1F` · `#2A2A2A` 와 같은 L\*), 내비 바는 컨테이너 층 유지, 판은 어두운 나무 그대로 | `6017ac1` |
| ③ 벽지 톤(D1) | Android 12+, 기본 꺼짐. 벽지 스킴 primary 의 OKLCH hue 만 `Ink` · `Neutral` · `NeutralVariant` 에(채도 그대로). 회색 벽지(채도 < 0.02)면 적용 안 함. Sage · Clay · Signal · 결과색 · 판은 불변. 색조 없음 = 이전과 **같은 인스턴스**(시험) | `6017ac1` |
| ④ 토큰 | 카드 반경은 처음부터 16 이었다(RapfiWeb `.panel` = `--radius-l`). 틀린 것은 `Shape.kt` 의 «카드 = 12» 한 줄 → 값은 그대로 두고 이름을 `radiusInset 12` · `radiusCard 16` 으로 | `6017ac1` |
| ⑤ 버튼 모양 | 확인만: 버튼 모양을 덮어쓴 곳이 하나도 없다 — 전부 Material 기본 알약. 이미 규칙대로 | — |
| 시험 | `ThemeContrastTest` +7: 색조 없음 + 15° 간격 24개 × 라이트/다크/AMOLED 에서 글자 쌍 전부 AA · 앱 고유색 AA · 크롬 채도 ≤ 0.025 · primary 와 의미색의 OKLab 거리 ≥ 0.08 · AMOLED 사다리. 수치는 커밋 전에 `Oklch.kt` 를 Python 으로 옮겨 미리 계산(최악 4.57:1 · 0.020 · 0.111) | `6017ac1` |
| 골든 | `theme_amoled` · `theme_tone250_light/dark` · `theme_tone30_light` 4장(run 40524985009). P1 push 의 verify 는 새 4장만 «골든 없음»으로 실패하고 기존 26장은 통과 — P1 이 기존 화면을 한 픽셀도 바꾸지 않았다는 증거. 같은 record 가 26장을 바이트까지 똑같이 다시 썼다. 30장 verify 통과(run 40525403581, 시험 실제 실행) | `f4dcc26` |

남은 것(실기에서만 확인 가능):

- **벽지 톤**: Robolectric 에는 벽지가 없어 `wallpaperHue()` 는 시험되지 않았다(골든은 hue 를 직접 넣어 그림). A16 에서 켜고 벽지를 바꿔 색조가 따라오는지 본다.
- **«기기 설정» 전환**: 폰의 다크 모드를 바꿨을 때 앱과 상태 바 아이콘이 함께 바뀌는지 본다.
- 설정 화면의 새 «테마» 줄은 화면 전체 골든이 없는 화면이라 골든 밖이다(P4 에서 설정 화면을 상태 없는 본문으로 나눌 때 들어간다).

## P2 — 2026-10-28 완료

| 항목 | 결과 | 커밋 |
|---|---|---|
| ① 레일 | 폭 600dp 이상(`RailLayoutMin`, Material compact/medium 경계 — 옆으로 누인 폰은 전부 해당)이면 6탭이 `NavigationRail` 로. 엔진 상태 점 유지. 높이 480dp 미만이면 선택된 탭만 라벨, 레일은 잘리지 않고 스크롤. 레일이 시작 쪽 인셋을 가져가므로 본문은 그 인셋을 다시 먹지 않게 소비 | `57a8c4a` · `2efdd93` |
| ② 보드 헤더 | 옆으로: **헤더 없음** — 판은 높이로 크기가 정해지므로 높이를 전부 판에. 엔진 칩 · 테마 버튼은 카드 열 맨 위(`ShellActions`). 세로: 제목 없는 48dp 헤더(선택된 탭이 이미 «보드»라 말함). 기보 메뉴 이동은 P3 | `57a8c4a` |
| ③ 접히는 헤더 | **설정 · DB 두 화면만**. 112dp → 스크롤하면 52dp(지금의 바와 같음). 계획의 «목록 화면 전부»에서 줄임: 연구(오프닝 탐색) · 정석(레슨)은 판이 있는 화면이라, 스크롤하지 않는 동안 큰 머리가 판 높이를 ~60dp 계속 빼앗는다 — «판이 주인공» 우선. Material 바와 앱 바의 액션 끝 여백(4 · 8dp)을 맞춰 탭 사이에서 칩이 움직이지 않게 | `57a8c4a` · `9e128bf` |
| ④ 하단 바 숨김 | `BarHider`: 손가락 스크롤만(플링 꼬리 · 콘솔 자동 스크롤 · 가로 스와이프 무시), 28dp 누적. 아래로 숨김, **위로 28dp 또는 맨 위에서 다시 보임**(Mornye 는 맨 위에서만 — 여기선 6탭이 앱을 도는 길이라 맨 위까지 올라가게 하지 않는다). 보드 탭에선 꺼짐. 스크롤을 소비하지 않고 보기만 함. 등장/퇴장은 `ChromeMotion`(감속 모드면 150~180ms) | `57a8c4a` |
| ⑤ 뒤로 | predictive back 켬(`enableOnBackInvokedCallback`). 화면 전환은 150ms 크로스페이드로 — Navigation 기본값은 700ms 페이드였다(`QuietSwitch` 규칙과 어긋나 있던 것) | `57a8c4a` |
| 탭 단일화(P0 ④ 이월분) | 연구(`ScrollableTabRow`) · 정석(`TabRow`) → `RapfiSegmented(fill = true)`(같은 폭으로 나누고 긴 라벨은 말줄임). 도구 · 랭킹은 P4 | `57a8c4a` |
| 골든 | `shell_nav`(하단 바 · 레일 둘) · `headers`(보드 헤더 · 접히는 헤더 · 꽉 찬 세그먼트) × 4 = 8장. 첫 verify 는 새 8장만 «골든 없음», 기존 30장 통과 — 헤더를 `ShellActions` 로 쪼갠 것이 기존 화면을 바꾸지 않았다. 38장 verify 통과(run 40529587726, 시험 실제 실행) | `a144481` |

**판 크기(계산 · A16 가로 ≈ 891 × 411dp, 상태 바 24dp · 제스처 바 16dp 가정).** 전: 높이 411 − 24 − (80 + 16) 하단 바 − 53 헤더 = 238 → 판 한 변 222dp. 후: 411 − 24 − 16 = 371 → **355dp(+133dp, +60%)**. 계획의 «약 80dp»보다 큰 것은 헤더(53dp)도 함께 빠졌기 때문. 실측은 아래.

배운 것: `Column` · `Row` 는 저마다 레이아웃 범위(DslMarker)라 그 안에서 바깥 `BoxWithConstraints` 의 `maxWidth` 를 바로 못 읽는다 — 블록 첫머리에서 지역 변수로 받아 둔다(`2efdd93`).

남은 것(실기):

- **판 한 변 실측**: A16 가로에서 보드 탭 캡처 → 계산값 355dp 와 대조.
- **predictive back**: 연구 → 뒤로 제스처가 보드로 미리보기와 함께 돌아가는지, 정석 레슨 안에서는 한 단계씩(`BackHandler`) 올라가는지, 시트 · 다이얼로그가 먼저 닫히는지.
- **하단 바 숨김**: 설정 · DB 에서 내렸다 올렸을 때, 엔진 콘솔이 스스로 스크롤할 때(숨으면 안 됨).
- **딥링크**: 정석 레슨 링크로 열었을 때 정석 탭으로 가고 탭 상태가 복원되는지.

P1 · P2 의 실기 확인 항목(벽지 톤 · «기기 설정» 전환 · 판 실측 · predictive back · 하단 바 숨김 · 딥링크)은
2026-10-28 사용자가 실기에서 확인을 마쳤다고 알려 왔다(항목별 수치는 전달받지 않음).

## P3 — 2026-10-28 완료

| 항목 | 결과 | 커밋 |
|---|---|---|
| ① 고정 판 + 시트 | 세로: 판과 툴바는 고정, 카드는 Material `BottomSheetScaffold` 위에. 시트는 요약 줄(최선수 · 승률 · 평가 · 깊이 / 대국이면 결과 · 차례 · 대진)에 얹혀 쉰다. **피크 = 판이 남긴 높이** — 쉬는 동안 판을 한 칸도 가리지 않는다. 화면이 짧아 그게 안 되면 판이 손잡이 + 요약 줄(88dp)만큼만 줄어든다(`portraitMetrics`, `PortraitMetricsTest`: A16 판 383dp · 피크 276dp, 360 × 640 판 320dp · 피크 88dp, 모든 조합에서 판 + 툴바 + 피크 = 높이). 시트 면은 카드 면 색, 손잡이는 Material 48dp 대신 24dp | `957b177` · `9ef67b2` |
| 3단 → 2단 | 계획의 «피크 / 절반 / 전체» 대신 **피크 / 전체**. 절반 정지를 넣으려면 드래그 · 중첩 스크롤 · 접근성 동작을 손으로 짜야 하는데, 이 PC 에선 제스처를 돌려 볼 수 없다. 시험된 Material 시트를 택하고, 절반이 아쉬우면 나중에 | — |
| ② 이동 툴바 | 알약 하나: 새 판 · `|<` `<` ▶ `>` `>|`, 대국 탭에선 ▶ 자리에 진영 전환(항상 6칸이라 탭 사이에 모양이 같다). ▶ 는 툴바의 단 하나의 주 행동(분석 중 = 채운 먹색). **스크럽**: 알약을 따라 끌면 28dp 마다 한 수, 한 수마다 틱 — 버튼과 같은 undo/redo 를 타고 같은 데서 멈춘다. 좁은 열에선 버튼이 36dp 까지 줄어 넘치지 않는다 | `957b177` |
| ③ 분석 \| 대국 · 새 판 | 분석 \| 대국은 보드 헤더 왼쪽(`RapfiTopBar` 의 `leading`), 새 판 · 진영 전환은 툴바로. `StageBar` · `MoveTransport` 삭제 | `957b177` |
| ④ 햅틱 | 내 돌이 놓임 `SegmentTick`, 거절된 탭 `Reject`, 승부 남 `Confirm`, 판 글자 롱프레스 `LongPress`, 스크럽 틱. ViewModel 은 손대지 않고 상태 변화로 맞춘다: 탭한 점에 1.5초 안에 돌이 놓이면 «놓임», 그 사이 알림이 뜨면 «거절». 엔진의 수는 무진동 | `957b177` |
| ⑤ 가로 | 두 칸 그대로, 오른쪽 열: 엔진 칩 → 분석 \| 대국 → 카드(스크롤) → 툴바 | `957b177` |
| 하지 않은 것 | 돌 착수 애니메이션(계획대로 — `drawBoard` 는 PNG 와 공유, 판 영역 규칙) | — |
| 골든 | 새로: 세로 판 + 쉬는 시트(4) · 올린 시트(2) · 가로(2). 의도된 변경: `board_chrome`(툴바 · 요약 줄) · `theme_*`(단계 바 → 모드 스위치). 나머지는 바이트까지 같게 재기록. 46장 verify 통과(run 40533128777, 시험 실제 실행) | `9b95b8d` |

배운 것: 공개 함수의 **매개변수 타입**이 실험 API(`SheetValue`)면 함수에 `@OptIn` 을 붙여도 모든 호출부가 opt-in 을 요구받는다. 실험 타입은 시그니처 밖으로(`sheetUp: Boolean`, `9ef67b2`).

남은 것(실기, A16):

- **탭 유실**: 분석 스트리밍 중 빠른 연속 탭 50회 — 전부 들어가는지(판 입력 코드는 손대지 않았지만 판을 감싼 레이아웃이 바뀌었다).
- **NPS**: 분석 30초 노드/초, P3 전 기준값과 ±3% 이내인지(기준값을 재 두지 않았다면 이번 빌드와 직전 릴리스 APK 로 같은 국면을 비교).
- **시트**: 끌어올리기 · 내리기, 카드 스크롤과 시트 드래그가 이어지는지, 뒤로 제스처가 펼친 시트를 먼저 접는지, 드래그 중 프레임.
- **스크럽 · 햅틱**: 끌기 감도(28dp/수)가 맞는지, 틱 · 놓임 · 거절 진동의 세기가 거슬리지 않는지.

### P3 실기 피드백 반영 — 2026-10-29

| 피드백 | 원인 · 조치 | 커밋 |
|---|---|---|
| 뒤로가기가 «캡처 뜬 것처럼» 끊겨 보임 | P2 에서 켠 predictive back 의 제스처 미리보기. 다시 끔 — 일반 뒤로 + 150ms 크로스페이드 | `219f18b` |
| 스크롤 안 되는 곳에서 끌어도 하단 바가 사라짐 | `BarHider` 가 손가락 이동량(`available`)을 셌다. 목록이 **실제로 스크롤한 양**(`consumed`)만 센다. `BarHiderTest` | `219f18b` |
| 엔진 카드의 분석/정지 · 최선수 · 승률 · 평가 · 깊이 삭제 | 요약 줄과 툴바 ▶ 가 이미 보여 준다. 카드에는 상태 · «직전 국면» 안내 · 세부 수치 · 후보 수순만. 가로 화면에도 요약 줄을 둠 | `ffcbeb5` |
| 요약 줄 깊이를 n/n 으로 | `18/27`(깊이 / 선택 깊이) | `ffcbeb5` |
| 분석을 멈추면 글자가 너무 흐림 | 직전 국면 결과를 55% 로 흐리던 것을 없앰 — 늘 분석 중과 같은 색, 어느 국면의 결과인지는 카드의 글로 | `ffcbeb5` |

골든 20장 갱신(엔진 카드 · 요약 줄이 든 것만), 46장 verify 통과(`f3775ec`).
