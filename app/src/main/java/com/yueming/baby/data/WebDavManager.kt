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
        val password: String,
        val backupPath: String = "/sata1-15529232180/yueming-backups/"
    )

    data class ConnectionTestResult(
        val success: Boolean,
        val message: String = "",
        val directoryContents: List<String> = emptyList()
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun testConnection(config: WebDavConfig): Result<ConnectionTestResult> = withContext(Dispatchers.IO) {
        try {
            val url = normalizeUrl(config.url)
            val request = buildRequest(config, url, "PROPFIND")
                .header("Depth", "0")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                // Also try to list the backup directory
                val dirContents = try {
                    val backupUrl = buildBackupUrl(config)
                    listDirectoryContents(config, backupUrl)
                } catch (_: Exception) { emptyList() }
                Result.success(ConnectionTestResult(true, "连接成功", dirContents))
            } else {
                Result.success(ConnectionTestResult(false, "服务器返回 ${response.code}", emptyList()))
            }
        } catch (e: Exception) {
            Result.success(ConnectionTestResult(false, "WebDAV 连接失败: ${e.message}", emptyList()))
        }
    }

    private fun buildBackupUrl(config: WebDavConfig): String {
        val url = normalizeUrl(config.url)
        val path = config.backupPath.trimEnd('/')
        return if (url.endsWith("/")) "${url}$path".replace("//", "/").replace("http:/", "http://").replace("https:/", "https://")
        else "$url/$path".replace("//", "/").replace("http:/", "http://").replace("https:/", "https://")
    }

    suspend fun uploadBackup(config: WebDavConfig, data: ByteArray, filename: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val dirUrl = buildBackupUrl(config)
            val fullUrl = "${dirUrl.trimEnd('/')}/$filename"

            android.util.Log.d("WebDavManager", "Uploading ${data.size} bytes to $fullUrl")

            // Create directory first via MKCOL
            createDirectory(config, dirUrl)

            val credential = Credentials.basic(config.username, config.password)
            val request = Request.Builder()
                .url(fullUrl)
                .header("Authorization", credential)
                .header("User-Agent", "YueMing-Android")
                .put(data.toRequestBody("application/zip".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            android.util.Log.d("WebDavManager", "Upload response: ${response.code}")
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
            Result.failure(Exception("上传备份失败: ${e.message}"))
        }
    }

    suspend fun downloadBackup(config: WebDavConfig, filename: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val dirUrl = buildBackupUrl(config)
            val fullUrl = "${dirUrl.trimEnd('/')}/$filename"

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
            val dirUrl = buildBackupUrl(config)
            val files = listDirectoryContents(config, dirUrl)
                .filter { it.endsWith(".zip") }
            Result.success(files)
        } catch (e: Exception) {
            Result.failure(Exception("列出备份失败: ${e.message}"))
        }
    }

    private suspend fun listDirectoryContents(config: WebDavConfig, dirUrl: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest(config, dirUrl, "PROPFIND")
                .header("Depth", "1")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                parsePropfindResponse(body)
            } else emptyList()
        } catch (_: Exception) { emptyList() }
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

    // --- JSON Data Operations ---

    suspend fun readJson(config: WebDavConfig, remotePath: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val fullUrl = buildAbsoluteUrl(config, remotePath)
            val request = buildRequest(config, fullUrl, "GET").build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                Result.success(response.body?.string() ?: "")
            } else if (response.code == 404) {
                Result.failure(NotFoundException("文件不存在: $remotePath"))
            } else {
                Result.failure(Exception("读取失败: HTTP ${response.code}"))
            }
        } catch (e: NotFoundException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(Exception("读取JSON失败: ${e.message}"))
        }
    }

    suspend fun writeJson(config: WebDavConfig, remotePath: String, json: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val fullUrl = buildAbsoluteUrl(config, remotePath)
            // Ensure parent directory exists
            val parentPath = remotePath.substringBeforeLast("/", "")
            if (parentPath.isNotEmpty()) {
                createDirectoryChain(config, parentPath)
            }
            val request = Request.Builder()
                .url(fullUrl)
                .header("Authorization", Credentials.basic(config.username, config.password))
                .header("User-Agent", "YueMing-Android")
                .put(json.toRequestBody("application/json".toMediaType()))
                .build()
            val response = client.newCall(request).execute()
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
            Result.failure(Exception("写入JSON失败: ${e.message}"))
        }
    }

    suspend fun uploadFile(config: WebDavConfig, remotePath: String, data: ByteArray, mimeType: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val fullUrl = buildAbsoluteUrl(config, remotePath)
            val parentPath = remotePath.substringBeforeLast("/", "")
            if (parentPath.isNotEmpty()) {
                createDirectoryChain(config, parentPath)
            }
            val request = Request.Builder()
                .url(fullUrl)
                .header("Authorization", Credentials.basic(config.username, config.password))
                .header("User-Agent", "YueMing-Android")
                .put(data.toRequestBody(mimeType.toMediaType()))
                .build()
            val response = client.newCall(request).execute()
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
            Result.failure(Exception("上传文件失败: ${e.message}"))
        }
    }

    suspend fun createDirectoryChain(config: WebDavConfig, dirPath: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val parts = dirPath.trimEnd('/').split("/").filter { it.isNotEmpty() }
            var current = normalizeUrl(config.url)
            for (part in parts) {
                current = "$current/$part".replace("//", "/").replace("http:/", "http://").replace("https:/", "https://")
                createDirectory(config, current)
            }
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private class NotFoundException(message: String) : Exception(message)

    private fun buildAbsoluteUrl(config: WebDavConfig, remotePath: String): String {
        val url = normalizeUrl(config.url)
        val path = if (remotePath.startsWith("/")) remotePath else "/$remotePath"
        return "$url$path".replace("//", "/").replace("http:/", "http://").replace("https:/", "https://")
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
