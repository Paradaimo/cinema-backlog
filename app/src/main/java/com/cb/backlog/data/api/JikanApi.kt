package com.cb.backlog.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable
data class JikanSearchResponse(val data: List<JikanAnime> = emptyList())

@Serializable
data class JikanAnime(
    @SerialName("mal_id") val malId: Long,
    val title: String? = null,
    @SerialName("title_english") val titleEnglish: String? = null,
    val synopsis: String? = null,
    val year: Int? = null,
    val images: JikanImages? = null,
    val studios: List<JikanNamed> = emptyList(),
)

@Serializable
data class JikanImages(val jpg: JikanImage? = null)

@Serializable
data class JikanImage(
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("large_image_url") val largeImageUrl: String? = null,
)

@Serializable
data class JikanNamed(val name: String? = null)

interface JikanApi {

    @GET("anime")
    suspend fun searchAnime(
        @Query("q") query: String,
        @Query("limit") limit: Int = 20,
        @Query("sfw") sfw: Boolean = true,
        @Query("order_by") orderBy: String = "members",
    ): JikanSearchResponse

    @GET("anime/{id}/full")
    suspend fun animeFull(@Path("id") id: Long): JikanSingleResponse
}

@Serializable
data class JikanSingleResponse(val data: JikanAnime? = null)
