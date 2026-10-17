package com.cb.backlog.data

import com.cb.backlog.data.api.JikanApi
import com.cb.backlog.data.api.KpApi
import com.cb.backlog.data.api.KpMovie
import kotlinx.serialization.json.Json

class CatalogRepository(
    private val kp: KpApi,
    private val jikan: JikanApi,
) {

    suspend fun searchMoviesAndShows(query: String): List<MediaInfo> =
        kp.search(query).docs
            .map { it.toMediaInfo() }

    suspend fun searchAnime(query: String): List<MediaInfo> =
        jikan.searchAnime(query).data.map { it.toMediaInfo() }

    suspend fun loadDetails(source: String, externalId: String, type: MediaType): MediaInfo =
        when (source) {
            SOURCE_KP -> loadKpDetails(externalId.toLong())
            SOURCE_JIKAN -> jikan.animeFull(externalId.toLong()).data?.toMediaInfo()
                ?: error("Аниме не найдено")
            else -> error("Неизвестный источник: $source")
        }

    private suspend fun loadKpDetails(id: Long): MediaInfo =
        kp.movieDetails(id).toMediaInfo()

    companion object {
        const val SOURCE_KP = "kinopoisk"
        const val SOURCE_JIKAN = "jikan"

        fun castToJson(cast: List<String>): String = Json.encodeToString(cast)
    }
}

private fun KpMovie.toMediaInfo(): MediaInfo {
    val type = when (type) {
        "anime" -> MediaType.ANIME
        "tv-series", "animated-series", "tv-show" -> MediaType.TV
        else -> MediaType.MOVIE
    }
    val directors = persons
        .filter { p -> p.enProfession == "director" || p.profession?.startsWith("Режисс") == true }
        .mapNotNull { it.name ?: it.enName }
    val cast = persons
        .filter { p -> p.enProfession == "actor" || p.profession?.startsWith("Акт") == true || p.profession?.startsWith("Актер") == true }
        .mapNotNull { it.name ?: it.enName }
        .take(10)
    return MediaInfo(
        source = CatalogRepository.SOURCE_KP,
        externalId = id.toString(),
        type = type,
        title = displayTitle,
        posterUrl = poster?.url ?: poster?.previewUrl,
        synopsis = description,
        year = year,
        director = directors.joinToString(", ").ifEmpty { null },
        cast = cast,
    )
}

private fun com.cb.backlog.data.api.JikanAnime.toMediaInfo(): MediaInfo = MediaInfo(
    source = CatalogRepository.SOURCE_JIKAN,
    externalId = malId.toString(),
    type = MediaType.ANIME,
    title = titleEnglish ?: title ?: "",
    posterUrl = images?.jpg?.largeImageUrl ?: images?.jpg?.imageUrl,
    synopsis = synopsis,
    year = year,
    director = studios.mapNotNull { it.name }.joinToString(", ").ifEmpty { null },
    cast = emptyList(),
)
