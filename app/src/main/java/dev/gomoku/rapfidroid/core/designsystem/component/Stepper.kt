package dev.gomoku.rapfidroid.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.gomoku.rapfidroid.core.designsystem.theme.Spacing

/**
 * A labelled −/+ control for a bounded whole number.
 *
 * The prove and review screens each kept a private copy of this, identical down
 * to the typography and to the minus sign (U+2212, not a hyphen) and differing
 * in exactly one expression: how far one press moves the value. That expression
 * is [step] now, so the two screens share the control and keep their own
 * arithmetic — review counts plies and seconds one at a time, while prove runs
 * budgets into the hundreds of seconds and grows the step to match.
 *
 * The buttons disable at the bounds rather than clamping in silence, so a value
 * that will not move shows why. Callers still sanitise what they store; the
 * bounds here are what the reader is told, not the last word on the value.
 *
 * The database screen's stepper is deliberately not this one: it sits inline
 * between two words of a sentence ("저장 간격 [−|5|+] 분") and is built from
 * `TextButton`s to stay that small.
 */
@Composable
fun Stepper(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    step: (Int) -> Int = { 1 },
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        OutlinedButton(
            onClick = { onChange(value - step(value)) },
            enabled = enabled && value > min,
        ) { Text("−") }
        Text("$value", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(
            onClick = { onChange(value + step(value)) },
            enabled = enabled && value < max,
        ) { Text("+") }
    }
}
