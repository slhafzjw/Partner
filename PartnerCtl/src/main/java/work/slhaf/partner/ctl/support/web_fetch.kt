package work.slhaf.partner.ctl.support

import java.io.IOException
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.URI
import java.net.http.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import kotlin.io.path.isDirectory

private val httpClient: HttpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(20))
    .followRedirects(HttpClient.Redirect.NORMAL)
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

fun downloadTo(
    url: String,
    targetPath: Path,
    onProgress: (downloaded: Long, total: Long?) -> Unit = { _, _ -> }
) {
    if (targetPath.isDirectory()) {
        throw IllegalArgumentException("Target path must be a file")
    }
    val targetPath = targetPath.toAbsolutePath().normalize()
    val targetFile = targetPath.toFile()
    val temp = Files.createTempFile(
        "${targetFile.name}-${System.currentTimeMillis()}", ".${targetFile.extension}.download"
    )

    try {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build()

        val response = httpClient.send(
            request,
            HttpResponse.BodyHandlers.ofInputStream()
        )

        if (response.statusCode() !in 200..299) {
            throw IllegalStateException("Failed to download from $url: HTTP ${response.statusCode()}")
        }

        val totalBytes = response.headers()
            .firstValue("Content-Length")
            .orElse(null)
            ?.toLongOrNull()

        response.body().use { input ->
            Files.newOutputStream(temp).use { output ->
                val buffer = ByteArray(8192)
                var downloaded = 0L

                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break

                    output.write(buffer, 0, read)
                    downloaded += read
                    onProgress(downloaded, totalBytes)
                }
            }
        }

        Files.move(temp, targetPath, StandardCopyOption.REPLACE_EXISTING)
    } catch (e: Exception) {
        Files.deleteIfExists(temp)
        throw e
    }
}
