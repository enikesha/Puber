package com.kino.puber.ui.feature.device.settings.model

import com.kino.puber.core.model.NavigationMode
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.domain.interactor.device.DeviceSettingType

internal sealed class DeviceSettingsActions : UIAction {

    data object UnlinkDevice : DeviceSettingsActions()
    data class ChangeSettingValue(val setting: DeviceSettingUIModel.TypeValue) : DeviceSettingsActions()
    data class ToggleListExpand(val setting: DeviceSettingUIModel.TypeList) : DeviceSettingsActions()
    data class SelectOption(val type: DeviceSettingType, val optionId: Int) : DeviceSettingsActions()
    data object ToggleSkipIntro : DeviceSettingsActions()
    data object ToggleSkipRecap : DeviceSettingsActions()
    data object ToggleSkipCredits : DeviceSettingsActions()
    data object ToggleDebugOverlay : DeviceSettingsActions()
    data object ToggleSurroundAudio : DeviceSettingsActions()
    data object ToggleWatchedIndicators : DeviceSettingsActions()
    data object ToggleDiscardEmbeddedArtworkMetadata : DeviceSettingsActions()
    data object ToggleHagcPlayback : DeviceSettingsActions()
    data class ChangeNavigationMode(val mode: NavigationMode) : DeviceSettingsActions()
    data object ToggleCartoonsTab : DeviceSettingsActions()
    data object ToggleAnimeTab : DeviceSettingsActions()
    data object ToggleShowAnime : DeviceSettingsActions()
    data object ToggleAutoUpdateCheck : DeviceSettingsActions()
    data object OpenApiDomainDialog : DeviceSettingsActions()
    data object OpenSpeedTest : DeviceSettingsActions()
    data object CloseApiDomainDialog : DeviceSettingsActions()
    data class SaveApiDomain(val domain: String) : DeviceSettingsActions()
    data object DetectApiDomain : DeviceSettingsActions()
    data object ResetApiDomain : DeviceSettingsActions()
    data object OpenMyShowsDialog : DeviceSettingsActions()
    data object CloseMyShowsDialog : DeviceSettingsActions()
    data class ConnectMyShows(val token: String) : DeviceSettingsActions()
    data object ValidateMyShowsConnection : DeviceSettingsActions()
    data object DisconnectMyShows : DeviceSettingsActions()
    data object ToggleMyShowsSync : DeviceSettingsActions()
}
