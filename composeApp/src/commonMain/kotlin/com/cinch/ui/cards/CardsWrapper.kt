package com.cinch.ui.cards

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CardsWrapper(
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    viewModel: CardsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CardsScreen(state = state, onAdd = onAdd, onOpen = onOpen)
}
