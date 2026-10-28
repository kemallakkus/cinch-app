package com.cinch.files

import androidx.compose.runtime.Composable

/**
 * Kullanıcının seçtiği yere dosya yazar ya da seçtiği dosyayı okur (yedek, CSV). Yer sistemin dosya seçicisinden
 * seçilir: telefon, Google Drive, indirilenler… Uygulama izin istemez, yalnız seçilen dosyaya erişir.
 */
interface FileTransfer {
    /** Bu platformda var mı (iOS'ta henüz yok). */
    val isSupported: Boolean

    /** @param mimeType "application/json" ya da "text/csv". */
    fun save(suggestedName: String, mimeType: String, content: ByteArray)

    fun open()
}

/**
 * @param onSaved kaydedildi mi (kullanıcı vazgeçtiyse ya da yazılamadıysa false).
 * @param onOpened seçilen dosyanın içeriği; vazgeçildiyse ya da okunamadıysa null.
 */
@Composable
expect fun rememberFileTransfer(onSaved: (Boolean) -> Unit, onOpened: (ByteArray?) -> Unit): FileTransfer
