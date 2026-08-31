package com.kino.puber.data.api

import com.kino.puber.data.api.models.MyShowsCheckResult
import com.kino.puber.data.api.models.MyShowsScrobbleRequest
import com.kino.puber.data.api.models.MyShowsScrobbleResponse
import com.kino.puber.data.api.models.MyShowsScrobbleResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

class MyShowsApiClient private constructor(
    private val httpClient: HttpClient,
) {

    constructor() : this(createHttpClient())

    suspend fun checkToken(token: String): Result<MyShowsCheckResult> = apiResult {
        val response = httpClient.get("$BASE_URL/check") {
            bearerAuth(token)
        }
        response.requireSuccess()
        response.toCheckResult()
    }

    suspend fun scrobbleEpisode(
        token: String,
        request: MyShowsScrobbleRequest,
    ): Result<MyShowsScrobbleResult> = apiResult {
        val start = postScrobble("start", token, request).toScrobbleResponse()
        val stop = try {
            postScrobble("stop", token, request).toScrobbleResponse()
        } catch (error: MyShowsApiException) {
            if (error.statusCode == UNAUTHORIZED || error.statusCode == FORBIDDEN) {
                throw error
            }
            postScrobble("stop", token, request).toScrobbleResponse()
        }
        MyShowsScrobbleResult(start = start, stop = stop)
    }

    private suspend fun HttpResponse.toCheckResult(): MyShowsCheckResult {
        val payload = parsePayload()
        return MyShowsCheckResult(
            httpStatus = status.value,
            accountName = payload.findFirstString(ACCOUNT_FIELDS),
            responseMessage = payload.findFirstString(RESPONSE_FIELDS),
        )
    }

    private suspend fun HttpResponse.toScrobbleResponse(): MyShowsScrobbleResponse {
        requireSuccess()
        val payload = parsePayload()
        return MyShowsScrobbleResponse(
            httpStatus = status.value,
            id = payload.findFirstPrimitive(ID_FIELDS)?.longOrNull,
            action = payload.findFirstString(ACTION_FIELDS),
            progress = payload.findFirstPrimitive(PROGRESS_FIELDS)?.doubleOrNull,
        )
    }

    private suspend fun HttpResponse.parsePayload(): JsonElement? {
        val body = bodyAsText()
        if (body.isBlank()) return null
        return runCatching { responseJson.parseToJsonElement(body) }.getOrNull()
    }

    private fun JsonElement?.findFirstString(
        fieldNames: Set<String>,
        depth: Int = 0,
    ): String? = findFirstPrimitive(fieldNames, depth)
        ?.takeIf { it.isString }
        ?.content
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.take(MAX_RESPONSE_VALUE_LENGTH)

    private fun JsonElement?.findFirstPrimitive(
        fieldNames: Set<String>,
        depth: Int = 0,
    ): JsonPrimitive? {
        if (this !is JsonObject || depth > MAX_RESPONSE_DEPTH) return null
        return fieldNames.firstNotNullOfOrNull { fieldName -> this[fieldName] as? JsonPrimitive }
            ?: values.firstNotNullOfOrNull { value ->
                value.findFirstPrimitive(fieldNames, depth + 1)
            }
    }

    private suspend fun postScrobble(
        action: String,
        token: String,
        request: MyShowsScrobbleRequest,
    ): HttpResponse = httpClient.post("$BASE_URL/$action") {
        bearerAuth(token)
        contentType(ContentType.Application.Json)
        setBody(request)
    }

    private suspend fun <T> apiResult(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

    private suspend fun HttpResponse.requireSuccess() {
        if (!status.isSuccess()) {
            throw MyShowsApiException(
                statusCode = status.value,
                responseMessage = parsePayload().findFirstString(ERROR_FIELDS),
            )
        }
    }

    internal companion object {
        private const val BASE_URL = "https://myshows.me/scrobble"
        private const val REQUEST_TIMEOUT_MS = 5_000L
        private const val UNAUTHORIZED = 401
        private const val FORBIDDEN = 403
        private const val MAX_RESPONSE_DEPTH = 2
        private const val MAX_RESPONSE_VALUE_LENGTH = 80
        private val ACCOUNT_FIELDS = setOf("login", "username", "user_name", "display_name", "name")
        private val RESPONSE_FIELDS = setOf("status", "message", "result")
        private val ERROR_FIELDS = setOf("error", "message", "detail")
        private val ID_FIELDS = setOf("id")
        private val ACTION_FIELDS = setOf("action")
        private val PROGRESS_FIELDS = setOf("progress")
        private val responseJson = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        fun forTesting(httpClient: HttpClient): MyShowsApiClient = MyShowsApiClient(httpClient)

        private fun createHttpClient(): HttpClient = HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(responseJson)
            }
            install(HttpTimeout) {
                requestTimeoutMillis = REQUEST_TIMEOUT_MS
                connectTimeoutMillis = REQUEST_TIMEOUT_MS
                socketTimeoutMillis = REQUEST_TIMEOUT_MS
            }
        }
    }
}

class MyShowsApiException(
    val statusCode: Int,
    val responseMessage: String? = null,
) : IOException(
    buildString {
        append("MyShows request failed with HTTP ")
        append(statusCode)
        responseMessage?.let {
            append(": ")
            append(it)
        }
    }
)
