package com.cinch.ui.cashflow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CashCalendarWrapper(
    onBack: () -> Unit,
    viewModel: CashCalendarViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onEvent(CashCalendarEvent.Resumed)
        onPauseOrDispose { }
    }
    CashCalendarScreen(state = state, onBack = onBack)
}
