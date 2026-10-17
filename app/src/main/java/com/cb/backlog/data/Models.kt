package com.cb.backlog.data

enum class WatchStatus(val label: String) {
    WANT("Хочу посмотреть"),
    WATCHING("Смотрю"),
    WATCHED("Посмотрено"),
    DROPPED("Брошено");

    companion object {
        fun fromRaw(value: String?): WatchStatus? = entries.firstOrNull { it.name == value }
    }
}

enum class MediaType(val label: String) {
    MOVIE("Фильм"),
    TV("Сериал"),
    ANIME("Аниме");

    companion object {
        fun fromRaw(value: String?): MediaType? = entries.firstOrNull { it.name == value }
    }
}

data class MediaInfo(
    val source: String,
    val externalId: String,
    val type: MediaType,
    val title: String,
    val posterUrl: String?,
    val synopsis: String?,
    val year: Int?,
    val director: String?,
    val cast: List<String>,
)

data class ListItem(
    val item: ItemRow,
    val addedByName: String,
    val statuses: Map<String, WatchStatus>,
    val ratings: Map<String, Int>,
    val myStatus: WatchStatus?,
    val myRating: Int?,
    val avgRating: Double?,
)

data class BacklogData(
    val listRow: ListRow,
    val members: List<MemberRow>,
    val items: List<ListItem>,
    val myUserId: String,
)

@kotlinx.serialization.Serializable
data class ListRow(
    @kotlinx.serialization.SerialName("id") val id: String,
    @kotlinx.serialization.SerialName("name") val name: String,
    @kotlinx.serialization.SerialName("invite_code") val inviteCode: String,
)

@kotlinx.serialization.Serializable
data class MemberRow(
    @kotlinx.serialization.SerialName("list_id") val listId: String,
    @kotlinx.serialization.SerialName("user_id") val userId: String,
    @kotlinx.serialization.SerialName("display_name") val displayName: String,
)

@kotlinx.serialization.Serializable
data class ItemRow(
    @kotlinx.serialization.SerialName("id") val id: String,
    @kotlinx.serialization.SerialName("list_id") val listId: String,
    @kotlinx.serialization.SerialName("source") val source: String,
    @kotlinx.serialization.SerialName("external_id") val externalId: String,
    @kotlinx.serialization.SerialName("type") val type: String,
    @kotlinx.serialization.SerialName("title") val title: String,
    @kotlinx.serialization.SerialName("poster_url") val posterUrl: String? = null,
    @kotlinx.serialization.SerialName("synopsis") val synopsis: String? = null,
    @kotlinx.serialization.SerialName("year") val year: Int? = null,
    @kotlinx.serialization.SerialName("director") val director: String? = null,
    @kotlinx.serialization.SerialName("cast_list") val castJson: String? = null,
    @kotlinx.serialization.SerialName("added_by") val addedBy: String,
    @kotlinx.serialization.SerialName("added_at") val addedAt: String? = null,
) {
    val castList: List<String>
        get() = runCatching {
            kotlinx.serialization.json.Json.decodeFromString<List<String>>(castJson ?: "[]")
        }.getOrDefault(emptyList())

    val mediaType: MediaType? get() = MediaType.fromRaw(type.uppercase())
}

@kotlinx.serialization.Serializable
data class StatusRow(
    @kotlinx.serialization.SerialName("item_id") val itemId: String,
    @kotlinx.serialization.SerialName("user_id") val userId: String,
    @kotlinx.serialization.SerialName("status") val status: String,
)

@kotlinx.serialization.Serializable
data class RatingRow(
    @kotlinx.serialization.SerialName("item_id") val itemId: String,
    @kotlinx.serialization.SerialName("user_id") val userId: String,
    @kotlinx.serialization.SerialName("score") val score: Int,
)

@kotlinx.serialization.Serializable
data class NewItemRow(
    @kotlinx.serialization.SerialName("list_id") val listId: String,
    @kotlinx.serialization.SerialName("source") val source: String,
    @kotlinx.serialization.SerialName("external_id") val externalId: String,
    @kotlinx.serialization.SerialName("type") val type: String,
    @kotlinx.serialization.SerialName("title") val title: String,
    @kotlinx.serialization.SerialName("poster_url") val posterUrl: String? = null,
    @kotlinx.serialization.SerialName("synopsis") val synopsis: String? = null,
    @kotlinx.serialization.SerialName("year") val year: Int? = null,
    @kotlinx.serialization.SerialName("director") val director: String? = null,
    @kotlinx.serialization.SerialName("cast_list") val castJson: String? = null,
    @kotlinx.serialization.SerialName("added_by") val addedBy: String,
)

@kotlinx.serialization.Serializable
data class NewStatusRow(
    @kotlinx.serialization.SerialName("item_id") val itemId: String,
    @kotlinx.serialization.SerialName("list_id") val listId: String,
    @kotlinx.serialization.SerialName("user_id") val userId: String,
    @kotlinx.serialization.SerialName("status") val status: String,
)

@kotlinx.serialization.Serializable
data class NewRatingRow(
    @kotlinx.serialization.SerialName("item_id") val itemId: String,
    @kotlinx.serialization.SerialName("list_id") val listId: String,
    @kotlinx.serialization.SerialName("user_id") val userId: String,
    @kotlinx.serialization.SerialName("score") val score: Int,
)
