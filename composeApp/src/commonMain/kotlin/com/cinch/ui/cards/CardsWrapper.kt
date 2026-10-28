package com.cinch.ui.cards

import androidx.compose.runtime.Composable
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.cinch.shared.receipt.OcrCodec
import com.cinch.ui.receipt.ReceiptReadingDialog
import com.cinch.ui.receipt.rememberReceiptFlow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/** @param onStatementRead banka ekranı fotoğraflarından okunanlar (`OcrCodec` ile); onay ekranına geçilir. */
@Composable
fun CardsWrapper(
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    onStatementRead: (String) -> Unit,
    viewModel: CardsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val reader = rememberReceiptFlow(snackbarHostState, maxImages = STATEMENT_PHOTOS) { scan ->
        onStatementRead(OcrCodec.encode(scan))
    }
    CardsScreen(
        state = state,
        onAdd = onAdd,
        onOpen = onOpen,
        snackbarHostState = snackbarHostState,
        onPickStatement = if (reader.isSupported) reader::pickFromGallery else null,
    )
    ReceiptReadingDialog(reader, title = "Ekran okunuyor…")
}
