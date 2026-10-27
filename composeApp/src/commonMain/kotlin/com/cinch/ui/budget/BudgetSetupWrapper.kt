package com.cinch.ui.budget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cinch.navigation.BudgetSetupRoute
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun BudgetSetupWrapper(
    route: BudgetSetupRoute,
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: BudgetSetupViewModel = koinViewModel { parametersOf(route) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnDone by rememberUpdatedState(onDone)

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                BudgetSetupEffect.Saved -> currentOnDone()
            }
        }
    }

    BudgetSetupScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack)
}
