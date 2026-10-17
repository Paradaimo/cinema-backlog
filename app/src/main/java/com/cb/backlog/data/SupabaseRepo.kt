package com.cb.backlog.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SupabaseRepo(private val client: SupabaseClient) {

    val sessionStatus = client.auth.sessionStatus

    fun currentUserId(): String? = client.auth.currentUserOrNull()?.id

    suspend fun signIn(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signUp(email: String, password: String) {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signOut() = client.auth.signOut(SignOutScope.LOCAL)

    suspend fun createList(name: String, displayName: String): ListRow =
        client.postgrest.rpc(
            function = "create_list",
            parameters = buildJsonObject {
                put("p_name", name)
                put("p_display_name", displayName)
            },
        ).decodeAs()

    suspend fun joinList(code: String, displayName: String): ListRow =
        client.postgrest.rpc(
            function = "join_list",
            parameters = buildJsonObject {
                put("p_code", code)
                put("p_display_name", displayName)
            },
        ).decodeAs()

    suspend fun myMembership(): MemberRow? {
        val uid = currentUserId() ?: return null
        return client.from("members")
            .select { filter { eq("user_id", uid) } }
            .decodeSingleOrNull()
    }

    suspend fun fetchBacklog(listId: String): BacklogData {
        val uid = currentUserId() ?: error("Не авторизован")

        val listRow = client.from("lists")
            .select { filter { eq("id", listId) } }
            .decodeSingle<ListRow>()

        val members = client.from("members")
            .select { filter { eq("list_id", listId) } }
            .decodeList<MemberRow>()

        val items = client.from("items")
            .select { filter { eq("list_id", listId) } }
            .decodeList<ItemRow>()
            .sortedByDescending { it.addedAt ?: "" }

        val statuses = client.from("item_status")
            .select { filter { eq("list_id", listId) } }
            .decodeList<StatusRow>()

        val ratings = client.from("item_ratings")
            .select { filter { eq("list_id", listId) } }
            .decodeList<RatingRow>()

        val namesById = members.associate { it.userId to it.displayName }

        val listItems = items.map { item ->
            ListItem(
                item = item,
                addedByName = namesById[item.addedBy] ?: "Неизвестно",
                statuses = statuses.filter { it.itemId == item.id }
                    .mapNotNull { s -> WatchStatus.fromRaw(s.status)?.let { s.userId to it } }
                    .toMap(),
                ratings = ratings.filter { it.itemId == item.id }
                    .associate { it.userId to it.score },
                myStatus = statuses.firstOrNull { it.itemId == item.id && it.userId == uid }
                    ?.let { WatchStatus.fromRaw(it.status) },
                myRating = ratings.firstOrNull { it.itemId == item.id && it.userId == uid }?.score,
                avgRating = ratings.filter { it.itemId == item.id }
                    .map { it.score }
                    .takeIf { it.isNotEmpty() }
                    ?.average(),
            )
        }

        return BacklogData(
            listRow = listRow,
            members = members,
            items = listItems,
            myUserId = uid,
        )
    }

    suspend fun addItem(info: MediaInfo) {
        val membership = myMembership() ?: error("Нет списка")
        client.from("items").insert(
            NewItemRow(
                listId = membership.listId,
                source = info.source,
                externalId = info.externalId,
                type = info.type.name,
                title = info.title,
                posterUrl = info.posterUrl,
                synopsis = info.synopsis,
                year = info.year,
                director = info.director,
                castJson = CatalogRepository.castToJson(info.cast),
                addedBy = currentUserId() ?: error("Не авторизован"),
            )
        )
    }

    suspend fun setStatus(itemId: String, status: WatchStatus?) {
        val membership = myMembership() ?: error("Нет списка")
        val uid = currentUserId() ?: error("Не авторизован")
        if (status == null) {
            client.from("item_status").delete {
                filter {
                    eq("item_id", itemId)
                    eq("user_id", uid)
                }
            }
        } else {
            client.from("item_status").upsert(
                NewStatusRow(itemId, membership.listId, uid, status.name)
            ) {
                onConflict = "item_id,user_id"
            }
        }
    }

    suspend fun setRating(itemId: String, score: Int?) {
        val membership = myMembership() ?: error("Нет списка")
        val uid = currentUserId() ?: error("Не авторизован")
        if (score == null) {
            client.from("item_ratings").delete {
                filter {
                    eq("item_id", itemId)
                    eq("user_id", uid)
                }
            }
        } else {
            client.from("item_ratings").upsert(
                NewRatingRow(itemId, membership.listId, uid, score)
            ) {
                onConflict = "item_id,user_id"
            }
        }
    }

    suspend fun removeItem(itemId: String) {
        client.from("items").delete { filter { eq("id", itemId) } }
    }

    /**
     * Поток изменений таблиц бэклога (items, item_status, item_ratings, members).
     * Каждое событие — сигнал перезагрузить данные.
     */
    fun backlogChanges(): Flow<Unit> = callbackFlow {
        client.realtime.connect()
        val channel: RealtimeChannel = client.channel("backlog")

        val jobs = listOf("items", "item_status", "item_ratings", "members").map { table ->
            launch {
                channel.postgresChangeFlow<PostgresAction>("public") {
                    this.table = table
                }.collect { trySend(Unit) }
            }
        }
        channel.subscribe()

        awaitClose {
            jobs.forEach { it.cancel() }
        }
    }
}
