# UI 전면 개편 마스터플랜 — RapfiDroid × Mornye 벤치마크

> **읽는 법.** 2026-10-27 에 세운 계획서다. 엔진 최적화(`엔진-고도화-마스터플랜.md`)와 백그라운드
> 안정화를 마친 뒤, 앱의 UI/UX 를 전면 개편하기 위한 조사 · 갭 분석 · 단계 계획을 담는다.
> **승인 전에는 앱 코드를 고치지 않는다.** 4부의 각 Phase 는 승인된 뒤 하나씩 진행하고, 맨 끝
> 「실행 기록」에 결과를 쌓는다. 5부의 「결정 필요」 여섯 개는 Phase 1 전에 답이 있어야 한다.
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
| P0 | 기반 정비 — 스크린샷 시험 · 토큰 채택 · 탭 단일화 · 햅틱/모션 인프라(시각 변화 없음) | 계획 |
| P1 | 테마 시스템 — 토큰 확장 · AMOLED · (선택) 벽지 톤 · 테마 모드 3상태 | 계획 |
| P2 | 셸 · 내비게이션 — 넓은 화면 레일 · 접히는 상단 바 · 스크롤 시 하단 바 숨김 | 계획 |
| P3 | **보드 화면 재구성** — 고정 판 + 스냅 시트 · 떠 있는 이동 툴바 · 판 햅틱 | 계획 |
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
| 작업 | ① **스크린샷 시험 하네스**(Roborazzi + Robolectric — JVM 단위 시험이라 CI `testDebugUnitTest` 안에서 돈다): 보드(세로/가로) · 연구 · 정석 · DB · 엔진 · 설정 × 라이트/다크 × 글꼴 1.0/1.3 ② BOM `2026.09.00` 이 실제로 해석하는 material3 버전 확인 — M3 Expressive(`MotionScheme` · `ButtonGroup` · `HorizontalFloatingToolbar` · `LoadingIndicator` · `ShortNavigationBar`)가 안정 API 인지 실험 API 인지 표로 남김 ③ `RapfiTokens` 도입(기존 `Spacing`/`Shapes` 값 그대로 이관 — 값 불변) ④ 공용 `RapfiSegmented` 로 탭 4종 단일화(외형은 현 ModeTabs 그대로) ⑤ `RapfiMotion`(spatial/effects × fast/default/slow, **감속 모드면 호출부 대체값**) · `RapfiHaptics`(설정 게이트) API 만 추가, 아직 호출 안 함 ⑥ `ConnectionScreen` 의 원본 `Card` → `FlatCard` |
| 시각 변화 | 없음(④⑥ 은 미세 정렬 차이만) |
| 검증 | 스크린샷 골든 최초 기록, 이후 P0 커밋들의 diff ≈ 0. `ThemeContrastTest` 녹색. 의존성 추가는 F-Droid 빌드 조건(`fdroid/`) 확인 후 |

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

# 5부. 결정 필요 (Phase 1 전)

| ID | 질문 | 권장 |
|---|---|---|
| D1 | 다이내믹 컬러 | **크롬 톤만, 기본 꺼짐**. 벽지 hue 를 `Ink`/`Neutral` 저채도 램프에만 — 의미 색 · 판은 불변. 전면 Monet 은 비권장(`Theme.kt` 의 기존 판단 유지) |
| D2 | 블러 · 반투명 유리 | **1차 출시엔 없음**. 불투명 틴트만. 블러는 P5 에서 실측 후 시트 스크림 한 곳만 |
| D3 | 하단 탭 6개 | **6 유지**(RapfiWeb 보존 계약 «레일 6»), 가로/넓은 화면은 레일. 5로 줄이려면 «엔진» 탭을 상단 칩 → 시트로 흡수하는 안이 있음 |
| D4 | 테마 모드 | **시스템 · 라이트 · 다크 3상태**. 데스크톱 `settings.txt` 를 들여온 경우는 그 값을 그대로(현 동작) |
| D5 | RapfiWeb 동기화 | 시트 · 레일 · 햅틱은 **안드로이드 고유로 갈라짐을 허용**하고, 색 램프 · 토큰 값만 계속 일치. 차이는 문서로 기록 |
| D6 | 모션 규칙 개정 | `Motion.kt` «미끄러지지 않는다 · 200ms 이하»를 **판 영역 규칙**으로 좁히고, 크롬(시트 · 툴바 · 내비)은 스프링 허용. 감속 모드면 전부 현행 값 |
| (부) | 스크린샷 시험 의존성 | Roborazzi(Apache-2.0) 추가 — 시험 전용(`testImplementation`)이라 APK · F-Droid 빌드 영향 없음 확인 후 |

# 6부. 리스크

| 리스크 | 대응 |
|---|---|
| 시트가 판을 가림 / 피크 높이가 기기마다 다름 | 피크 = `화면 높이 − 판 − 툴바` 로 계산, 최소 높이 미만이면 요약 띠만 |
| M3 Expressive API 가 실험 API | P0 에서 버전 표 작성. 실험 API 는 얇은 래퍼 한 곳에서만 호출해 교체 비용을 가둔다 |
| UI 재구성 범위 확대로 분석 중 재구성 비용 증가 → NPS · 발열 | 요약 띠 · 툴바를 `BoardUiState` 의 필요한 필드만 읽는 작은 컴포저블로 분리, P3 · P6 에서 NPS 측정 |
| 로컬 Gradle 없음 | 모든 판정은 CI(단위 시험 + 스크린샷) + 실기. 메모리 «성능 판정은 CI» 원칙 준용 |
| 다이얼로그 → 시트 전환 시 키보드 · 뒤로가기 순서 | 공용 `RapfiSheet` 한 곳에서 IME 인셋 · predictive back 처리 |
| GPL 코드 혼입 | Mornye 코드는 참조만, 붙여 넣지 않는다. 수치(반경 · 간격 등)는 일반 관례 수준으로만 차용 |

# 실행 기록

(승인 후 Phase 별로 결과를 쌓는다.)
