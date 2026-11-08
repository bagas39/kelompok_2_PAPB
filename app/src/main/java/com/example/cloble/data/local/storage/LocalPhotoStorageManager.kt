package com.example.cloble.data.local.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class LocalPhotoStorageManager(private val context: Context) {

    private val photosDir: File
        get() {
            val dir = File(context.filesDir, "clothing_photos")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    suspend fun savePhotoFromUri(uri: Uri): String = withContext(Dispatchers.IO) {
        val targetFile = File(photosDir, "img_${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val bitmap = BitmapFactory.decodeStream(inputStream)
            if (bitmap != null) {
                val scaledBitmap = scaleBitmapDown(bitmap, 1280)
                FileOutputStream(targetFile).use { out ->
                    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                if (scaledBitmap != bitmap) {
                    scaledBitmap.recycle()
                }
                bitmap.recycle()
            } else {

                context.contentResolver.openInputStream(uri)?.use { rawIn ->
                    FileOutputStream(targetFile).use { rawOut ->
                        rawIn.copyTo(rawOut)
                    }
                }
            }
        } ?: throw IllegalArgumentException("Could not open input stream from uri: $uri")

        targetFile.absolutePath
    }

    suspend fun saveBitmap(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val targetFile = File(photosDir, "img_${UUID.randomUUID()}.jpg")
        val scaledBitmap = scaleBitmapDown(bitmap, 1280)
        FileOutputStream(targetFile).use { out ->
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle()
        }
        targetFile.absolutePath
    }

    fun deletePhoto(path: String): Boolean {
        return try {
            val file = File(path)
            if (file.exists() && file.parentFile?.absolutePath == photosDir.absolutePath) {
                file.delete()
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun scaleBitmapDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }
        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (width > height) {
            newWidth = maxDimension
            newHeight = (maxDimension / ratio).toInt()
        } else {
            newHeight = maxDimension
            newWidth = (maxDimension * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
