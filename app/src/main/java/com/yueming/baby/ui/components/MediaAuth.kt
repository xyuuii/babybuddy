package com.yueming.baby.ui.components

import android.net.Uri
import com.yueming.baby.data.WebDavManager
import com.yueming.baby.data.cloud.CloudStorageConfig
import com.yueming.baby.data.cloud.StorageProtocol
import com.yueming.baby.data.cloud.toWebDavConfig
import okhttp3.Credentials

internal fun webDavAuthHeadersFor(
    filePath: String,
    cloudConfig: CloudStorageConfig,
    legacyWebDavConfig: WebDavManager.WebDavConfig? = null
): Map<String, String> {
    val uri = Uri.parse(filePath)
    val config = effectiveWebDavConfig(cloudConfig, legacyWebDavConfig)
    return if (isConfiguredWebDavUrl(uri, config)) {
        mapOf("Authorization" to Credentials.basic(config.username, config.password))
    } else {
        emptyMap()
    }
}

internal fun isConfiguredWebDavUrl(
    uri: Uri,
    cloudConfig: CloudStorageConfig,
    legacyWebDavConfig: WebDavManager.WebDavConfig? = null
): Boolean {
    return isConfiguredWebDavUrl(uri, effectiveWebDavConfig(cloudConfig, legacyWebDavConfig))
}

private fun effectiveWebDavConfig(
    cloudConfig: CloudStorageConfig,
    legacyWebDavConfig: WebDavManager.WebDavConfig?
): WebDavManager.WebDavConfig {
    return legacyWebDavConfig?.takeIf { it.url.isNotBlank() }
        ?: if (cloudConfig.protocol == StorageProtocol.WEBDAV && cloudConfig.host.isNotBlank()) {
            cloudConfig.toWebDavConfig()
        } else {
            WebDavManager.WebDavConfig(url = "", username = "", password = "")
        }
}

private fun isConfiguredWebDavUrl(uri: Uri, config: WebDavManager.WebDavConfig): Boolean {
    if (config.url.isBlank()) return false
    val baseUri = Uri.parse(normalizeWebDavUrl(config.url))
    val uriPort = explicitOrDefaultPort(uri)
    val basePort = explicitOrDefaultPort(baseUri)
    return (uri.scheme == "http" || uri.scheme == "https") &&
        uri.host.equals(baseUri.host, ignoreCase = true) &&
        uriPort == basePort
}

private fun normalizeWebDavUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim().trimEnd('/')
    return if (trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
    ) {
        trimmed
    } else {
        "http://$trimmed"
    }
}

private fun explicitOrDefaultPort(uri: Uri): Int {
    if (uri.port != -1) return uri.port
    return when (uri.scheme?.lowercase()) {
        "https" -> 443
        "http" -> 80
        else -> -1
    }
}
