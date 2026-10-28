package dev.gomoku.zenyth.feature.explorer

import dev.gomoku.zenyth.core.designsystem.component.rememberCalmFling
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
import androidx.compose.material.icons.automirrored.filled.LastPage
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material3.AlertDialog
import dev.gomoku.zenyth.core.designsystem.component.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import dev.gomoku.zenyth.core.designsystem.component.OutlinedButton
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
import dev.gomoku.zenyth.core.designsystem.component.GradeMark
import dev.gomoku.zenyth.core.designsystem.component.LocalSnackbarHostState
import dev.gomoku.zenyth.core.designsystem.component.MiniBoard
import dev.gomoku.zenyth.core.designsystem.component.ReadingWidth
import dev.gomoku.zenyth.core.designsystem.component.drawGradeMark
import dev.gomoku.zenyth.core.designsystem.theme.RapfiTheme
import dev.gomoku.zenyth.core.designsystem.theme.tabular
import dev.gomoku.zenyth.core.i18n.tr
import dev.gomoku.zenyth.core.model.ExplorerGameRow
import dev.gomoku.zenyth.core.model.ExplorerNext
import dev.gomoku.zenyth.core.model.ExplorerPosition
import dev.gomoku.zenyth.core.model.ExplorerStatus
import dev.gomoku.zenyth.core.model.Move
import dev.gomoku.zenyth.core.model.OpeningEval
import dev.gomoku.zenyth.core.model.RjGame
import dev.gomoku.zenyth.feature.data.DataSetupCard

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

    LaunchedEffect(ui.notice) {
        ui.notice?.let {
            snackbar.showSnackbar(it)
            viewModel.onNoticeShown()
        }
    }

    confirmLoad?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmLoad = null },
            title = { Text(tr("기보를 보드에 올릴까요?", "Put this game on the board?", "要把这局棋谱放到棋盘上吗?")) },
            text = { Text(tr("지금 보드의 대국은 이 기보로 바뀝니다.", "The game on the board is replaced by this one.", "棋盘上的对局会被此棋谱替换。")) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onLoadGame(id)
                    confirmLoad = null
                }) { Text(tr("올리기", "Put it on", "放上")) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLoad = null }) { Text(tr("취소", "Cancel", "取消")) }
            },
        )
    }

    val info: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
        item { HeaderCard(ui) }

        // With no data the build is the thing to do next.
        if (ui.status == ExplorerStatus.NO_PACKS) item { DataSetupCard() }

        ui.position?.let { pos ->
            if (pos.games > 0) {
                item { KpiRow(pos) }
                item { FrequencyLine(pos, ui.packs?.totalGames ?: 0) }
            }
            pos.grade?.let { g ->
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            tr("이 국면 ", "This position ", "此局面"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        GradeMark(g, size = 18.dp)
                    }
                }
            }
            if (pos.next.isNotEmpty()) {
                item { SectionTitle(tr("다음 수", "Next moves", "后续着法")) }
                items(pos.next, key = { it.move.x * 100 + it.move.y }) { row ->
                    NextRow(row, ui.barScale, pos.games) { viewModel.onPlayNext(row.move) }
                }
            }
        }

        // 환원 is pure computation from the board: true with no packs too.
        if (ui.transpositions.isNotEmpty()) {
            item { TranspositionCard(ui.transpositions) }
        }

        if (ui.status == ExplorerStatus.OK) {
            item {
                OutlinedTextField(
                    value = ui.filter,
                    onValueChange = viewModel::onFilterChange,
                    label = { Text(tr("선수 이름", "Player name", "棋手姓名")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                SectionTitle(
                    tr("대국 ${ui.games.matched}판", "${ui.games.matched} games", "对局 ${ui.games.matched} 盘") +
                        if (ui.games.matched > ui.games.shown) tr(" (${ui.games.shown}판 표시)", " (${ui.games.shown} shown)", " (显示 ${ui.games.shown} 盘)") else "",
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

        ui.packs?.let { packs ->
            item {
                dev.gomoku.zenyth.core.designsystem.component.DisclosureCard(
                    id = "explorer-data",
                    title = tr("RenjuNet 자료", "RenjuNet data", "RenjuNet 数据"),
                    summary = tr("${packs.totalGames}판", "${packs.totalGames} games", "${packs.totalGames} 盘"),
                ) {
                    Text(
                        tr("대국 ${packs.totalGames}판 · 국면 ${packs.positions}개 · ${packs.dateText}", "${packs.totalGames} games · ${packs.positions} positions · ${packs.dateText}", "对局 ${packs.totalGames} 盘 · 局面 ${packs.positions} 个 · ${packs.dateText}"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        tr("시작 ${packs.maxPlies}수 이내, ${packs.minGames}판 이상 나온 국면", "Positions within ${packs.maxPlies} moves, seen in ${packs.minGames}+ games", "开局 ${packs.maxPlies} 手以内、出现 ${packs.minGames} 盘以上的局面"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = viewModel::onClearPacks) { Text(tr("자료 지우기", "Clear the data", "清除数据")) }
                }
            }
        }
    }

    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp && maxWidth > maxHeight
        if (wide) {
            val side = androidx.compose.ui.unit.min(maxHeight - 24.dp, maxWidth * 0.55f)
            Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ExplorerBoard(ui, viewModel, Modifier.width(side))
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = info,
                    flingBehavior = rememberCalmFling(),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxHeight().align(Alignment.TopCenter).widthIn(max = ReadingWidth),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                flingBehavior = rememberCalmFling(),
            ) {
                item { ExplorerBoard(ui, viewModel, Modifier.fillMaxWidth()) }
                info()
            }
        }
    }
}

/**
 * The explorer's own board: the line on the board, every graded next move as
 * its shape and every played one as its game count. Tap a point to play it;
 * the arrows step through the line.
 */
@Composable
private fun ExplorerBoard(
    ui: OpeningExplorerUiState,
    viewModel: OpeningExplorerViewModel,
    modifier: Modifier = Modifier,
) {
    val next = ui.position?.next.orEmpty()
    val grades = next.mapNotNull { r -> r.grade?.let { r.move to it } }.toMap()
    val counts = next.filter { it.games > 0 && it.move !in grades }.take(12).associate {
        it.move to dev.gomoku.zenyth.core.designsystem.component.BoardMark("${it.games}", dev.gomoku.zenyth.core.designsystem.component.BoardMarkColors.Plain)
    }
    val render = dev.gomoku.zenyth.core.designsystem.component.BoardRender(
        stones = ui.stones,
        lastMove = ui.stones.lastOrNull(),
        gradeMarks = grades,
        marks = counts,
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        dev.gomoku.zenyth.core.designsystem.component.GomokuBoard(
            render = render,
            modifier = Modifier.fillMaxWidth(),
            onTap = viewModel::onTapBoard,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            NavButton(Icons.Filled.FirstPage, tr("처음으로", "To start", "到开头"), ui.stones.isNotEmpty(), viewModel::onStart)
            NavButton(Icons.Filled.ChevronLeft, tr("한 수 뒤로", "Back one", "后退一步"), ui.stones.isNotEmpty(), viewModel::onBack)
            NavButton(Icons.Filled.ChevronRight, tr("한 수 앞으로", "Forward one", "前进一步"), ui.ahead > 0, viewModel::onForward)
            NavButton(Icons.AutoMirrored.Filled.LastPage, tr("끝으로", "To end", "到末尾"), ui.ahead > 0, viewModel::onEnd)
        }
    }
}

@Composable
private fun NavButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    androidx.compose.material3.IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        androidx.compose.material3.Icon(icon, contentDescription = label)
    }
}

/** A card face from the calm set (white, hairline, no shadow). */
@Composable
private fun Card(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = dev.gomoku.zenyth.core.designsystem.component.cardColor(),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(content = content)
    }
}

@Composable
private fun HeaderCard(ui: OpeningExplorerUiState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (ui.status) {
                ExplorerStatus.NO_PACKS ->
                    Text(tr("실전 통계 없음 · 이름과 유불리는 그대로 나옵니다", "No real-game statistics · names and grades still work", "无实战统计 · 名称和优劣评级照常显示"), style = MaterialTheme.typography.bodyMedium)
                ExplorerStatus.WRONG_SIZE ->
                    Text(tr("15×15 판에서만 동작합니다", "Works on a 15×15 board only", "仅适用于 15×15 棋盘"), style = MaterialTheme.typography.titleMedium)
                ExplorerStatus.NO_STATS ->
                    Text(tr("이 국면의 통계가 없습니다", "No statistics for this position", "此局面没有统计"), style = MaterialTheme.typography.titleMedium)
                ExplorerStatus.OK ->
                    Text(
                        ui.position?.line.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                    )
            }
            NameChain(ui.nameChain)
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
        Kpi(tr("대국", "Games", "对局"), pos.games, null, null, Modifier.weight(1f))
        Kpi(tr("결과 흑승", "Result B", "结果 黑胜"), pos.blackWins, pos.percent(pos.blackWins), colors.resultBlack, Modifier.weight(1f))
        Kpi(tr("결과 백승", "Result W", "结果 白胜"), pos.whiteWins, pos.percent(pos.whiteWins), colors.resultWhite, Modifier.weight(1f))
        Kpi(tr("결과 무승부", "Result D", "结果 和棋"), pos.draws, pos.percent(pos.draws), null, Modifier.weight(1f))
    }
}

/** How *often* this position was reached — the other half of the pair above. */
@Composable
private fun FrequencyLine(pos: ExplorerPosition, total: Int) {
    val parts = listOfNotNull(
        pos.shareOfParent?.let { tr("직전 국면의 %.1f%%", "%.1f%% of the move before", "占上一手的 %.1f%%").format(it) },
        pos.shareOfAll(total)?.let {
            tr("전체 대비 %.2f%% (%,d판)", "%.2f%% of all games (%,d)", "占全部的 %.2f%% (%,d 盘)").format(it, total)
        },
    )
    if (parts.isEmpty()) return
    Text(
        tr("빈도: ", "Frequency: ", "频率:") + parts.joinToString(" · "),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
                tr("환원 · 같은 돌에 이르는 다른 수순 ${rows.size}가지",
                    "${rows.size} other move orders reaching the same stones",
                    "同型 · 到达相同棋子的其他手顺 ${rows.size} 种"),
                style = MaterialTheme.typography.labelLarge,
            )
            for (r in rows) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        r.chain.joinToString(" › ").ifEmpty { tr("이름 없음", "unnamed", "无名称") },
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
                    Text(tr("${row.games}판", "${row.games} games", "${row.games} 盘"), style = MaterialTheme.typography.bodySmall.tabular())
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
                    "${row.black} · ${row.white}",
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
            TextButton(onClick = onLoad) { Text(tr("올리기", "Put it on", "放上")) }
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
            Text(tr("대국 #${g.id}", "Game #${g.id}", "对局 #${g.id}"), style = MaterialTheme.typography.titleSmall)
            Text("● ${mark(g.result, 0)} ${g.black}${country(g.blackCountry)}")
            Text("○ ${mark(g.result, 2)} ${g.white}${country(g.whiteCountry)}")
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            val bits = buildList {
                rule?.let { add(tr("규칙 $it", "Rule $it", "规则 $it")) }
                if (g.rated) add(tr("레이팅", "Rating", "等级分"))
                add(tr("${g.cells.size}수", "${g.cells.size} moves", "${g.cells.size} 手"))
                if (useful(g.swap)) add(tr("스왑 ${g.swap}", "Swap ${g.swap}", "交换 ${g.swap}"))
                if (useful(g.alt)) add(tr("대체 ${g.alt}", "Alt ${g.alt}", "打点 ${g.alt}"))
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
                g.round.takeIf { useful(it) }?.let { tr("라운드 $it", "Round $it", "第 $it 轮") },
            )
            if (tour.isNotEmpty()) {
                Text(tour.joinToString("  ·  "), style = MaterialTheme.typography.labelSmall)
            }
            opening?.let { Text(tr("주형: $it", "Opening: $it", "开局: $it"), style = MaterialTheme.typography.bodySmall) }
            Text(
                tr("renju.net 기보 번호 #${g.id}", "renju.net game #${g.id}", "renju.net 棋谱编号 #${g.id}"),
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

private fun country(c: String) = if (c.isBlank() || c == "?") "" else " · $c"

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
}
