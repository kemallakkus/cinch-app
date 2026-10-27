package com.cinch.ui.month

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MonthWrapper(
    onAddIncome: () -> Unit,
    onOpenIncome: (Long) -> Unit,
    viewModel: MonthViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleResumeEffect(viewModel) {
        viewModel.onEvent(MonthEvent.Resumed)
        onPauseOrDispose { }
    }

    MonthScreen(state = state, onAddIncome = onAddIncome, onOpenIncome = onOpenIncome)
}
