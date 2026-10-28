package com.cinch.ui.statement

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cinch.navigation.StatementReviewRoute
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun StatementReviewWrapper(
    route: StatementReviewRoute,
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: StatementReviewViewModel = koinViewModel { parametersOf(route) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnDone by rememberUpdatedState(onDone)

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is StatementReviewEffect.Saved -> currentOnDone()
            }
        }
    }

    StatementReviewScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack)
}
