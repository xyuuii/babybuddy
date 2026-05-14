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
    val imageRequest = remember(context, model, cloudConfig) {
        ImageRequest.Builder(context)
            .data(model)
            .crossfade(true)
            .apply {
                if (model is String) {
                    webDavAuthHeadersFor(model, cloudConfig).forEach { (name, value) ->
                        addHeader(name, value)
                    }
                }
            }
            .build()
    }

    AsyncImage(
        model = imageRequest,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale
    )
}
