package com.yueming.baby.data.cloud

enum class StorageProtocol { WEBDAV, SMB, FTP }

data class CloudStorageConfig(
    val protocol: StorageProtocol = StorageProtocol.WEBDAV,
    val host: String = "192.168.0.28",
    val port: Int = 5005,
    val username: String = "15529232180",
    val password: String = "Xy3366422957",
    val webdavPath: String = "/sata1-15529232180/yueming/",
    val smbShare: String = "sata1-15529232180",
    val smbDomain: String = "",
    val ftpPath: String = "/sata1-15529232180/yueming/"
)

