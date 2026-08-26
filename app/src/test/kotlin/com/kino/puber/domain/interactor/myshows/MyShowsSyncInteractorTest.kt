package com.kino.puber.domain.interactor.myshows

import com.kino.puber.data.api.MyShowsApiClient
import com.kino.puber.data.api.models.Episode
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.ItemType
import com.kino.puber.data.api.models.MyShowsCheckResult
import com.kino.puber.data.api.models.MyShowsScrobbleRequest
import com.kino.puber.data.api.models.MyShowsScrobbleResponse
import com.kino.puber.data.api.models.MyShowsScrobbleResult
import com.kino.puber.data.api.models.Season
import com.kino.puber.data.repository.ItemDetailsRepository
import com.kino.puber.data.repository.MyShowsPreferencesRepository
import com.kino.puber.data.repository.MyShowsPairingServer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class MyShowsSyncInteractorTest {

    private val apiClient = mockk<MyShowsApiClient>()
    private val preferences = mockk<MyShowsPreferencesRepository>(relaxed = true)
    private val itemDetailsRepository = mockk<ItemDetailsRepository>()
    private val pairingServer = mockk<MyShowsPairingServer>(relaxed = true)

    @Test
    fun connect_savesTokenAndEnablesSyncOnlyAfterValidation() = runTest {
        coEvery { apiClient.checkToken("valid-token") } returns Result.success(checkResult())
        val interactor = interactor(this)

        val result = interactor.connect("  valid-token  ")

        assertTrue(result.isSuccess)
        verify(exactly = 1) { preferences.connect("valid-token") }
    }

    @Test
    fun connect_doesNotSaveTokenWhenValidationFails() = runTest {
        coEvery { apiClient.checkToken("invalid-token") } returns
            Result.failure(IllegalStateException("Unauthorized"))
        val interactor = interactor(this)

        val result = interactor.connect("invalid-token")

        assertTrue(result.isFailure)
        verify(exactly = 0) { preferences.connect(any()) }
    }

    @Test
    fun syncEpisodeWatched_normalizesIdsAndSendsExactEpisode() = runTest {
        every { preferences.token } returns "secret-token"
        coEvery { itemDetailsRepository.getItemDetails(42) } returns series()
        val requests = mutableListOf<MyShowsScrobbleRequest>()
        coEvery { apiClient.scrobbleEpisode("secret-token", capture(requests)) } returns
            Result.success(scrobbleResult())

        val result = interactor(this).syncEpisodeWatched(42, season = 1, episode = 2)

        assertTrue(result.isSuccess)
        val request = requests.single()
        assertEquals(100.0, request.progress)
        assertEquals("puber", request.sourceApp)
        assertEquals("tt0944947", request.show.ids.imdb)
        assertEquals(464963, request.show.ids.kinopoisk)
        assertEquals(1, request.episode.season)
        assertEquals(2, request.episode.number)
        assertEquals("Second", request.episode.title)
    }

    @Test
    fun syncSeasonWatched_sendsEveryEpisodeInOrder() = runTest {
        every { preferences.token } returns "secret-token"
        coEvery { itemDetailsRepository.getItemDetails(42) } returns series()
        val requests = mutableListOf<MyShowsScrobbleRequest>()
        coEvery { apiClient.scrobbleEpisode("secret-token", capture(requests)) } returns
            Result.success(scrobbleResult())

        val result = interactor(this).syncSeasonWatched(42, season = 1)

        assertTrue(result.isSuccess)
        assertEquals(listOf(1, 2), requests.map { it.episode.number })
        coVerify(exactly = 2) { apiClient.scrobbleEpisode("secret-token", any()) }
    }

    @Test
    fun syncEpisodeWatched_ignoresMovies() = runTest {
        every { preferences.token } returns "secret-token"
        coEvery { itemDetailsRepository.getItemDetails(7) } returns
            Item(id = 7, title = "Movie", type = ItemType.MOVIE)

        val result = interactor(this).syncEpisodeWatched(7, season = 1, episode = 1)

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { apiClient.scrobbleEpisode(any(), any()) }
    }

    @Test
    fun enqueueEpisodeWatched_doesNothingWhenSyncIsDisabled() = runTest {
        every { preferences.isSyncEnabled } returns false
        val interactor = interactor(this)

        interactor.enqueueEpisodeWatched(42, season = 1, episode = 2)
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 0) { itemDetailsRepository.getItemDetails(any()) }
    }

    private fun interactor(scope: TestScope) = MyShowsSyncInteractor(
        apiClient = apiClient,
        preferences = preferences,
        itemDetailsRepository = itemDetailsRepository,
        pairingServer = pairingServer,
        syncScope = scope,
    )

    private fun checkResult() = MyShowsCheckResult(httpStatus = 200, accountName = "test-user")

    private fun scrobbleResult() = MyShowsScrobbleResult(
        start = MyShowsScrobbleResponse(httpStatus = 200, action = "start"),
        stop = MyShowsScrobbleResponse(httpStatus = 200, action = "scrobble"),
    )

    private fun series() = Item(
        id = 42,
        title = "Game of Thrones",
        type = ItemType.SERIAL,
        year = 2011,
        imdb = "0944947",
        kinopoisk = "464963",
        seasons = listOf(
            Season(
                id = 10,
                number = 1,
                episodes = listOf(
                    Episode(id = 101, number = 1, title = "Pilot"),
                    Episode(id = 102, number = 2, title = "Second"),
                ),
            ),
        ),
    )
}
