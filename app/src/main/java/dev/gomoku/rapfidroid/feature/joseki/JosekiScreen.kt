package dev.gomoku.rapfidroid.feature.joseki

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.gomoku.rapfidroid.core.designsystem.component.RapfiTopBar
import dev.gomoku.rapfidroid.core.i18n.tr
import dev.gomoku.rapfidroid.ui.PlaceholderScreen

/** 정석 — the official curriculum and the user's own joseki (RapfiWeb W9). */
@Composable
fun JosekiScreen(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        RapfiTopBar(title = tr("정석", "Joseki"))
        PlaceholderScreen(tr("정석", "Joseki"), tr("준비 중입니다", "Coming soon"))
    }
}
