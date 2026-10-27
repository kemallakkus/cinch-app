package com.cinch.ui.budget

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinch.navigation.BudgetSetupRoute
import com.cinch.shared.budget.BudgetConfig
import com.cinch.shared.domain.repository.BudgetRepository
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.shared.money.MoneyInput
import com.cinch.ui.common.toInputText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BudgetSetupViewModel(
    route: BudgetSetupRoute,
    private val budgets: BudgetRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BudgetSetupState(isOnboarding = route.isOnboarding))
    val state: StateFlow<BudgetSetupState> = _state.asStateFlow()

    private val _effect = Channel<BudgetSetupEffect>()
    val effect = _effect.receiveAsFlow()

    /** Düzenlemede formda olmayan alanlar (para birimi, haftalık hedef) buradan korunur. */
    private var existing: BudgetConfig? = null
    private val currency get() = existing?.currency ?: Currency.TRY

    init {
        viewModelScope.launch { loadExisting() }
        viewModelScope.launch {
            snapshotFlow { _state.value.weeklyLimit.text.toString() }.collect { text ->
                val daily = MoneyInput.parse(text, currency)?.takeIf { !it.isZero }
                    ?.let { Money(it.minor / 7, it.currency) }
                _state.update { it.copy(dailyHint = daily, limitError = false) }
            }
        }
        viewModelScope.launch {
            snapshotFlow { _state.value.salaryDay.text.toString() }.collect {
                _state.update { it.copy(salaryDayError = false) }
            }
        }
    }

    fun onEvent(event: BudgetSetupEvent) {
        when (event) {
            is BudgetSetupEvent.WeekStartPicked -> _state.update { it.copy(weekStart = event.day) }
            BudgetSetupEvent.Save -> save()
        }
    }

    private suspend fun loadExisting() {
        val config = budgets.get()
        existing = config
        if (config != null) {
            _state.value.weeklyLimit.setTextAndPlaceCursorAtEnd(config.weeklyLimit.toInputText())
            config.salaryDay?.let { _state.value.salaryDay.setTextAndPlaceCursorAtEnd(it.toString()) }
        }
        _state.update { it.copy(isLoading = false, weekStart = config?.weekStart ?: it.weekStart) }
    }

    private fun save() {
        val current = _state.value
        if (current.isLoading || current.isSaving) return

        val limit = MoneyInput.parse(current.weeklyLimit.text.toString(), currency)?.takeIf { !it.isZero }
        val salaryText = current.salaryDay.text.toString().trim()
        val salaryDay = salaryText.toIntOrNull()?.takeIf { it in 1..31 }
        val salaryInvalid = salaryText.isNotEmpty() && salaryDay == null
        if (limit == null || salaryInvalid) {
            _state.update { it.copy(limitError = limit == null, salaryDayError = salaryInvalid) }
            return
        }

        val config = (existing ?: BudgetConfig(weeklyLimit = limit))
            .copy(weeklyLimit = limit, weekStart = current.weekStart, salaryDay = salaryDay)
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                budgets.save(config)
                _effect.send(BudgetSetupEffect.Saved)
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }
}
