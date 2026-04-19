package com.familyhome.app.di

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
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient         = true
    }

    /** Ktor HTTP client used by [SyncClient] for local network communication. */
    @Provides @Singleton
    fun provideKtorClient(): HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        install(Logging) { level = LogLevel.INFO }
    }
}
