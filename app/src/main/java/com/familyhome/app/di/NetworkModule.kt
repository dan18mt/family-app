package com.familyhome.app.di

import com.familyhome.app.agent.AnthropicApiService
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import javax.inject.Singleton

private const val ANTHROPIC_BASE_URL = "https://api.anthropic.com/"

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient         = true
    }

    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
            )
            .build()

    @Provides @Singleton
    fun provideAnthropicApiService(okHttpClient: OkHttpClient): AnthropicApiService =
        Retrofit.Builder()
            .baseUrl(ANTHROPIC_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AnthropicApiService::class.java)

    /** Ktor HTTP client used by [SyncClient] for local network communication. */
    @Provides @Singleton
    fun provideKtorClient(): HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        install(Logging) { level = LogLevel.INFO }
    }
}
