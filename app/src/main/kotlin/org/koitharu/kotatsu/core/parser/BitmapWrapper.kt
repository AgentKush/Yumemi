package org.koitharu.kotatsu.core.parser

import android.graphics.Canvas
import android.os.Build
import androidx.annotation.IntRange
import androidx.core.graphics.createBitmap
import org.koitharu.kotatsu.parsers.bitmap.Bitmap
import org.koitharu.kotatsu.parsers.bitmap.Rect
import java.io.OutputStream
import android.graphics.Bitmap as AndroidBitmap
import android.graphics.Rect as AndroidRect

/**
 * Supported compression formats for bitmap output.
 */
enum class BitmapCompressFormat {
	/** Lossless compression, best for graphics with transparency */
	PNG,
	/** Lossy compression, smaller files, good for photos */
	JPEG,
	/** Modern format with better compression, requires API 14+ (lossy) or API 30+ (lossless) */
	WEBP,
	/** Lossless WebP, requires API 30+ */
	WEBP_LOSSLESS,
}

class BitmapWrapper private constructor(
	private val androidBitmap: AndroidBitmap,
) : Bitmap, AutoCloseable {

	private val canvas by lazy { Canvas(androidBitmap) } // is not always used, so initialized lazily

	override val height: Int
		get() = androidBitmap.height

	override val width: Int
		get() = androidBitmap.width

	override fun drawBitmap(sourceBitmap: Bitmap, src: Rect, dst: Rect) {
		val androidSourceBitmap = (sourceBitmap as BitmapWrapper).androidBitmap
		canvas.drawBitmap(androidSourceBitmap, src.toAndroidRect(), dst.toAndroidRect(), null)
	}

	override fun close() {
		androidBitmap.recycle()
	}

	/**
	 * Compress bitmap to output stream with default PNG format.
	 * Use [compressTo] with format parameter for more control.
	 */
	fun compressTo(output: OutputStream) {
		compressTo(output, BitmapCompressFormat.PNG)
	}

	/**
	 * Compress bitmap to output stream with specified format and quality.
	 *
	 * @param output The output stream to write compressed data to
	 * @param format The compression format to use
	 * @param quality Quality hint for lossy formats (0-100). Ignored for PNG and WEBP_LOSSLESS.
	 *                Higher values produce better quality but larger files.
	 */
	fun compressTo(
		output: OutputStream,
		format: BitmapCompressFormat,
		@IntRange(from = 0, to = 100) quality: Int = 90,
	) {
		val androidFormat = when (format) {
			BitmapCompressFormat.PNG -> AndroidBitmap.CompressFormat.PNG
			BitmapCompressFormat.JPEG -> AndroidBitmap.CompressFormat.JPEG
			BitmapCompressFormat.WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
				AndroidBitmap.CompressFormat.WEBP_LOSSY
			} else {
				@Suppress("DEPRECATION")
				AndroidBitmap.CompressFormat.WEBP
			}
			BitmapCompressFormat.WEBP_LOSSLESS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
				AndroidBitmap.CompressFormat.WEBP_LOSSLESS
			} else {
				// Fallback to PNG for lossless on older API
				AndroidBitmap.CompressFormat.PNG
			}
		}
		val effectiveQuality = when (format) {
			BitmapCompressFormat.PNG, BitmapCompressFormat.WEBP_LOSSLESS -> 100 // Quality ignored for lossless
			else -> quality.coerceIn(0, 100)
		}
		androidBitmap.compress(androidFormat, effectiveQuality, output)
	}

	companion object {

		fun create(width: Int, height: Int) = BitmapWrapper(
			createBitmap(width, height, AndroidBitmap.Config.ARGB_8888),
		)

		fun create(bitmap: AndroidBitmap) = BitmapWrapper(
			if (bitmap.isMutable) bitmap else bitmap.copy(AndroidBitmap.Config.ARGB_8888, true),
		)

		private fun Rect.toAndroidRect() = AndroidRect(left, top, right, bottom)
	}
}
