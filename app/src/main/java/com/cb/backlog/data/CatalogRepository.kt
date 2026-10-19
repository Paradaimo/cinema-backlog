package com.cb.backlog.data

import com.cb.backlog.data.api.JikanApi
import com.cb.backlog.data.api.KitsuItem
import com.cb.backlog.data.api.KpApi
import com.cb.backlog.data.api.KpMovie
import com.cb.backlog.data.api.SHIKI_BASE
import com.cb.backlog.data.api.ShikiAnime
import com.cb.backlog.data.api.ShikimoriApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CatalogRepository(
    private val kp: KpApi,
    private val shikimori: ShikimoriApi,
    private val jikan: JikanApi,
    private val kitsu: com.cb.backlog.data.api.KitsuApi,
) {

    suspend fun searchMoviesAndShows(query: String): List<MediaInfo> =
        kp.search(query).docs
            .map { it.toMediaInfo() }

    suspend fun searchAnime(query: String): List<MediaInfo> =
        try {
            shikimori.search(query).map { it.toMediaInfo() }
        } catch (e: Exception) {
            try {
                jikan.searchAnime(query).data.map { it.toMediaInfo() }
            } catch (e2: Exception) {
                kitsu.searchAnime(query).data.map { it.toMediaInfo() }
            }
        }

    suspend fun loadDetails(source: String, externalId: String, type: MediaType): MediaInfo =
        when (source) {
            SOURCE_KP -> loadKpDetails(externalId.toLong())
            SOURCE_SHIKI, SOURCE_JIKAN -> loadAnimeDetails(externalId.toLong())
            SOURCE_KITSU -> error("Детали недоступны для этого источника")
            else -> error("Неизвестный источник: $source")
        }

    private suspend fun loadKpDetails(id: Long): MediaInfo =
        kp.movieDetails(id).toMediaInfo()

    private suspend fun loadAnimeDetails(id: Long): MediaInfo =
        try {
            shikimori.animeDetails(id).toMediaInfo()
        } catch (e: Exception) {
            jikan.animeFull(id).data?.toMediaInfo() ?: error("Аниме не найдено")
        }

    companion object {
        const val SOURCE_KP = "kinopoisk"
        const val SOURCE_SHIKI = "shikimori"
        const val SOURCE_JIKAN = "jikan"
        const val SOURCE_KITSU = "kitsu"

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

private fun com.cb.backlog.data.api.KitsuItem.toMediaInfo(): MediaInfo {
    val a = attributes
    val year = a?.startDate?.take(4)?.toIntOrNull()
    return MediaInfo(
        source = CatalogRepository.SOURCE_KITSU,
        externalId = id,
        type = MediaType.ANIME,
        title = a?.titles?.en ?: a?.titles?.enJp ?: a?.canonicalTitle ?: "",
        posterUrl = a?.posterImage?.large ?: a?.posterImage?.original
            ?: a?.posterImage?.medium ?: a?.posterImage?.small,
        synopsis = a?.synopsis ?: a?.description,
        year = year,
        director = null,
        cast = emptyList(),
    )
}

private fun ShikiAnime.toMediaInfo(): MediaInfo {
    val cleanDescription = description
        ?.replace(Regex("\\[[^\\]]*\\]"), "")
        ?.replace(Regex("<br\\s*/?>"), "\n")
        ?.trim()
    val year = airedOn?.let { Regex("\\d{4}").find(it)?.value?.toIntOrNull() }
    return MediaInfo(
        source = CatalogRepository.SOURCE_SHIKI,
        externalId = id.toString(),
        type = MediaType.ANIME,
        title = displayTitle,
        posterUrl = image?.original?.let { SHIKI_BASE + it }
            ?: image?.preview?.let { SHIKI_BASE + it },
        synopsis = cleanDescription?.takeIf { it.isNotBlank() },
        year = year,
        director = studios.mapNotNull { it.name }.joinToString(", ").ifEmpty { null },
        cast = emptyList(),
    )
}
