package com.cinch.files

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * iOS'ta henüz yok: Xcode projesi kurulunca `UIDocumentPickerViewController` ile yazılacak. O zamana kadar Ayarlar'daki
 * yedek düğmeleri gizlenir.
 */
@Composable
actual fun rememberFileTransfer(onSaved: (Boolean) -> Unit, onOpened: (ByteArray?) -> Unit): FileTransfer = remember {
    object : FileTransfer {
        override val isSupported: Boolean = false
        override fun save(suggestedName: String, mimeType: String, content: ByteArray) = Unit
        override fun open() = Unit
    }
}
