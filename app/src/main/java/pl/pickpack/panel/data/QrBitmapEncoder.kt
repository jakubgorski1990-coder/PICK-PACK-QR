package pl.pickpack.panel.data

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Local clean QR generation. Quiet zone ≥ 4 modules; no logo / decoration.
 */
object QrBitmapEncoder {
    fun encode(
        payload: String,
        sizePx: Int = 1024,
        quietZoneModules: Int = 4,
    ): Bitmap? {
        if (payload.isBlank() || payload == "IMAGE_FALLBACK") return null
        return try {
            val hints = mapOf(
                EncodeHintType.MARGIN to quietZoneModules,
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.CHARACTER_SET to "UTF-8",
            )
            val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bmp
        } catch (_: Exception) {
            null
        }
    }
}
