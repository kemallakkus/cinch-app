package com.cinch.files

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Sistem dosya seçicisi (Storage Access Framework): kaydetmede "Farklı kaydet" penceresi, açmada dosya seçici.
 * Google Drive gibi bulut klasörleri de seçilebilir. Depolama izni gerekmez.
 */
@Composable
actual fun rememberFileTransfer(onSaved: (Boolean) -> Unit, onOpened: (ByteArray?) -> Unit): FileTransfer {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnSaved by rememberUpdatedState(onSaved)
    val currentOnOpened by rememberUpdatedState(onOpened)
    // Kaydedilecek içerik, kullanıcı yeri seçene kadar bekler.
    val pending = remember { arrayOfNulls<ByteArray>(1) }

    fun write(uri: Uri?) {
        val content = pending[0]
        pending[0] = null
        if (uri == null || content == null) {
            currentOnSaved(false)
            return
        }
        scope.launch {
            val ok = try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(content) } != null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            currentOnSaved(ok)
        }
    }

    // Belge türü seçicide dosya adına uzantı ekler; tür başına bir başlatıcı.
    val createJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { write(it) }
    val createCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { write(it) }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            currentOnOpened(null)
        } else {
            scope.launch {
                val bytes = try {
                    withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                currentOnOpened(bytes)
            }
        }
    }

    return remember(createJson, createCsv, openDocument) {
        object : FileTransfer {
            override val isSupported: Boolean = true

            override fun save(suggestedName: String, mimeType: String, content: ByteArray) {
                pending[0] = content
                if (mimeType == "text/csv") createCsv.launch(suggestedName) else createJson.launch(suggestedName)
            }

            override fun open() {
                // Bazı dosya yöneticileri .json'u "application/octet-stream" ya da metin olarak verir.
                openDocument.launch(arrayOf("application/json", "application/octet-stream", "text/plain", "*/*"))
            }
        }
    }
}
