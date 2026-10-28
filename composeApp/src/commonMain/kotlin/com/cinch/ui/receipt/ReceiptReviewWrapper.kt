package com.cinch.ui.receipt

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cinch.navigation.ReceiptReviewRoute
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** @param onDone kaydedildi; [String] doluysa sabit gider ödendi işaretlendi (adı). */
@Composable
fun ReceiptReviewWrapper(
    route: ReceiptReviewRoute,
    onDone: (fixedName: String?) -> Unit,
    onBack: () -> Unit,
    viewModel: ReceiptReviewViewModel = koinViewModel { parametersOf(route) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnDone by rememberUpdatedState(onDone)

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ReceiptReviewEffect.Saved -> currentOnDone(effect.fixedName)
            }
        }
    }

    ReceiptReviewScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack)
}
