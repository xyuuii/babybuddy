package com.yueming.baby.ui.components

import android.net.Uri
import com.yueming.baby.data.cloud.CloudStorageConfig
import com.yueming.baby.data.cloud.StorageProtocol
import okhttp3.Credentials

internal fun webDavAuthHeadersFor(filePath: String, cloudConfig: CloudStorageConfig): Map<String, String> {
    val uri = Uri.parse(filePath)
    return if (isConfiguredWebDavUrl(uri, cloudConfig)) {
        mapOf("Authorization" to Credentials.basic(cloudConfig.username, cloudConfig.password))
    } else {
        emptyMap()
    }
}

internal fun isConfiguredWebDavUrl(uri: Uri, cloudConfig: CloudStorageConfig): Boolean {
    return cloudConfig.protocol == StorageProtocol.WEBDAV &&
        (uri.scheme == "http" || uri.scheme == "https") &&
        uri.host == cloudConfig.host &&
        (uri.port == cloudConfig.port || uri.port == -1)
}
