package com.kino.puber.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class MyShowsCheckResult(
    val httpStatus: Int,
    val accountName: String? = null,
    val responseMessage: String? = null,
)

data class MyShowsScrobbleResult(
    val start: MyShowsScrobbleResponse,
    val stop: MyShowsScrobbleResponse,
)

data class MyShowsScrobbleResponse(
    val httpStatus: Int,
    val id: Long? = null,
    val action: String? = null,
    val progress: Double? = null,
)

@Serializable
data class MyShowsScrobbleRequest(
    val progress: Double,
    @SerialName("source_app") val sourceApp: String,
    val show: MyShowsShow,
    val episode: MyShowsEpisode,
)

@Serializable
data class MyShowsShow(
    val title: String,
    val year: Int? = null,
    val ids: MyShowsIds,
)

@Serializable
data class MyShowsIds(
    val imdb: String? = null,
    val kinopoisk: Int? = null,
)

@Serializable
data class MyShowsEpisode(
    val season: Int,
    val number: Int,
    val title: String? = null,
)
