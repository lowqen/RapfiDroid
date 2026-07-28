package dev.gomoku.yixindroid.domain.review

import com.google.common.truth.Truth.assertThat
import dev.gomoku.yixindroid.core.model.GameReport
import dev.gomoku.yixindroid.core.model.GradingPreset
import dev.gomoku.yixindroid.core.model.Move
import dev.gomoku.yixindroid.core.model.PositionRecord
import dev.gomoku.yixindroid.core.model.ReviewBudget
import dev.gomoku.yixindroid.core.model.ReviewData
import org.junit.Test

/**
 * The exported files against the desktop's writers (`review_export_to`,
 * `queue_write_report`, `report_export_html`).
 */
class ReportFormatsTest {

    private val size = 15
    private val moves = listOf(Move(x = 7, y = 7), Move(x = 8, y = 6))

    private val report = GameReport.of(
        title = "test game",
        data = ReviewData(
            moves = moves,
            size = size,
            records = listOf(
                PositionRecord(blackWinRate = 0.52, best = moves[0], gap = 0.12),
                PositionRecord(blackWinRate = 0.40, best = Move(x = 5, y = 5), gap = null),
                PositionRecord(blackWinRate = null),
            ),
        ),
        budget = ReviewBudget(seconds = 3),
        preset = GradingPreset.DEFAULT,
        skipOpening = false,
        createdAt = 0,
        ruleName = "Renju",
    )

    // ---- CSV ---------------------------------------------------------------

    @Test
    fun csvKeepsTheDesktopHeaderAndOneRowPerPosition() {
        val lines = ReportFormats.csv(report).trim().lines()
        assertThat(lines.first())
            .isEqualTo("position,black_winrate,mate,bestmove,played,played_class,gap")
        assertThat(lines).hasSize(4)          // header + positions 0..2
        assertThat(lines[1]).isEqualTo("0,0.5200,0,h8,h8,,0.1200")
        // Position 2 was never searched: the winrate and mate columns stay empty.
        assertThat(lines[3]).startsWith("2,,,")
    }

    @Test
    fun csvGradesAreTheEnglishNames() {
        assertThat(ReportFormats.csv(report)).contains("Best")
    }

    // ---- Markdown ----------------------------------------------------------

    @Test
    fun markdownHasTheDesktopSections() {
        val md = ReportFormats.markdown(report)
        assertThat(md).startsWith("# 게임 리뷰: test game")
        assertThat(md).contains("- 수: 2, 수당 3 s")
        assertThat(md).contains("| 등급 | 흑 | 백 |")
        assertThat(md).contains("## 최악의 수")
        assertThat(md).contains("| # | 수 | 등급 | dWR | 흑 승률 | 최선 | 격차 | 코멘트 |")
        assertThat(md).contains("| 1 | h8 |")
    }

    @Test
    fun aDepthBudgetIsSpelledLikeTheDesktop() {
        val byDepth = report.copy(budget = ReviewBudget(byDepth = true, depth = 20))
        assertThat(ReportFormats.markdown(byDepth)).contains("수당 depth 20")
    }

    // ---- HTML --------------------------------------------------------------

    @Test
    fun theHtmlPageIsTheShellWithTheGameObjectInIt() {
        val shell = ReportShell(head = "<head><title>", body = "</title>const GAME = ", tail = ";</html>")
        val html = ReportFormats.html(report, shell, "2026-07-28 21:00")
        assertThat(html).startsWith("<head><title>test game - Yixin 게임 리포트</title>const GAME = {")
        assertThat(html).endsWith(";</html>")
        assertThat(html).contains("\"size\":15")
        assertThat(html).contains("\"rule\":\"Renju\"")
        assertThat(html).contains("\"spm\":3,\"dpm\":0")
        assertThat(html).contains("\"movestr\":\"h8i9\"")
    }

    @Test
    fun everyGradeIsInTheLegendSoThePageCanColourIt() {
        val json = ReportFormats.gameJson(report, "2026-07-28 21:00", "")
        // 11 entries: the desktop writes MQ_NONE..MQ_FORCED.
        assertThat(json.split("{\"n\":").size - 1).isEqualTo(11)
        assertThat(json).contains("\"kk\":{\"brilliant\":1,\"great\":2,\"best\":3")
    }

    @Test
    fun titlesAreEscapedForBothHtmlAndJson() {
        val nasty = report.copy(title = "a<b>&\"c\"")
        val html = ReportFormats.html(
            nasty,
            ReportShell("", "", ""),
            "2026-07-28 21:00",
        )
        assertThat(html).contains("a&lt;b&gt;&amp;&quot;c&quot;")
        assertThat(html).contains("\"title\":\"a<b>&\\\"c\\\"\"")
    }

    // ---- helpers -----------------------------------------------------------

    @Test
    fun fixedFormatsLikePrintf() {
        assertThat(ReportFormats.fixed(0.5, 4)).isEqualTo("0.5000")
        assertThat(ReportFormats.fixed(-1.0, 3)).isEqualTo("-1.000")
        assertThat(ReportFormats.fixed(12.34, 1)).isEqualTo("12.3")
        assertThat(ReportFormats.fixed(12.36, 1)).isEqualTo("12.4")
        assertThat(ReportFormats.fixed(7.0, 0)).isEqualTo("7")
    }

    @Test
    fun jsonEscapesControlCharacters() {
        assertThat(ReportFormats.json("a\nb\\c")).isEqualTo("\"a\\nb\\\\c\"")
    }
}
