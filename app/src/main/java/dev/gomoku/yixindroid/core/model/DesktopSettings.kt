package dev.gomoku.yixindroid.core.model

import dev.gomoku.yixindroid.core.i18n.tr
import java.util.Locale

/** The two files the desktop persists settings in, parsed strictly by position. */
enum class SettingsFile(val fileName: String, val lineCount: Int) {
    MAIN("settings.txt", 47),
    DEV("settings_dev.txt", 20),
}

/** UI grouping only — the file order is what the desktop cares about. */
enum class SettingCategory {
    GAME, ENGINE, DISPLAY, DATABASE, RESEARCH, APPEARANCE, SYSTEM,
    ;

    /** A getter, not a constructor argument: an enum constant is built once for
     *  the life of the process, and the language can change under it. */
    val label: String
        get() = when (this) {
            GAME -> tr("대국", "Game")
            ENGINE -> tr("엔진", "Engine")
            DISPLAY -> tr("표시", "Display")
            DATABASE -> tr("데이터베이스", "Database")
            RESEARCH -> tr("연구·검토", "Research")
            APPEARANCE -> tr("외관", "Appearance")
            SYSTEM -> tr("시스템", "System")
        }
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

    /**
     * The settings an ordinary session actually touches. Everything else is
     * real and editable, but hidden behind the "고급 설정" switch — the desktop
     * spreads its 67 entries over a tabbed dialog and several menus, which a
     * single phone list cannot imitate without becoming unreadable.
     *
     * Nothing is removed: the files still round-trip all 67 lines, and the
     * hidden ones appear the moment the switch goes on.
     */
    val EVERYDAY: Set<String> = setOf(
        // game
        "boardSize", "level", "timeoutTurnSec", "timeoutMatchSec",
        "computerBlack", "computerWhite", "showForbidden", "showClock",
        // engine
        "threadNum", "hashSizeMb", "multiPv", "pondering", "style",
        // display
        "showNumber", "showAnalysis", "showAnalysisWinrate", "showEvalBar",
        "showWrGraph", "showMoveBadge", "showBoardText", "boardZoomPercent",
        "darkMode",
        // database
        "useDatabase", "databaseReadonly", "dbAutoSave",
        // research
        "mqPreset", "skipOpening",
    )

    /** Is this one of the everyday settings? */
    fun isEveryday(id: String): Boolean = id in EVERYDAY

    private fun buildMain(): List<SettingSpec> = build(SettingsFile.MAIN) {
        int(
            "boardSize", "board size (10 ~ 22)", tr("보드 크기", "Board Size"), SettingCategory.GAME,
            SettingEditor.Number(5, 22), note = tr("변경하면 엔진을 다시 시작하고 판을 비웁니다", "Changing this restarts the engine and clears the board"),
            get = { it.boardSize }, set = { s, v -> s.copy(boardSize = v) },
        )
        int(
            "language", "language (0: English, 1,2,...: custom)", tr("언어", "Language"), SettingCategory.APPEARANCE,
            SettingEditor.Choice(
                listOf(
                    ChoiceOption(0, "English"), ChoiceOption(1, "简体中文"),
                    ChoiceOption(2, "繁體中文"), ChoiceOption(3, "한국어"),
                    ChoiceOption(4, "日本語"), ChoiceOption(5, "Русский"),
                    ChoiceOption(6, "Tiếng Việt"),
                ),
            ),
            note = tr("이 앱은 한국어·영어만 지원합니다. 값은 PC 파일 그대로 보관하지만, ", "This app ships Korean and English only. The value is kept as the PC file has it, but") +
                tr("툴바 라벨은 한국어면 3.lng, 그 밖에는 0.lng(영어)를 읽습니다. ", "toolbar labels are read from 3.lng for Korean and 0.lng (English) otherwise.") +
                tr("앱 화면의 언어는 안드로이드 시스템 언어를 따릅니다", "The app's own language follows the Android system language"),
            get = { it.language }, set = { s, v -> s.copy(language = v) },
        )
        int(
            "rule",
            "rule (0: freestyle, 1: standard, 2: free renju, 3: swap after 1st move, 5: soosorv, 6: swap-2)",
            tr("규칙", "Rule"), SettingCategory.GAME,
            SettingEditor.Choice(
                listOf(
                    ChoiceOption(0, tr("프리스타일 오목", "Freestyle Gomoku")), ChoiceOption(1, tr("표준 오목", "Standard Gomoku")),
                    ChoiceOption(2, tr("자유 렌주", "Free Renju")), ChoiceOption(3, tr("첫 수 이후 교환", "Swap After First Move")),
                    ChoiceOption(4, tr("RIF 오프닝 규칙", "RIF Opening Rule")), ChoiceOption(5, tr("Soosorv-8 오프닝 규칙", "Soosorv-8 Opening Rule")),
                    ChoiceOption(6, tr("스왑2", "Swap2")),
                ),
            ),
            engineKey = "rule",
            note = tr("오프닝 규칙(3~6)의 엔진 기본 규칙은 각각 프리스타일/렌주/렌주/표준입니다", "Opening rules 3-6 sit on freestyle / renju / renju / standard respectively"),
            get = { it.rule }, set = { s, v -> s.copy(rule = v) },
        )
        bool(
            "computerBlack", "computer play black (0: no, 1: yes)", tr("컴퓨터가 흑돌 플레이", "Computer Plays Black"),
            SettingCategory.GAME, note = tr("새 대국을 시작할 때 적용됩니다(보드 화면에서도 바꿀 수 있음)", "Applies when a new game starts (the board screen can change it too)"),
            get = { it.computerBlack }, set = { s, v -> s.copy(computerBlack = v) },
        )
        bool(
            "computerWhite", "computer play white (0: no, 1: yes)", tr("컴퓨터가 백돌 플레이", "Computer Plays White"),
            SettingCategory.GAME, note = tr("새 대국을 시작할 때 적용됩니다(보드 화면에서도 바꿀 수 있음)", "Applies when a new game starts (the board screen can change it too)"),
            get = { it.computerWhite }, set = { s, v -> s.copy(computerWhite = v) },
        )
        int(
            "level", "level (0: unlimited time 1: custom level 2-12: predefined level)",
            tr("레벨", "Level"), SettingCategory.ENGINE,
            SettingEditor.Choice(
                buildList {
                    add(ChoiceOption(0, tr("무제한 시간", "Unlimited Time")))
                    add(ChoiceOption(1, tr("사용자 정의 레벨", "Custom Level")))
                    for (l in 2..11) add(ChoiceOption(l, tr("사전 정의 레벨 $l", "Predefined Level $l")))
                },
            ),
            engineKey = "max_node",
            note = tr("무제한/사전 정의는 노드 표를 쓰고, 사용자 정의만 아래 시간·깊이·노드를 씁니다", "Unlimited and predefined levels use a node table; only Custom uses the time, depth and node values below"),
            get = { it.level }, set = { s, v -> s.copy(level = v) },
        )
        int(
            "timeoutTurnSec", "time limit (turn)", tr("턴 시간", "Turn time"), SettingCategory.ENGINE,
            SettingEditor.Number(1, 1_000_000, tr("초", "s")), engineKey = "timeout_turn",
            note = tr("사용자 정의 레벨에서만 사용", "Custom level only"),
            get = { it.timeoutTurnSec }, set = { s, v -> s.copy(timeoutTurnSec = v) },
        )
        int(
            "timeoutMatchSec", "time limit (match)", tr("매치 시간", "Match time"), SettingCategory.ENGINE,
            SettingEditor.Number(1, 10_000_000, tr("초", "s")), engineKey = "timeout_match",
            note = tr("사용자 정의 레벨에서만 사용", "Custom level only"),
            get = { it.timeoutMatchSec }, set = { s, v -> s.copy(timeoutMatchSec = v) },
        )
        int(
            "maxDepth", "max depth", tr("최대 깊이", "Max depth"), SettingCategory.ENGINE,
            SettingEditor.Number(2, 484, tr("플라이", "ply")), engineKey = "max_depth",
            get = { it.maxDepth }, set = { s, v -> s.copy(maxDepth = v) },
        )
        long(
            "maxNode", "max node", tr("최대 노드 수", "Max node number"), SettingCategory.ENGINE,
            SettingEditor.Number(1_000, 2_000_000_000), engineKey = "max_node",
            get = { it.maxNode }, set = { s, v -> s.copy(maxNode = v) },
        )
        int(
            "style", "style (rash 0 ~ 5 cautious)", tr("스타일", "Style"), SettingCategory.ENGINE,
            SettingEditor.Choice(
                listOf(
                    ChoiceOption(0, tr("성급함 (0)", "Rash (0)")), ChoiceOption(1, "1"), ChoiceOption(2, "2"),
                    ChoiceOption(3, "3"), ChoiceOption(4, "4"), ChoiceOption(5, tr("신중함 (5)", "Cautious (5)")),
                ),
            ),
            engineKey = "caution_factor",
            get = { it.style }, set = { s, v -> s.copy(style = v) },
        )
        int(
            "toolbarStyle",
            "toolbar style (0: only icon, 1: both icon and words, 2: both with horizontally stacked)",
            tr("툴바 스타일", "Toolbar style"), SettingCategory.APPEARANCE,
            SettingEditor.Choice(
                listOf(
                    ChoiceOption(0, tr("아이콘만", "Icon only")), ChoiceOption(1, tr("아이콘+글자", "Icon and text")),
                    ChoiceOption(2, tr("아이콘+글자(가로)", "Icon and text (horizontal)")),
                ),
            ),
            note = tr("데스크톱 전용 — 값만 보관합니다", "Desktop only — the value is kept, nothing more"),
            get = { it.toolbarStyle }, set = { s, v -> s.copy(toolbarStyle = v) },
        )
        bool(
            "showLog", "show log (0: no, 1: yes)", tr("로그", "Log"), SettingCategory.DISPLAY,
            note = tr("연결 탭의 piskvork 원시 콘솔", "The raw piskvork console on the engine tab"),
            get = { it.showLog }, set = { s, v -> s.copy(showLog = v) },
        )
        bool(
            "showNumber", "show number (0: no, 1: yes)", tr("번호 매기기", "Numeration"), SettingCategory.DISPLAY,
            get = { it.showNumber }, set = { s, v -> s.copy(showNumber = v) },
        )
        bool(
            "showAnalysis", "show analysis (0: no, 1: yes)", tr("분석 표시", "Analysis overlay"), SettingCategory.DISPLAY,
            note = tr("보드 위 후보수 태그·실시간 표시", "Candidate tags and realtime markers on the board"),
            get = { it.showAnalysis }, set = { s, v -> s.copy(showAnalysis = v) },
        )
        bool(
            "showAnalysisWinrate", "show analysis winrate (0: no, 1: yes)", tr("승률 분석", "Win-rate analysis"),
            SettingCategory.DISPLAY,
            note = tr("끄면 칸별 승률 태그를 기록하지 않습니다(실시간 후보·패배 표시는 유지) — main.c와 동일", "Off records no per-point win-rate tags, keeping the realtime candidate and loss markers — as main.c does"),
            get = { it.showAnalysisWinrate }, set = { s, v -> s.copy(showAnalysisWinrate = v) },
        )
        bool(
            "showWarning", "show warning (0: no, 1: yes)", tr("실행 취소 경고", "Undo warning"),
            SettingCategory.DISPLAY, note = tr("판 초기화처럼 되돌릴 수 없는 동작에 확인창", "Confirms actions that cannot be undone, like clearing the board"),
            get = { it.showWarning }, set = { s, v -> s.copy(showWarning = v) },
        )
        int(
            "threadNum", "number of threads", tr("스레드 수", "Number of Threads"), SettingCategory.ENGINE,
            SettingEditor.Number(1, 256), engineKey = "thread_num",
            note = tr("엔진이 알려주는 상한(MAX_THREAD_NUM)까지", "Up to the ceiling the engine reports (MAX_THREAD_NUM)"),
            get = { it.threadNum }, set = { s, v -> s.copy(threadNum = v) },
        )
        int(
            "hashSizeMb", "hash size (MB)", tr("해시 크기", "Hash Size"), SettingCategory.ENGINE,
            SettingEditor.Number(1, 65_536, "MB"), engineKey = "hash_size",
            note = tr("엔진에는 KB로 변환해 보냅니다(MB<<10)", "Sent to the engine in KB (MB<<10)"),
            get = { it.hashSizeMb }, set = { s, v -> s.copy(hashSizeMb = v) },
        )
        int(
            "multiPv", "default number of multi-pv", tr("멀티-PV 수", "Number of Multi-PV"), SettingCategory.ENGINE,
            SettingEditor.Number(1, 225), engineKey = "yxnbest",
            get = { it.multiPv }, set = { s, v -> s.copy(multiPv = v) },
        )
        bool(
            "blockAutoReset", "block autoreset (0: no, 1: yes)", tr("차단 자동 해제", "Auto-reset blocks"),
            SettingCategory.RESEARCH, note = tr("엔진 탭 도구에서 사용", "Used by the tools on the engine tab"),
            get = { it.blockAutoReset }, set = { s, v -> s.copy(blockAutoReset = v) },
        )
        bool(
            "blockPathAutoReset", "blockpath autoreset (0: no, 1: yes)", tr("차단 경로 자동 해제", "Auto-reset blocked paths"),
            SettingCategory.RESEARCH, note = tr("엔진 탭 도구에서 사용", "Used by the tools on the engine tab"),
            get = { it.blockPathAutoReset }, set = { s, v -> s.copy(blockPathAutoReset = v) },
        )
        bool(
            "pondering", "pondering (0: off, 1: on)", tr("사색", "Pondering"), SettingCategory.ENGINE,
            engineKey = "pondering",
            get = { it.pondering }, set = { s, v -> s.copy(pondering = v) },
        )
        int(
            "vcThread", "checkmate in global search (0: no, 1: vct, 2: vc2)",
            tr("전역 검색 추가 위협 확인", "Additional Threat Check in Global Search"), SettingCategory.ENGINE,
            SettingEditor.Choice(
                listOf(
                    ChoiceOption(0, tr("없음", "None")), ChoiceOption(1, tr("VCT 확인", "Check VCT")), ChoiceOption(2, tr("VC2 확인", "Check VC2")),
                ),
            ),
            engineKey = "vcthread",
            get = { it.vcThread }, set = { s, v -> s.copy(vcThread = v) },
        )
        bool(
            "hashAutoClear", "hash autoclear (0: no, 1: yes)", tr("해시 자동 지우기", "Auto-clear hash"),
            SettingCategory.ENGINE, note = tr("탐색 전에 yxhashclear 를 보냅니다", "Sends yxhashclear before a search"),
            get = { it.hashAutoClear }, set = { s, v -> s.copy(hashAutoClear = v) },
        )
        int(
            "toolbarPos", "toolbar postion (0: left vertical, 1: right horizontal)",
            tr("툴바 위치", "Toolbar position"), SettingCategory.APPEARANCE,
            SettingEditor.Choice(
                listOf(ChoiceOption(0, tr("왼쪽 세로", "Left, vertical")), ChoiceOption(1, tr("오른쪽 가로", "Right, horizontal"))),
            ),
            note = tr("데스크톱 전용 — 값만 보관합니다", "Desktop only — the value is kept, nothing more"),
            get = { it.toolbarPos }, set = { s, v -> s.copy(toolbarPos = v) },
        )
        bool(
            "darkMode", "enable dark mode", tr("다크 모드", "Dark mode"), SettingCategory.APPEARANCE,
            get = { it.darkMode }, set = { s, v -> s.copy(darkMode = v) },
        )
        bool(
            "showClock", "show clock (0: no, 1: yes)", tr("시계", "Clock"), SettingCategory.GAME,
            note = tr("보드 화면 대국 패널에 반영됩니다", "Shown in the game panel on the board screen"),
            get = { it.showClock }, set = { s, v -> s.copy(showClock = v) },
        )
        int(
            "incrementMs", "time increment per move", tr("수당 시간 증가", "Time increment per move"), SettingCategory.GAME,
            SettingEditor.Number(0, 200_000, "ms"), engineKey = "time_increment",
            note = tr("이 줄만 밀리초 단위입니다(데스크톱 대화상자는 초로 보여줌)", "This line alone is in milliseconds (the desktop dialog shows seconds)"),
            get = { it.incrementMs }, set = { s, v -> s.copy(incrementMs = v) },
        )
        bool(
            "showForbidden", "show forbidden moves", tr("금수 표시", "Show forbidden points"), SettingCategory.DISPLAY,
            note = tr("렌주 계열 규칙에서만 조회합니다(YXSHOWFORBID)", "Asked for under renju rules only (YXSHOWFORBID)"),
            get = { it.showForbidden }, set = { s, v -> s.copy(showForbidden = v) },
        )
        bool(
            "checkTimeout", "check timeout", tr("시간 초과 확인", "Timeout warning"), SettingCategory.GAME,
            note = tr("보드 화면 대국 패널에 반영됩니다", "Shown in the game panel on the board screen"),
            get = { it.checkTimeout }, set = { s, v -> s.copy(checkTimeout = v) },
        )
        bool(
            "useDatabase", "use database moves (0: no, 1: yes)", tr("데이터베이스 사용", "Use database"),
            SettingCategory.DATABASE, engineKey = "usedatabase",
            get = { it.useDatabase }, set = { s, v -> s.copy(useDatabase = v) },
        )
        bool(
            "databaseReadonly", "enable database read-only mode (0: no, 1: yes)",
            tr("데이터베이스 읽기 전용", "Database read-only"), SettingCategory.DATABASE, engineKey = "database_readonly",
            note = tr("항상 엔진으로 밀어 보냅니다 — 서버 설정이 읽기 전용이면 탐색 결과가 조용히 버려집니다", "Always pushed to the engine — if the server's own config is read-only, search results are dropped silently"),
            get = { it.databaseReadonly }, set = { s, v -> s.copy(databaseReadonly = v) },
        )
        bool(
            "showBoardText", "show database baord texts (0: no, 1: yes)", tr("보드 텍스트", "Board text"),
            SettingCategory.DATABASE, note = tr("켜면 저장된 값 대신 사용자가 쓴 라벨을 보드에 표시합니다",
                "Shows the labels you wrote instead of the stored values"),
            get = { it.showBoardText }, set = { s, v -> s.copy(showBoardText = v) },
        )
        bool(
            "showDbDelConfirm", "show database delall confirmation (0: no, 1: yes)", tr("삭제 확인", "Confirm deletions"),
            SettingCategory.DATABASE, note = tr("DB 일괄 삭제 같은 되돌릴 수 없는 연산에 확인창 — 일괄 삭제 자체는 기본 잠금입니다",
                "Confirms operations that cannot be undone; bulk deletes are locked by default anyway"),
            get = { it.showDbDelConfirm }, set = { s, v -> s.copy(showDbDelConfirm = v) },
        )
        bool(
            "recordDebugLog", "record debug log", tr("디버그 로그 기록", "Record a debug log"), SettingCategory.SYSTEM,
            note = tr("콘솔 양방향을 상한 있는 파일로 남기고 설정에서 내보냅니다",
                "Records both sides of the console to a capped file you can export from settings"),
            get = { it.recordDebugLog }, set = { s, v -> s.copy(recordDebugLog = v) },
        )
        int(
            "logScale", "log area horizontal scale", tr("로그 영역 배율", "Log area scale"), SettingCategory.APPEARANCE,
            SettingEditor.Number(50, 300, "%"),
            note = tr("원시 콘솔 글자 크기 배율 — 100 % = 앱 기본(데스크톱에서는 HiDPI 배율)", "Text size of the raw console — 100 % is the app default (HiDPI scaling on the desktop)"),
            get = { it.logScale }, set = { s, v -> s.copy(logScale = v) },
        )
        bool(
            "nbestSym", "symmetric nbest for the 5th moves", tr("5수 대칭 nbest", "Fifth-move symmetry nbest"),
            SettingCategory.RESEARCH, engineKey = "nbestsym",
            get = { it.nbestSym }, set = { s, v -> s.copy(nbestSym = v) },
        )
        int(
            "lossSaturation", "lossing move color saturation (0~100)", tr("패배 수 색 채도", "Losing move saturation"),
            SettingCategory.APPEARANCE, SettingEditor.Number(0, 100, "%"),
            get = { it.lossSaturation }, set = { s, v -> s.copy(lossSaturation = v) },
        )
        int(
            "winSaturation", "winning move color saturation (0~100)", tr("승리 수 색 채도", "Winning move saturation"),
            SettingCategory.APPEARANCE, SettingEditor.Number(0, 100, "%"),
            get = { it.winSaturation }, set = { s, v -> s.copy(winSaturation = v) },
        )
        int(
            "minSaturation", "min winrate color saturation (0~100)", tr("최소 승률 채도", "Minimum win-rate saturation"),
            SettingCategory.APPEARANCE, SettingEditor.Number(0, 100, "%"),
            get = { it.minSaturation }, set = { s, v -> s.copy(minSaturation = v) },
        )
        int(
            "maxSaturation", "max winrate color saturation (0~100)", tr("최대 승률 채도", "Maximum win-rate saturation"),
            SettingCategory.APPEARANCE, SettingEditor.Number(0, 100, "%"),
            get = { it.maxSaturation }, set = { s, v -> s.copy(maxSaturation = v) },
        )
        int(
            "colorValue", "value of color (0~100)", tr("색 명도", "Colour value"), SettingCategory.APPEARANCE,
            SettingEditor.Number(0, 100, "%"),
            get = { it.colorValue }, set = { s, v -> s.copy(colorValue = v) },
        )
        text(
            "boardTextFont", "Board Text Font", tr("보드 텍스트 글꼴", "Board Text Font"), SettingCategory.APPEARANCE,
            note = tr("데스크톱 전용 — 값만 보관합니다", "Desktop only — the value is kept, nothing more"),
            get = { it.boardTextFont }, set = { s, v -> s.copy(boardTextFont = v) },
        )
        text(
            "textLogFont", "Text Log Font", tr("로그 글꼴", "Log Font"), SettingCategory.APPEARANCE,
            note = tr("데스크톱 전용 — 값만 보관합니다", "Desktop only — the value is kept, nothing more"),
            get = { it.textLogFont }, set = { s, v -> s.copy(textLogFont = v) },
        )
        text(
            "dbCommentFont", "Database Comment Font", tr("DB 주석 글꼴", "Database Comment Font"), SettingCategory.APPEARANCE,
            note = tr("데스크톱 전용 — 값만 보관합니다", "Desktop only — the value is kept, nothing more"),
            get = { it.dbCommentFont }, set = { s, v -> s.copy(dbCommentFont = v) },
        )
        text(
            "dbCommentFont2", "Database Comment Font", tr("DB 주석 글꼴 2", "Database Comment Font 2"), SettingCategory.APPEARANCE,
            note = tr("데스크톱 전용 — 47번째 줄, 46번과 주석이 같습니다", "Desktop only — line 47, whose comment is the same as line 46"),
            get = { it.dbCommentFont2 }, set = { s, v -> s.copy(dbCommentFont2 = v) },
        )
    }

    private fun buildDev(): List<SettingSpec> = build(SettingsFile.DEV) {
        bool(
            "showEvalBar", "show evaluation bar (0: no, 1: yes)", tr("평가 바 표시", "Show the eval bar"),
            SettingCategory.DISPLAY,
            get = { it.showEvalBar }, set = { s, v -> s.copy(showEvalBar = v) },
        )
        bool(
            "showWrGraph", "show winrate graph (0: no, 1: yes)", tr("승률 그래프 표시", "Show the win-rate graph"),
            SettingCategory.DISPLAY,
            get = { it.showWrGraph }, set = { s, v -> s.copy(showWrGraph = v) },
        )
        int(
            "mqPreset", "move quality preset (0: strict, 1: default, 2: lenient)",
            tr("수 품질 기준", "Move-grade preset"), SettingCategory.RESEARCH,
            SettingEditor.Choice(
                listOf(ChoiceOption(0, tr("엄격", "Strict")), ChoiceOption(1, tr("기본", "Default")), ChoiceOption(2, tr("느슨", "Lenient"))),
            ),
            note = tr("리뷰 등급 기준 — 같은 손실을 얼마나 엄하게 볼지", "How harshly a review judges the same loss"),
            get = { it.mqPreset }, set = { s, v -> s.copy(mqPreset = v) },
        )
        bool(
            "showMoveBadge", "show move badges on stones (0: no, 1: yes)", tr("돌에 수 품질 배지", "Grade badges on stones"),
            SettingCategory.DISPLAY, note = tr("리뷰가 끝난 뒤 돌 위에 등급 배지를 그립니다", "Draws the grade badge on the stone once a review has finished"),
            get = { it.showMoveBadge }, set = { s, v -> s.copy(showMoveBadge = v) },
        )
        int(
            "proveBudget0Sec", "prove mode initial budget per node (seconds)",
            tr("증명 초기 예산", "Prove: starting budget"), SettingCategory.RESEARCH, SettingEditor.Number(1, 3_600, tr("초/노드", "s per node")),
            note = tr("노드당 시작 예산 — 실패하면 2배씩 올립니다", "Budget a node starts with — doubled each time it fails"),
            get = { it.proveBudget0Sec }, set = { s, v -> s.copy(proveBudget0Sec = v) },
        )
        int(
            "proveBudgetMaxSec", "prove mode budget cap (seconds)", tr("증명 예산 상한", "Prove: budget cap"),
            SettingCategory.RESEARCH, SettingEditor.Number(1, 36_000, tr("초", "s")),
            note = tr("이 예산까지 올려도 결론이 없으면 그 노드는 포기합니다", "A node that reaches this budget without a conclusion is given up"),
            get = { it.proveBudgetMaxSec }, set = { s, v -> s.copy(proveBudgetMaxSec = v) },
        )
        int(
            "proveNbest", "prove mode attacker candidates (yxnbest k)", tr("증명 공격 후보 수", "Prove: attack candidates"),
            SettingCategory.RESEARCH, SettingEditor.Number(1, 8),
            note = tr("OR 노드에서 yxnbest 로 받는 공격 후보 수 (실패 시 1씩 확장)", "Attack moves asked for with yxnbest at an OR node (one more each time it fails)"),
            get = { it.proveNbest }, set = { s, v -> s.copy(proveNbest = v) },
        )
        int(
            "boardZoomPercent", "board zoom scale percent (60~300)", tr("보드 확대", "Board zoom"),
            SettingCategory.DISPLAY, SettingEditor.Number(60, 300, "%"),
            get = { it.boardZoomPercent }, set = { s, v -> s.copy(boardZoomPercent = v) },
        )
        fixed(
            "reservedFitBoard", "reserved (was: fit board to window)", tr("예약 슬롯", "Reserved slot"),
            SettingCategory.SYSTEM,
            note = tr("데스크톱이 위치로 파싱하므로 줄을 지우면 이후 설정이 모두 밀립니다 — 읽고 그대로 씁니다", "The desktop parses by line position, so deleting a line shifts every setting after it — this one is read and written back untouched"),
            get = { it.reservedFitBoard }, set = { s, v -> s.copy(reservedFitBoard = v) },
        )
        bool(
            "dbAutoSave", "auto-save database periodically (0: no, 1: yes)", tr("DB 주기 자동 저장", "Save the database periodically"),
            SettingCategory.DATABASE, note = tr("엔진이 쉬는 동안에만 YXSAVEDATABASE 를 보냅니다 — 탐색을 끊지 않습니다",
                "Sends YXSAVEDATABASE only while the engine is idle, so a search is never interrupted"),
            get = { it.dbAutoSave }, set = { s, v -> s.copy(dbAutoSave = v) },
        )
        int(
            "dbAutoSaveMinutes", "database auto-save interval in minutes", tr("DB 자동 저장 간격", "Database auto-save interval"),
            SettingCategory.DATABASE, SettingEditor.Number(1, 1_440, tr("분", "min")), note = null,
            get = { it.dbAutoSaveMinutes }, set = { s, v -> s.copy(dbAutoSaveMinutes = v) },
        )
        bool(
            "proveBestFirst", "prove best attack move first (0: no, 1: yes)",
            tr("증명: 최선 공격 먼저", "Prove: best attack first"), SettingCategory.RESEARCH,
            note = tr("최선수 하나만 전개하고 실패할 때만 다음 후보를 꺼냅니다", "Expands the best move alone and reaches for the next candidate only when it fails"),
            get = { it.proveBestFirst }, set = { s, v -> s.copy(proveBestFirst = v) },
        )
        bool(
            "proveProbe", "prove early probe of strongest defense (0: no, 1: yes)",
            tr("증명: 최강 방어 조기 탐색", "Prove: probe the strongest defence early"), SettingCategory.RESEARCH,
            note = tr("성립할 것 같은 방어를 먼저 한 번 확인 — 반증되면 그 가지 전체가 무의미해집니다", "Checks a defence that looks like it holds once, up front — disproving it makes the whole branch moot"),
            get = { it.proveProbe }, set = { s, v -> s.copy(proveProbe = v) },
        )
        bool(
            "reviewByDepth", "review budget unit (0: seconds, 1: depth)", tr("검토 예산 단위 = 깊이", "Review budget in depth"),
            SettingCategory.RESEARCH,
            get = { it.reviewByDepth }, set = { s, v -> s.copy(reviewByDepth = v) },
        )
        int(
            "reviewDepth", "review fixed depth per move", tr("검토 고정 깊이", "Review depth"),
            SettingCategory.RESEARCH, SettingEditor.Number(4, 64, tr("플라이", "ply")),
            get = { it.reviewDepth }, set = { s, v -> s.copy(reviewDepth = v) },
        )
        bool(
            "proveByDepth", "prove budget unit (0: seconds, 1: depth)", tr("증명 예산 단위 = 깊이", "Prove budget in depth"),
            SettingCategory.RESEARCH, note = tr("시간 대신 고정 깊이로 탐색합니다", "Searches to a fixed depth instead of for a fixed time"),
            get = { it.proveByDepth }, set = { s, v -> s.copy(proveByDepth = v) },
        )
        int(
            "proveDepth0", "prove initial depth per node", tr("증명 초기 깊이", "Prove: starting depth"),
            SettingCategory.RESEARCH, SettingEditor.Number(4, 64, tr("플라이", "ply")),
            note = tr("깊이 모드에서 노드당 시작 깊이 (실패 시 +2)", "Depth a node starts with in depth mode (+2 each time it fails)"),
            get = { it.proveDepth0 }, set = { s, v -> s.copy(proveDepth0 = v) },
        )
        int(
            "proveDepthMax", "prove depth cap per node", tr("증명 깊이 상한", "Prove: depth cap"),
            SettingCategory.RESEARCH, SettingEditor.Number(4, 128, tr("플라이", "ply")),
            get = { it.proveDepthMax }, set = { s, v -> s.copy(proveDepthMax = v) },
        )
        bool(
            "skipOpening", "skip grading/search of opening moves 1-5 (0: no, 1: yes)",
            tr("1~5수 채점 생략", "Skip grading moves 1-5"), SettingCategory.RESEARCH,
            note = tr("리뷰가 오프닝 국면을 탐색하지 않고 등급도 매기지 않습니다", "A review neither searches nor grades the opening positions"),
            get = { it.skipOpening }, set = { s, v -> s.copy(skipOpening = v) },
        )
        fixed(
            "devDefaults", "one-time dark+korean defaults applied (do not edit)",
            tr("초기 기본값 적용 표시", "Defaults-applied marker"), SettingCategory.SYSTEM,
            note = tr("데스크톱이 한 번만 쓰는 표시입니다 — 편집하지 않습니다", "A marker the desktop writes once — not for editing"),
            get = { if (it.devDefaults) 1 else 0 },
            set = { s, v -> s.copy(devDefaults = v != 0) },
        )
    }

    /**
     * The tables are built once per language. `tr()` resolves when a spec is
     * created, so a table built at startup would leave the settings screen as
     * the one place still speaking the language the app launched in after the
     * system switches. Everything else in a spec — line, comment, getter — is
     * locale-independent, so a rebuild only re-resolves the label and the note.
     */
    private var builtFor: String = ""
    private var mainSpecs: List<SettingSpec> = emptyList()
    private var devSpecs: List<SettingSpec> = emptyList()

    private fun tables(): Pair<List<SettingSpec>, List<SettingSpec>> {
        val language = Locale.getDefault().language
        if (language != builtFor || mainSpecs.isEmpty()) {
            mainSpecs = buildMain()
            devSpecs = buildDev()
            builtFor = language
        }
        return mainSpecs to devSpecs
    }

    val MAIN: List<SettingSpec> get() = tables().first

    val DEV: List<SettingSpec> get() = tables().second

    val ALL: List<SettingSpec> get() = tables().let { (main, dev) -> main + dev }

    fun spec(id: String): SettingSpec? = ALL.firstOrNull { it.id == id }

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
