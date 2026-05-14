package com.yueming.baby.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

/**
 * Generates and caches local thumbnails for grid display.
 * Full-resolution media is fetched from WebDAV only when viewing.
 * Google Photos best practice: keep only thumbnails locally.
 */
object ThumbnailManager {
    private const val THUMB_MAX_SIZE = 400
    private const val JPEG_QUALITY = 80

    fun generateThumbnail(context: Context, sourceUri: Uri, isVideo: Boolean = false): String? {
        return try {
            val thumbDir = File(context.cacheDir, "thumbnails")
            if (!thumbDir.exists()) thumbDir.mkdirs()

            val thumbFile = File(thumbDir, "thumb_${sourceUri.hashCode().toUInt()}.jpg")
            if (thumbFile.exists()) return thumbFile.absolutePath

            val bitmap: Bitmap? = if (isVideo) {
                generateVideoThumbnail(sourceUri.toString())
            } else {
                generateImageThumbnail(context, sourceUri)
            }

            bitmap?.let { bmp ->
                val scaled = ThumbnailUtils.extractThumbnail(bmp, THUMB_MAX_SIZE, THUMB_MAX_SIZE,
                    ThumbnailUtils.OPTIONS_RECYCLE_INPUT)
                FileOutputStream(thumbFile).use { out ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }
                scaled.recycle()
                if (bmp != scaled) bmp.recycle()
                thumbFile.absolutePath
            }
        } catch (e: Exception) {
            android.util.Log.e("ThumbnailManager", "Failed to generate thumbnail", e)
            null
        }
    }

    private fun generateImageThumbnail(context: Context, uri: Uri): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }

            opts.inSampleSize = calculateInSampleSize(opts.outWidth, opts.outHeight, THUMB_MAX_SIZE)
            opts.inJustDecodeBounds = false

            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }
        } catch (e: Exception) {
            // Fallback: try ContentResolver.loadThumbnail (API 29+)
            try {
                val size = android.util.Size(THUMB_MAX_SIZE, THUMB_MAX_SIZE)
                context.contentResolver.loadThumbnail(uri, size, null)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun generateVideoThumbnail(path: String): Bitmap? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(path)
            val bitmap = retriever.frameAtTime
            retriever.release()
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqSize: Int): Int {
        var size = 1
        if (height > reqSize || width > reqSize) {
            val halfH = height / 2
            val halfW = width / 2
            while (halfH / size >= reqSize && halfW / size >= reqSize) {
                size *= 2
            }
        }
        return size
    }

    fun deleteThumbnail(thumbnailPath: String?) {
        if (thumbnailPath != null) {
            try { File(thumbnailPath).delete() } catch (_: Exception) {}
        }
    }
}
