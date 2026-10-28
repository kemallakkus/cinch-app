package com.cinch.ui.cashflow

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinch.shared.budget.CashBalanceStore
import com.cinch.shared.budget.CashForecast
import com.cinch.shared.budget.ForecastCashFlow
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.shared.money.MoneyInput
import com.cinch.shared.time.LocalClock
import com.cinch.ui.common.toInputText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * @param balance "Hesabında şu an ne kadar var?" alanı; boşsa takvim yalnız giriş-çıkışları gösterir.
 * @param balanceDate bakiyenin girildiği gün; eskiyse güncellemesi istenir.
 */
@Immutable
data class CashCalendarState(
    val isLoading: Boolean = true,
    val today: LocalDate,
    val balance: TextFieldState = TextFieldState(),
    val balanceDate: LocalDate? = null,
    val forecast: CashForecast? = null,
)

sealed interface CashCalendarEvent {
    data object Resumed : CashCalendarEvent
}

/** Para takvimi: bakiye girildikçe ve kayıtlar değiştikçe tahmin yeniden hesaplanır. */
@OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest
class CashCalendarViewModel(
    forecast: ForecastCashFlow,
    private val balances: CashBalanceStore,
    private val clock: LocalClock,
) : ViewModel() {

    private val today = MutableStateFlow(clock.today())
    private val balanceField = TextFieldState()
    private val balanceDate = MutableStateFlow<LocalDate?>(null)

    init {
        balances.get()?.let { (money, date) ->
            balanceField.setTextAndPlaceCursorAtEnd(money.toInputText())
            balanceDate.value = date
        }
    }

    private val balance = snapshotFlow { balanceField.text.toString().trim() }
        .distinctUntilChanged()
        .map { text -> text.takeIf { it.isNotEmpty() }?.let { MoneyInput.parse(it, Currency.TRY) } }

    val state: StateFlow<CashCalendarState> = combine(today, balance, ::Pair)
        .flatMapLatest { (day, money) ->
            combine(forecast(day, money), balanceDate) { result, date ->
                CashCalendarState(isLoading = false, today = day, balance = balanceField, balanceDate = date, forecast = result)
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            CashCalendarState(today = today.value, balance = balanceField, balanceDate = balanceDate.value),
        )

    init {
        // Kullanıcı yazdıkça kaydedilir (ilk yükleme hariç); bir dahaki açılışta hazır gelir.
        viewModelScope.launch {
            snapshotFlow { balanceField.text.toString().trim() }
                .distinctUntilChanged()
                .drop(1)
                .collect { text ->
                    val money = if (text.isEmpty()) null else MoneyInput.parse(text, Currency.TRY) ?: return@collect
                    val day = clock.today()
                    balances.set(money, day)
                    balanceDate.value = money?.let { day }
                }
        }
    }

    fun onEvent(event: CashCalendarEvent) {
        when (event) {
            CashCalendarEvent.Resumed -> today.value = clock.today()
        }
    }
}

/** Takvimde tutar rengi ve işareti için. */
internal val Money.isIncome: Boolean get() = minor > 0
