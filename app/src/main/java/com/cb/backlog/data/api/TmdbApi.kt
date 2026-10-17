package com.cb.backlog.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/w500"

@Serializable
data class TmdbSearchResponse(
    val page: Int = 1,
    val results: List<TmdbSearchItem> = emptyList(),
)

@Serializable
data class TmdbSearchItem(
    val id: Long,
    @SerialName("media_type") val mediaType: String? = null,
    val title: String? = null,
    val name: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
) {
    val displayTitle: String get() = title ?: name ?: originalTitle ?: originalName ?: ""
}

@Serializable
data class TmdbCredits(
    val crew: List<TmdbCrewMember> = emptyList(),
    val cast: List<TmdbCastMember> = emptyList(),
)

@Serializable
data class TmdbCrewMember(val name: String? = null, val job: String? = null)

@Serializable
data class TmdbCastMember(val name: String? = null, val character: String? = null)

@Serializable
data class TmdbMovieDetails(
    val id: Long,
    val title: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    val runtime: Int? = null,
    val credits: TmdbCredits? = null,
)

@Serializable
data class TmdbTvDetails(
    val id: Long,
    val name: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("number_of_seasons") val numberOfSeasons: Int? = null,
    @SerialName("created_by") val createdBy: List<TmdbCreatedBy> = emptyList(),
    val credits: TmdbCredits? = null,
)

@Serializable
data class TmdbCreatedBy(val name: String? = null)

interface TmdbApi {

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("language") language: String = "ru-RU",
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("page") page: Int = 1,
    ): TmdbSearchResponse

    @GET("movie/{id}")
    suspend fun movieDetails(
        @Path("id") id: Long,
        @Query("append_to_response") append: String = "credits",
        @Query("language") language: String = "ru-RU",
    ): TmdbMovieDetails

    @GET("tv/{id}")
    suspend fun tvDetails(
        @Path("id") id: Long,
        @Query("append_to_response") append: String = "credits",
        @Query("language") language: String = "ru-RU",
    ): TmdbTvDetails
}
