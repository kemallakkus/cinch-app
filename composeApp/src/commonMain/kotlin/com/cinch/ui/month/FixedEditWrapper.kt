package com.cinch.ui.month

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cinch.navigation.FixedEditRoute
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun FixedEditWrapper(
    route: FixedEditRoute,
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: FixedEditViewModel = koinViewModel { parametersOf(route) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnDone by rememberUpdatedState(onDone)

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                FixedEditEffect.Done -> currentOnDone()
            }
        }
    }

    FixedEditScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack)
}
