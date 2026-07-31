package dev.gomoku.yixindroid.feature.explorer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gomoku.yixindroid.core.model.ExplorerGameRow
import dev.gomoku.yixindroid.core.model.ExplorerNext
import dev.gomoku.yixindroid.core.model.ExplorerPosition
import dev.gomoku.yixindroid.core.model.ExplorerStatus
import dev.gomoku.yixindroid.core.model.RjGame

/** renju.net-style colours for the three result bars (main.c:5376-5378). */
private val BlackBar = Color(0xFF6FB1E4)
private val WhiteBar = Color(0xFF7FCE97)
private val DrawBar = Color(0xFFC9CED6)

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
    val snackbar = remember { SnackbarHostState() }
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
            title = { Text("기보를 보드에 올릴까요?") },
            text = { Text("지금 보드의 대국은 이 기보로 바뀝니다.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onLoadGame(id)
                    confirmLoad = null
                }) { Text("올리기") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLoad = null }) { Text("취소") }
            },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { HeaderCard(ui, onImport = { pick.launch(arrayOf("*/*")) }, onClear = viewModel::onClearPacks) }

            ui.position?.let { pos ->
                item { KpiRow(pos) }
                if (pos.next.isNotEmpty()) {
                    item {
                        SectionTitle("다음 수 ${pos.next.size}가지 — 누르면 보드에 둡니다")
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
                        label = { Text("선수 이름 검색 (라틴 문자)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    SectionTitle(
                        "대국 ${ui.games.shown} / ${ui.games.matched}" +
                            if (ui.games.matched > ui.games.shown) " (표시 상한 1,000)" else "",
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
                ExplorerStatus.NO_PACKS -> {
                    Text("오프닝 익스플로러 팩이 없습니다", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "RenjuNet 공식 DB(.rif)를 renju.net 에서 내려받아 PC의 rifdb 스크립트 " +
                            "3개(rif_import → rif_aggregate → rif_pack)로 renju_stats.pack / " +
                            "renju_games.pack 을 만든 뒤 여기서 두 파일을 고르세요.\n" +
                            "RenjuNet 라이선스는 비상업·오프라인 전용이라 팩은 앱에 동봉되지 않고, " +
                            "이 기기 밖으로 나가지도 않습니다.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                ExplorerStatus.WRONG_SIZE ->
                    Text("익스플로러는 15×15 판에서만 동작합니다", style = MaterialTheme.typography.titleMedium)
                ExplorerStatus.NO_STATS -> {
                    Text("이 국면의 통계가 없습니다", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "팩은 시작 ${ui.packs?.maxPlies ?: 20}수 이내, " +
                            "${ui.packs?.minGames ?: 2}판 이상 나온 국면만 담습니다.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                ExplorerStatus.OK -> {
                    Text(
                        ui.position?.line.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                    ui.position?.openingLabel?.let {
                        Text("주형(RIF): $it", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            ui.packs?.let {
                Text(
                    "팩: 대국 ${it.totalGames}판 · 국면 ${it.positions}개 · ${it.dateText} 생성",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            if (ui.importing) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onImport, enabled = !ui.importing) {
                    Text(if (ui.packs == null) "팩 불러오기" else "다시 불러오기")
                }
                if (ui.packs != null) {
                    OutlinedButton(onClick = onClear, enabled = !ui.importing) { Text("지우기") }
                }
            }
        }
    }
}

/** All / Black won / White won / Draw, like the desktop's four stat cards. */
@Composable
private fun KpiRow(pos: ExplorerPosition) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Kpi("전체", pos.games, null, null, Modifier.weight(1f))
        Kpi("흑승", pos.blackWins, pos.percent(pos.blackWins), Color(0xFF3F83C6), Modifier.weight(1f))
        Kpi("백승", pos.whiteWins, pos.percent(pos.whiteWins), Color(0xFF3F9D63), Modifier.weight(1f))
        Kpi("무승부", pos.draws, pos.percent(pos.draws), null, Modifier.weight(1f))
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
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = color ?: MaterialTheme.colorScheme.onSurface,
            )
            Text(
                percent?.let { "%.1f%%".format(it) } ?: " ",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** One next-move row: the move, its game count, then three bars on one shared
 *  scale (`rj_barcell` — the widths are the counts). */
@Composable
private fun NextRow(row: ExplorerNext, scale: Int, parentGames: Int, onPlay: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onPlay)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.move.label(),
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(52.dp),
                )
                Text("${row.games}판", style = MaterialTheme.typography.bodySmall)
                if (parentGames > 0) {
                    Text(
                        "  (%.1f%%)".format(100.0 * row.games / parentGames),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Bar(row.blackWins, scale, BlackBar, Modifier.weight(1f))
                Bar(row.draws, scale, DrawBar, Modifier.weight(1f))
                Bar(row.whiteWins, scale, WhiteBar, Modifier.weight(1f))
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
                .background(color, RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (count > 0) {
                Text(
                    "$count",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF12314E),
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
            TextButton(onClick = onLoad) { Text("올리기") }
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
            Text("대국 #${g.id}", style = MaterialTheme.typography.titleSmall)
            Text("● ${mark(g.result, 0)} ${g.black}${country(g.blackCountry)}")
            Text("○ ${mark(g.result, 2)} ${g.white}${country(g.whiteCountry)}")
            Divider(Modifier.padding(vertical = 4.dp))
            val bits = buildList {
                rule?.let { add("규칙 $it") }
                if (g.rated) add("레이팅")
                add("${g.cells.size}수")
                if (useful(g.swap)) add("스왑 ${g.swap}")
                if (useful(g.alt)) add("대체 ${g.alt}")
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
                g.round.takeIf { useful(it) }?.let { "라운드 $it" },
            )
            if (tour.isNotEmpty()) {
                Text(tour.joinToString("  ·  "), style = MaterialTheme.typography.labelSmall)
            }
            opening?.let { Text("주형: $it", style = MaterialTheme.typography.bodySmall) }
            Text(
                "renju.net 기보 번호 #${g.id}",
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
