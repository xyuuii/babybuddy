package com.yueming.baby.data.cloud

enum class StorageProtocol { WEBDAV, SMB, FTP }

data class CloudStorageConfig(
    val protocol: StorageProtocol = StorageProtocol.WEBDAV,
    val host: String = "",
    val port: Int = 5005,
    val username: String = "",
    val password: String = "",
    val webdavPath: String = "/babybuddy/",
    val smbShare: String = "",
    val smbDomain: String = "",
    val ftpPath: String = "/babybuddy/"
)
