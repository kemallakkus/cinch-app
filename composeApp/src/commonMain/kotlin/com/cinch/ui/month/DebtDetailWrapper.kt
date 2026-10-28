package com.cinch.ui.month

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cinch.navigation.DebtDetailRoute
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun DebtDetailWrapper(
    route: DebtDetailRoute,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    viewModel: DebtDetailViewModel = koinViewModel { parametersOf(route) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confetti by remember { mutableIntStateOf(0) }
    val currentOnBack by rememberUpdatedState(onBack)

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is DebtDetailEffect.Paid -> {
                    confetti++
                    if (effect.finished) launch { snackbarHostState.showSnackbar("🎉 Borç bitti! Tebrikler.") }
                }
            }
        }
    }
    // Borç formdan silindiyse bu ekranın gösterecek bir şeyi kalmaz.
    LaunchedEffect(state.isLoading, state.fixed) {
        if (!state.isLoading && state.fixed == null) currentOnBack()
    }

    DebtDetailScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onEdit = onEdit,
        snackbarHostState = snackbarHostState,
        confettiTrigger = confetti,
    )
}
