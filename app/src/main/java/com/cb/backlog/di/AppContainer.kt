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

    private val jikanRetrofit: Retrofit = Retrofit.Builder()
        .baseUrl("https://api.jikan.moe/v4/")
        .client(okHttp)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val catalogRepository = CatalogRepository(
        kp = kpRetrofit.create(com.cb.backlog.data.api.KpApi::class.java),
        kpToken = BuildConfig.KP_TOKEN,
        jikan = jikanRetrofit.create(JikanApi::class.java),
    )
}
