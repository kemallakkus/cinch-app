package com.cinch.ui.cards

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cinch.navigation.CardDetailRoute
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun CardDetailWrapper(
    route: CardDetailRoute,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    viewModel: CardDetailViewModel = koinViewModel { parametersOf(route) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CardDetailScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack, onEdit = onEdit)
}
