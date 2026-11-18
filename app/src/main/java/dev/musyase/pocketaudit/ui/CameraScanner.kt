package dev.musyase.pocketaudit.ui

import android.annotation.SuppressLint
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@SuppressLint("UnsafeOptInUsageError")
@Composable
fun CameraScanner(onCode: (String) -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val consumed = remember { AtomicBoolean(false) }
    val disposed = remember { AtomicBoolean(false) }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE, Barcode.FORMAT_CODE_128, Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8)
                .build()
        )
    }

    DisposableEffect(owner) {
        disposed.set(false)
        val future = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            try {
                if (disposed.get()) return@Runnable
                val provider = future.get()
                if (disposed.get()) return@Runnable
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { proxy ->
                    val image = proxy.image
                    if (image == null || consumed.get()) {
                        proxy.close()
                    } else {
                        scanner.process(InputImage.fromMediaImage(image, proxy.imageInfo.rotationDegrees))
                            .addOnSuccessListener { results ->
                                val code = results.firstNotNullOfOrNull { it.rawValue?.trim()?.takeIf(String::isNotEmpty) }
                                if (code != null && consumed.compareAndSet(false, true)) onCode(code)
                            }
                            .addOnCompleteListener { proxy.close() }
                    }
                }
                provider.unbindAll()
                provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (_: Exception) {
                onError("Camera preview is unavailable. Enter the code below.")
            }
        }
        future.addListener(listener, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed.set(true)
            if (future.isDone) runCatching { future.get().unbindAll() }
            scanner.close()
            executor.shutdown()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxWidth().height(290.dp).clip(RoundedCornerShape(18.dp)),
    )
}
