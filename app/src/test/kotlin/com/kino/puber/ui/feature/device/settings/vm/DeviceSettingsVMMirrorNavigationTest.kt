package com.kino.puber.ui.feature.device.settings.vm

import com.kino.puber.core.model.NavigationMode
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.data.api.models.DeviceResponse
import com.kino.puber.data.api.models.DeviceResponseModel
import com.kino.puber.data.api.models.MyShowsCheckResult
import com.kino.puber.data.api.models.SettingsResponse
import com.kino.puber.data.preferences.ContentPreferences
import com.kino.puber.data.preferences.NavigationPreferencesRepository
import com.kino.puber.data.repository.PlayerPreferencesRepository
import com.kino.puber.data.repository.MyShowsPairingSession
import com.kino.puber.domain.interactor.api.ApiDomainDetectionResult
import com.kino.puber.domain.interactor.api.ApiDomainInteractor
import com.kino.puber.domain.interactor.api.ApiDomainState
import com.kino.puber.domain.interactor.api.ApiDomainUpdateResult
import com.kino.puber.domain.interactor.device.IDeviceInfoInteractor
import com.kino.puber.domain.interactor.device.IDeviceSettingInteractor
import com.kino.puber.domain.interactor.myshows.IMyShowsSyncInteractor
import com.kino.puber.domain.interactor.myshows.MyShowsSettings
import com.kino.puber.domain.interactor.update.IAppUpdateInteractor
import com.kino.puber.domain.model.BluetoothAudioDelay
import com.kino.puber.domain.model.TrackPreferenceScope
import com.kino.puber.ui.feature.device.settings.mappers.DeviceUiSettingsMapper
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsActions
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsListUi
import com.kino.puber.ui.feature.device.settings.model.DeviceSettingsState
import com.kino.puber.ui.feature.device.settings.model.DeviceUi
import com.kino.puber.ui.feature.device.speedtest.SpeedTestScreen
import com.kino.puber.util.FakeResourceProvider
import com.kino.puber.util.MainDispatcherExtension
import io.mockk.Called
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class DeviceSettingsVMMirrorNavigationTest {

    companion object {
        @JvmField
        @RegisterExtension
        val mainDispatcher = MainDispatcherExtension()
    }

    private val router = mockk<AppRouter>(relaxed = true)
    private val deviceSettingInteractor = mockk<IDeviceSettingInteractor>(relaxed = true)
    private val deviceInfoInteractor = mockk<IDeviceInfoInteractor>(relaxed = true)
    private val deviceUiSettingsMapper = mockk<DeviceUiSettingsMapper>(relaxed = true)
    private val playerPreferencesRepository = mockk<PlayerPreferencesRepository>(relaxed = true)
    private val navigationPreferencesRepository = mockk<NavigationPreferencesRepository>(relaxed = true)
    private val apiDomainInteractor = mockk<ApiDomainInteractor>(relaxed = true)
    private val appUpdateInteractor = mockk<IAppUpdateInteractor>(relaxed = true)
    private val myShowsSyncInteractor = mockk<IMyShowsSyncInteractor>(relaxed = true)
    private val errorHandler = mockk<ErrorHandler>(relaxed = true)

    @Test
    fun saveApiDomain_keepsDeviceSettingsOpen() {
        val state = ApiDomainState(
            domain = "api.custom.example",
            customDomain = "api.custom.example",
        )
        every { apiDomainInteractor.saveCustomDomain("api.custom.example") } returns
            ApiDomainUpdateResult.Success(state)
        val vm = createVM()

        vm.onAction(DeviceSettingsActions.OpenApiDomainDialog)
        assertTrue(vm.testStateValue.isApiDomainDialogOpen)
        clearMocks(router, answers = false)

        vm.onAction(DeviceSettingsActions.SaveApiDomain("api.custom.example"))

        assertEquals("api.custom.example", vm.testStateValue.apiDomain.currentDomain)
        assertEquals("api.custom.example", vm.testStateValue.apiDomain.customDomain)
        assertFalse(vm.testStateValue.isApiDomainDialogOpen)
        verify { router wasNot Called }
    }

    @Test
    fun resetApiDomain_keepsDeviceSettingsOpen() {
        val state = ApiDomainState(
            domain = "service-kp.com",
            customDomain = null,
        )
        every { apiDomainInteractor.resetToDefault() } returns state
        val vm = createVM()

        vm.onAction(DeviceSettingsActions.OpenApiDomainDialog)
        assertTrue(vm.testStateValue.isApiDomainDialogOpen)
        clearMocks(router, answers = false)

        vm.onAction(DeviceSettingsActions.ResetApiDomain)

        assertEquals("service-kp.com", vm.testStateValue.apiDomain.currentDomain)
        assertEquals(null, vm.testStateValue.apiDomain.customDomain)
        assertFalse(vm.testStateValue.isApiDomainDialogOpen)
        verify { router wasNot Called }
    }

    @Test
    fun detectApiDomain_keepsDeviceSettingsOpen() {
        val state = ApiDomainState(
            domain = "api.detected.example",
            customDomain = "api.detected.example",
        )
        coEvery { apiDomainInteractor.detectAndSaveWorkingDomain() } returns
            ApiDomainDetectionResult.Success(state)
        val vm = createVM()

        vm.onAction(DeviceSettingsActions.OpenApiDomainDialog)
        assertTrue(vm.testStateValue.isApiDomainDialogOpen)
        clearMocks(router, answers = false)

        vm.onAction(DeviceSettingsActions.DetectApiDomain)
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        assertEquals("api.detected.example", vm.testStateValue.apiDomain.currentDomain)
        assertEquals("api.detected.example", vm.testStateValue.apiDomain.customDomain)
        assertFalse(vm.testStateValue.isApiDomainDialogOpen)
        verify { router wasNot Called }
    }

    @Test
    fun media3PlaybackToggles_loadDefaultsAndPersistIndependently() {
        stubSuccessfulDeviceLoad()
        every { playerPreferencesRepository.discardEmbeddedArtworkMetadata } returns true
        every { playerPreferencesRepository.hagcPlaybackEnabled } returns false
        every { playerPreferencesRepository.bluetoothAudioDelay } returns BluetoothAudioDelay.OFF
        every { playerPreferencesRepository.bluetoothSyncControlsEnabled } returns false
        val vm = createVM()

        vm.testOnStart()
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        val loadedState = vm.testStateValue.state as DeviceSettingsState.Success
        assertTrue(loadedState.discardEmbeddedArtworkMetadata)
        assertFalse(loadedState.hagcPlaybackEnabled)

        vm.onAction(DeviceSettingsActions.ToggleDiscardEmbeddedArtworkMetadata)
        vm.onAction(DeviceSettingsActions.ToggleHagcPlayback)
        vm.onAction(DeviceSettingsActions.ToggleBluetoothSyncControls)

        val updatedState = vm.testStateValue.state as DeviceSettingsState.Success
        assertFalse(updatedState.discardEmbeddedArtworkMetadata)
        assertTrue(updatedState.hagcPlaybackEnabled)
        assertTrue(updatedState.bluetoothSyncControlsEnabled)
        assertEquals(BluetoothAudioDelay.OFF, updatedState.bluetoothAudioDelay)
        verify { playerPreferencesRepository.discardEmbeddedArtworkMetadata = false }
        verify { playerPreferencesRepository.hagcPlaybackEnabled = true }
        verify { playerPreferencesRepository.bluetoothSyncControlsEnabled = true }
    }

    @Test
    fun openSpeedTest_navigatesToSpeedTest() {
        val vm = createVM()

        vm.onAction(DeviceSettingsActions.OpenSpeedTest)

        verify(exactly = 1) {
            router.navigateTo(match { it is SpeedTestScreen })
        }
    }

    @Test
    fun trackPreferenceScope_loadsTheStoredScopeAndPersistsANewOne() {
        stubSuccessfulDeviceLoad()
        every { playerPreferencesRepository.trackPreferenceScope } returns TrackPreferenceScope.PER_VIDEO
        val vm = createVM()

        vm.testOnStart()
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        val loadedState = vm.testStateValue.state as DeviceSettingsState.Success
        assertEquals(TrackPreferenceScope.PER_VIDEO, loadedState.trackPreferenceScope)

        vm.onAction(DeviceSettingsActions.ChangeTrackPreferenceScope(TrackPreferenceScope.GLOBAL))

        val updatedState = vm.testStateValue.state as DeviceSettingsState.Success
        assertEquals(TrackPreferenceScope.GLOBAL, updatedState.trackPreferenceScope)
        verify { playerPreferencesRepository.trackPreferenceScope = TrackPreferenceScope.GLOBAL }
    }

    @Test
    fun trackPreferenceScope_ignoresTheScopeThatIsAlreadyActive() {
        stubSuccessfulDeviceLoad()
        every { playerPreferencesRepository.trackPreferenceScope } returns TrackPreferenceScope.PER_VIDEO
        val vm = createVM()
        vm.testOnStart()
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()
        clearMocks(playerPreferencesRepository, answers = false)

        vm.onAction(DeviceSettingsActions.ChangeTrackPreferenceScope(TrackPreferenceScope.PER_VIDEO))

        verify(exactly = 0) { playerPreferencesRepository.trackPreferenceScope = any() }
    }

    @Test
    fun connectMyShows_validatesTokenAndEnablesSync() {
        stubSuccessfulDeviceLoad()
        val vm = createVM()
        every { myShowsSyncInteractor.getSettings() } returnsMany listOf(
            MyShowsSettings(isConnected = false, isSyncEnabled = false),
            MyShowsSettings(isConnected = true, isSyncEnabled = true),
        )
        coEvery { myShowsSyncInteractor.connect(" token ") } returns Result.success(
            MyShowsCheckResult(httpStatus = 200, accountName = "test-user"),
        )
        vm.testOnStart()
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        vm.onAction(DeviceSettingsActions.OpenMyShowsDialog)
        vm.onAction(DeviceSettingsActions.ConnectMyShows(" token "))
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        val state = vm.testStateValue.state as DeviceSettingsState.Success
        assertTrue(state.isMyShowsConnected)
        assertTrue(state.isMyShowsSyncEnabled)
        assertTrue(state.myShowsApiStatusText?.contains("test-user") == true)
        assertFalse(vm.testStateValue.isMyShowsDialogOpen)
        io.mockk.coVerify { myShowsSyncInteractor.connect(" token ") }
    }

    @Test
    fun connectedMyShows_loadsApiResponseIntoSettings() {
        stubSuccessfulDeviceLoad()
        val vm = createVM()
        val apiResult = CompletableDeferred<MyShowsCheckResult>()
        every { myShowsSyncInteractor.getSettings() } returns
            MyShowsSettings(isConnected = true, isSyncEnabled = true)
        coEvery { myShowsSyncInteractor.validateConnection() } coAnswers {
            Result.success(apiResult.await())
        }

        vm.testOnStart()
        mainDispatcher.dispatcher.scheduler.runCurrent()

        val checkingState = vm.testStateValue.state as DeviceSettingsState.Success
        assertTrue(checkingState.isMyShowsConnected)
        assertTrue(checkingState.myShowsApiStatusText != null)

        apiResult.complete(MyShowsCheckResult(httpStatus = 200, accountName = "test-user"))
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        val state = vm.testStateValue.state as DeviceSettingsState.Success
        assertTrue(state.myShowsApiStatusText?.contains("test-user") == true)
        io.mockk.coVerify(exactly = 1) { myShowsSyncInteractor.validateConnection() }
    }

    @Test
    fun toggleMyShowsSync_isIgnoredUntilConnected() {
        stubSuccessfulDeviceLoad()
        every { myShowsSyncInteractor.getSettings() } returns
            MyShowsSettings(isConnected = false, isSyncEnabled = false)
        val vm = createVM()
        vm.testOnStart()
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        vm.onAction(DeviceSettingsActions.ToggleMyShowsSync)

        verify(exactly = 0) { myShowsSyncInteractor.setSyncEnabled(any()) }
    }

    @Test
    fun phonePairing_receivedTokenIsValidatedAndClosesDialog() {
        stubSuccessfulDeviceLoad()
        val tokenReceiver = slot<(String) -> Unit>()
        val vm = createVM()
        every { myShowsSyncInteractor.getSettings() } returnsMany listOf(
            MyShowsSettings(isConnected = false, isSyncEnabled = false),
            MyShowsSettings(isConnected = true, isSyncEnabled = true),
        )
        every { myShowsSyncInteractor.startPairing(capture(tokenReceiver)) } returns Result.success(
            MyShowsPairingSession("http://192.168.1.2:1234/pair/test/")
        )
        coEvery { myShowsSyncInteractor.connect("phone-token") } returns Result.success(
            MyShowsCheckResult(httpStatus = 200),
        )
        vm.testOnStart()
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        vm.onAction(DeviceSettingsActions.OpenMyShowsDialog)
        tokenReceiver.captured("phone-token")
        mainDispatcher.dispatcher.scheduler.advanceUntilIdle()

        val state = vm.testStateValue.state as DeviceSettingsState.Success
        assertTrue(state.isMyShowsConnected)
        assertFalse(vm.testStateValue.isMyShowsDialogOpen)
        verify { myShowsSyncInteractor.reportPairingResult(isConnected = true) }
    }

    private fun stubSuccessfulDeviceLoad() {
        val settings = mockk<SettingsResponse>()
        val device = DeviceResponseModel(
            id = 1,
            title = "TV",
            hardware = "Emulator",
            software = "Android",
            created = 0,
            updated = 0,
            lastSeen = 0,
            isBrowser = false,
            settings = settings,
        )
        every {
            deviceSettingInteractor.getCurrentDeviceSettings()
        } returns flowOf(Result.success(DeviceResponse(status = 200, device = device)))
        every {
            deviceUiSettingsMapper.mapSettings(settings, any())
        } returns DeviceSettingsListUi(emptyList())
        every { deviceUiSettingsMapper.mapDevice(device) } returns DeviceUi(
            title = device.title,
            hardware = device.hardware,
            software = device.software,
        )
        every {
            navigationPreferencesRepository.contentPreferences
        } returns MutableStateFlow(
            ContentPreferences(
                showCartoonsTab = false,
                showAnimeTab = false,
                showAnime = true,
            )
        )
        every { navigationPreferencesRepository.getNavigationMode() } returns NavigationMode.TopTabs
    }

    private fun createVM(): DeviceSettingsVM {
        every { apiDomainInteractor.getState() } returns ApiDomainState(
            domain = "service-kp.com",
            customDomain = null,
        )
        every { myShowsSyncInteractor.getSettings() } returns
            MyShowsSettings(isConnected = false, isSyncEnabled = false)
        every { myShowsSyncInteractor.startPairing(any()) } returns Result.success(
            MyShowsPairingSession("http://192.168.1.2:1234/pair/test/")
        )
        return DeviceSettingsVM(
            deviceSettingInteractor = deviceSettingInteractor,
            deviceInfoInteractor = deviceInfoInteractor,
            deviceUiSettingsMapper = deviceUiSettingsMapper,
            playerPreferencesRepository = playerPreferencesRepository,
            navigationPreferencesRepository = navigationPreferencesRepository,
            apiDomainInteractor = apiDomainInteractor,
            appUpdateInteractor = appUpdateInteractor,
            myShowsSyncInteractor = myShowsSyncInteractor,
            errorHandler = errorHandler,
            resources = FakeResourceProvider(),
            router = router,
        )
    }
}
