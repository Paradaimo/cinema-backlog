package com.cb.backlog.di

import android.content.Context
import com.cb.backlog.BuildConfig
import com.cb.backlog.data.CatalogRepository
import com.cb.backlog.data.SupabaseRepo
import com.cb.backlog.data.api.JikanApi
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {

    val prefs: android.content.SharedPreferences =
        context.getSharedPreferences("cb_prefs", Context.MODE_PRIVATE)

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    val supabaseClient: SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
    ) {
        install(Auth)
        install(Postgrest)
        install(Realtime)
        httpEngine = OkHttp.create()
    }

    val supabaseRepo = SupabaseRepo(supabaseClient)

    private val kpClient: OkHttpClient = okHttp.newBuilder()
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .addHeader("X-API-KEY", BuildConfig.KP_TOKEN)
                    .build()
            )
        }
        .build()

    private val kpRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl("https://api.kinopoisk.dev/")
        .client(kpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val shikimoriClient: OkHttpClient = okHttp.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .dns(object : okhttp3.Dns {
            // IPv4 only: на мобильных сетях IPv6-коннекты часто висят до таймаута
            override fun lookup(hostname: String): List<java.net.InetAddress> =
                java.net.InetAddress.getAllByName(hostname).toList()
                    .filter { it is java.net.Inet4Address }
        })
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "CB-Backlog/0.3 (Android)")
                    .header("Accept", "application/json")
                    .build()
            )
        }
        .build()

    private fun shikimoriRetrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(shikimoriClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val jikanRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl("https://api.jikan.moe/v4/")
        .client(okHttp)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val kitsuRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl("https://kitsu.io/api/edge/")
        .client(okHttp)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val catalogRepository = CatalogRepository(
        kp = kpRetrofit.create(com.cb.backlog.data.api.KpApi::class.java),
        shikimoriOne = shikimoriRetrofit("https://shikimori.one/")
            .create(com.cb.backlog.data.api.ShikimoriApi::class.java),
        shikimoriMe = shikimoriRetrofit("https://shikimori.me/")
            .create(com.cb.backlog.data.api.ShikimoriApi::class.java),
        jikan = jikanRetrofit.create(JikanApi::class.java),
        kitsu = kitsuRetrofit.create(com.cb.backlog.data.api.KitsuApi::class.java),
    )
}
