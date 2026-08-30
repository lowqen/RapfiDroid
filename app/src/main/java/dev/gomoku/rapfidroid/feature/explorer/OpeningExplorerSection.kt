package dev.gomoku.rapfidroid.feature.explorer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gomoku.rapfidroid.core.designsystem.component.EmptyState
import dev.gomoku.rapfidroid.core.designsystem.component.LocalSnackbarHostState
import dev.gomoku.rapfidroid.core.designsystem.component.MiniBoard
import dev.gomoku.rapfidroid.core.designsystem.component.ReadingWidth
import dev.gomoku.rapfidroid.core.designsystem.component.drawGradeMark
import dev.gomoku.rapfidroid.core.designsystem.theme.RapfiTheme
import dev.gomoku.rapfidroid.core.designsystem.theme.tabular
import dev.gomoku.rapfidroid.core.i18n.tr
import dev.gomoku.rapfidroid.core.model.ExplorerGameRow
import dev.gomoku.rapfidroid.core.model.ExplorerNext
import dev.gomoku.rapfidroid.core.model.ExplorerPosition
import dev.gomoku.rapfidroid.core.model.ExplorerStatus
import dev.gomoku.rapfidroid.core.model.Move
import dev.gomoku.rapfidroid.core.model.OpeningEval
import dev.gomoku.rapfidroid.core.model.RjGame
import dev.gomoku.rapfidroid.feature.bundle.DataImportCard

/**
 * 오프닝 익스플로러 — RenjuNet statistics for the position on the board
 * (main.c:5301-5724).
 *
 * ⚠ The packs are user-imported and never redistributed: without them this is
 * a how-to notice, which is the intended state for anyone who has not built
 * them from their own `.rif` download.
 */
@Composable
fun OpeningExplorerSection(
    modifier: Modifier = Modifier,
    viewModel: OpeningExplorerViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    // The app's one snackbar, not a second host inside a tab — a notice raised
    // here used to appear in a different place from one raised on the board.
    val snackbar = LocalSnackbarHostState.current
    var confirmLoad by remember { mutableStateOf<Int?>(null) }

    val pick = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> viewModel.onImport(uris) }

    LaunchedEffect(ui.notice) {
        ui.notice?.let {
            snackbar.showSnackbar(it)
            viewModel.onNoticeShown()
        }
    }

    confirmLoad?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmLoad = null },
            title = { Text(tr("기보를 보드에 올릴까요?", "Put this game on the board?")) },
            text = { Text(tr("지금 보드의 대국은 이 기보로 바뀝니다.", "The game on the board is replaced by this one.")) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onLoadGame(id)
                    confirmLoad = null
                }) { Text(tr("올리기", "Put it on")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLoad = null }) { Text(tr("취소", "Cancel")) }
            },
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxHeight().wrapContentWidth().widthIn(max = ReadingWidth),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { HeaderCard(ui, onImport = { pick.launch(arrayOf("*/*")) }, onClear = viewModel::onClearPacks) }

        // With no packs the folder import is the thing to do next, so it is
        // offered here rather than only on the settings screen.
        if (ui.status == ExplorerStatus.NO_PACKS) item { DataImportCard() }

        // 환원 is pure computation from the board, so it goes outside the
        // position block: just as true with no packs and no statistics.
        if (ui.transpositions.isNotEmpty()) {
            item { TranspositionCard(ui.transpositions) }
        }

        ui.position?.let { pos ->
            if (pos.games > 0) {
                item { KpiRow(pos) }
                item { FrequencyLine(pos, ui.packs?.totalGames ?: 0) }
            }
            pos.grade?.let { g ->
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            tr("이 국면: ", "This position: "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        GradeMark(g, size = 18.dp)
                    }
                }
            }
            item { GradedBoard(pos, ui.stones) }
            if (pos.next.isNotEmpty()) {
                item {
                    val graded = pos.next.count { it.grade != null }
                    SectionTitle(
                        tr("다음 수 ${pos.next.size}가지 — 누르면 보드에 둡니다", "${pos.next.size} continuations — tap one to play it") +
                            if (graded > 0) tr(" · 유불리 ${graded}개", " · $graded graded") else "",
                    )
                }
                items(pos.next, key = { it.move.x * 100 + it.move.y }) { row ->
                    NextRow(row, ui.barScale, pos.games) { viewModel.onPlayNext(row.move) }
                }
            }
        }

        if (ui.status == ExplorerStatus.OK) {
            item {
                OutlinedTextField(
                    value = ui.filter,
                    onValueChange = viewModel::onFilterChange,
                    label = { Text(tr("선수 이름 검색 (라틴 문자)", "Search a player name (Latin letters)")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                SectionTitle(
                    tr("대국 ${ui.games.shown} / ${ui.games.matched}", "Games ${ui.games.shown} / ${ui.games.matched}") +
                        if (ui.games.matched > ui.games.shown) tr(" (표시 상한 1,000)", " (1,000 shown at most)") else "",
                )
            }
            items(ui.games.rows, key = { it.id }) { row ->
                GameRow(
                    row = row,
                    selected = ui.selected?.id == row.id,
                    onSelect = { viewModel.onSelectGame(row.id) },
                    onLoad = { confirmLoad = row.id },
                )
            }
            ui.selected?.let { g ->
                item { GameDetail(g, ui.selectedRule, ui.selectedOpening) }
            }
        }
    }
}

@Composable
private fun HeaderCard(
    ui: OpeningExplorerUiState,
    onImport: () -> Unit,
    onClear: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (ui.status) {
                // The app's one empty state (see [EmptyState]). How to *get*
                // the packs is the card below; what stays here is the part that
                // is true whatever the user does — the licence, and that the
                // names and grades work regardless.
                ExplorerStatus.NO_PACKS -> EmptyState(
                    icon = Icons.Filled.Inventory2,
                    title = tr("오프닝 익스플로러 팩이 없습니다", "No opening explorer packs"),
                    body = tr("아래에서 PC 의 자료 폴더를 고르면 됩니다. RenjuNet 라이선스가 비상업·오프라인 ", "Pick the PC's data folder below. The RenjuNet licence is non-commercial and offline only, ") +
                        tr("전용이라 팩은 앱에 동봉되지 않고 이 기기 밖으로 나가지도 않습니다. ", "so the packs are not shipped with the app and never leave this device. ") +
                        tr("오프닝 이름과 흑 5수 유불리는 팩 없이도 그대로 나옵니다.", "Opening names and the black-5 grades work without them."),
                )
                ExplorerStatus.WRONG_SIZE ->
                    Text(tr("익스플로러는 15×15 판에서만 동작합니다", "The explorer works on a 15×15 board only"), style = MaterialTheme.typography.titleMedium)
                ExplorerStatus.NO_STATS -> {
                    Text(tr("이 국면의 통계가 없습니다", "No statistics for this position"), style = MaterialTheme.typography.titleMedium)
                    Text(
                        tr("팩은 시작 ${ui.packs?.maxPlies ?: 20}수 이내, ", "The packs hold positions within the first ${ui.packs?.maxPlies ?: 20} moves") +
                            tr("${ui.packs?.minGames ?: 2}판 이상 나온 국면만 담습니다.", "that occur in at least ${ui.packs?.minGames ?: 2} games."),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                ExplorerStatus.OK ->
                    Text(
                        ui.position?.line.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                    )
            }
            // The chain is computed from the board alone, so it belongs
            // outside the `when`: it is just as true with no packs and no
            // statistics as with them (main.c `rjexp_sync`).
            NameChain(ui.nameChain)
            ui.packs?.let {
                Text(
                    tr("팩: 대국 ${it.totalGames}판 · 국면 ${it.positions}개 · ${it.dateText} 생성", "Packs: ${it.totalGames} games · ${it.positions} positions · built ${it.dateText}"),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (ui.importing) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onImport, enabled = !ui.importing) {
                    Text(if (ui.packs == null) tr("팩 불러오기", "Load packs") else tr("다시 불러오기", "Load again"))
                }
                if (ui.packs != null) {
                    OutlinedButton(onClick = onClear, enabled = !ui.importing) { Text(tr("지우기", "Clear")) }
                }
            }
        }
    }
}

/**
 * "천원 › 간접막기 › 화월" — the named steps of this line, earlier ones dimmed
 * and the deepest one in bold, so the eye lands on the name that just changed.
 * Empty chain draws nothing at all: most positions have no name, and that is
 * the normal case rather than a gap to apologise for.
 */
@Composable
private fun NameChain(chain: List<String>) {
    if (chain.isEmpty()) return
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        buildAnnotatedString {
            chain.forEachIndexed { i, name ->
                if (i == chain.lastIndex) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(name) }
                } else {
                    withStyle(SpanStyle(color = dim)) { append("$name › ") }
                }
            }
        },
        style = MaterialTheme.typography.titleSmall,
    )
}

/**
 * The desktop's four stat cards, with the labels saying which question they
 * answer. Three of them are the **result** split of the games that reached
 * here — not how often the shape appears, which is [FrequencyLine] below.
 * Running the two together is exactly the confusion this wording removes.
 */
@Composable
private fun KpiRow(pos: ExplorerPosition) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val colors = RapfiTheme.colors
        Kpi(tr("대국", "Games"), pos.games, null, null, Modifier.weight(1f))
        Kpi(tr("결과 흑승", "Result B"), pos.blackWins, pos.percent(pos.blackWins), colors.resultBlack, Modifier.weight(1f))
        Kpi(tr("결과 백승", "Result W"), pos.whiteWins, pos.percent(pos.whiteWins), colors.resultWhite, Modifier.weight(1f))
        Kpi(tr("결과 무승부", "Result D"), pos.draws, pos.percent(pos.draws), null, Modifier.weight(1f))
    }
}

/** How *often* this position was reached — the other half of the pair above. */
@Composable
private fun FrequencyLine(pos: ExplorerPosition, total: Int) {
    val parts = listOfNotNull(
        pos.shareOfParent?.let { tr("직전 국면의 %.1f%%", "%.1f%% of the move before").format(it) },
        pos.shareOfAll(total)?.let {
            tr("전체 대비 %.2f%% (%,d판)", "%.2f%% of all games (%,d)").format(it, total)
        },
    )
    if (parts.isEmpty()) return
    Text(
        tr("빈도: ", "Frequency: ") + parts.joinToString(" · "),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The 흑 5수 유불리 grade as the user's evaluation table draws it: a coloured
 * shape plus its name.
 *
 * Deliberately not the engine's winrate colour ramp — that is a number the
 * engine computed and this is one a person wrote down, and painting them alike
 * would make the two indistinguishable on the same screen.
 */
@Composable
private fun GradeMark(grade: OpeningEval.Grade, size: Dp = 14.dp, withName: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(size)) {
            val side = this.size.minDimension
            drawGradeMark(grade, Offset(side / 2f, side / 2f), side * 0.46f)
        }
        if (withName) {
            Text(
                "  " + tr(grade.ko, grade.en),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/**
 * 환원 — the other move orders that reach these very stones.
 *
 * Computed, never stored: three black stones and two white ones have twelve
 * orders at most, and the renju rules throw nearly all of them away (1.1 left
 * on average). Each surviving order earns its own name, which is the point —
 * the same picture is 한성's 4th move down one path and an indirect opening's
 * down another. The evaluation, being a fact about the position, is one number
 * for all of them.
 */
@Composable
private fun TranspositionCard(rows: List<Transposition>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp).fillMaxWidth()) {
            Text(
                tr("환원 — 같은 모양에 이르는 다른 수순 ${rows.size}가지",
                    "${rows.size} other move orders reaching the same stones"),
                style = MaterialTheme.typography.labelLarge,
            )
            for (r in rows) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        r.chain.joinToString(" › ").ifEmpty { tr("이름 없음", "unnamed") },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        r.line,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * The position with every graded fifth move on it — the same picture the user's
 * evaluation table draws, and the same rows as the list below it.
 *
 * The desktop puts this beside its table for the same reason: eleven grades
 * over a dozen points are a shape, not a column of words.
 */
@Composable
private fun GradedBoard(pos: ExplorerPosition, stones: List<Move>) {
    val marks = pos.next.mapNotNull { row -> row.grade?.let { row.move to it } }
    if (marks.isEmpty()) return
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MiniBoard(
                stones = stones,
                modifier = Modifier.size(260.dp),
                marks = marks,
            )
        }
    }
}

@Composable
private fun Kpi(
    label: String,
    value: Int,
    percent: Double?,
    color: Color?,
    modifier: Modifier = Modifier,
) {
    Card(modifier) {
        Column(
            Modifier.padding(vertical = 8.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(
                "$value",
                style = MaterialTheme.typography.headlineSmall.tabular(),
                fontWeight = FontWeight.Bold,
                color = color ?: MaterialTheme.colorScheme.onSurface,
            )
            Text(
                percent?.let { "%.1f%%".format(it) } ?: " ",
                style = MaterialTheme.typography.labelSmall.tabular(),
            )
        }
    }
}

/**
 * One next-move row: the move, the name it makes, its grade, how often it was
 * played, then three result bars on one shared scale (`rj_barcell` — the widths
 * are the counts) and black's score rate.
 *
 * [parentGames] is this position's own game count, so the share reads "of the
 * games that got here". A row with no games at all is one the table knows and
 * the packs do not; it shows an em dash rather than a misleading zero.
 */
@Composable
private fun NextRow(row: ExplorerNext, scale: Int, parentGames: Int, onPlay: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onPlay)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.move.label(),
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace,
                    // A minimum, not a width: at 200 % font scale a fixed 52dp
                    // clipped the coordinate it exists to show.
                    modifier = Modifier.widthIn(min = 52.dp),
                )
                row.name?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                row.grade?.let {
                    GradeMark(it)
                    Spacer(Modifier.width(8.dp))
                }
                Spacer(Modifier.weight(1f))
                if (row.games > 0) {
                    Text(tr("${row.games}판", "${row.games} games"), style = MaterialTheme.typography.bodySmall.tabular())
                    if (parentGames > 0) {
                        Text(
                            "  %.1f%%".format(100.0 * row.games / parentGames),
                            style = MaterialTheme.typography.labelSmall.tabular(),
                        )
                    }
                } else {
                    Text("—", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (row.games > 0) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val colors = RapfiTheme.colors
                    Bar(row.blackWins, scale, colors.resultBlack, Modifier.weight(1f))
                    Bar(row.draws, scale, colors.resultDraw, Modifier.weight(1f))
                    Bar(row.whiteWins, scale, colors.resultWhite, Modifier.weight(1f))
                    Text(
                        row.blackScore?.let { "%.0f%%".format(it) } ?: " ",
                        style = MaterialTheme.typography.labelSmall.tabular(),
                        modifier = Modifier.width(40.dp),
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
private fun Bar(count: Int, scale: Int, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.padding(end = 4.dp)) {
        val fraction = if (scale > 0) (count.toFloat() / scale).coerceIn(0f, 1f) else 0f
        Box(
            Modifier.fillMaxWidth(fraction.coerceAtLeast(if (count > 0) 0.18f else 0f))
                .height(18.dp)
                .background(color, MaterialTheme.shapes.extraSmall),
            contentAlignment = Alignment.Center,
        ) {
            if (count > 0) {
                Text(
                    "$count",
                    style = MaterialTheme.typography.labelSmall.tabular(),
                    color = RapfiTheme.colors.onResult,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun GameRow(
    row: ExplorerGameRow,
    selected: Boolean,
    onSelect: () -> Unit,
    onLoad: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onSelect),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${row.black} — ${row.white}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                )
                Text(
                    "${row.year} · ${row.tournament}",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Text(
                row.result,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
            )
            TextButton(onClick = onLoad) { Text(tr("올리기", "Put it on")) }
        }
    }
}

/** The detail pane: players with the winner marked, then tournament and
 *  opening (main.c `rjexp_detail_set`). Blank and "-----" fields carry no
 *  information and are dropped, like `rjexp_dseg` does. */
@Composable
private fun GameDetail(g: RjGame, rule: String?, opening: String?) {
    fun useful(v: String) = v.isNotBlank() && v != "?" && v.any { it != '-' }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(tr("대국 #${g.id}", "Game #${g.id}"), style = MaterialTheme.typography.titleSmall)
            Text("● ${mark(g.result, 0)} ${g.black}${country(g.blackCountry)}")
            Text("○ ${mark(g.result, 2)} ${g.white}${country(g.whiteCountry)}")
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            val bits = buildList {
                rule?.let { add(tr("규칙 $it", "Rule $it")) }
                if (g.rated) add(tr("레이팅", "Rating"))
                add(tr("${g.cells.size}수", "${g.cells.size} moves"))
                if (useful(g.swap)) add(tr("스왑 ${g.swap}", "Swap ${g.swap}"))
                if (useful(g.alt)) add(tr("대체 ${g.alt}", "Alt ${g.alt}"))
                if (useful(g.info)) add(g.info)
            }
            Text(bits.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall)
            Text(g.tournament, style = MaterialTheme.typography.bodyMedium)
            val dates = when {
                useful(g.tourStart) || useful(g.tourEnd) ->
                    listOf(g.tourStart, g.tourEnd).filter { useful(it) }.joinToString(" ~ ")
                g.year > 0 -> "${g.year}"
                else -> ""
            }
            val tour = listOfNotNull(
                dates.takeIf { it.isNotEmpty() },
                g.tourCountry.takeIf { useful(it) },
                g.round.takeIf { useful(it) }?.let { tr("라운드 $it", "Round $it") },
            )
            if (tour.isNotEmpty()) {
                Text(tour.joinToString("  ·  "), style = MaterialTheme.typography.labelSmall)
            }
            opening?.let { Text(tr("주형: $it", "Opening: $it"), style = MaterialTheme.typography.bodySmall) }
            Text(
                tr("renju.net 기보 번호 #${g.id}", "renju.net game #${g.id}"),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

private fun mark(result: Int, side: Int) = when {
    result == 1 -> "½"
    result == side -> "✔"
    else -> "✘"
}

private fun country(c: String) = if (c.isBlank() || c == "?") "" else " — $c"

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
}
