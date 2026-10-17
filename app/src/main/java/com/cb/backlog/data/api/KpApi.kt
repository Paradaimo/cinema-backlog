package com.cb.backlog.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable
data class KpSearchResponse(
    val docs: List<KpMovie> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class KpPoster(val url: String? = null, val previewUrl: String? = null)

@Serializable
data class KpGenre(val name: String? = null)

@Serializable
data class KpPerson(
    val id: Long? = null,
    val name: String? = null,
    val enName: String? = null,
    val profession: String? = null,
    @SerialName("enProfession") val enProfession: String? = null,
)

@Serializable
data class KpSeason(val number: Int? = null)

@Serializable
data class KpMovie(
    val id: Long,
    val name: String? = null,
    val alternativeName: String? = null,
    val year: Int? = null,
    val type: String? = null,
    val description: String? = null,
    val movieLength: Int? = null,
    @SerialName("seriesLength") val seriesLengthMinutes: Int? = null,
    val poster: KpPoster? = null,
    val genres: List<KpGenre> = emptyList(),
    val persons: List<KpPerson> = emptyList(),
    @SerialName("seasonsInfo") val seasons: List<KpSeason> = emptyList(),
) {
    val displayTitle: String get() = name ?: alternativeName ?: ""
}

interface KpApi {

    @GET("v1.4/movie/search")
    suspend fun search(
        @Query("query") query: String,
        @Query("limit") limit: Int = 20,
        @Query("page") page: Int = 1,
    ): KpSearchResponse

    @GET("v1.4/movie/{id}")
    suspend fun movieDetails(@Path("id") id: Long): KpMovie
}

