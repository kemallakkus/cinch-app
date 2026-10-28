package com.cinch.ui.home

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cinch.ui.notifications.NotificationPromptWrapper
import com.cinch.ui.receipt.ReceiptReadingDialog
import com.cinch.ui.receipt.rememberReceiptFlow
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeWrapper(
    onOpenExpense: (Long) -> Unit,
    onOpenHistory: () -> Unit,
    onReceiptRead: (String) -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val receipt = rememberReceiptFlow(snackbarHostState) { scan -> onReceiptRead(scan.text) }
    var confetti by remember { mutableIntStateOf(0) }

    LifecycleResumeEffect(viewModel) {
        viewModel.onEvent(HomeEvent.Resumed)
        onPauseOrDispose { }
    }

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is HomeEffect.EntrySaved -> if (effect.onTrack) confetti++
                is HomeEffect.ExpenseDeleted -> launch {
                    // Art arda silmede eski snackbar beklemesin; son silinen geri alınabilir.
                    snackbarHostState.currentSnackbarData?.dismiss()
                    val result = snackbarHostState.showSnackbar(
                        message = "Harcama silindi",
                        actionLabel = "Geri al",
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.onEvent(HomeEvent.UndoDelete(effect.expense))
                    }
                }
            }
        }
    }

    HomeScreen(
        state = state,
        onEvent = viewModel::onEvent,
        snackbarHostState = snackbarHostState,
        confettiTrigger = confetti,
        onOpenExpense = onOpenExpense,
        onOpenHistory = onOpenHistory,
        onScanReceipt = if (receipt.isSupported) receipt::scan else null,
        onPickReceipt = if (receipt.isSupported) receipt::pickFromGallery else null,
        prompt = { NotificationPromptWrapper() },
    )
    ReceiptReadingDialog(receipt)
}
