@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.cinch.files

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.uikit.LocalUIViewController
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.dataWithBytes
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.getBytes
import platform.Foundation.writeToURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.darwin.NSObject

/**
 * iOS: sistemin dosya penceresi (`UIDocumentPickerViewController`). Kaydetmede dosya önce uygulamanın geçici
 * klasörüne yazılır, kullanıcı yerini seçince (Dosyalar, iCloud Drive, Google Drive…) oraya kopyalanır.
 */
@Composable
actual fun rememberFileTransfer(onSaved: (Boolean) -> Unit, onOpened: (ByteArray?) -> Unit): FileTransfer {
    val host = LocalUIViewController.current
    val saved by rememberUpdatedState(onSaved)
    val opened by rememberUpdatedState(onOpened)
    return remember(host) { IosFileTransfer(host, onSaved = { saved(it) }, onOpened = { opened(it) }) }
}

private class IosFileTransfer(
    private val host: UIViewController,
    private val onSaved: (Boolean) -> Unit,
    private val onOpened: (ByteArray?) -> Unit,
) : FileTransfer {

    override val isSupported: Boolean = true

    // UIKit delegeleri zayıf tutar; burada güçlü tutulur.
    private val exportDelegate = PickerDelegate(
        onPicked = { onSaved(true) },
        onCancelled = { onSaved(false) },
    )
    private val openDelegate = PickerDelegate(
        onPicked = { url -> onOpened(read(url)) },
        onCancelled = { onOpened(null) },
    )

    override fun save(suggestedName: String, mimeType: String, content: ByteArray) {
        val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + suggestedName)
        if (!content.toNSData().writeToURL(url, atomically = true)) {
            onSaved(false)
            return
        }
        val picker = UIDocumentPickerViewController(forExportingURLs = listOf(url), asCopy = true)
        picker.delegate = exportDelegate
        present(picker)
    }

    override fun open() {
        // Yedek .json; bazı bulut uygulamaları uzantıyı düşürdüğü için her dosya seçilebilir, içerik zaten denetleniyor.
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeJSON, UTTypeData), asCopy = true)
        picker.delegate = openDelegate
        present(picker)
    }

    private fun present(controller: UIViewController) {
        var top = host
        while (true) top = top.presentedViewController ?: break
        top.presentViewController(controller, animated = true, completion = null)
    }

    private fun read(url: NSURL): ByteArray? {
        val scoped = url.startAccessingSecurityScopedResource()
        try {
            return NSData.dataWithContentsOfURL(url)?.toByteArray()
        } finally {
            if (scoped) url.stopAccessingSecurityScopedResource()
        }
    }
}

private class PickerDelegate(
    private val onPicked: (NSURL) -> Unit,
    private val onCancelled: () -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        if (url == null) onCancelled() else onPicked(url)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onCancelled()
    }
}

internal fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.dataWithBytes(it.addressOf(0), size.toULong()) }

internal fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply { usePinned { getBytes(it.addressOf(0), length) } }
}
