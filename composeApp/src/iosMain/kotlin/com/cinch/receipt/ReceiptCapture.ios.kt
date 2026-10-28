package com.cinch.receipt

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * iOS'ta henüz yok: Xcode projesi kurulunca VisionKit tarayıcı (`VNDocumentCameraViewController`), fotoğraf
 * seçici (`PHPickerViewController`) ve Vision metin tanıma (`VNRecognizeTextRequest`) ile yazılacak. O zamana
 * kadar fiş düğmeleri gizlenir.
 */
@Composable
actual fun rememberReceiptCapture(maxImages: Int, onResult: (ReceiptCaptureResult) -> Unit): ReceiptCapture = remember {
    object : ReceiptCapture {
        override val isSupported: Boolean = false
        override fun scan() = Unit
        override fun pickFromGallery() = Unit
    }
}
