package com.kino.puber.ui.feature.player.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kino.puber.R
import com.kino.puber.domain.model.BluetoothAudioDelay

@Composable
internal fun BluetoothSyncPanel(
    visible: Boolean,
    isFocusOwner: Boolean,
    delay: BluetoothAudioDelay,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxSize(),
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            BluetoothSyncBar(
                isFocusOwner = isFocusOwner,
                delay = delay,
                onDecrease = onDecrease,
                onIncrease = onIncrease,
                onReset = onReset,
                onSave = onSave,
            )
        }
    }
}

@Composable
private fun BluetoothSyncBar(
    isFocusOwner: Boolean,
    delay: BluetoothAudioDelay,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
) {
    val focusRequester = rememberRequestingFocusRequester(
        focusKey = Unit,
        isFocusOwner = isFocusOwner,
    )
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusBorder = if (isFocused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth(PANEL_WIDTH_FRACTION)
            .height(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            .border(
                width = 2.dp,
                color = focusBorder,
                shape = RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> onDecrease()
                    Key.DirectionRight -> onIncrease()
                    Key.DirectionUp -> onReset()
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> onSave()
                    else -> return@onPreviewKeyEvent false
                }
                true
            }
            .focusable(interactionSource = interactionSource),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BluetoothSyncLabel(modifier = Modifier.weight(LABEL_WEIGHT))
        BluetoothOffsetTrack(
            delay = delay,
            modifier = Modifier.weight(1f),
        )
        BluetoothSyncValue(delay = delay)
    }
}

@Composable
private fun BluetoothSyncLabel(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.player_sync_offset_title),
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = stringResource(R.string.player_sync_adjust_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun BluetoothSyncValue(delay: BluetoothAudioDelay) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = if (delay == BluetoothAudioDelay.OFF) {
                stringResource(R.string.player_sync_value_zero)
            } else {
                stringResource(R.string.player_sync_value, delay.milliseconds)
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.player_sync_reset_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BluetoothOffsetTrack(
    delay: BluetoothAudioDelay,
    modifier: Modifier = Modifier,
) {
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f)
    val centerColor = MaterialTheme.colorScheme.onSurfaceVariant
    val thumbColor = MaterialTheme.colorScheme.primary
    val fraction = (delay.milliseconds + BluetoothAudioDelay.MAX_DELAY_MS).toFloat() /
        (BluetoothAudioDelay.MAX_DELAY_MS * 2)

    Canvas(
        modifier = modifier
            .height(20.dp),
    ) {
        val centerY = size.height / 2f
        val trackStart = 8.dp.toPx()
        val trackEnd = size.width - trackStart
        drawLine(
            color = trackColor,
            start = Offset(trackStart, centerY),
            end = Offset(trackEnd, centerY),
            strokeWidth = 6.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = centerColor,
            start = Offset(size.width / 2f, 2.dp.toPx()),
            end = Offset(size.width / 2f, size.height - 2.dp.toPx()),
            strokeWidth = 2.dp.toPx(),
        )
        drawCircle(
            color = thumbColor,
            radius = 8.dp.toPx(),
            center = Offset(trackStart + (trackEnd - trackStart) * fraction, centerY),
        )
    }
}

private const val PANEL_WIDTH_FRACTION = 0.9f
private const val LABEL_WEIGHT = 0.55f
