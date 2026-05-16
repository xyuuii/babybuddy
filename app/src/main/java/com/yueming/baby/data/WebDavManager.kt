package com.yueming.baby.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object WebDavManager {
    data class WebDavConfig(
        val url: String,
        val username: String,
        val password: String,
        val dataPath: String = "/babybuddy/data"
    )

    data class ConnectionTestResult(
        val success: Boolean,
        val message: String = "",
        val directoryContents: List<String> = emptyList()
    )

    data class UploadVerificationResult(
        val exists: Boolean,
        val sizeMatches: Boolean,
        val remoteSize: Long? = null,
        val message: String = ""
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val directoryAttemptCache = ConcurrentHashMap.newKeySet<String>()

    suspend fun testConnection(config: WebDavConfig): Result<ConnectionTestResult> = withContext(Dispatchers.IO) {
        try {
            if (config.url.isBlank()) {
                return@withContext Result.success(ConnectionTestResult(false, "WebDAV 地址不能为空", emptyList()))
            }

            val dataRoot = config.dataPath.trim().trimEnd('/').ifBlank { "/babybuddy" }
            val rootReady = createDirectoryChain(config, dataRoot).getOrDefault(false)
            if (!rootReady) {
                return@withContext Result.success(
                    ConnectionTestResult(false, "无法创建或访问 WebDAV 路径：$dataRoot", emptyList())
                )
            }

            val probeName = ".babybuddy_connection_check_${System.currentTimeMillis()}.json"
            val probePath = "$dataRoot/$probeName"
            val probePayload = """{"ok":true,"probe":"babybuddy"}"""
            val writeOk = writeJson(config, probePath, probePayload).getOrElse { error ->
                return@withContext Result.success(
                    ConnectionTestResult(false, "WebDAV 写入验证失败：${error.message}", emptyList())
                )
            }
            if (!writeOk) {
                return@withContext Result.success(
                    ConnectionTestResult(false, "WebDAV 写入验证失败：服务器未接受测试文件", emptyList())
                )
            }

            val readBack = readJson(config, probePath).getOrElse { error ->
                deleteFile(config, probePath)
                return@withContext Result.success(
                    ConnectionTestResult(false, "WebDAV 读取验证失败：${error.message}", emptyList())
                )
            }
            deleteFile(config, probePath)

            if (readBack != probePayload) {
                return@withContext Result.success(
                    ConnectionTestResult(false, "WebDAV 读取验证失败：测试文件内容不一致", emptyList())
                )
            }

            val directoryContents = listDirectory(config, dataRoot).getOrDefault(emptySet()).toList().sorted()
            return@withContext Result.success(ConnectionTestResult(true, "连接成功，读写验证通过", directoryContents))
        } catch (e: Exception) {
            Result.success(ConnectionTestResult(false, "WebDAV 连接失败: ${e.message}", emptyList()))
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
            url = "http://$url"  // Default to HTTP for LAN/NAS
        }
        return url
    }

    private fun createDirectory(config: WebDavConfig, dirUrl: String): Boolean {
        try {
            if (directoryAttemptCache.contains(dirUrl)) return true
            if (directoryExists(config, dirUrl)) {
                directoryAttemptCache.add(dirUrl)
                return true
            }
            val request = buildRequest(config, dirUrl, "MKCOL").build()
            val response = client.newCall(request).execute()
            val code = response.code
            response.close()
            // 201=Created, 405=Already exists. 403 means permission denied, not a healthy connection.
            val ok = code in listOf(200, 201, 204, 301, 405)
            val existsAfterMkcol = if (ok) true else directoryExists(config, dirUrl)
            if (existsAfterMkcol) directoryAttemptCache.add(dirUrl)
            if (!ok) android.util.Log.w("WebDavManager", "MKCOL $dirUrl → $code")
            return existsAfterMkcol
        } catch (e: Exception) {
            android.util.Log.w("WebDavManager", "MKCOL error: $dirUrl → ${e.message}")
            return false
        }
    }

    private fun directoryExists(config: WebDavConfig, dirUrl: String): Boolean {
        return try {
            val request = buildRequest(config, dirUrl, "PROPFIND")
                .header("Depth", "0")
                .build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            android.util.Log.w("WebDavManager", "PROPFIND directory check failed: $dirUrl → ${e.message}")
            false
        }
    }

    suspend fun listDirectory(config: WebDavConfig, dirPath: String): Result<Set<String>> = withContext(Dispatchers.IO) {
        try {
            val fullUrl = buildAbsoluteUrl(config, dirPath)
            val propfindBody = """
                <D:propfind xmlns:D="DAV:">
                  <D:prop>
                    <D:displayname/>
                    <D:getcontentlength/>
                  </D:prop>
                </D:propfind>
            """.trimIndent()
            val request = buildRequest(config, fullUrl, "PROPFIND")
                .header("Depth", "1")
                .header("Content-Type", "application/xml; charset=utf-8")
                .method("PROPFIND", propfindBody.toRequestBody("application/xml".toMediaType()))
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val fileNames = parsePropfindHrefs(body, dirPath)
                Result.success(fileNames)
            } else {
                android.util.Log.w("WebDavManager", "PROPFIND listing failed: HTTP ${response.code}")
                Result.success(emptySet())
            }
        } catch (e: Exception) {
            android.util.Log.w("WebDavManager", "listDirectory error: ${e.message}")
            Result.success(emptySet())
        }
    }

    private fun parsePropfindHrefs(xmlBody: String, dirPath: String): Set<String> {
        val hrefPattern = Regex("<[^>]*href[^>]*>(.*?)</[^>]*href>", RegexOption.IGNORE_CASE)
        val dirPrefix = normalizeHrefPath(dirPath).trimEnd('/')
        return hrefPattern.findAll(xmlBody).mapNotNull { match ->
            val href = normalizeHrefPath(
                match.groupValues[1]
                    .replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&quot;", "\"")
                    .replace("&apos;", "'")
                    .trim()
            ).trimEnd('/')

            if (href == dirPrefix) return@mapNotNull null

            val prefix = "$dirPrefix/"
            val relativePath = when {
                href.startsWith(prefix, ignoreCase = true) -> href.substring(prefix.length)
                else -> {
                    val embeddedIndex = href.indexOf(prefix, ignoreCase = true)
                    if (embeddedIndex < 0) return@mapNotNull null
                    href.substring(embeddedIndex + prefix.length)
                }
            }.trim('/')

            relativePath.takeIf { it.isNotEmpty() }?.substringBefore('/')
        }.filter { it.isNotEmpty() }.toSet()
    }

    private fun normalizeHrefPath(href: String): String {
        val rawPath = runCatching {
            java.net.URI(href).rawPath.takeIf { !it.isNullOrBlank() } ?: href
        }.getOrDefault(href)
        val decoded = runCatching { java.net.URLDecoder.decode(rawPath, "UTF-8") }.getOrDefault(rawPath)
        return decoded.replace(Regex("/{2,}"), "/").trimStart('/')
    }

    suspend fun fileExists(config: WebDavConfig, remotePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val fullUrl = buildAbsoluteUrl(config, remotePath)
            val request = buildRequest(config, fullUrl, "HEAD").build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
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
            android.util.Log.d("WebDavManager", "writeJson -> $fullUrl, size=${json.length} chars")
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
            android.util.Log.d("WebDavManager", "writeJson response: code=${response.code}, success=${response.isSuccessful}")
            if (!response.isSuccessful) {
                android.util.Log.e("WebDavManager", "writeJson failed: HTTP ${response.code}, body=${response.body?.string()}")
            }
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
            android.util.Log.e("WebDavManager", "writeJson exception: remotePath=$remotePath, error=${e.message}", e)
            Result.failure(Exception("写入JSON失败: ${e.message}"))
        }
    }

    suspend fun uploadFile(config: WebDavConfig, remotePath: String, data: ByteArray, mimeType: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val fullUrl = buildAbsoluteUrl(config, remotePath)
            android.util.Log.d("WebDavManager", "uploadFile -> $fullUrl, size=${data.size} bytes, mime=$mimeType")
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
            android.util.Log.d("WebDavManager", "uploadFile response: code=${response.code}, success=${response.isSuccessful}")
            if (!response.isSuccessful) {
                android.util.Log.e("WebDavManager", "uploadFile failed: HTTP ${response.code}, body=${response.body?.string()}")
            }
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
            android.util.Log.e("WebDavManager", "uploadFile exception: remotePath=$remotePath, error=${e.message}", e)
            Result.failure(Exception("上传文件失败: ${e.message}"))
        }
    }

    suspend fun uploadFile(config: WebDavConfig, remotePath: String, file: File, mimeType: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val fullUrl = buildAbsoluteUrl(config, remotePath)
            android.util.Log.d("WebDavManager", "uploadFile -> $fullUrl, size=${file.length()} bytes, mime=$mimeType")
            val parentPath = remotePath.substringBeforeLast("/", "")
            if (parentPath.isNotEmpty()) {
                createDirectoryChain(config, parentPath)
            }
            val request = Request.Builder()
                .url(fullUrl)
                .header("Authorization", Credentials.basic(config.username, config.password))
                .header("User-Agent", "YueMing-Android")
                .put(file.asRequestBody(mimeType.toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                android.util.Log.d("WebDavManager", "uploadFile response: code=${response.code}, success=${response.isSuccessful}")
                if (!response.isSuccessful) {
                    android.util.Log.e("WebDavManager", "uploadFile failed: HTTP ${response.code}, body=${response.body?.string()}")
                }
                Result.success(response.isSuccessful)
            }
        } catch (e: Exception) {
            android.util.Log.e("WebDavManager", "uploadFile exception: remotePath=$remotePath, error=${e.message}", e)
            Result.failure(Exception("上传文件失败: ${e.message}"))
        }
    }

    suspend fun verifyUploadedFile(
        config: WebDavConfig,
        remotePath: String,
        expectedSize: Long
    ): Result<UploadVerificationResult> = withContext(Dispatchers.IO) {
        try {
            val fullUrl = buildAbsoluteUrl(config, remotePath)
            val headRequest = Request.Builder()
                .url(fullUrl)
                .header("Authorization", Credentials.basic(config.username, config.password))
                .header("User-Agent", "YueMing-Android")
                .head()
                .build()
            client.newCall(headRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val remoteSize = response.header("Content-Length")?.toLongOrNull()
                    return@withContext Result.success(
                        UploadVerificationResult(
                            exists = true,
                            sizeMatches = remoteSize == null || remoteSize == expectedSize,
                            remoteSize = remoteSize,
                            message = if (remoteSize == null) "文件存在" else "远端大小 $remoteSize / 本地大小 $expectedSize"
                        )
                    )
                }
                android.util.Log.w("WebDavManager", "HEAD verify failed: HTTP ${response.code}, fallback to PROPFIND")
            }

            val propfindRequest = buildRequest(config, fullUrl, "PROPFIND")
                .header("Depth", "0")
                .build()
            client.newCall(propfindRequest).execute().use { response ->
                Result.success(
                    UploadVerificationResult(
                        exists = response.isSuccessful,
                        sizeMatches = response.isSuccessful,
                        remoteSize = null,
                        message = "PROPFIND HTTP ${response.code}"
                    )
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("WebDavManager", "verifyUploadedFile exception: remotePath=$remotePath, error=${e.message}", e)
            Result.failure(Exception("上传验证失败: ${e.message}"))
        }
    }

    suspend fun deleteFile(config: WebDavConfig, remotePath: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val fullUrl = buildAbsoluteUrl(config, remotePath)
            val request = buildRequest(config, fullUrl, "DELETE").build()
            client.newCall(request).execute().use { response ->
                val deleted = response.isSuccessful || response.code == 404
                if (!deleted) {
                    android.util.Log.e("WebDavManager", "deleteFile failed: HTTP ${response.code}, path=$remotePath")
                }
                Result.success(deleted)
            }
        } catch (e: Exception) {
            android.util.Log.e("WebDavManager", "deleteFile exception: remotePath=$remotePath, error=${e.message}", e)
            Result.failure(Exception("删除远端文件失败: ${e.message}"))
        }
    }

    suspend fun createDirectoryChain(config: WebDavConfig, dirPath: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val parts = dirPath.trimEnd('/').split("/").filter { it.isNotEmpty() }
            var current = normalizeUrl(config.url).trimEnd('/')
            for (part in parts) {
                current = "$current/$part"
                val ok = createDirectory(config, current)
                if (!ok) return@withContext Result.success(false)
                android.util.Log.d("WebDavManager", "createDirectoryChain: $current → $ok")
            }
            Result.success(true)
        } catch (e: Exception) {
            android.util.Log.e("WebDavManager", "createDirectoryChain failed: ${e.message}")
            Result.failure(e)
        }
    }

    private class NotFoundException(message: String) : Exception(message)

    private fun buildAbsoluteUrl(config: WebDavConfig, remotePath: String): String {
        val base = normalizeUrl(config.url).trimEnd('/')
        val path = if (remotePath.startsWith("/")) remotePath else "/$remotePath"
        return "$base$path"
    }
}
