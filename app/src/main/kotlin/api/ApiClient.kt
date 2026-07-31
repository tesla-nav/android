package io.github.teslanav.app.api

import io.github.teslanav.app.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class ApiException(message: String) : Exception(message)

open class ApiClient(
    private val baseUrl: String,
    private val accessToken: String? = null,
    private val enableLogging: Boolean = BuildConfig.DEBUG
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    protected val client = HttpClient(CIO) {
        defaultRequest {
            header(HttpHeaders.ContentType, ContentType.Application.Json)
            accessToken?.let { token -> bearerAuth(token) }
        }
        install(ContentNegotiation) {
            json(json)
        }
        if (enableLogging) {
            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        android.util.Log.d("TeslaApiClient", message)
                    }
                }
                level = LogLevel.INFO
            }
        }
    }

    protected suspend fun <T> safeRequest(
        method: HttpMethod,
        endpoint: String? = null,
        fullUrl: String? = null,
        body: Any? = null,
        block: suspend (HttpResponse) -> T
    ): Result<T> {
        val urlString = fullUrl ?: endpoint?.let { "$baseUrl$it" }
            ?: return Result.failure(IllegalArgumentException("Either endpoint or fullUrl must be provided"))

        return try {
            val response = client.request(urlString) {
                this.method = method
                if (body != null) {
                    setBody(body)
                }
            }
            if (response.status.isSuccess()) {
                Result.success(block(response))
            } else {
                val errorBody = try {
                    response.bodyAsText()
                } catch (e: Exception) {
                    "Could not read error body: ${e.message}"
                }
                Result.failure(
                    ApiException("Error in $urlString: HTTP ${response.status.value} ${response.status.description} - $errorBody")
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun close() {
        client.close()
    }
}
