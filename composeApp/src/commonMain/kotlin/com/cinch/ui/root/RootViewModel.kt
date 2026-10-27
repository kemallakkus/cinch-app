package com.cinch.ui.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinch.navigation.BudgetSetupRoute
import com.cinch.navigation.CinchRoute
import com.cinch.navigation.HomeRoute
import com.cinch.shared.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** Açılışta hangi ekranla başlanacağına karar verir: bütçe yoksa kurulum, varsa ana ekran. */
class RootViewModel(budgets: BudgetRepository) : ViewModel() {

    /** Veritabanı okunana kadar null; o sırada boş arka plan gösterilir. */
    val startRoute: StateFlow<CinchRoute?> = flow {
        emit(if (budgets.get() == null) BudgetSetupRoute(isOnboarding = true) else HomeRoute)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)
}
