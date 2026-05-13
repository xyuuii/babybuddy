package com.yueming.baby.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object WebDavManager {
    data class WebDavConfig(
        val url: String,
        val username: String,
        val password: String
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val BACKUP_DIR = "yueming-backups/"

    suspend fun testConnection(config: WebDavConfig): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = normalizeUrl(config.url)
            val request = buildRequest(config, url, "PROPFIND")
                .header("Depth", "0")
                .build()
            val response = client.newCall(request).execute()
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
            Result.failure(Exception("WebDAV 连接失败: ${e.message}"))
        }
    }

    suspend fun uploadBackup(config: WebDavConfig, data: ByteArray, filename: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = normalizeUrl(config.url)
            val encodedFilename = filename
            val fullUrl = if (url.endsWith("/")) "${url}$BACKUP_DIR$encodedFilename"
            else "$url/$BACKUP_DIR$encodedFilename"

            // Create directory first
            val dirUrl = if (url.endsWith("/")) "${url}$BACKUP_DIR" else "$url/$BACKUP_DIR"
            createDirectory(config, dirUrl)

            val mediaType = "application/zip".toMediaType()
            val request = buildRequest(config, fullUrl, "PUT")
                .put(data.toRequestBody(mediaType))
                .build()
            val response = client.newCall(request).execute()
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
            Result.failure(Exception("上传备份失败: ${e.message}"))
        }
    }

    suspend fun downloadBackup(config: WebDavConfig, filename: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val url = normalizeUrl(config.url)
            val fullUrl = if (url.endsWith("/")) "${url}$BACKUP_DIR$filename"
            else "$url/$BACKUP_DIR$filename"

            val request = buildRequest(config, fullUrl, "GET").build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val bytes = response.body?.bytes() ?: ByteArray(0)
                Result.success(bytes)
            } else {
                Result.failure(Exception("下载失败: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("下载备份失败: ${e.message}"))
        }
    }

    suspend fun listBackups(config: WebDavConfig): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val url = normalizeUrl(config.url)
            val fullUrl = if (url.endsWith("/")) "${url}$BACKUP_DIR" else "$url/$BACKUP_DIR"

            val request = buildRequest(config, fullUrl, "PROPFIND")
                .header("Depth", "1")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val files = parsePropfindResponse(body)
                    .filter { it.endsWith(".zip") }
                Result.success(files)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.failure(Exception("列出备份失败: ${e.message}"))
        }
    }

    private fun buildRequest(config: WebDavConfig, url: String, method: String): Request.Builder {
        val credential = Credentials.basic(config.username, config.password)
        return Request.Builder()
            .url(url)
            .method(method, null)
            .header("Authorization", credential)
            .header("User-Agent", "YueMing-Android")
    }

    private fun normalizeUrl(rawUrl: String): String {
        var url = rawUrl.trimEnd('/')
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        return url
    }

    private fun createDirectory(config: WebDavConfig, dirUrl: String) {
        try {
            val request = buildRequest(config, dirUrl, "MKCOL").build()
            client.newCall(request).execute().close()
        } catch (_: Exception) {
            // Directory may already exist, ignore
        }
    }

    private fun parsePropfindResponse(xml: String): List<String> {
        val result = mutableListOf<String>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(ByteArrayInputStream(xml.toByteArray()), "UTF-8")

            var eventType = parser.eventType
            var inHref = false
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "href") {
                            inHref = true
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inHref) {
                            val text = parser.text
                            if (text.isNotBlank()) {
                                val name = text.trimEnd('/').substringAfterLast('/')
                                if (name.isNotBlank()) {
                                    result.add(name)
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        inHref = false
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) { }
        return result
    }
}
