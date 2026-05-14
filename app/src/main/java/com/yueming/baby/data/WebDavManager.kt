package com.yueming.baby.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

object WebDavManager {
    data class WebDavConfig(
        val url: String,
        val username: String,
        val password: String,
        val dataPath: String = "/sata1-15529232180/yueming/data"
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

    suspend fun testConnection(config: WebDavConfig): Result<ConnectionTestResult> = withContext(Dispatchers.IO) {
        try {
            val url = normalizeUrl(config.url)
            val request = buildRequest(config, url, "PROPFIND")
                .header("Depth", "0")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                Result.success(ConnectionTestResult(true, "连接成功"))
            } else {
                Result.success(ConnectionTestResult(false, "服务器返回 ${response.code}", emptyList()))
            }
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
            val request = buildRequest(config, dirUrl, "MKCOL").build()
            val response = client.newCall(request).execute()
            val code = response.code
            response.close()
            // 201=Created, 405=Already exists, 301/403=OK for some servers
            val ok = code in listOf(201, 405, 301, 403, 200)
            if (!ok) android.util.Log.w("WebDavManager", "MKCOL $dirUrl → $code")
            return ok
        } catch (e: Exception) {
            android.util.Log.w("WebDavManager", "MKCOL error: $dirUrl → ${e.message}")
            return false
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

    suspend fun createDirectoryChain(config: WebDavConfig, dirPath: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val parts = dirPath.trimEnd('/').split("/").filter { it.isNotEmpty() }
            var current = normalizeUrl(config.url)
            for (part in parts) {
                current = "$current/$part".replace("//", "/").replace("http:/", "http://").replace("https:/", "https://")
                val ok = createDirectory(config, current)
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
        val url = normalizeUrl(config.url)
        val path = if (remotePath.startsWith("/")) remotePath else "/$remotePath"
        return "$url$path".replace("//", "/").replace("http:/", "http://").replace("https:/", "https://")
    }
}
