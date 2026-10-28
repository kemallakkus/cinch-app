package com.cinch.ui.receipt

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cinch.receipt.ReceiptCapture
import com.cinch.receipt.ReceiptCaptureResult
import com.cinch.receipt.rememberReceiptCapture
import com.cinch.shared.receipt.OcrScan
import kotlinx.coroutines.launch

/** Fiş tarama akışı: tarayıcı/galeri, okunurken bekleme penceresi, hata mesajı. Ana ekran ve "Bu ay" kullanır. */
@Stable
class ReceiptFlow internal constructor(
    private val capture: ReceiptCapture,
    private val reading: MutableState<Boolean>,
) {
    /** Bu platformda yoksa (iOS, şimdilik) fiş düğmeleri gösterilmez. */
    val isSupported: Boolean get() = capture.isSupported
    val isReading: Boolean get() = reading.value

    fun scan() = capture.scan()
    fun pickFromGallery() = capture.pickFromGallery()
}

/**
 * @param maxImages birden çok fotoğraf (banka ekranı); fiş için 1.
 * @param onRead okunanlar; çağıran onay ekranına geçer.
 * @param snackbarHostState hata mesajları burada gösterilir ("Fotoğrafta yazı bulamadık").
 */
@Composable
fun rememberReceiptFlow(
    snackbarHostState: SnackbarHostState,
    maxImages: Int = 1,
    onRead: (OcrScan) -> Unit,
): ReceiptFlow {
    val scope = rememberCoroutineScope()
    val currentOnRead by rememberUpdatedState(onRead)
    val reading = remember { mutableStateOf(false) }
    val capture = rememberReceiptCapture(maxImages) { result ->
        reading.value = result == ReceiptCaptureResult.Reading
        when (result) {
            is ReceiptCaptureResult.Read -> currentOnRead(result.scan)
            is ReceiptCaptureResult.Failed -> scope.launch { snackbarHostState.showSnackbar(result.message) }
            ReceiptCaptureResult.Reading, ReceiptCaptureResult.Cancelled -> Unit
        }
    }
    return remember(capture) { ReceiptFlow(capture, reading) }
}

/** Okuma birkaç saniye sürer; bu sırada kapatılamayan küçük bir bekleme penceresi. */
@Composable
fun ReceiptReadingDialog(flow: ReceiptFlow, title: String = "Fiş okunuyor…") {
    if (!flow.isReading) return
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        title = { Text(title) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator(Modifier.size(28.dp))
                Text("Birkaç saniye sürebilir. Fotoğraf telefonundan çıkmaz.", style = MaterialTheme.typography.bodyMedium)
            }
        },
    )
}
