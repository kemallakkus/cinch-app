package com.cinch.ui.cards

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cinch.navigation.CardEditRoute
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * @param onDeleted kart silindi: o karta ait ekranlar (detay, düzenleme) yığından çıkarılır.
 */
@Composable
fun CardEditWrapper(
    route: CardEditRoute,
    onDone: () -> Unit,
    onDeleted: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: CardEditViewModel = koinViewModel { parametersOf(route) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnDone by rememberUpdatedState(onDone)
    val currentOnDeleted by rememberUpdatedState(onDeleted)

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                CardEditEffect.Saved -> currentOnDone()
                is CardEditEffect.Deleted -> currentOnDeleted(effect.cardId)
            }
        }
    }

    CardEditScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack)
}
