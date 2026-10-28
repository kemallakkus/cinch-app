package com.cinch.ui.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cinch.navigation.ExpenseEditRoute
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ExpenseEditWrapper(
    route: ExpenseEditRoute,
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: ExpenseEditViewModel = koinViewModel { parametersOf(route) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnDone by rememberUpdatedState(onDone)

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                ExpenseEditEffect.Done -> currentOnDone()
            }
        }
    }
    // Kayıt başka yerden silindiyse (ör. geri alınan sabit gider ödemesi) düzenlenecek bir şey kalmadı.
    LaunchedEffect(state.isMissing) {
        if (state.isMissing) currentOnDone()
    }

    ExpenseEditScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack)
}
