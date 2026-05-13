package work.slhaf.partner.ctl.support

import java.io.IOException
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.URI
import java.net.http.*
import java.time.Duration

private val httpClient: HttpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(20))
    .followRedirects(HttpClient.Redirect.NEVER)
    .apply {
        proxySelectorFromEnv()?.let(::proxy)
    }
    .build()

private fun proxySelectorFromEnv(): ProxySelector? {
    val proxyText = System.getenv("HTTPS_PROXY")
        ?: System.getenv("https_proxy")
        ?: return null

    val proxyUri = URI.create(proxyText)
    val host = proxyUri.host
        ?: throw IllegalArgumentException("Invalid HTTPS_PROXY host: $proxyText")

    val port = proxyUri.port
    if (port == -1) {
        throw IllegalArgumentException("HTTPS_PROXY must include port: $proxyText")
    }

    return ProxySelector.of(InetSocketAddress(host, port))
}

fun fetchText(url: String): String {
    var lastError: Exception? = null

    repeat(3) { attempt ->
        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", "partnerctl")
                .GET()
                .build()

            val response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            )

            if (response.statusCode() !in 200..299) {
                throw IOException("Failed to fetch $url: HTTP ${response.statusCode()}")
            }

            return response.body()
        } catch (e: HttpTimeoutException) {
            lastError = e
        } catch (e: HttpConnectTimeoutException) {
            lastError = e
        } catch (e: IOException) {
            lastError = e
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Interrupted while fetching $url", e)
        }

        if (attempt < 2) {
            Thread.sleep(500L * (attempt + 1))
        }
    }

    throw IOException("Failed to fetch $url after retries", lastError)
}
