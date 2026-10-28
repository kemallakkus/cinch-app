package com.cinch.receipt

import androidx.compose.runtime.Composable
import com.cinch.shared.receipt.OcrScan

sealed interface ReceiptCaptureResult {
    /** Fotoğraf(lar) alındı, üstündeki yazı okunuyor (birkaç saniye). */
    data object Reading : ReceiptCaptureResult

    /** Okunan satırlar, konumlarıyla; fiş için [OcrScan.text], ekstre için konumlar da kullanılır. */
    data class Read(val scan: OcrScan) : ReceiptCaptureResult

    data object Cancelled : ReceiptCaptureResult

    /** Kullanıcıya gösterilecek kısa açıklama. */
    data class Failed(val message: String) : ReceiptCaptureResult
}

/**
 * Fiş, fatura ya da banka ekranı fotoğrafı alır ve üstündeki yazıyı telefonda okur; fotoğraf hiçbir yere
 * gönderilmez, okunduktan sonra saklanmaz.
 */
interface ReceiptCapture {
    /** Bu platformda var mı (iOS'ta henüz yok: Xcode projesi kurulunca Vision ile gelecek). */
    val isSupported: Boolean

    /**
     * Belge tarayıcı: kenarları bulur, kırpar, düzeltir; tarayıcının içinden galeriden de seçilebilir. Banka
     * uygulaması başka telefondaysa onun ekranı bu telefonla taranabilir.
     */
    fun scan()

    /** Sistem fotoğraf seçicisi (izin istemez). Başka telefondan gönderilen ekran fotoğrafları buradan seçilir. */
    fun pickFromGallery()
}

/**
 * Sonuçlar [onResult]'a sırayla gelir: [ReceiptCaptureResult.Reading], ardından okunan yazı ya da hata.
 *
 * @param maxImages birden çok fotoğraf: banka ekranı tek kareye sığmaz (özet, "daha fazla", hareketler).
 */
@Composable
expect fun rememberReceiptCapture(maxImages: Int = 1, onResult: (ReceiptCaptureResult) -> Unit): ReceiptCapture
