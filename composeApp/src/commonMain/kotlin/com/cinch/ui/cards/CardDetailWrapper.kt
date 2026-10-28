package com.cinch.ui.cards

import androidx.compose.runtime.Composable
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cinch.navigation.CardDetailRoute
import com.cinch.shared.receipt.OcrCodec
import com.cinch.ui.receipt.ReceiptReadingDialog
import com.cinch.ui.receipt.rememberReceiptFlow
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** @param onStatementRead banka ekranı fotoğraflarından okunanlar (`OcrCodec` ile); onay ekranına geçilir. */
@Composable
fun CardDetailWrapper(
    route: CardDetailRoute,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onStatementRead: (String) -> Unit,
    viewModel: CardDetailViewModel = koinViewModel { parametersOf(route) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // Banka ekranı birkaç kareye yayılır (özet, "daha fazla", hareketler).
    val reader = rememberReceiptFlow(snackbarHostState, maxImages = STATEMENT_PHOTOS) { scan ->
        onStatementRead(OcrCodec.encode(scan))
    }
    CardDetailScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onEdit = onEdit,
        snackbarHostState = snackbarHostState,
        onScanStatement = if (reader.isSupported) reader::scan else null,
        onPickStatement = if (reader.isSupported) reader::pickFromGallery else null,
    )
    ReceiptReadingDialog(reader, title = "Ekran okunuyor…")
}

/** Bir seferde seçilebilecek banka ekranı fotoğrafı. */
internal const val STATEMENT_PHOTOS = 6
