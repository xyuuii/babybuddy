package com.yueming.baby.data.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import java.io.File

object FtpManager {
    private fun connect(config: CloudStorageConfig): FTPClient {
        val ftp = FTPClient()
        ftp.connectTimeout = 15000
        ftp.connect(config.host, config.port)
        ftp.login(config.username, config.password)
        ftp.enterLocalPassiveMode()
        ftp.setFileType(FTP.BINARY_FILE_TYPE)
        return ftp
    }

    private fun buildRemotePath(config: CloudStorageConfig, remoteName: String): String {
        val basePath = config.ftpPath.trimEnd('/')
        return "$basePath/$remoteName"
    }

    private fun ensureDirectories(ftp: FTPClient, path: String) {
        val parts = path.trimEnd('/').split("/").filter { it.isNotEmpty() }
        var current = ""
        for (part in parts) {
            current += "/$part"
            try {
                ftp.changeWorkingDirectory(current)
            } catch (_: Exception) {
                ftp.makeDirectory(current)
            }
        }
    }

    suspend fun uploadFile(config: CloudStorageConfig, localFile: File, remoteName: String): Result<String> = withContext(Dispatchers.IO) {
        val ftp = try {
            connect(config)
        } catch (e: Exception) {
            return@withContext Result.failure(Exception("FTP connection failed: ${e.message}"))
        }
        try {
            val remotePath = buildRemotePath(config, remoteName)
            val parentPath = remotePath.substringBeforeLast("/")
            ensureDirectories(ftp, parentPath)

            localFile.inputStream().use { input ->
                if (!ftp.storeFile(remotePath, input)) {
                    return@withContext Result.failure(Exception("FTP upload failed: ${ftp.replyString}"))
                }
            }
            Result.success(remotePath)
        } catch (e: Exception) {
            Result.failure(Exception("FTP upload failed: ${e.message}"))
        } finally {
            try { ftp.logout() } catch (_: Exception) {}
            try { ftp.disconnect() } catch (_: Exception) {}
        }
    }

    suspend fun downloadFile(config: CloudStorageConfig, remotePath: String, localFile: File): Result<Boolean> = withContext(Dispatchers.IO) {
        val ftp = try {
            connect(config)
        } catch (e: Exception) {
            return@withContext Result.failure(Exception("FTP connection failed: ${e.message}"))
        }
        try {
            localFile.outputStream().use { output ->
                if (!ftp.retrieveFile(remotePath, output)) {
                    return@withContext Result.failure(Exception("FTP download failed: ${ftp.replyString}"))
                }
            }
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(Exception("FTP download failed: ${e.message}"))
        } finally {
            try { ftp.logout() } catch (_: Exception) {}
            try { ftp.disconnect() } catch (_: Exception) {}
        }
    }

    suspend fun testConnection(config: CloudStorageConfig): Result<Boolean> = withContext(Dispatchers.IO) {
        val ftp = try {
            connect(config)
        } catch (e: Exception) {
            return@withContext Result.failure(Exception("FTP connection failed: ${e.message}"))
        }
        try {
            Result.success(ftp.isConnected)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { ftp.logout() } catch (_: Exception) {}
            try { ftp.disconnect() } catch (_: Exception) {}
        }
    }
}
