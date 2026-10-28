package com.cinch.ui.insights

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cinch.navigation.InsightsRoute
import com.cinch.shared.domain.Category
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun InsightsWrapper(
    route: InsightsRoute,
    onOpenCategory: (Category, LocalDate) -> Unit,
    onBack: () -> Unit,
    viewModel: InsightsViewModel = koinViewModel { parametersOf(route) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleResumeEffect(viewModel) {
        viewModel.onEvent(InsightsEvent.Resumed)
        onPauseOrDispose { }
    }

    InsightsScreen(state = state, onEvent = viewModel::onEvent, onOpenCategory = onOpenCategory, onBack = onBack)
}
