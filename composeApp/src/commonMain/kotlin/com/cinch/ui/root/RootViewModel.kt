package com.cinch.ui.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinch.navigation.BudgetSetupRoute
import com.cinch.navigation.CinchRoute
import com.cinch.navigation.HomeRoute
import com.cinch.navigation.IntroRoute
import com.cinch.shared.domain.repository.BudgetRepository
import com.cinch.shared.prefs.KeyValueStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * Açılışta hangi ekranla başlanacağına karar verir: ilk açılışta tanıtım, tanıtım görüldü ama bütçe yoksa
 * kurulum, bütçe varsa ana ekran. Bütçesi olan (tanıtımdan önceki sürümden gelen) kullanıcı tanıtımı görmez.
 */
class RootViewModel(budgets: BudgetRepository, private val prefs: KeyValueStore) : ViewModel() {

    /** Veritabanı okunana kadar null; o sırada boş arka plan gösterilir. */
    val startRoute: StateFlow<CinchRoute?> = flow {
        val hasBudget = budgets.get() != null
        if (hasBudget) prefs.putBoolean(INTRO_SEEN, true)
        emit(
            when {
                hasBudget -> HomeRoute
                prefs.getBoolean(INTRO_SEEN) -> BudgetSetupRoute(isOnboarding = true)
                else -> IntroRoute
            },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)

    /** Tanıtım bitti ya da geçildi: bir daha gösterilmez (bütçe kurulmadan çıkılsa bile). */
    fun introFinished() {
        prefs.putBoolean(INTRO_SEEN, true)
    }

    companion object {
        const val INTRO_SEEN = "intro_seen"
    }
}
