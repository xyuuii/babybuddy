package com.yueming.baby.data.cloud

import com.yueming.baby.data.WebDavManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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
                val wdConfig = WebDavManager.WebDavConfig(
                    url = "${config.host}:${config.port}",
                    username = config.username,
                    password = config.password,
                    backupPath = config.webdavPath
                )
                val data = localFile.readBytes()
                val result = WebDavManager.uploadBackup(wdConfig, data, remoteName)
                result.fold(
                    onSuccess = {
                        val remotePath = "${wdConfig.url}${config.webdavPath.trimEnd('/')}/$remoteName"
                        UploadResult(true, remotePath)
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
                    val wdConfig = WebDavManager.WebDavConfig(
                        url = "${config.host}:${config.port}",
                        username = config.username,
                        password = config.password,
                        backupPath = config.webdavPath
                    )
                    val result = WebDavManager.downloadBackup(wdConfig, remotePath)
                    result.fold(
                        onSuccess = { bytes ->
                            localFile.writeBytes(bytes)
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
                val scheme = "http"
                "$scheme://${config.host}:${config.port}$remotePath"
            }
            StorageProtocol.SMB, StorageProtocol.FTP -> {
                remotePath
            }
        }
    }

    suspend fun deleteFile(remotePath: String, config: CloudStorageConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            when (config.protocol) {
                StorageProtocol.WEBDAV -> {
                    // WebDAV DELETE not implemented in current WebDavManager, skip
                    true
                }
                StorageProtocol.SMB -> {
                    false
                }
                StorageProtocol.FTP -> {
                    false
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun testConnection(config: CloudStorageConfig): Result<Boolean> = withContext(Dispatchers.IO) {
        when (config.protocol) {
            StorageProtocol.WEBDAV -> {
                val wdConfig = WebDavManager.WebDavConfig(
                    url = "${config.host}:${config.port}",
                    username = config.username,
                    password = config.password,
                    backupPath = config.webdavPath
                )
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
