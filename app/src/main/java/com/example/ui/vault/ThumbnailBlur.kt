package com.example.ui.vault

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlin.math.max
import kotlin.math.roundToInt

/** Small CPU preview blur, including Android versions before RenderEffect (API 31). */
internal fun blurThumbnail(bytes: ByteArray, level: Float): Bitmap? {
    val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val scale = 120f / max(original.width, original.height)
    val small = Bitmap.createScaledBitmap(original,
        max(1, (original.width * scale).roundToInt()),
        max(1, (original.height * scale).roundToInt()), true)
    if (small !== original) original.recycle()
    val width = small.width
    val height = small.height
    var pixels = IntArray(width * height)
    small.getPixels(pixels, 0, width, 0, 0, width, height)
    val radius = (level.coerceIn(6f, 36f) / 3f).roundToInt()
    repeat(3) {
        for (horizontal in listOf(true, false)) {
            val result = IntArray(pixels.size)
            for (y in 0 until height) for (x in 0 until width) {
                var red = 0; var green = 0; var blue = 0
                for (offset in -radius..radius) {
                    val px = if (horizontal) (x + offset).coerceIn(0, width - 1) else x
                    val py = if (horizontal) y else (y + offset).coerceIn(0, height - 1)
                    val color = pixels[py * width + px]
                    red += (color ushr 16) and 255
                    green += (color ushr 8) and 255
                    blue += color and 255
                }
                val count = radius * 2 + 1
                result[y * width + x] = (255 shl 24) or ((red / count) shl 16) or
                    ((green / count) shl 8) or (blue / count)
            }
            pixels = result
        }
    }
    val result = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    small.recycle()
    return result
}
