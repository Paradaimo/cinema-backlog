package com.cb.backlog.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

data class UpdateInfo(val versionName: String, val downloadUrl: String)

object UpdateChecker {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder().build()

    @Serializable
    private data class Release(
        @SerialName("tag_name") val tagName: String? = null,
        @SerialName("html_url") val htmlUrl: String? = null,
        val assets: List<Asset> = emptyList(),
    )

    @Serializable
    private data class Asset(
        val name: String? = null,
        @SerialName("browser_download_url") val browserDownloadUrl: String? = null,
    )

    fun parseVersion(raw: String): List<Int> =
        raw.trim().removePrefix("v")
            .takeWhile { it.isDigit() || it == '.' }
            .split('.')
            .map { it.toIntOrNull() ?: 0 }

    fun isNewer(remote: String, current: String): Boolean {
        val r = parseVersion(remote)
        val c = parseVersion(current)
        for (i in 0 until maxOf(r.size, c.size)) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }

    suspend fun check(repo: String, currentVersion: String): UpdateInfo? =
        withContext(Dispatchers.IO) {
            if (repo.isBlank()) return@withContext null
            runCatching {
                val request = Request.Builder()
                    .url("https://api.github.com/repos/$repo/releases/latest")
                    .header("Accept", "application/vnd.github+json")
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val release = json.decodeFromString<Release>(resp.body!!.string())
                    val apk = release.assets.firstOrNull { it.name?.endsWith(".apk") == true }
                        ?: return@withContext null
                    val remoteVersion = release.tagName ?: return@withContext null
                    if (!isNewer(remoteVersion, currentVersion)) return@withContext null
                    UpdateInfo(
                        versionName = remoteVersion.removePrefix("v"),
                        downloadUrl = apk.browserDownloadUrl ?: release.htmlUrl ?: return@withContext null,
                    )
                }
            }.getOrNull()
        }
}
