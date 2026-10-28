package dev.gomoku.zenyth.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.gomoku.zenyth.core.i18n.tr
import dev.gomoku.zenyth.core.model.OpeningEval

/**
 * The 흑 5수 유불리 grade as the evaluation table draws it: a coloured shape,
 * and its name when [withName]. Not the engine's colour ladder — this is a
 * value a person wrote down, and it must not look like one the engine computed.
 */
@Composable
fun GradeMark(
    grade: OpeningEval.Grade,
    modifier: Modifier = Modifier,
    size: Dp = 14.dp,
    withName: Boolean = true,
) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Canvas(Modifier.size(size)) {
            val side = this.size.minDimension
            drawGradeMark(grade, Offset(side / 2f, side / 2f), side * 0.46f)
        }
        if (withName) {
            Text(tr(grade.ko, grade.en, grade.zh), style = MaterialTheme.typography.labelMedium)
        }
    }
}
