package com.cinch.ui.month

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cinch.ui.receipt.ReceiptReadingDialog
import com.cinch.ui.receipt.rememberReceiptFlow
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MonthWrapper(
    onAddIncome: () -> Unit,
    onOpenIncome: (Long) -> Unit,
    onAddFixed: () -> Unit,
    onOpenFixed: (Long) -> Unit,
    onOpenDebt: (Long) -> Unit,
    onOpenInsights: () -> Unit,
    onReceiptRead: (String) -> Unit,
    viewModel: MonthViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confetti by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val receipt = rememberReceiptFlow(snackbarHostState) { scan -> onReceiptRead(scan.text) }

    LifecycleResumeEffect(viewModel) {
        viewModel.onEvent(MonthEvent.Resumed)
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is MonthEffect.Paid -> {
                    confetti++
                    effect.finishedName?.let { name -> launch { snackbarHostState.showSnackbar("🎉 $name bitti! Tebrikler.") } }
                }
            }
        }
    }

    MonthScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onAddIncome = onAddIncome,
        onOpenIncome = onOpenIncome,
        onAddFixed = onAddFixed,
        onOpenFixed = onOpenFixed,
        onOpenDebt = onOpenDebt,
        onOpenInsights = onOpenInsights,
        onScanBill = if (receipt.isSupported) receipt::scan else null,
        snackbarHostState = snackbarHostState,
        confettiTrigger = confetti,
    )
    ReceiptReadingDialog(receipt)
}
