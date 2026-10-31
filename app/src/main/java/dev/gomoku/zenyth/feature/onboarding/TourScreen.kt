package dev.gomoku.zenyth.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gomoku.zenyth.core.i18n.tr

/**
 * The tour, over the app. For now only its way out — the six pages come in O3
 * of the plan. An opaque [Surface] takes every touch, so nothing reaches the
 * board underneath.
 */
@Composable
fun TourScreen(onFinish: () -> Unit) {
    BackHandler(onBack = onFinish)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)) {
            TextButton(onClick = onFinish, modifier = Modifier.align(Alignment.TopEnd)) {
                Text(tr("건너뛰기", "Skip", "跳过"))
            }
        }
    }
}
