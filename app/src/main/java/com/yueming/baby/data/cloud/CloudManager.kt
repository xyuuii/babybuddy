package com.yueming.baby.data.cloud

import com.yueming.baby.data.WebDavManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

fun CloudStorageConfig.toWebDavConfig(): WebDavManager.WebDavConfig {
    return WebDavManager.WebDavConfig(
        url = buildWebDavBaseUrl(),
        username = username,
        password = password,
        dataPath = webdavPath
    )
}

fun CloudStorageConfig.buildWebDavBaseUrl(): String {
    val rawHost = host.trim().trimEnd('/')
    if (rawHost.startsWith("http://", ignoreCase = true) ||
        rawHost.startsWith("https://", ignoreCase = true)
    ) {
        return rawHost
    }

    val hostWithPort = when {
        rawHost.startsWith("[") -> rawHost
        rawHost.count { it == ':' } == 1 && rawHost.substringAfterLast(':').all { it.isDigit() } -> rawHost
        rawHost.contains(":") -> "[$rawHost]"
        else -> "$rawHost:$port"
    }
    return "http://$hostWithPort"
}

object CloudManager {
    data class UploadResult(
        val success: Boolean,
        val remotePath: String,
        val error: String? = null
    )

    suspend fun uploadFile(localPath: String, remoteName: String, config: CloudStorageConfig): UploadResult = withContext(Dispatchers.IO) {
        val localFile = File(localPath)
        if (!localFile.exists()) {
            return@withContext UploadResult(false, "", "Local file not found: $localPath")
        }
        when (config.protocol) {
            StorageProtocol.WEBDAV -> {
                val wdConfig = config.toWebDavConfig()
                val mimeType = when (localFile.extension.lowercase()) {
                    "mp4" -> "video/mp4"
                    "webm" -> "video/webm"
                    "png" -> "image/png"
                    "jpg", "jpeg" -> "image/jpeg"
                    else -> "application/octet-stream"
                }
                val fullRemotePath = "${config.webdavPath.trimEnd('/')}/media/$remoteName"
                val result = WebDavManager.uploadFile(wdConfig, fullRemotePath, localFile, mimeType)
                result.fold(
                    onSuccess = {
                        UploadResult(true, fullRemotePath)
                    },
                    onFailure = { UploadResult(false, "", it.message) }
                )
            }
            StorageProtocol.SMB -> {
                val result = SmbManager.uploadFile(config, localFile, remoteName)
                result.fold(
                    onSuccess = { path -> UploadResult(true, path) },
                    onFailure = { UploadResult(false, "", it.message) }
                )
            }
            StorageProtocol.FTP -> {
                val result = FtpManager.uploadFile(config, localFile, remoteName)
                result.fold(
                    onSuccess = { path -> UploadResult(true, path) },
                    onFailure = { UploadResult(false, "", it.message) }
                )
            }
        }
    }

    suspend fun downloadFile(remotePath: String, localPath: String, config: CloudStorageConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            val localFile = File(localPath)
            localFile.parentFile?.mkdirs()
            when (config.protocol) {
                StorageProtocol.WEBDAV -> {
                    val wdConfig = config.toWebDavConfig()
                    // Read file content via readJson (it's just a GET request)
                    val result = WebDavManager.readJson(wdConfig, remotePath)
                    result.fold(
                        onSuccess = { content ->
                            localFile.writeBytes(content.toByteArray(Charsets.UTF_8))
                            true
                        },
                        onFailure = { false }
                    )
                }
                StorageProtocol.SMB -> {
                    SmbManager.downloadFile(config, remotePath, localFile).getOrDefault(false)
                }
                StorageProtocol.FTP -> {
                    FtpManager.downloadFile(config, remotePath, localFile).getOrDefault(false)
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    fun getPublicUrl(remotePath: String, config: CloudStorageConfig): String {
        return when (config.protocol) {
            StorageProtocol.WEBDAV -> {
                "${config.buildWebDavBaseUrl().trimEnd('/')}/${remotePath.trimStart('/')}"
            }
            StorageProtocol.SMB, StorageProtocol.FTP -> {
                remotePath
            }
        }
    }

    suspend fun deleteFile(remotePath: String, config: CloudStorageConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            when (config.protocol) {
                StorageProtocol.WEBDAV -> true
                StorageProtocol.SMB -> false
                StorageProtocol.FTP -> false
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun testConnection(config: CloudStorageConfig): Result<Boolean> = withContext(Dispatchers.IO) {
        when (config.protocol) {
            StorageProtocol.WEBDAV -> {
                val wdConfig = config.toWebDavConfig()
                val result = WebDavManager.testConnection(config = wdConfig)
                result.fold(
                    onSuccess = { testResult -> Result.success(testResult.success) },
                    onFailure = { Result.failure(it) }
                )
            }
            StorageProtocol.SMB -> SmbManager.testConnection(config)
            StorageProtocol.FTP -> FtpManager.testConnection(config)
        }
    }
}
