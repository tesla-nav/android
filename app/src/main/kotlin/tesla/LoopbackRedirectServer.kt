package io.github.teslanav.app.tesla

import android.content.Context
import android.util.Log
import io.github.teslanav.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URLDecoder

private const val TAG = "LoopbackRedirectServer"

class LoopbackAuthTimeoutException(message: String) : Exception(message)

/**
 * Single-use local HTTP server (127.0.0.1): captures the OAuth redirect
 * after Tesla login, without exposing anything outside the device.
 */
class LoopbackRedirectServer(private val context: Context, private val port: Int) {

    suspend fun awaitCallback(timeoutMs: Long = 120_000): Map<String, String> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Listening on 127.0.0.1:$port (timeout ${timeoutMs}ms)")
        ServerSocket(port).use { server ->
            server.soTimeout = timeoutMs.toInt()
            val client = try {
                server.accept()
            } catch (e: SocketTimeoutException) {
                Log.w(TAG, "Timeout: no callback received")
                throw LoopbackAuthTimeoutException(context.getString(R.string.loopback_timeout))
            }
            client.use { socket ->
                Log.d(TAG, "Connection received from the browser")
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val requestLine = reader.readLine().orEmpty()

                val callbackHtml = "<html><body><p>${context.getString(R.string.callback_success_message)}</p></body></html>"
                PrintWriter(socket.getOutputStream(), true).apply {
                    print("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nConnection: close\r\n\r\n$callbackHtml")
                    flush()
                }

                val params = parseQueryParams(requestLine)
                Log.d(TAG, "Callback parsed: ${params.keys}")
                params
            }
        }
    }

    private fun parseQueryParams(requestLine: String): Map<String, String> {
        val path = requestLine.split(" ").getOrNull(1).orEmpty()
        val query = path.substringAfter("?", missingDelimiterValue = "")
        if (query.isBlank()) return emptyMap()

        return query.split("&")
            .filter { it.isNotBlank() }
            .mapNotNull { pair ->
                val parts = pair.split("=", limit = 2)
                val key = parts.getOrNull(0) ?: return@mapNotNull null
                val value = parts.getOrElse(1) { "" }
                key to URLDecoder.decode(value, "UTF-8")
            }
            .toMap()
    }
}
