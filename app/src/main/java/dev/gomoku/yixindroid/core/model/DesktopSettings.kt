package dev.gomoku.yixindroid.core.model

/** The two files the desktop persists settings in, parsed strictly by position. */
enum class SettingsFile(val fileName: String, val lineCount: Int) {
    MAIN("settings.txt", 47),
    DEV("settings_dev.txt", 20),
}

/** UI grouping only — the file order is what the desktop cares about. */
enum class SettingCategory(val label: String) {
    GAME("대국"),
    ENGINE("엔진"),
    DISPLAY("표시"),
    DATABASE("데이터베이스"),
    RESEARCH("연구·검토"),
    APPEARANCE("외관"),
    SYSTEM("시스템"),
}

data class ChoiceOption(val value: Int, val label: String)

sealed interface SettingEditor {
    data object Toggle : SettingEditor
    data class Number(val min: Long, val max: Long, val unit: String = "") : SettingEditor
    data class Choice(val options: List<ChoiceOption>) : SettingEditor
    data object Text : SettingEditor

    /** Shown but not editable: reserved slots and one-time markers. */
    data object Fixed : SettingEditor
}

/**
 * One persisted setting: where it lives in the desktop files, how it is edited,
 * and how it maps onto [AppSettings]. [line] is 1-based and assigned from the
 * table position, so the table *is* the file layout — a line cannot be dropped
 * or reordered by accident (the desktop reads both files positionally, so a
 * dropped line would silently shift every setting after it).
 */
class SettingSpec internal constructor(
    val id: String,
    val file: SettingsFile,
    val line: Int,
    /** The trailing `;comment` the desktop writes — reproduced verbatim. */
    val comment: String,
    val label: String,
    val category: SettingCategory,
    val editor: SettingEditor,
    /** `INFO <key>` this drives, when it is an engine parameter. */
    val engineKey: String?,
    /** Anything the user should know: desktop-only, pending phase, caveats. */
    val note: String?,
    private val getter: (AppSettings) -> String,
    private val setter: (AppSettings, String) -> AppSettings,
) {
    val isText: Boolean get() = editor is SettingEditor.Text

    fun read(settings: AppSettings): String = getter(settings)

    /** Apply a raw file/UI value, clamped and validated like `load_setting` does. */
    fun write(settings: AppSettings, raw: String): AppSettings = setter(settings, raw)

    /** The exact line the desktop would write (fonts use no tab before the `;`). */
    fun render(settings: AppSettings): String =
        if (isText) "${read(settings)};$comment" else "${read(settings)}\t;$comment"
}

/**
 * The complete desktop settings inventory: 47 + 20 = **67 entries**, in file
 * order. Source of truth: `save_setting()` / `load_setting()` in
 * `Yixin-Board/main.c` (11598 / 14046) checked against the deployed
 * `test-yixin/settings.txt` and `settings_dev.txt`.
 */
object DesktopSettings {

    val MAIN: List<SettingSpec> = build(SettingsFile.MAIN) {
        int(
            "boardSize", "board size (10 ~ 22)", "보드 크기", SettingCategory.GAME,
            SettingEditor.Number(5, 22), note = "변경하면 엔진을 다시 시작하고 판을 비웁니다",
            get = { it.boardSize }, set = { s, v -> s.copy(boardSize = v) },
        )
        int(
            "language", "language (0: English, 1,2,...: custom)", "언어", SettingCategory.APPEARANCE,
            SettingEditor.Choice(
                listOf(
                    ChoiceOption(0, "English"), ChoiceOption(1, "简体中文"),
                    ChoiceOption(2, "繁體中文"), ChoiceOption(3, "한국어"),
                    ChoiceOption(4, "日本語"), ChoiceOption(5, "Русский"),
                    ChoiceOption(6, "Tiếng Việt"),
                ),
            ),
            note = "앱 UI는 한국어 고정 — 이 값은 PC와 주고받기 위해 보관됩니다(P11에서 적용)",
            get = { it.language }, set = { s, v -> s.copy(language = v) },
        )
        int(
            "rule",
            "rule (0: freestyle, 1: standard, 2: free renju, 3: swap after 1st move, 5: soosorv, 6: swap-2)",
            "규칙", SettingCategory.GAME,
            SettingEditor.Choice(
                listOf(
                    ChoiceOption(0, "프리스타일 오목"), ChoiceOption(1, "표준 오목"),
                    ChoiceOption(2, "자유 렌주"), ChoiceOption(3, "첫 수 이후 교환"),
                    ChoiceOption(4, "RIF 오프닝 규칙"), ChoiceOption(5, "Soosorv-8 오프닝 규칙"),
                    ChoiceOption(6, "스왑2"),
                ),
            ),
            engineKey = "rule",
            note = "오프닝 규칙(3~6)의 엔진 기본 규칙은 각각 프리스타일/렌주/렌주/표준입니다",
            get = { it.rule }, set = { s, v -> s.copy(rule = v) },
        )
        bool(
            "computerBlack", "computer play black (0: no, 1: yes)", "컴퓨터가 흑돌 플레이",
            SettingCategory.GAME, note = "새 대국을 시작할 때 적용됩니다(보드 화면에서도 바꿀 수 있음)",
            get = { it.computerBlack }, set = { s, v -> s.copy(computerBlack = v) },
        )
        bool(
            "computerWhite", "computer play white (0: no, 1: yes)", "컴퓨터가 백돌 플레이",
            SettingCategory.GAME, note = "새 대국을 시작할 때 적용됩니다(보드 화면에서도 바꿀 수 있음)",
            get = { it.computerWhite }, set = { s, v -> s.copy(computerWhite = v) },
        )
        int(
            "level", "level (0: unlimited time 1: custom level 2-12: predefined level)",
            "레벨", SettingCategory.ENGINE,
            SettingEditor.Choice(
                buildList {
                    add(ChoiceOption(0, "무제한 시간"))
                    add(ChoiceOption(1, "사용자 정의 레벨"))
                    for (l in 2..11) add(ChoiceOption(l, "사전 정의 레벨 $l"))
                },
            ),
            engineKey = "max_node",
            note = "무제한/사전 정의는 노드 표를 쓰고, 사용자 정의만 아래 시간·깊이·노드를 씁니다",
            get = { it.level }, set = { s, v -> s.copy(level = v) },
        )
        int(
            "timeoutTurnSec", "time limit (turn)", "턴 시간", SettingCategory.ENGINE,
            SettingEditor.Number(1, 1_000_000, "초"), engineKey = "timeout_turn",
            note = "사용자 정의 레벨에서만 사용",
            get = { it.timeoutTurnSec }, set = { s, v -> s.copy(timeoutTurnSec = v) },
        )
        int(
            "timeoutMatchSec", "time limit (match)", "매치 시간", SettingCategory.ENGINE,
            SettingEditor.Number(1, 10_000_000, "초"), engineKey = "timeout_match",
            note = "사용자 정의 레벨에서만 사용",
            get = { it.timeoutMatchSec }, set = { s, v -> s.copy(timeoutMatchSec = v) },
        )
        int(
            "maxDepth", "max depth", "최대 깊이", SettingCategory.ENGINE,
            SettingEditor.Number(2, 484, "플라이"), engineKey = "max_depth",
            get = { it.maxDepth }, set = { s, v -> s.copy(maxDepth = v) },
        )
        long(
            "maxNode", "max node", "최대 노드 수", SettingCategory.ENGINE,
            SettingEditor.Number(1_000, 2_000_000_000), engineKey = "max_node",
            get = { it.maxNode }, set = { s, v -> s.copy(maxNode = v) },
        )
        int(
            "style", "style (rash 0 ~ 5 cautious)", "스타일", SettingCategory.ENGINE,
            SettingEditor.Choice(
                listOf(
                    ChoiceOption(0, "성급함 (0)"), ChoiceOption(1, "1"), ChoiceOption(2, "2"),
                    ChoiceOption(3, "3"), ChoiceOption(4, "4"), ChoiceOption(5, "신중함 (5)"),
                ),
            ),
            engineKey = "caution_factor",
            get = { it.style }, set = { s, v -> s.copy(style = v) },
        )
        int(
            "toolbarStyle",
            "toolbar style (0: only icon, 1: both icon and words, 2: both with horizontally stacked)",
            "툴바 스타일", SettingCategory.APPEARANCE,
            SettingEditor.Choice(
                listOf(
                    ChoiceOption(0, "아이콘만"), ChoiceOption(1, "아이콘+글자"),
                    ChoiceOption(2, "아이콘+글자(가로)"),
                ),
            ),
            note = "데스크톱 전용 — 값만 보관합니다",
            get = { it.toolbarStyle }, set = { s, v -> s.copy(toolbarStyle = v) },
        )
        bool(
            "showLog", "show log (0: no, 1: yes)", "로그", SettingCategory.DISPLAY,
            note = "연결 탭의 piskvork 원시 콘솔",
            get = { it.showLog }, set = { s, v -> s.copy(showLog = v) },
        )
        bool(
            "showNumber", "show number (0: no, 1: yes)", "번호 매기기", SettingCategory.DISPLAY,
            get = { it.showNumber }, set = { s, v -> s.copy(showNumber = v) },
        )
        bool(
            "showAnalysis", "show analysis (0: no, 1: yes)", "분석 표시", SettingCategory.DISPLAY,
            note = "보드 위 후보수 태그·실시간 표시",
            get = { it.showAnalysis }, set = { s, v -> s.copy(showAnalysis = v) },
        )
        bool(
            "showAnalysisWinrate", "show analysis winrate (0: no, 1: yes)", "승률 분석",
            SettingCategory.DISPLAY,
            note = "끄면 칸별 승률 태그를 기록하지 않습니다(실시간 후보·패배 표시는 유지) — main.c와 동일",
            get = { it.showAnalysisWinrate }, set = { s, v -> s.copy(showAnalysisWinrate = v) },
        )
        bool(
            "showWarning", "show warning (0: no, 1: yes)", "실행 취소 경고",
            SettingCategory.DISPLAY, note = "판 초기화처럼 되돌릴 수 없는 동작에 확인창",
            get = { it.showWarning }, set = { s, v -> s.copy(showWarning = v) },
        )
        int(
            "threadNum", "number of threads", "스레드 수", SettingCategory.ENGINE,
            SettingEditor.Number(1, 256), engineKey = "thread_num",
            note = "엔진이 알려주는 상한(MAX_THREAD_NUM)까지",
            get = { it.threadNum }, set = { s, v -> s.copy(threadNum = v) },
        )
        int(
            "hashSizeMb", "hash size (MB)", "해시 크기", SettingCategory.ENGINE,
            SettingEditor.Number(1, 65_536, "MB"), engineKey = "hash_size",
            note = "엔진에는 KB로 변환해 보냅니다(MB<<10)",
            get = { it.hashSizeMb }, set = { s, v -> s.copy(hashSizeMb = v) },
        )
        int(
            "multiPv", "default number of multi-pv", "멀티-PV 수", SettingCategory.ENGINE,
            SettingEditor.Number(1, 225), engineKey = "yxnbest",
            get = { it.multiPv }, set = { s, v -> s.copy(multiPv = v) },
        )
        bool(
            "blockAutoReset", "block autoreset (0: no, 1: yes)", "차단 자동 해제",
            SettingCategory.RESEARCH, note = "엔진 탭 도구에서 사용",
            get = { it.blockAutoReset }, set = { s, v -> s.copy(blockAutoReset = v) },
        )
        bool(
            "blockPathAutoReset", "blockpath autoreset (0: no, 1: yes)", "차단 경로 자동 해제",
            SettingCategory.RESEARCH, note = "엔진 탭 도구에서 사용",
            get = { it.blockPathAutoReset }, set = { s, v -> s.copy(blockPathAutoReset = v) },
        )
        bool(
            "pondering", "pondering (0: off, 1: on)", "사색", SettingCategory.ENGINE,
            engineKey = "pondering",
            get = { it.pondering }, set = { s, v -> s.copy(pondering = v) },
        )
        int(
            "vcThread", "checkmate in global search (0: no, 1: vct, 2: vc2)",
            "전역 검색 추가 위협 확인", SettingCategory.ENGINE,
            SettingEditor.Choice(
                listOf(
                    ChoiceOption(0, "없음"), ChoiceOption(1, "VCT 확인"), ChoiceOption(2, "VC2 확인"),
                ),
            ),
            engineKey = "vcthread",
            get = { it.vcThread }, set = { s, v -> s.copy(vcThread = v) },
        )
        bool(
            "hashAutoClear", "hash autoclear (0: no, 1: yes)", "해시 자동 지우기",
            SettingCategory.ENGINE, note = "탐색 전에 yxhashclear 를 보냅니다",
            get = { it.hashAutoClear }, set = { s, v -> s.copy(hashAutoClear = v) },
        )
        int(
            "toolbarPos", "toolbar postion (0: left vertical, 1: right horizontal)",
            "툴바 위치", SettingCategory.APPEARANCE,
            SettingEditor.Choice(
                listOf(ChoiceOption(0, "왼쪽 세로"), ChoiceOption(1, "오른쪽 가로")),
            ),
            note = "데스크톱 전용 — 값만 보관합니다",
            get = { it.toolbarPos }, set = { s, v -> s.copy(toolbarPos = v) },
        )
        bool(
            "darkMode", "enable dark mode", "다크 모드", SettingCategory.APPEARANCE,
            get = { it.darkMode }, set = { s, v -> s.copy(darkMode = v) },
        )
        bool(
            "showClock", "show clock (0: no, 1: yes)", "시계", SettingCategory.GAME,
            note = "보드 화면 대국 패널에 반영됩니다",
            get = { it.showClock }, set = { s, v -> s.copy(showClock = v) },
        )
        int(
            "incrementMs", "time increment per move", "수당 시간 증가", SettingCategory.GAME,
            SettingEditor.Number(0, 200_000, "ms"), engineKey = "time_increment",
            note = "이 줄만 밀리초 단위입니다(데스크톱 대화상자는 초로 보여줌)",
            get = { it.incrementMs }, set = { s, v -> s.copy(incrementMs = v) },
        )
        bool(
            "showForbidden", "show forbidden moves", "금수 표시", SettingCategory.DISPLAY,
            note = "렌주 계열 규칙에서만 조회합니다(YXSHOWFORBID)",
            get = { it.showForbidden }, set = { s, v -> s.copy(showForbidden = v) },
        )
        bool(
            "checkTimeout", "check timeout", "시간 초과 확인", SettingCategory.GAME,
            note = "보드 화면 대국 패널에 반영됩니다",
            get = { it.checkTimeout }, set = { s, v -> s.copy(checkTimeout = v) },
        )
        bool(
            "useDatabase", "use database moves (0: no, 1: yes)", "데이터베이스 사용",
            SettingCategory.DATABASE, engineKey = "usedatabase",
            get = { it.useDatabase }, set = { s, v -> s.copy(useDatabase = v) },
        )
        bool(
            "databaseReadonly", "enable database read-only mode (0: no, 1: yes)",
            "데이터베이스 읽기 전용", SettingCategory.DATABASE, engineKey = "database_readonly",
            note = "항상 엔진으로 밀어 보냅니다 — 서버 설정이 읽기 전용이면 탐색 결과가 조용히 버려집니다",
            get = { it.databaseReadonly }, set = { s, v -> s.copy(databaseReadonly = v) },
        )
        bool(
            "showBoardText", "show database baord texts (0: no, 1: yes)", "보드 텍스트",
            SettingCategory.DATABASE, note = "DB 보드 텍스트 표시는 P7",
            get = { it.showBoardText }, set = { s, v -> s.copy(showBoardText = v) },
        )
        bool(
            "showDbDelConfirm", "show database delall confirmation (0: no, 1: yes)", "삭제 확인",
            SettingCategory.DATABASE, note = "DB 일괄 삭제는 P7(기본 비활성)",
            get = { it.showDbDelConfirm }, set = { s, v -> s.copy(showDbDelConfirm = v) },
        )
        bool(
            "recordDebugLog", "record debug log", "디버그 로그 기록", SettingCategory.SYSTEM,
            note = "파일 기록은 P11 — 현재는 값만 보관합니다",
            get = { it.recordDebugLog }, set = { s, v -> s.copy(recordDebugLog = v) },
        )
        int(
            "logScale", "log area horizontal scale", "로그 영역 배율", SettingCategory.APPEARANCE,
            SettingEditor.Number(50, 300, "%"),
            note = "원시 콘솔 글자 크기 배율 — 100 % = 앱 기본(데스크톱에서는 HiDPI 배율)",
            get = { it.logScale }, set = { s, v -> s.copy(logScale = v) },
        )
        bool(
            "nbestSym", "symmetric nbest for the 5th moves", "5수 대칭 nbest",
            SettingCategory.RESEARCH, engineKey = "nbestsym",
            get = { it.nbestSym }, set = { s, v -> s.copy(nbestSym = v) },
        )
        int(
            "lossSaturation", "lossing move color saturation (0~100)", "패배 수 색 채도",
            SettingCategory.APPEARANCE, SettingEditor.Number(0, 100, "%"),
            get = { it.lossSaturation }, set = { s, v -> s.copy(lossSaturation = v) },
        )
        int(
            "winSaturation", "winning move color saturation (0~100)", "승리 수 색 채도",
            SettingCategory.APPEARANCE, SettingEditor.Number(0, 100, "%"),
            get = { it.winSaturation }, set = { s, v -> s.copy(winSaturation = v) },
        )
        int(
            "minSaturation", "min winrate color saturation (0~100)", "최소 승률 채도",
            SettingCategory.APPEARANCE, SettingEditor.Number(0, 100, "%"),
            get = { it.minSaturation }, set = { s, v -> s.copy(minSaturation = v) },
        )
        int(
            "maxSaturation", "max winrate color saturation (0~100)", "최대 승률 채도",
            SettingCategory.APPEARANCE, SettingEditor.Number(0, 100, "%"),
            get = { it.maxSaturation }, set = { s, v -> s.copy(maxSaturation = v) },
        )
        int(
            "colorValue", "value of color (0~100)", "색 명도", SettingCategory.APPEARANCE,
            SettingEditor.Number(0, 100, "%"),
            get = { it.colorValue }, set = { s, v -> s.copy(colorValue = v) },
        )
        text(
            "boardTextFont", "Board Text Font", "보드 텍스트 글꼴", SettingCategory.APPEARANCE,
            note = "데스크톱 전용 — 값만 보관합니다",
            get = { it.boardTextFont }, set = { s, v -> s.copy(boardTextFont = v) },
        )
        text(
            "textLogFont", "Text Log Font", "로그 글꼴", SettingCategory.APPEARANCE,
            note = "데스크톱 전용 — 값만 보관합니다",
            get = { it.textLogFont }, set = { s, v -> s.copy(textLogFont = v) },
        )
        text(
            "dbCommentFont", "Database Comment Font", "DB 주석 글꼴", SettingCategory.APPEARANCE,
            note = "데스크톱 전용 — 값만 보관합니다",
            get = { it.dbCommentFont }, set = { s, v -> s.copy(dbCommentFont = v) },
        )
        text(
            "dbCommentFont2", "Database Comment Font", "DB 주석 글꼴 2", SettingCategory.APPEARANCE,
            note = "데스크톱 전용 — 47번째 줄, 46번과 주석이 같습니다",
            get = { it.dbCommentFont2 }, set = { s, v -> s.copy(dbCommentFont2 = v) },
        )
    }

    val DEV: List<SettingSpec> = build(SettingsFile.DEV) {
        bool(
            "showEvalBar", "show evaluation bar (0: no, 1: yes)", "평가 바 표시",
            SettingCategory.DISPLAY,
            get = { it.showEvalBar }, set = { s, v -> s.copy(showEvalBar = v) },
        )
        bool(
            "showWrGraph", "show winrate graph (0: no, 1: yes)", "승률 그래프 표시",
            SettingCategory.DISPLAY,
            get = { it.showWrGraph }, set = { s, v -> s.copy(showWrGraph = v) },
        )
        int(
            "mqPreset", "move quality preset (0: strict, 1: default, 2: lenient)",
            "수 품질 기준", SettingCategory.RESEARCH,
            SettingEditor.Choice(
                listOf(ChoiceOption(0, "엄격"), ChoiceOption(1, "기본"), ChoiceOption(2, "느슨")),
            ),
            note = "리뷰 등급 기준 — 같은 손실을 얼마나 엄하게 볼지",
            get = { it.mqPreset }, set = { s, v -> s.copy(mqPreset = v) },
        )
        bool(
            "showMoveBadge", "show move badges on stones (0: no, 1: yes)", "돌에 수 품질 배지",
            SettingCategory.DISPLAY, note = "리뷰가 끝난 뒤 돌 위에 등급 배지를 그립니다",
            get = { it.showMoveBadge }, set = { s, v -> s.copy(showMoveBadge = v) },
        )
        int(
            "proveBudget0Sec", "prove mode initial budget per node (seconds)",
            "증명 초기 예산", SettingCategory.RESEARCH, SettingEditor.Number(1, 3_600, "초/노드"),
            note = "노드당 시작 예산 — 실패하면 2배씩 올립니다",
            get = { it.proveBudget0Sec }, set = { s, v -> s.copy(proveBudget0Sec = v) },
        )
        int(
            "proveBudgetMaxSec", "prove mode budget cap (seconds)", "증명 예산 상한",
            SettingCategory.RESEARCH, SettingEditor.Number(1, 36_000, "초"),
            note = "이 예산까지 올려도 결론이 없으면 그 노드는 포기합니다",
            get = { it.proveBudgetMaxSec }, set = { s, v -> s.copy(proveBudgetMaxSec = v) },
        )
        int(
            "proveNbest", "prove mode attacker candidates (yxnbest k)", "증명 공격 후보 수",
            SettingCategory.RESEARCH, SettingEditor.Number(1, 8),
            note = "OR 노드에서 yxnbest 로 받는 공격 후보 수 (실패 시 1씩 확장)",
            get = { it.proveNbest }, set = { s, v -> s.copy(proveNbest = v) },
        )
        int(
            "boardZoomPercent", "board zoom scale percent (60~300)", "보드 확대",
            SettingCategory.DISPLAY, SettingEditor.Number(60, 300, "%"),
            get = { it.boardZoomPercent }, set = { s, v -> s.copy(boardZoomPercent = v) },
        )
        fixed(
            "reservedFitBoard", "reserved (was: fit board to window)", "예약 슬롯",
            SettingCategory.SYSTEM,
            note = "데스크톱이 위치로 파싱하므로 줄을 지우면 이후 설정이 모두 밀립니다 — 읽고 그대로 씁니다",
            get = { it.reservedFitBoard }, set = { s, v -> s.copy(reservedFitBoard = v) },
        )
        bool(
            "dbAutoSave", "auto-save database periodically (0: no, 1: yes)", "DB 주기 자동 저장",
            SettingCategory.DATABASE, note = "YXSAVEDATABASE 주기 저장은 P7",
            get = { it.dbAutoSave }, set = { s, v -> s.copy(dbAutoSave = v) },
        )
        int(
            "dbAutoSaveMinutes", "database auto-save interval in minutes", "DB 자동 저장 간격",
            SettingCategory.DATABASE, SettingEditor.Number(1, 1_440, "분"), note = "P7",
            get = { it.dbAutoSaveMinutes }, set = { s, v -> s.copy(dbAutoSaveMinutes = v) },
        )
        bool(
            "proveBestFirst", "prove best attack move first (0: no, 1: yes)",
            "증명: 최선 공격 먼저", SettingCategory.RESEARCH,
            note = "최선수 하나만 전개하고 실패할 때만 다음 후보를 꺼냅니다",
            get = { it.proveBestFirst }, set = { s, v -> s.copy(proveBestFirst = v) },
        )
        bool(
            "proveProbe", "prove early probe of strongest defense (0: no, 1: yes)",
            "증명: 최강 방어 조기 탐색", SettingCategory.RESEARCH,
            note = "성립할 것 같은 방어를 먼저 한 번 확인 — 반증되면 그 가지 전체가 무의미해집니다",
            get = { it.proveProbe }, set = { s, v -> s.copy(proveProbe = v) },
        )
        bool(
            "reviewByDepth", "review budget unit (0: seconds, 1: depth)", "검토 예산 단위 = 깊이",
            SettingCategory.RESEARCH,
            get = { it.reviewByDepth }, set = { s, v -> s.copy(reviewByDepth = v) },
        )
        int(
            "reviewDepth", "review fixed depth per move", "검토 고정 깊이",
            SettingCategory.RESEARCH, SettingEditor.Number(4, 64, "플라이"),
            get = { it.reviewDepth }, set = { s, v -> s.copy(reviewDepth = v) },
        )
        bool(
            "proveByDepth", "prove budget unit (0: seconds, 1: depth)", "증명 예산 단위 = 깊이",
            SettingCategory.RESEARCH, note = "시간 대신 고정 깊이로 탐색합니다",
            get = { it.proveByDepth }, set = { s, v -> s.copy(proveByDepth = v) },
        )
        int(
            "proveDepth0", "prove initial depth per node", "증명 초기 깊이",
            SettingCategory.RESEARCH, SettingEditor.Number(4, 64, "플라이"),
            note = "깊이 모드에서 노드당 시작 깊이 (실패 시 +2)",
            get = { it.proveDepth0 }, set = { s, v -> s.copy(proveDepth0 = v) },
        )
        int(
            "proveDepthMax", "prove depth cap per node", "증명 깊이 상한",
            SettingCategory.RESEARCH, SettingEditor.Number(4, 128, "플라이"),
            get = { it.proveDepthMax }, set = { s, v -> s.copy(proveDepthMax = v) },
        )
        bool(
            "skipOpening", "skip grading/search of opening moves 1-5 (0: no, 1: yes)",
            "1~5수 채점 생략", SettingCategory.RESEARCH,
            note = "리뷰가 오프닝 국면을 탐색하지 않고 등급도 매기지 않습니다",
            get = { it.skipOpening }, set = { s, v -> s.copy(skipOpening = v) },
        )
        fixed(
            "devDefaults", "one-time dark+korean defaults applied (do not edit)",
            "초기 기본값 적용 표시", SettingCategory.SYSTEM,
            note = "데스크톱이 한 번만 쓰는 표시입니다 — 편집하지 않습니다",
            get = { if (it.devDefaults) 1 else 0 },
            set = { s, v -> s.copy(devDefaults = v != 0) },
        )
    }

    val ALL: List<SettingSpec> = MAIN + DEV

    private val byId: Map<String, SettingSpec> = ALL.associateBy { it.id }

    fun spec(id: String): SettingSpec? = byId[id]

    fun of(file: SettingsFile): List<SettingSpec> =
        if (file == SettingsFile.MAIN) MAIN else DEV

    fun byCategory(category: SettingCategory): List<SettingSpec> =
        ALL.filter { it.category == category }

    // ---------------------------------------------------------------- builder

    private fun build(file: SettingsFile, block: Builder.() -> Unit): List<SettingSpec> =
        Builder(file).apply(block).specs.also {
            require(it.size == file.lineCount) {
                "${file.fileName}: ${it.size} specs but the desktop writes ${file.lineCount} lines"
            }
        }

    private class Builder(private val file: SettingsFile) {
        val specs = mutableListOf<SettingSpec>()

        fun int(
            id: String,
            comment: String,
            label: String,
            category: SettingCategory,
            editor: SettingEditor,
            engineKey: String? = null,
            note: String? = null,
            get: (AppSettings) -> Int,
            set: (AppSettings, Int) -> AppSettings,
        ) = add(
            id, comment, label, category, editor, engineKey, note,
            getter = { get(it).toString() },
            setter = { s, raw ->
                val parsed = raw.trim().toLongOrNull()
                if (parsed == null) s else set(s, sanitize(editor, parsed, get(s).toLong()).toInt())
            },
        )

        fun long(
            id: String,
            comment: String,
            label: String,
            category: SettingCategory,
            editor: SettingEditor,
            engineKey: String? = null,
            note: String? = null,
            get: (AppSettings) -> Long,
            set: (AppSettings, Long) -> AppSettings,
        ) = add(
            id, comment, label, category, editor, engineKey, note,
            getter = { get(it).toString() },
            setter = { s, raw ->
                val parsed = raw.trim().toLongOrNull()
                if (parsed == null) s else set(s, sanitize(editor, parsed, get(s)))
            },
        )

        fun bool(
            id: String,
            comment: String,
            label: String,
            category: SettingCategory,
            engineKey: String? = null,
            note: String? = null,
            get: (AppSettings) -> Boolean,
            set: (AppSettings, Boolean) -> AppSettings,
        ) = add(
            id, comment, label, category, SettingEditor.Toggle, engineKey, note,
            getter = { if (get(it)) "1" else "0" },
            setter = { s, raw ->
                // The desktop treats any non-zero as on (`t != 0`).
                val parsed = raw.trim().toLongOrNull()
                if (parsed == null) s else set(s, parsed != 0L)
            },
        )

        fun text(
            id: String,
            comment: String,
            label: String,
            category: SettingCategory,
            note: String? = null,
            get: (AppSettings) -> String,
            set: (AppSettings, String) -> AppSettings,
        ) = add(
            id, comment, label, category, SettingEditor.Text, null, note,
            getter = get,
            // read_str_from_file cuts at the first ';', so a value can never
            // contain one without corrupting the next parse.
            setter = { s, raw -> set(s, raw.substringBefore(';').trimEnd()) },
        )

        /** Read and written verbatim, never offered for editing. */
        fun fixed(
            id: String,
            comment: String,
            label: String,
            category: SettingCategory,
            note: String? = null,
            get: (AppSettings) -> Int,
            set: (AppSettings, Int) -> AppSettings,
        ) = add(
            id, comment, label, category, SettingEditor.Fixed, null, note,
            getter = { get(it).toString() },
            setter = { s, raw ->
                val parsed = raw.trim().toIntOrNull()
                if (parsed == null) s else set(s, parsed)
            },
        )

        private fun add(
            id: String,
            comment: String,
            label: String,
            category: SettingCategory,
            editor: SettingEditor,
            engineKey: String?,
            note: String?,
            getter: (AppSettings) -> String,
            setter: (AppSettings, String) -> AppSettings,
        ) {
            specs += SettingSpec(
                id = id,
                file = file,
                line = specs.size + 1,
                comment = comment,
                label = label,
                category = category,
                editor = editor,
                engineKey = engineKey,
                note = note,
                getter = getter,
                setter = setter,
            )
        }

        private fun sanitize(editor: SettingEditor, value: Long, fallback: Long): Long =
            when (editor) {
                is SettingEditor.Number -> value.coerceIn(editor.min, editor.max)
                is SettingEditor.Choice ->
                    if (editor.options.any { it.value.toLong() == value }) value else fallback
                else -> value
            }
    }
}
