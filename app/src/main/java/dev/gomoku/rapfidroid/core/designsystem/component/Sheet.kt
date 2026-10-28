package dev.gomoku.rapfidroid.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.gomoku.rapfidroid.core.designsystem.theme.RapfiTheme

/**
 * A sheet that asks for something — the app's one shape for typing a value in
 * (UI overhaul P4).
 *
 * The board's input dialogs (the position string, a point's label, a position
 * comment, the balance bias) sat in the middle of the screen, where the keyboard
 * pushed them about and covered half of what was being edited. A sheet rises
 * from the bottom with the keyboard under it: Material's modal sheet keeps the
 * keyboard and the system bars out of the content by itself, so the field is
 * never behind the keys.
 *
 * Laid out like the dialog it replaces — title, [content], then the buttons on
 * the right, [dismiss] before [confirm] — so moving one across is mechanical.
 * Questions that decide something (swap, resign, a new board over a game) stay
 * dialogs: those should stop the screen, not slide over it.
 *
 * The sheet opens fully expanded (nothing here is worth a half state) and its
 * body scrolls when the keyboard leaves it too little room.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RapfiSheet(
    title: String,
    onDismiss: () -> Unit,
    confirm: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    dismiss: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = RapfiTheme.tokens.radiusSheet, topEnd = RapfiTheme.tokens.radiusSheet),
        containerColor = cardColor(),
        dragHandle = { SheetGrip() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState(), flingBehavior = rememberCalmFling())
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                dismiss?.invoke(this)
                confirm()
            }
        }
    }
}

/** The same 4dp grip as the board's sheet, with room above and below. */
@Composable
private fun SheetGrip() {
    Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(width = 32.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        )
    }
}
