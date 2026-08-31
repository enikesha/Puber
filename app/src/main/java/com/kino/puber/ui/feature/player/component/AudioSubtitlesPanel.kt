package com.kino.puber.ui.feature.player.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.kino.puber.R
import com.kino.puber.domain.model.BluetoothAudioDelay
import com.kino.puber.ui.feature.player.model.AudioTrackUIState
import com.kino.puber.ui.feature.player.model.SoundModeUIState
import com.kino.puber.ui.feature.player.model.SubtitleTrackUIState

@Composable
internal fun AudioSubtitlesPanel(
    visible: Boolean,
    isFocusOwner: Boolean,
    soundModes: List<SoundModeUIState>,
    selectedSoundModeIndex: Int,
    audioTracks: List<AudioTrackUIState>,
    selectedAudioTrackIndex: Int,
    subtitleTracks: List<SubtitleTrackUIState>,
    selectedSubtitleIndex: Int,
    bluetoothAudioDelay: BluetoothAudioDelay,
    showBluetoothSyncControls: Boolean,
    onSoundModeSelected: (Int) -> Unit,
    onAudioTrackSelected: (Int) -> Unit,
    onSubtitleSelected: (Int) -> Unit,
    onSubtitleSizeClick: () -> Unit,
    onBluetoothSyncClick: () -> Unit,
    onBackPressed: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxSize(),
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
    ) {
        val initialFocusTarget = when {
            soundModes.isNotEmpty() -> AudioSubtitlesFocusTarget.SoundMode
            audioTracks.isNotEmpty() -> AudioSubtitlesFocusTarget.AudioTrack
            subtitleTracks.isNotEmpty() -> AudioSubtitlesFocusTarget.Subtitle
            else -> AudioSubtitlesFocusTarget.SubtitleSize
        }
        val panelFocusRequester = rememberRequestingFocusRequester(
            focusKey = initialFocusTarget,
            isFocusOwner = isFocusOwner,
        )

        AudioSubtitlesPanelContainer {
            AudioSubtitlesColumns(
                soundModes = soundModes,
                selectedSoundModeIndex = selectedSoundModeIndex,
                audioTracks = audioTracks,
                selectedAudioTrackIndex = selectedAudioTrackIndex,
                subtitleTracks = subtitleTracks,
                selectedSubtitleIndex = selectedSubtitleIndex,
                panelFocusRequester = panelFocusRequester,
                initialFocusTarget = initialFocusTarget,
                onSoundModeSelected = onSoundModeSelected,
                onAudioTrackSelected = onAudioTrackSelected,
                onSubtitleSelected = onSubtitleSelected,
                onSubtitleSizeClick = onSubtitleSizeClick,
                bluetoothAudioDelay = bluetoothAudioDelay,
                showBluetoothSyncControls = showBluetoothSyncControls,
                onBluetoothSyncClick = onBluetoothSyncClick,
            )
        }
    }
}

private enum class AudioSubtitlesFocusTarget {
    SoundMode,
    AudioTrack,
    Subtitle,
    SubtitleSize,
}

@Composable
private fun CompactPanelAction(
    label: String,
    value: String? = null,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier,
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.small),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            if (value != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AudioSubtitlesPanelContainer(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .padding(horizontal = 48.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
        ) {
            content()
        }
    }
}

@Composable
private fun AudioSubtitlesColumns(
    soundModes: List<SoundModeUIState>,
    selectedSoundModeIndex: Int,
    audioTracks: List<AudioTrackUIState>,
    selectedAudioTrackIndex: Int,
    subtitleTracks: List<SubtitleTrackUIState>,
    selectedSubtitleIndex: Int,
    panelFocusRequester: FocusRequester,
    initialFocusTarget: AudioSubtitlesFocusTarget,
    onSoundModeSelected: (Int) -> Unit,
    onAudioTrackSelected: (Int) -> Unit,
    onSubtitleSelected: (Int) -> Unit,
    onSubtitleSizeClick: () -> Unit,
    bluetoothAudioDelay: BluetoothAudioDelay,
    showBluetoothSyncControls: Boolean,
    onBluetoothSyncClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 24.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        SoundModeColumn(
            soundModes = soundModes,
            selectedSoundModeIndex = selectedSoundModeIndex,
            panelFocusRequester = panelFocusRequester.takeIf {
                initialFocusTarget == AudioSubtitlesFocusTarget.SoundMode
            },
            bluetoothAudioDelay = bluetoothAudioDelay,
            showBluetoothSyncControls = showBluetoothSyncControls,
            onSoundModeSelected = onSoundModeSelected,
            onBluetoothSyncClick = onBluetoothSyncClick,
        )
        AudioTrackColumn(
            audioTracks,
            selectedAudioTrackIndex,
            panelFocusRequester.takeIf { initialFocusTarget == AudioSubtitlesFocusTarget.AudioTrack },
            onAudioTrackSelected,
        )
        SubtitleColumn(
            subtitleTracks = subtitleTracks,
            selectedSubtitleIndex = selectedSubtitleIndex,
            panelFocusRequester = panelFocusRequester.takeIf {
                initialFocusTarget == AudioSubtitlesFocusTarget.Subtitle
            },
            subtitleSizeFocusRequester = panelFocusRequester.takeIf {
                initialFocusTarget == AudioSubtitlesFocusTarget.SubtitleSize
            },
            onSubtitleSelected = onSubtitleSelected,
            onSubtitleSizeClick = onSubtitleSizeClick,
        )
    }
}

@Composable
private fun RowScope.SoundModeColumn(
    soundModes: List<SoundModeUIState>,
    selectedSoundModeIndex: Int,
    panelFocusRequester: FocusRequester?,
    bluetoothAudioDelay: BluetoothAudioDelay,
    showBluetoothSyncControls: Boolean,
    onSoundModeSelected: (Int) -> Unit,
    onBluetoothSyncClick: () -> Unit,
) {
    if (soundModes.isEmpty()) return
    val labels = remember(soundModes) { soundModes.map { it.label } }
    SettingsPanelColumn(
        header = stringResource(R.string.player_panel_sound),
        items = labels,
        selectedIndex = selectedSoundModeIndex,
        onItemSelected = onSoundModeSelected,
        modifier = Modifier.weight(SOUND_COLUMN_WEIGHT),
        firstItemFocusRequester = panelFocusRequester,
        footer = if (showBluetoothSyncControls) {
            {
                CompactPanelAction(
                    label = stringResource(R.string.player_sync_change_offset),
                    value = if (bluetoothAudioDelay == BluetoothAudioDelay.OFF) {
                        stringResource(R.string.player_sync_value_zero)
                    } else {
                        stringResource(
                            R.string.player_sync_value,
                            bluetoothAudioDelay.milliseconds,
                        )
                    },
                    onClick = onBluetoothSyncClick,
                )
            }
        } else {
            null
        },
    )
}

@Composable
private fun RowScope.AudioTrackColumn(
    audioTracks: List<AudioTrackUIState>,
    selectedAudioTrackIndex: Int,
    panelFocusRequester: FocusRequester?,
    onAudioTrackSelected: (Int) -> Unit,
) {
    if (audioTracks.isEmpty()) return
    val labels = remember(audioTracks) { audioTracks.map { it.label } }
    SettingsPanelColumn(
        header = stringResource(R.string.player_panel_audio),
        items = labels,
        selectedIndex = selectedAudioTrackIndex,
        onItemSelected = onAudioTrackSelected,
        modifier = Modifier.weight(AUDIO_COLUMN_WEIGHT),
        firstItemFocusRequester = panelFocusRequester,
    )
}

@Composable
private fun RowScope.SubtitleColumn(
    subtitleTracks: List<SubtitleTrackUIState>,
    selectedSubtitleIndex: Int,
    panelFocusRequester: FocusRequester?,
    subtitleSizeFocusRequester: FocusRequester?,
    onSubtitleSelected: (Int) -> Unit,
    onSubtitleSizeClick: () -> Unit,
) {
    val labels = remember(subtitleTracks) { subtitleTracks.map { it.label } }
    SettingsPanelColumn(
        header = stringResource(R.string.player_panel_subtitles),
        items = labels,
        selectedIndex = selectedSubtitleIndex,
        onItemSelected = onSubtitleSelected,
        modifier = Modifier.weight(SUBTITLE_COLUMN_WEIGHT),
        firstItemFocusRequester = panelFocusRequester,
        footer = {
            CompactPanelAction(
                label = stringResource(R.string.player_subtitle_size),
                focusRequester = subtitleSizeFocusRequester,
                onClick = onSubtitleSizeClick,
            )
        },
    )
}

private const val SOUND_COLUMN_WEIGHT = 1f
private const val AUDIO_COLUMN_WEIGHT = 1.5f
private const val SUBTITLE_COLUMN_WEIGHT = 1f
