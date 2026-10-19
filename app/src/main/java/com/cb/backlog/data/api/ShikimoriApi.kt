package com.cb.backlog.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

const val SHIKI_BASE = "https://shikimori.one"
const val SHIKI_ME_BASE = "https://shikimori.me"

@Serializable
data class ShikiAnime(
    val id: Long,
    val name: String? = null,
    val russian: String? = null,
    val image: ShikiImage? = null,
    val description: String? = null,
    @SerialName("aired_on") val airedOn: String? = null,
    val kind: String? = null,
    val episodes: Int? = null,
    val studios: List<ShikiStudio> = emptyList(),
    val genres: List<ShikiGenre> = emptyList(),
) {
    val displayTitle: String get() = russian?.takeIf { it.isNotBlank() } ?: name ?: ""
}

@Serializable
data class ShikiImage(
    val original: String? = null,
    val preview: String? = null,
)

@Serializable
data class ShikiStudio(val name: String? = null)

@Serializable
data class ShikiGenre(val russian: String? = null, val name: String? = null)

interface ShikimoriApi {

    @GET("api/animes")
    suspend fun search(
        @Query("search") query: String,
        @Query("limit") limit: Int = 20,
    ): List<ShikiAnime>

    @GET("api/animes/{id}")
    suspend fun animeDetails(@Path("id") id: Long): ShikiAnime
}
