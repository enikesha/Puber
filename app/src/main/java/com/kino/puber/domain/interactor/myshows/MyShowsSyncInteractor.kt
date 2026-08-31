package com.kino.puber.domain.interactor.myshows

import com.kino.puber.data.api.MyShowsApiClient
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.MyShowsCheckResult
import com.kino.puber.data.api.models.MyShowsEpisode
import com.kino.puber.data.api.models.MyShowsIds
import com.kino.puber.data.api.models.MyShowsScrobbleRequest
import com.kino.puber.data.api.models.MyShowsShow
import com.kino.puber.data.api.models.isSeriesLike
import com.kino.puber.data.api.normalizedImdbTitleIdOrNull
import com.kino.puber.data.repository.ItemDetailsRepository
import com.kino.puber.data.repository.MyShowsPreferencesRepository
import com.kino.puber.data.repository.MyShowsPairingServer
import com.kino.puber.data.repository.MyShowsPairingSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

internal data class MyShowsSettings(
    val isConnected: Boolean,
    val isSyncEnabled: Boolean,
)

internal interface IMyShowsSyncInteractor {
    fun getSettings(): MyShowsSettings
    suspend fun connect(token: String): Result<MyShowsCheckResult>
    suspend fun validateConnection(): Result<MyShowsCheckResult>
    fun disconnect()
    fun setSyncEnabled(enabled: Boolean)
    fun startPairing(onTokenReceived: (String) -> Unit): Result<MyShowsPairingSession>
    fun reportPairingResult(isConnected: Boolean)
    fun stopPairing()
    fun enqueueEpisodeWatched(itemId: Int, season: Int, episode: Int)
    fun enqueueSeasonWatched(itemId: Int, season: Int)
}

internal class MyShowsSyncInteractor(
    private val apiClient: MyShowsApiClient,
    private val preferences: MyShowsPreferencesRepository,
    private val itemDetailsRepository: ItemDetailsRepository,
    private val pairingServer: MyShowsPairingServer,
    private val syncScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : IMyShowsSyncInteractor {

    override fun getSettings(): MyShowsSettings = MyShowsSettings(
        isConnected = preferences.isConnected,
        isSyncEnabled = preferences.isSyncEnabled,
    )

    override suspend fun connect(token: String): Result<MyShowsCheckResult> {
        val normalizedToken = token.trim()
        if (normalizedToken.isEmpty()) {
            return Result.failure(IllegalArgumentException("MyShows token is empty"))
        }
        return apiClient.checkToken(normalizedToken)
            .logConnectionCheck("connect")
            .onSuccess { preferences.connect(normalizedToken) }
    }

    override suspend fun validateConnection(): Result<MyShowsCheckResult> {
        val token = preferences.token
            ?: return Result.failure(IllegalStateException("MyShows is not connected"))
        return apiClient.checkToken(token).logConnectionCheck("validate")
    }

    override fun disconnect() = preferences.disconnect()

    override fun setSyncEnabled(enabled: Boolean) {
        preferences.isSyncEnabled = enabled
    }

    override fun startPairing(
        onTokenReceived: (String) -> Unit,
    ): Result<MyShowsPairingSession> = pairingServer.start(onTokenReceived)

    override fun reportPairingResult(isConnected: Boolean) = pairingServer.reportPairingResult(isConnected)

    override fun stopPairing() = pairingServer.stop()

    override fun enqueueEpisodeWatched(itemId: Int, season: Int, episode: Int) {
        if (!preferences.isSyncEnabled) return
        syncScope.launch {
            syncEpisodeWatched(itemId, season, episode).logFailure(
                "episode itemId=$itemId season=$season episode=$episode",
            )
        }
    }

    override fun enqueueSeasonWatched(itemId: Int, season: Int) {
        if (!preferences.isSyncEnabled) return
        syncScope.launch {
            syncSeasonWatched(itemId, season).logFailure(
                "season itemId=$itemId season=$season",
            )
        }
    }

    internal suspend fun syncEpisodeWatched(
        itemId: Int,
        season: Int,
        episode: Int,
    ): Result<Unit> = syncResult {
        val item = itemDetailsRepository.getItemDetails(itemId)
        if (!item.type.isSeriesLike()) return@syncResult
        val episodeModel = item.seasons
            .orEmpty()
            .firstOrNull { it.number == season }
            ?.episodes
            .orEmpty()
            .firstOrNull { it.number == episode }
            ?: return@syncResult
        sendEpisode(item, season, episodeModel.number, episodeModel.title)
    }

    internal suspend fun syncSeasonWatched(itemId: Int, season: Int): Result<Unit> = syncResult {
        val item = itemDetailsRepository.getItemDetails(itemId)
        if (!item.type.isSeriesLike()) return@syncResult
        val episodes = item.seasons
            .orEmpty()
            .firstOrNull { it.number == season }
            ?.episodes
            .orEmpty()
        for (episode in episodes) {
            sendEpisode(item, season, episode.number, episode.title)
        }
    }

    private suspend fun sendEpisode(
        item: Item,
        season: Int,
        episode: Int,
        episodeTitle: String?,
    ) {
        val token = preferences.token ?: return
        val result = apiClient.scrobbleEpisode(
            token = token,
            request = item.toScrobbleRequest(season, episode, episodeTitle),
        ).getOrThrow()
        Timber.i(
            "MyShows scrobble accepted: itemId=%d season=%d episode=%d " +
                "startAction=%s startHttp=%d startId=%s " +
                "stopAction=%s stopHttp=%d stopId=%s stopProgress=%s",
            item.id,
            season,
            episode,
            result.start.action ?: "unknown",
            result.start.httpStatus,
            result.start.id,
            result.stop.action ?: "unknown",
            result.stop.httpStatus,
            result.stop.id,
            result.stop.progress,
        )
    }

    private fun Item.toScrobbleRequest(
        season: Int,
        episode: Int,
        episodeTitle: String?,
    ) = MyShowsScrobbleRequest(
        progress = COMPLETED_PROGRESS,
        sourceApp = SOURCE_APP,
        show = MyShowsShow(
            title = title,
            year = year,
            ids = MyShowsIds(
                imdb = imdb?.normalizedImdbTitleIdOrNull(),
                kinopoisk = kinopoisk?.trim()?.toIntOrNull(),
            ),
        ),
        episode = MyShowsEpisode(
            season = season,
            number = episode,
            title = episodeTitle,
        ),
    )

    private suspend fun syncResult(block: suspend () -> Unit): Result<Unit> = try {
        block()
        Result.success(Unit)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

    private fun Result<Unit>.logFailure(operation: String) {
        exceptionOrNull()?.let { error ->
            Timber.w(error, "MyShows watched-state sync failed: %s", operation)
        }
    }

    private fun Result<MyShowsCheckResult>.logConnectionCheck(
        operation: String,
    ): Result<MyShowsCheckResult> = onSuccess { result ->
        Timber.i(
            "MyShows connection check accepted: operation=%s http=%d accountPresent=%s",
            operation,
            result.httpStatus,
            result.accountName != null,
        )
    }.onFailure { error ->
        Timber.w(error, "MyShows connection check failed: operation=%s", operation)
    }

    private companion object {
        const val SOURCE_APP = "puber"
        const val COMPLETED_PROGRESS = 100.0
    }
}
