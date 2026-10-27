package com.cinch.ui.month

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cinch.navigation.IncomeEditRoute
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun IncomeEditWrapper(
    route: IncomeEditRoute,
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: IncomeEditViewModel = koinViewModel { parametersOf(route) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnDone by rememberUpdatedState(onDone)

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                IncomeEditEffect.Done -> currentOnDone()
            }
        }
    }

    IncomeEditScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack)
}
