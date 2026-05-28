package com.cloth.wardrobe.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.roundToInt

/**
 * 与 uni-app `utils/image.js` 一致：
 * - 宽最大 800px（等比缩放）
 * - JPEG 质量约 82%，单张约 400KB 内
 * - 超限则逐步降质量（步长 6%），仍超限则缩小宽度（步长 80px，最低 480px）
 */
object ImageCompressor {
    const val MAX_IMAGE_BYTES = 400 * 1024
    const val COMPRESS_MAX_WIDTH = 800
    const val COMPRESS_QUALITY = 0.82f
    private const val MIN_QUALITY = 0.5f
    private const val QUALITY_STEP = 0.06f
    private const val MIN_WIDTH = 480
    private const val WIDTH_STEP = 80

    /** 从相册/相机 Uri 压缩并写入目标 jpg 文件 */
    fun compressUriToJpegFile(context: Context, uri: Uri, dest: File) {
        val (local, deleteAfter) = materializeUri(context, uri)
        try {
            val source = decodeScaledBitmap(local, COMPRESS_MAX_WIDTH)
                ?: throw IllegalArgumentException("无法读取图片，请换一张或重试")
            try {
                writeCompressedJpeg(source, dest, COMPRESS_MAX_WIDTH, COMPRESS_QUALITY)
            } finally {
                if (!source.isRecycled) source.recycle()
            }
        } finally {
            if (deleteAfter) local.delete()
        }
    }

    /**
     * 相册 content:// 往往只能读一次；先落到本地临时文件再解码。
     * @return 本地文件与是否需在解码后删除
     */
    private fun materializeUri(context: Context, uri: Uri): Pair<File, Boolean> {
        if (uri.scheme == "file") {
            val f = File(uri.path.orEmpty())
            if (f.isFile && f.length() > 0L) return f to false
        }
        val temp = File(context.cacheDir, "decode_${System.currentTimeMillis()}")
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("无法打开图片")
        input.use { src ->
            temp.outputStream().use { dst -> src.copyTo(dst) }
        }
        if (temp.length() <= 0L) {
            temp.delete()
            throw IOException("图片为空")
        }
        return temp to true
    }

    private fun decodeScaledBitmap(local: File, maxWidth: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            decodeWithImageDecoder(local, maxWidth)?.let { return it }
        }
        return decodeWithBitmapFactory(local, maxWidth)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeWithImageDecoder(file: File, maxWidth: Int): Bitmap? {
        return try {
            val source = ImageDecoder.createSource(file)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                if (w > maxWidth && w > 0) {
                    val scale = maxWidth.toFloat() / w
                    decoder.setTargetSize(
                        maxWidth,
                        (h * scale).roundToInt().coerceAtLeast(1)
                    )
                }
                decoder.isMutableRequired = false
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeWithBitmapFactory(file: File, maxWidth: Int): Bitmap? {
        val path = file.absolutePath
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxWidth)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val rough = BitmapFactory.decodeFile(path, opts) ?: return null
        return scaleToMaxWidth(rough, maxWidth)
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxWidth: Int): Int {
        var inSampleSize = 1
        if (width > maxWidth) {
            var half = width / 2
            while (half > maxWidth) {
                inSampleSize *= 2
                half /= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun scaleToMaxWidth(bitmap: Bitmap, maxWidth: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxWidth) return bitmap
        val newH = (h * maxWidth.toFloat() / w).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, maxWidth, newH, true)
        if (scaled != bitmap) bitmap.recycle()
        return scaled
    }

    private fun scaleBitmapToWidth(bitmap: Bitmap, maxWidth: Int): Bitmap =
        scaleToMaxWidth(bitmap, maxWidth)

    private fun writeCompressedJpeg(
        source: Bitmap,
        dest: File,
        initialMaxWidth: Int,
        startQuality: Float
    ) {
        var bitmap = source
        var maxWidth = initialMaxWidth.coerceAtMost(bitmap.width)
        var quality = startQuality

        while (true) {
            val bytes = jpegBytes(bitmap, quality)
            if (bytes.size <= MAX_IMAGE_BYTES) {
                dest.parentFile?.mkdirs()
                FileOutputStream(dest).use { it.write(bytes) }
                if (bitmap !== source && !bitmap.isRecycled) bitmap.recycle()
                return
            }
            if (quality > MIN_QUALITY + 0.001f) {
                quality = (quality - QUALITY_STEP).coerceAtLeast(MIN_QUALITY)
                continue
            }
            if (maxWidth > MIN_WIDTH) {
                maxWidth -= WIDTH_STEP
                val next = scaleBitmapToWidth(bitmap, maxWidth)
                if (next !== bitmap && !bitmap.isRecycled) bitmap.recycle()
                bitmap = next
                quality = COMPRESS_QUALITY
                continue
            }
            if (bitmap !== source && !bitmap.isRecycled) bitmap.recycle()
            throw IllegalArgumentException("图片过大，请换一张较小的照片")
        }
    }

    private fun jpegBytes(bitmap: Bitmap, quality: Float): ByteArray {
        val q = (quality * 100).roundToInt().coerceIn(1, 100)
        val stream = ByteArrayOutputStream()
        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, q, stream)) {
            throw IllegalStateException("图片压缩失败")
        }
        return stream.toByteArray()
    }
}
