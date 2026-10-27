package com.cinch.ui.month

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MonthWrapper(
    onAddIncome: () -> Unit,
    onOpenIncome: (Long) -> Unit,
    onAddFixed: () -> Unit,
    onOpenFixed: (Long) -> Unit,
    viewModel: MonthViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confetti by remember { mutableIntStateOf(0) }

    LifecycleResumeEffect(viewModel) {
        viewModel.onEvent(MonthEvent.Resumed)
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                MonthEffect.Paid -> confetti++
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
        confettiTrigger = confetti,
    )
}
