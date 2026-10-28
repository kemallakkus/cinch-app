package com.cinch.receipt

import androidx.compose.runtime.Composable

sealed interface ReceiptCaptureResult {
    /** Fotoğraf alındı, üstündeki yazı okunuyor (birkaç saniye). */
    data object Reading : ReceiptCaptureResult

    /** Okunan yazı, görüntüdeki satır düzeninde (bkz. `OcrLayout`). */
    data class Read(val text: String) : ReceiptCaptureResult

    data object Cancelled : ReceiptCaptureResult

    /** Kullanıcıya gösterilecek kısa açıklama. */
    data class Failed(val message: String) : ReceiptCaptureResult
}

/**
 * Fiş ya da fatura fotoğrafı alır ve üstündeki yazıyı telefonda okur; fotoğraf hiçbir yere gönderilmez,
 * okunduktan sonra saklanmaz.
 */
interface ReceiptCapture {
    /** Bu platformda var mı (iOS'ta henüz yok: Xcode projesi kurulunca Vision ile gelecek). */
    val isSupported: Boolean

    /** Belge tarayıcı: kenarları bulur, kırpar, düzeltir; tarayıcının içinden galeriden de seçilebilir. */
    fun scan()

    /** Sistem fotoğraf seçicisi (izin istemez); tarayıcı açılamayan telefonlar için de yol. */
    fun pickFromGallery()
}

/** Sonuçlar [onResult]'a sırayla gelir: [ReceiptCaptureResult.Reading], ardından okunan yazı ya da hata. */
@Composable
expect fun rememberReceiptCapture(onResult: (ReceiptCaptureResult) -> Unit): ReceiptCapture
