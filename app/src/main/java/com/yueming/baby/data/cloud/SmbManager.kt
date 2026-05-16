package com.yueming.baby.data.cloud

import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import jcifs.smb.SmbFileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Properties

object SmbManager {
    private fun createContext(config: CloudStorageConfig): CIFSContext {
        val props = Properties()
        props.setProperty("jcifs.smb.client.minVersion", "SMB202")
        props.setProperty("jcifs.smb.client.maxVersion", "SMB311")
        if (config.smbDomain.isNotEmpty()) {
            props.setProperty("jcifs.smb.client.domain", config.smbDomain)
        }
        val propConfig = PropertyConfiguration(props)
        val baseCtx = BaseContext(propConfig)
        return baseCtx.withCredentials(
            NtlmPasswordAuthenticator(config.username, config.password)
        )
    }

    private fun buildSmbUrl(config: CloudStorageConfig, path: String): String {
        val share = config.smbShare
        val smbPath = if (path.startsWith("/")) path else "/$path"
        return "smb://${config.host}/${share}${smbPath}"
    }

    private fun buildSmbUrlForFile(config: CloudStorageConfig, remoteName: String): String {
        val basePath = config.webdavPath.trimEnd('/')
        val fullPath = "$basePath/$remoteName"
        return buildSmbUrl(config, fullPath)
    }

    suspend fun uploadFile(config: CloudStorageConfig, localFile: File, remoteName: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val ctx = createContext(config)
            val smbUrl = buildSmbUrlForFile(config, remoteName)
            val smbFile = SmbFile(smbUrl, ctx)

            // Ensure parent directory exists
            val parent = smbFile.parent
            if (parent != null) {
                try { SmbFile(parent, ctx).mkdir() } catch (_: Exception) {}
            }

            localFile.inputStream().use { input ->
                SmbFileOutputStream(smbFile).use { output ->
                    input.copyTo(output)
                }
            }
            Result.success(smbUrl)
        } catch (e: Exception) {
            Result.failure(Exception("SMB upload failed: ${e.message}"))
        }
    }

    suspend fun downloadFile(config: CloudStorageConfig, remotePath: String, localFile: File): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val ctx = createContext(config)
            val smbFile = SmbFile(remotePath, ctx)
            smbFile.inputStream.use { input ->
                localFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(Exception("SMB download failed: ${e.message}"))
        }
    }

    suspend fun testConnection(config: CloudStorageConfig): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val ctx = createContext(config)
            val smbUrl = buildSmbUrl(config, config.webdavPath)
            val smbFile = SmbFile(smbUrl, ctx)
            val exists = smbFile.exists()
            Result.success(exists)
        } catch (e: Exception) {
            Result.failure(Exception("SMB connection failed: ${e.message}"))
        }
    }
}
