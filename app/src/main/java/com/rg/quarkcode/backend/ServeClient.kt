package com.rg.quarkcode.backend

import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

// HTTP client for the opencode Serve API (local native ELF or remote).
// okhttp3 comes transitively via retrofit-core + logging-interceptor.
object ServeClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    fun service(host: String, username: String, password: String): OpenCodeService {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val http = OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val authed = if (password.isNotEmpty()) {
                    chain.request().newBuilder()
                        .header("Authorization", Credentials.basic(username, password))
                        .build()
                } else {
                    chain.request()
                }
                chain.proceed(authed)
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
        val base = host.trimEnd('/') + "/"
        return Retrofit.Builder()
            .baseUrl(base)
            .client(http)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenCodeService::class.java)
    }
}
