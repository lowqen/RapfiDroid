package dev.gomoku.yixindroid.feature.rankings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gomoku.yixindroid.core.designsystem.component.MiniBoard
import dev.gomoku.yixindroid.core.designsystem.theme.DrawGray
import dev.gomoku.yixindroid.core.designsystem.theme.WinBlue
import dev.gomoku.yixindroid.core.designsystem.theme.WinGreen
import dev.gomoku.yixindroid.core.model.ResultSplit

@Composable
fun RankingsScreen(
    modifier: Modifier = Modifier,
    viewModel: RankingsViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.onImport(uri) }

    Column(modifier = modifier.fillMaxSize()) {
        Header(ui, onFilter = viewModel::onOpenFilter)
        ui.dataError?.let { DataErrorBanner(it) }
        ui.error?.let { ErrorBanner(it, viewModel::onDismissError) }

        TabRow(selectedTabIndex = ui.tab.ordinal) {
            Tab(selected = ui.tab == RankTab.THREE_MOVE,
                onClick = { viewModel.onSelectTab(RankTab.THREE_MOVE) },
                text = { Text("3수 (주형)") })
            Tab(selected = ui.tab == RankTab.FIVE_MOVE,
                onClick = { viewModel.onSelectTab(RankTab.FIVE_MOVE) },
                text = { Text("5수 모양") })
        }

        when (ui.tab) {
            RankTab.THREE_MOVE -> ThreeMoveTab(ui, viewModel, Modifier.weight(1f))
            RankTab.FIVE_MOVE -> FiveMoveTab(ui, viewModel, Modifier.weight(1f))
        }
    }

    if (ui.filterSheetOpen) {
        FilterSheet(ui, viewModel, onImport = { picker.launch(arrayOf("application/json", "text/plain", "*/*")) })
    }
}

@Composable
private fun Header(ui: RankingsUiState, onFilter: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("오프닝 랭킹", style = MaterialTheme.typography.titleLarge)
            val sub = if (ui.freqLoaded) {
                "실전 %,d판 · 이론 %,d형".format(ui.freqGameCount, ui.shapeTotal)
            } else {
                "이론 %,d형 (rank5) · 실전 데이터 없음".format(ui.shapeTotal)
            }
            Text(sub, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val badge = if (ui.filterActive) "필터·" + selectedLabel(ui) else "필터"
        AssistChip(onClick = onFilter, label = { Text(badge) },
            leadingIcon = { Icon(Icons.Filled.FilterList, contentDescription = null) })
    }
}

private fun selectedLabel(ui: RankingsUiState): String = buildString {
    if (ui.selectedPlayers.isNotEmpty()) append(ui.selectedPlayers.first().name)
    val rules = ui.filter.ruleIndices.size
    if (rules > 0) { if (isNotEmpty()) append("·"); append("룰$rules") }
    val extra = ui.selectedPlayers.size - 1
    if (extra > 0) append(" +$extra")
}

@Composable
private fun DataErrorBanner(message: String) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Text(message, color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onDismiss) { Text("닫기") }
        }
    }
}

// ---------------- 3-move tab ----------------

@Composable
private fun ThreeMoveTab(ui: RankingsUiState, vm: RankingsViewModel, modifier: Modifier) {
    // A re-sorted or re-filtered list is a *new* list, and a lazy list keeps the
    // item that was on screen: it re-finds it by key and scrolls to wherever it
    // landed, which drops the user into the middle of a ranking they just asked
    // to see from the top. Reset when the content itself changes — an identical
    // list (toggling a sort with no dataset loaded) compares equal and is left
    // alone, so an idle recomposition never yanks the scroll.
    val gridState = rememberLazyGridState()
    LaunchedEffect(ui.openingCards) { gridState.scrollToItem(0) }
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DirectFilter.entries.forEach { df ->
                FilterChip(selected = ui.directFilter == df, onClick = { vm.onDirectFilter(df) },
                    label = { Text(when (df) {
                        DirectFilter.ALL -> "전체"; DirectFilter.DIRECT -> "직접(直)"
                        DirectFilter.INDIRECT -> "간접(間)"
                    }) })
            }
            Spacer(Modifier.weight(1f))
            if (ui.freqLoaded) {
                FilterChip(selected = ui.sortThreeByFreq, onClick = { vm.onToggleThreeSort() },
                    label = { Text(if (ui.sortThreeByFreq) "실전순" else "번호순") })
            }
        }
        if (ui.freqLoaded) {
            Text("필터 대국 %,d판".format(ui.threeTotalGames),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 158.dp),
            state = gridState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(ui.openingCards, key = { it.index }) { card ->
                OpeningCardView(card, ui.freqLoaded, ui.threeTotalGames)
            }
        }
    }
}

@Composable
private fun OpeningCardView(card: OpeningCard, freqLoaded: Boolean, totalGames: Int) {
    Card {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(card.abbr, style = MaterialTheme.typography.titleMedium,
                    color = if (card.direct) WinBlue else WinGreen, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Text("${card.korean} · ${card.romaji}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            MiniBoard(card.moves, Modifier.fillMaxWidth().height(120.dp))
            Spacer(Modifier.height(6.dp))
            if (freqLoaded && card.split != null && card.split.total > 0) {
                val pct = if (totalGames > 0) card.split.total * 100.0 / totalGames else 0.0
                Text("%,d판 · %.1f%%".format(card.split.total, pct),
                    style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                ResultBar(card.split)
            } else {
                Text("이론 ${card.theoryShapeCount}형",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---------------- 5-move tab ----------------

@Composable
private fun FiveMoveTab(ui: RankingsUiState, vm: RankingsViewModel, modifier: Modifier) {
    // Same as the 3-move grid: a new ordering starts at the top. Here the keys
    // usually vanish entirely (이론순 ↔ 실전순 share almost no rows), so the list
    // falls back to holding the raw index — the middle of the old scroll.
    val listState = rememberLazyListState()
    LaunchedEffect(ui.fiveRows) { listState.scrollToItem(0) }
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(selected = ui.fiveSort == FiveSort.THEORY,
                onClick = { vm.onFiveSort(FiveSort.THEORY) }, label = { Text("이론순") })
            FilterChip(selected = ui.fiveSort == FiveSort.EMPIRICAL, enabled = ui.freqLoaded,
                onClick = { vm.onFiveSort(FiveSort.EMPIRICAL) }, label = { Text("실전순") })
            Spacer(Modifier.weight(1f))
            BoardScope.entries.forEach { sc ->
                FilterChip(selected = ui.boardScope == sc, onClick = { vm.onBoardScope(sc) },
                    label = { Text(sc.label) },
                    enabled = ui.fiveSort == FiveSort.THEORY)
            }
        }
        OutlinedTextField(
            value = ui.fiveQuery, onValueChange = vm::onFiveQueryChange,
            label = { Text("수순 검색 (예: h8 i9)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        )
        if (ui.fiveSort == FiveSort.THEORY) GroupChart(ui.groupDist)
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            state = listState,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(ui.fiveRows, key = { "${it.rankRaw}:${it.repMoves}" }) { row ->
                FiveRowView(row)
            }
        }
    }
}

@Composable
private fun GroupChart(dist: List<Pair<Int, Int>>) {
    if (dist.isEmpty()) return
    val total = dist.sumOf { it.second }.coerceAtLeast(1)
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text("경우의 수 그룹 분포", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(4.dp))) {
            dist.forEachIndexed { i, (group, n) ->
                Box(Modifier.weight(n.toFloat().coerceAtLeast(0.001f)).fillMaxHeight()
                    .background(groupColor(i)))
            }
        }
        Spacer(Modifier.height(4.dp))
        FlowRowLegend(dist)
    }
}

@Composable
private fun FlowRowLegend(dist: List<Pair<Int, Int>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        dist.forEachIndexed { i, (group, n) ->
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(groupColor(i)))
                Text("×$group: %,d".format(n), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private val groupPalette = listOf(WinBlue, WinGreen, Color(0xFFE7C77E), Color(0xFFC58AE0))
private fun groupColor(i: Int) = groupPalette[i % groupPalette.size]

@Composable
private fun FiveRowView(row: FiveRow) {
    Card {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(44.dp)) {
                Text("#${row.rankRaw}", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold)
                Text("×${row.group}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MiniBoard(row.moves, Modifier.size(76.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (row.openingIndex < 26) row.opening else "—",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (row.openingIndex in 0..12) WinBlue else WinGreen)
                    row.empiricalCount?.let {
                        Text("실전 %,d판".format(it), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(row.repMoves,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                row.split?.let { if (it.total > 0) { Spacer(Modifier.height(4.dp)); ResultBar(it) } }
            }
        }
    }
}

// ---------------- shared bits ----------------

@Composable
private fun ResultBar(split: ResultSplit) {
    val decided = (split.blackWins + split.draws + split.whiteWins).coerceAtLeast(1)
    Column {
        Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(3.dp))) {
            Box(Modifier.weight(split.blackWins.toFloat().coerceAtLeast(0.001f)).fillMaxHeight().background(WinBlue))
            Box(Modifier.weight(split.draws.toFloat().coerceAtLeast(0.001f)).fillMaxHeight().background(DrawGray))
            Box(Modifier.weight(split.whiteWins.toFloat().coerceAtLeast(0.001f)).fillMaxHeight().background(WinGreen))
        }
        Text("흑 ${pct(split.blackWins, decided)} · 무 ${pct(split.draws, decided)} · 백 ${pct(split.whiteWins, decided)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun pct(part: Int, total: Int): String =
    if (total <= 0) "0%" else "%.0f%%".format(part * 100.0 / total)

// ---------------- filter sheet ----------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(ui: RankingsUiState, vm: RankingsViewModel, onImport: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = vm::onCloseFilter, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("필터", style = MaterialTheme.typography.titleLarge)

            // dataset
            if (ui.freqLoaded) {
                Text("실전 데이터: %,d판 (%s)".format(ui.freqGameCount, ui.freqGenerated ?: "-"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("실전 데이터가 없습니다. freq_data.json을 임포트하면 실전 빈도·승률이 표시됩니다. " +
                    "(RenjuNet 파생 — 기기 반입 전용, 재배포 금지)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onImport, label = { Text("freq 임포트") },
                    leadingIcon = { Icon(Icons.Filled.UploadFile, contentDescription = null) })
                if (ui.freqLoaded) {
                    AssistChip(onClick = vm::onClearFreq, label = { Text("데이터 해제") })
                }
                if (ui.importing) Text("불러오는 중…", style = MaterialTheme.typography.labelMedium)
            }

            if (ui.freqLoaded) {
                // player search
                OutlinedTextField(value = ui.playerQuery, onValueChange = vm::onPlayerQueryChange,
                    label = { Text("선수 검색 (이름/국가)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                if (ui.playerSuggestions.isNotEmpty()) {
                    Surface(tonalElevation = 2.dp, shape = RoundedCornerShape(8.dp)) {
                        LazyColumn(Modifier.fillMaxWidth().height(180.dp)) {
                            items(ui.playerSuggestions, key = { it.index }) { p ->
                                Text(if (p.country.isBlank()) p.name else "${p.name}  ·  ${p.country}",
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable { vm.onSelectPlayer(p) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                if (ui.selectedPlayers.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ui.selectedPlayers.forEach { p ->
                            InputChip(selected = true, onClick = { vm.onRemovePlayer(p) },
                                label = { Text(p.name) },
                                trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "제거",
                                    modifier = Modifier.size(16.dp)) })
                        }
                    }
                }

                // rule chips
                if (ui.ruleOptions.isNotEmpty()) {
                    Text("룰", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ui.ruleOptions.forEach { (idx, name) ->
                            FilterChip(selected = idx in ui.filter.ruleIndices,
                                onClick = { vm.onToggleRule(idx) }, label = { Text(name) })
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = vm::onClearFilter) { Text("필터 초기화") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = vm::onCloseFilter) { Text("적용") }
                }
            }
        }
    }
}

