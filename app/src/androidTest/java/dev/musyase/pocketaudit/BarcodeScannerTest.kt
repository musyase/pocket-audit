package dev.musyase.pocketaudit

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BarcodeScannerTest {
    @Test fun bundledScannerReadsSampleCodeWithoutNetwork() {
        val matrix = QRCodeWriter().encode("LAB-101", BarcodeFormat.QR_CODE, 320, 320)
        val bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        for (y in 0 until 320) for (x in 0 until 320) {
            bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        }
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        )
        try {
            val codes = Tasks.await(scanner.process(InputImage.fromBitmap(bitmap, 0)), 15, TimeUnit.SECONDS)
            assertEquals("LAB-101", codes.single().rawValue)
        } finally {
            scanner.close()
            bitmap.recycle()
        }
    }
}
