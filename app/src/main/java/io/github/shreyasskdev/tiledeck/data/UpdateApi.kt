package io.github.shreyasskdev.tiledeck.data

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

interface UpdateApi {

    /** Fetches the failsafe pointer JSON from a raw GitHub URL. */
    @GET
    suspend fun fetchPointer(@Url url: String): UpdatePointer

    /**
     * Fetches the latest release from a base API URL. The caller appends
     * "/releases/latest" to the pointer's releases_url.
     */
    @GET
    suspend fun fetchLatestRelease(@Url url: String): GitHubRelease

    companion object {
        private const val POINTER_URL =
            "https://raw.githubusercontent.com/shreyasskdev/tiledeck-widgets/main/update.json"

        fun pointerUrl(): String = POINTER_URL

        fun create(): UpdateApi {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl("https://api.github.com/")   // placeholder, overridden by @Url
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(UpdateApi::class.java)
        }
    }
}