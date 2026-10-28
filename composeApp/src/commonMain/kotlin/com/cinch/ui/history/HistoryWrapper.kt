package com.cinch.ui.history

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cinch.navigation.HistoryRoute
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun HistoryWrapper(
    route: HistoryRoute,
    onOpenExpense: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: HistoryViewModel = koinViewModel { parametersOf(route) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LifecycleResumeEffect(viewModel) {
        viewModel.onEvent(HistoryEvent.Resumed)
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is HistoryEffect.ExpenseDeleted -> launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    val result = snackbarHostState.showSnackbar(
                        message = "Harcama silindi",
                        actionLabel = "Geri al",
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.onEvent(HistoryEvent.UndoDelete(effect.expense))
                    }
                }
            }
        }
    }

    HistoryScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenExpense = onOpenExpense,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
    )
}
