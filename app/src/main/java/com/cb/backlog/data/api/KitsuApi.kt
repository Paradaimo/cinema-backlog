package com.cb.backlog.data.api

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

@Serializable
data class KitsuResponse(val data: List<KitsuItem> = emptyList())

@Serializable
data class KitsuItem(val id: String, val attributes: KitsuAttributes? = null)

@Serializable
data class KitsuAttributes(
    @kotlinx.serialization.SerialName("canonicalTitle") val canonicalTitle: String? = null,
    @kotlinx.serialization.SerialName("titles") val titles: KitsuTitles? = null,
    val synopsis: String? = null,
    val description: String? = null,
    @kotlinx.serialization.SerialName("startDate") val startDate: String? = null,
    @kotlinx.serialization.SerialName("subtype") val subtype: String? = null,
    @kotlinx.serialization.SerialName("posterImage") val posterImage: KitsuPoster? = null,
)

@Serializable
data class KitsuTitles(val en: String? = null, @kotlinx.serialization.SerialName("en_jp") val enJp: String? = null)

@Serializable
data class KitsuPoster(
    val small: String? = null,
    val medium: String? = null,
    val large: String? = null,
    val original: String? = null,
)

interface KitsuApi {

    @GET("anime")
    suspend fun searchAnime(
        @Query("filter[text]") query: String,
        @Query("page[limit]") limit: Int = 20,
    ): KitsuResponse
}
