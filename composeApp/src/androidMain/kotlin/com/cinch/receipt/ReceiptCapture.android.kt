package com.cinch.receipt

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.cinch.shared.receipt.OcrLayout
import com.cinch.shared.receipt.OcrLine
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Tarama: ML Kit belge tarayıcı (Google Play hizmetleriyle gelir, kamera izni istemez). Galeri: sistem fotoğraf
 * seçicisi (izin istemez). Okuma: uygulamaya gömülü ML Kit metin tanıma, internetsiz çalışır.
 */
@Composable
actual fun rememberReceiptCapture(onResult: (ReceiptCaptureResult) -> Unit): ReceiptCapture {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnResult by rememberUpdatedState(onResult)

    val read: (Uri) -> Unit = remember(context, scope) {
        { uri ->
            currentOnResult(ReceiptCaptureResult.Reading)
            scope.launch {
                val result = try {
                    val text = ReceiptTextReader.read(context, uri)
                    if (text.isBlank()) {
                        ReceiptCaptureResult.Failed("Fotoğrafta yazı bulamadık. Fişi düz ve aydınlık bir yerde tekrar dene.")
                    } else {
                        ReceiptCaptureResult.Read(text)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ReceiptCaptureResult.Failed("Fotoğraf okunamadı. Bir daha dener misin?")
                }
                currentOnResult(result)
            }
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val uri = if (result.resultCode == Activity.RESULT_OK) {
            GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages?.firstOrNull()?.imageUri
        } else {
            null
        }
        if (uri != null) read(uri) else currentOnResult(ReceiptCaptureResult.Cancelled)
    }
    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) read(uri) else currentOnResult(ReceiptCaptureResult.Cancelled)
    }
    val activity = remember(context) { context.findActivity() }

    return remember(activity, scanLauncher, pickLauncher) {
        object : ReceiptCapture {
            override val isSupported: Boolean = true

            override fun scan() {
                val host = activity ?: return currentOnResult(ReceiptCaptureResult.Failed(SCANNER_UNAVAILABLE))
                val options = GmsDocumentScannerOptions.Builder()
                    .setGalleryImportAllowed(true)
                    .setPageLimit(1)
                    .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                    .build()
                GmsDocumentScanning.getClient(options).getStartScanIntent(host)
                    .addOnSuccessListener { sender -> scanLauncher.launch(IntentSenderRequest.Builder(sender).build()) }
                    .addOnFailureListener { currentOnResult(ReceiptCaptureResult.Failed(SCANNER_UNAVAILABLE)) }
            }

            override fun pickFromGallery() {
                pickLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
    }
}

private const val SCANNER_UNAVAILABLE =
    "Tarayıcı açılamadı (Google Play hizmetleri gerekiyor). Fotoğrafı galeriden seçebilirsin."

/** Görüntüdeki yazıyı satır satır okur; sütunlara bölünmüş satırları [OcrLayout] birleştirir. */
private object ReceiptTextReader {

    /** Büyük fotoğraflar belleği taşırmasın diye uzun kenar en fazla bu kadar piksele küçültülür. */
    private const val MAX_SIDE = 3000

    suspend fun read(context: Context, uri: Uri): String {
        val image = withContext(Dispatchers.Default) { loadImage(context, uri) }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text = recognizer.process(image).await()
            val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                OcrLine(line.text, box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.bottom.toFloat())
            }
            return OcrLayout.rows(lines).joinToString("\n")
        } finally {
            recognizer.close()
        }
    }

    private fun loadImage(context: Context, uri: Uri): InputImage =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            // ImageDecoder fotoğrafın yönünü (EXIF) kendisi düzeltir. ML Kit yazılım bitmap'i ister.
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > MAX_SIDE) {
                    val scale = MAX_SIDE.toFloat() / longest
                    decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                }
            }
            InputImage.fromBitmap(bitmap, 0)
        } else {
            // Android 8 ve öncesi: ML Kit dosyayı kendisi açar ve yönünü düzeltir.
            InputImage.fromFilePath(context, uri)
        }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
