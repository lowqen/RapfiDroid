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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gomoku.yixindroid.core.designsystem.component.MiniBoard
import dev.gomoku.yixindroid.core.designsystem.component.EmptyState
import dev.gomoku.yixindroid.core.designsystem.theme.YixinTheme
import dev.gomoku.yixindroid.core.designsystem.theme.tabular
import dev.gomoku.yixindroid.core.i18n.tr
import dev.gomoku.yixindroid.core.model.Opening26
import dev.gomoku.yixindroid.core.model.ResultSplit
import dev.gomoku.yixindroid.feature.bundle.DataImportCard

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
        ui.error?.let { ErrorBanner(it, viewModel::onDismissError) }

        // Without the dataset both tabs are near-empty, so the way to fill them
        // goes above the tabs rather than inside a filter sheet the user has no
        // reason to open yet.
        if (!ui.freqLoaded) {
            DataImportCard(Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }

        // A segmented control, not a second tab row. This screen already sits
        // inside the research tabs, and tabs nested in tabs read as one broken
        // row rather than two levels; a segmented button says "two views of the
        // same thing", which is what these are.
        val tabs = listOf(
            RankTab.THREE_MOVE to tr("3수 (주형)", "Move 3 (openings)"),
            RankTab.FIVE_MOVE to tr("5수 모양", "Move 5 shapes"),
        )
        SingleChoiceSegmentedButtonRow(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            tabs.forEachIndexed { i, (tab, label) ->
                SegmentedButton(
                    selected = ui.tab == tab,
                    onClick = { viewModel.onSelectTab(tab) },
                    shape = SegmentedButtonDefaults.itemShape(i, tabs.size),
                ) { Text(label, maxLines = 1) }
            }
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
            // No title of its own: the tab above already says 랭킹, and a screen
            // that repeats its own tab label spends a line saying nothing.
            val sub = if (ui.freqLoaded) {
                tr("실전 %,d판", "%,d games").format(ui.freqGameCount)
            } else {
                tr("실전 데이터 없음 — 필터에서 freq_data.json 을 불러오세요",
                    "No game data — load freq_data.json from the filter sheet")
            }
            Text(sub, style = MaterialTheme.typography.bodyMedium.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val badge = if (ui.filterActive) tr("필터·", "Filter ·") + selectedLabel(ui) else tr("필터", "Filter")
        AssistChip(onClick = onFilter, label = { Text(badge) },
            leadingIcon = { Icon(Icons.Filled.FilterList, contentDescription = null) })
    }
}

private fun selectedLabel(ui: RankingsUiState): String = buildString {
    if (ui.selectedPlayers.isNotEmpty()) append(ui.selectedPlayers.first().name)
    val rules = ui.filter.ruleIndices.size
    if (rules > 0) { if (isNotEmpty()) append("·"); append(tr("룰$rules", "rule$rules")) }
    val extra = ui.selectedPlayers.size - 1
    if (extra > 0) append(" +$extra")
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onDismiss) { Text(tr("닫기", "Close")) }
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
                        DirectFilter.ALL -> tr("전체", "All"); DirectFilter.DIRECT -> tr("직접(直)", "Direct")
                        DirectFilter.INDIRECT -> tr("간접(間)", "Indirect")
                    }) })
            }
            Spacer(Modifier.weight(1f))
            if (ui.freqLoaded) {
                FilterChip(selected = ui.sortThreeByFreq, onClick = { vm.onToggleThreeSort() },
                    label = { Text(if (ui.sortThreeByFreq) tr("실전순", "By games") else tr("번호순", "By number")) })
            }
        }
        if (ui.freqLoaded) {
            Text(tr("필터 대국 %,d판", "%,d games match").format(ui.threeTotalGames),
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
                // 直 / 間 is a category, not a result, so it no longer borrows
                // the black-won / white-won colours.
                Text(card.abbr, style = MaterialTheme.typography.titleMedium,
                    color = if (card.direct) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Bold)
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
                Text(tr("%,d판 · %.1f%%", "%,d games · %.1f%%").format(card.split.total, pct),
                    style = MaterialTheme.typography.labelMedium.tabular())
                Spacer(Modifier.height(4.dp))
                ResultBar(card.split)
            }
        }
    }
}

// ---------------- 5-move tab ----------------

@Composable
private fun FiveMoveTab(ui: RankingsUiState, vm: RankingsViewModel, modifier: Modifier) {
    // Same as the 3-move grid: a new ordering starts at the top. A filter change
    // can drop every visible key, so the list would otherwise hold a raw index —
    // the middle of the old scroll.
    val fiveState = rememberLazyGridState()
    LaunchedEffect(ui.fiveRows) { fiveState.scrollToItem(0) }
    Column(modifier) {
        OutlinedTextField(
            value = ui.fiveQuery, onValueChange = vm::onFiveQueryChange,
            label = { Text(tr("수순 검색 (예: h8 i9)", "Search a move order (e.g. h8 i9)")) }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        )
        // The same adaptive grid the 3-move tab uses: one column on a phone,
        // two or more on a tablet, from one number instead of a breakpoint.
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 320.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
            state = fiveState,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (ui.fiveRows.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        icon = Icons.Filled.BarChart,
                        title = tr("표시할 모양이 없습니다", "Nothing to show"),
                        body = tr(
                            "5수 탭은 실전 빈도만 보여 줍니다. 자료를 반입하면 채워집니다.",
                            "This tab is built from real games only. Import the dataset and it fills in.",
                        ),
                    )
                }
            }
            items(ui.fiveRows, key = { it.repMoves }) { row ->
                FiveRowView(row, ui.freqGameCount)
            }
        }
    }
}


@Composable
private fun FiveRowView(row: FiveRow, totalGames: Int) {
    Card {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            MiniBoard(row.moves, Modifier.size(76.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (row.openingIndex in 0 until 26) Opening26.abbr[row.openingIndex] else "—",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (row.openingIndex in 0..12) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        tr("실전 %,d판", "%,d games").format(row.count) +
                            if (totalGames > 0) " · %.2f%%".format(row.count * 100.0 / totalGames) else "",
                        style = MaterialTheme.typography.labelSmall.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
    val colors = YixinTheme.colors
    Column {
        Row(Modifier.fillMaxWidth().height(10.dp).clip(MaterialTheme.shapes.extraSmall)) {
            Box(Modifier.weight(split.blackWins.toFloat().coerceAtLeast(0.001f)).fillMaxHeight().background(colors.resultBlack))
            Box(Modifier.weight(split.draws.toFloat().coerceAtLeast(0.001f)).fillMaxHeight().background(colors.resultDraw))
            Box(Modifier.weight(split.whiteWins.toFloat().coerceAtLeast(0.001f)).fillMaxHeight().background(colors.resultWhite))
        }
        Text(tr("흑 ${pct(split.blackWins, decided)} · 무 ${pct(split.draws, decided)} · 백 ${pct(split.whiteWins, decided)}", "Black ${pct(split.blackWins, decided)} · draw ${pct(split.draws, decided)} · White ${pct(split.whiteWins, decided)}"),
            style = MaterialTheme.typography.labelSmall.tabular(),
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
            Text(tr("필터", "Filter"), style = MaterialTheme.typography.titleLarge)

            // dataset
            if (ui.freqLoaded) {
                Text(tr("실전 데이터: %,d판 (%s)", "Game data: %,d games (%s)").format(ui.freqGameCount, ui.freqGenerated ?: "-"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(tr("실전 데이터가 없습니다. freq_data.json을 임포트하면 실전 빈도·승률이 표시됩니다. ", "No game data. Import freq_data.json to see how often each opening is played and how it scores.") +
                    tr("(RenjuNet 파생 — 기기 반입 전용, 재배포 금지)", "(RenjuNet derived — bring it to the device yourself, do not redistribute)"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onImport, label = { Text(tr("freq 임포트", "Import freq")) },
                    leadingIcon = { Icon(Icons.Filled.UploadFile, contentDescription = null) })
                if (ui.freqLoaded) {
                    AssistChip(onClick = vm::onClearFreq, label = { Text(tr("데이터 해제", "Forget the data")) })
                }
                if (ui.importing) Text(tr("불러오는 중…", "Loading…"), style = MaterialTheme.typography.labelMedium)
            }

            if (ui.freqLoaded) {
                // player search
                OutlinedTextField(value = ui.playerQuery, onValueChange = vm::onPlayerQueryChange,
                    label = { Text(tr("선수 검색 (이름/국가)", "Search a player (name or country)")) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                if (ui.playerSuggestions.isNotEmpty()) {
                    Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.small) {
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
                                trailingIcon = { Icon(Icons.Filled.Close, contentDescription = tr("제거", "Remove"),
                                    modifier = Modifier.size(16.dp)) })
                        }
                    }
                }

                // rule chips
                if (ui.ruleOptions.isNotEmpty()) {
                    Text(tr("룰", "Rule"), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ui.ruleOptions.forEach { (idx, name) ->
                            FilterChip(selected = idx in ui.filter.ruleIndices,
                                onClick = { vm.onToggleRule(idx) }, label = { Text(name) })
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = vm::onClearFilter) { Text(tr("필터 초기화", "Reset the filter")) }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = vm::onCloseFilter) { Text(tr("적용", "Apply")) }
                }
            }
        }
    }
}

