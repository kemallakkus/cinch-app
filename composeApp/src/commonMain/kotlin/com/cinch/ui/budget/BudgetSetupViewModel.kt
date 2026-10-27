package com.cinch.ui.budget

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinch.navigation.BudgetSetupRoute
import com.cinch.shared.budget.BudgetConfig
import com.cinch.shared.budget.WeeklyLimitSuggestion
import com.cinch.shared.domain.repository.BudgetRepository
import com.cinch.shared.domain.repository.FixedExpenseRepository
import com.cinch.shared.domain.repository.IncomeRepository
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
    private val incomes: IncomeRepository,
    private val fixedExpenses: FixedExpenseRepository,
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
    }

    fun onEvent(event: BudgetSetupEvent) {
        when (event) {
            is BudgetSetupEvent.WeekStartPicked -> _state.update { it.copy(weekStart = event.day) }
            BudgetSetupEvent.UseSuggestion -> _state.value.suggestion?.weekly?.let {
                _state.value.weeklyLimit.setTextAndPlaceCursorAtEnd(it.toInputText())
            }
            BudgetSetupEvent.Save -> save()
        }
    }

    private suspend fun loadExisting() {
        val config = budgets.get()
        existing = config
        if (config != null) {
            _state.value.weeklyLimit.setTextAndPlaceCursorAtEnd(config.weeklyLimit.toInputText())
        }
        _state.update {
            it.copy(isLoading = false, weekStart = config?.weekStart ?: it.weekStart, suggestion = suggestion())
        }
    }

    /** Gelir girilmemişse öneri yok; ekranda gelir eklemeye davet edilir. */
    private suspend fun suggestion(): LimitSuggestion? {
        val income = Money.sum(incomes.all().filter { it.currency == currency }.map { it.amount }, currency)
        if (income.isZero) return null
        val fixed = Money.sum(fixedExpenses.all().filter { it.currency == currency }.map { it.amount }, currency)
        return LimitSuggestion(income, fixed, WeeklyLimitSuggestion.suggest(income, fixed))
    }

    private fun save() {
        val current = _state.value
        if (current.isLoading || current.isSaving) return

        val limit = MoneyInput.parse(current.weeklyLimit.text.toString(), currency)?.takeIf { !it.isZero }
        if (limit == null) {
            _state.update { it.copy(limitError = true) }
            return
        }

        val config = (existing ?: BudgetConfig(weeklyLimit = limit))
            .copy(weeklyLimit = limit, weekStart = current.weekStart)
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
