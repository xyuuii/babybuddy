package com.yueming.baby.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.yueming.baby.data.DataManager

@Composable
fun AuthenticatedAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    val context = LocalContext.current
    val cloudConfig by DataManager.cloudStorageConfig.collectAsState()
    val legacyWebDavConfig by DataManager.webDavConfig.collectAsState()
    val imageLoader = remember { DataManager.CoilImageLoaderHolder.instance }

    val imageRequest = remember(model, cloudConfig, legacyWebDavConfig) {
        ImageRequest.Builder(context)
            .data(model)
            .crossfade(200)
            .apply {
                if (model is String) {
                    webDavAuthHeadersFor(model, cloudConfig, legacyWebDavConfig).forEach { (name, value) ->
                        addHeader(name, value)
                    }
                }
            }
            .build()
    }

    if (imageLoader != null) {
        AsyncImage(
            model = imageRequest,
            imageLoader = imageLoader,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        AsyncImage(
            model = imageRequest,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    }
}
