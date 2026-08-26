package com.kino.puber.data.api

import com.kino.puber.data.api.models.MyShowsEpisode
import com.kino.puber.data.api.models.MyShowsIds
import com.kino.puber.data.api.models.MyShowsScrobbleRequest
import com.kino.puber.data.api.models.MyShowsShow
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class MyShowsApiClientTest {

    @Test
    fun checkToken_usesBearerTokenAndCheckEndpoint() = runTest {
        val api = MyShowsApiClient.forTesting(client { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/scrobble/check", request.url.encodedPath)
            assertEquals("Bearer secret-token", request.headers[HttpHeaders.Authorization])
            respondJson("""{"status":"ok","user":{"login":"test-user"}}""")
        })

        val result = api.checkToken("secret-token").getOrThrow()

        assertEquals(200, result.httpStatus)
        assertEquals("test-user", result.accountName)
        assertEquals("ok", result.responseMessage)
    }

    @Test
    fun scrobbleEpisode_sendsStartThenStopWithExpectedPayload() = runTest {
        val paths = mutableListOf<String>()
        val api = MyShowsApiClient.forTesting(client { request ->
            paths += request.url.encodedPath
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("Bearer secret-token", request.headers[HttpHeaders.Authorization])
            val body = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
            assertEquals("puber", body.getValue("source_app").jsonPrimitive.content)
            assertEquals("100.0", body.getValue("progress").jsonPrimitive.content)
            val show = body.getValue("show").jsonObject
            assertEquals("Game of Thrones", show.getValue("title").jsonPrimitive.content)
            val episode = body.getValue("episode").jsonObject
            assertEquals("3", episode.getValue("season").jsonPrimitive.content)
            assertEquals("9", episode.getValue("number").jsonPrimitive.content)
            respondJson("{}")
        })

        val result = api.scrobbleEpisode("secret-token", request())

        assertTrue(result.isSuccess)
        assertEquals(200, result.getOrThrow().start.httpStatus)
        assertEquals(200, result.getOrThrow().stop.httpStatus)
        assertEquals(listOf("/scrobble/start", "/scrobble/stop"), paths)
    }

    @Test
    fun scrobbleEpisode_retriesStopOnceAfterTransientFailure() = runTest {
        var requestNumber = 0
        val api = MyShowsApiClient.forTesting(client {
            requestNumber += 1
            when (requestNumber) {
                2 -> respondJson("{}", HttpStatusCode.BadGateway)
                else -> respondJson("{}")
            }
        })

        assertTrue(api.scrobbleEpisode("secret-token", request()).isSuccess)
        assertEquals(3, requestNumber)
    }

    @Test
    fun scrobbleEpisode_doesNotRetryAuthenticationFailure() = runTest {
        var requestNumber = 0
        val api = MyShowsApiClient.forTesting(client {
            requestNumber += 1
            if (requestNumber == 2) {
                respondJson("{}", HttpStatusCode.Unauthorized)
            } else {
                respondJson("{}")
            }
        })

        val result = api.scrobbleEpisode("secret-token", request())

        assertTrue(result.isFailure)
        assertEquals(2, requestNumber)
        assertEquals(401, (result.exceptionOrNull() as MyShowsApiException).statusCode)
    }

    private fun request() = MyShowsScrobbleRequest(
        progress = 100.0,
        sourceApp = "puber",
        show = MyShowsShow(
            title = "Game of Thrones",
            year = 2011,
            ids = MyShowsIds(imdb = "tt0944947", kinopoisk = 464963),
        ),
        episode = MyShowsEpisode(season = 3, number = 9, title = "The Rains of Castamere"),
    )

    private fun client(handler: io.ktor.client.engine.mock.MockRequestHandler): HttpClient {
        return HttpClient(MockEngine(handler)) {
            install(ContentNegotiation) {
                json(Json { explicitNulls = false })
            }
        }
    }

    private fun MockRequestHandleScope.respondJson(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(
        content = body,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )
}
