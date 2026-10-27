package com.cinch.shared.budget

import com.cinch.shared.domain.Expense
import com.cinch.shared.domain.repository.BudgetRepository
import com.cinch.shared.domain.repository.ExpenseRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/**
 * Bütçe ayarını ve harcamaları izleyip [BudgetEngine] ile güncel [BudgetStatus] yayar.
 * Harcama eklenince, silinince ya da bütçe değişince yeniden hesaplar.
 * Kullanıcı henüz bütçe kurmadıysa null yayar.
 */
class ObserveBudgetStatus(
    private val budgets: BudgetRepository,
    private val expenses: ExpenseRepository,
    private val engine: BudgetEngine,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(today: LocalDate): Flow<BudgetStatus?> =
        budgets.observe()
            .flatMapLatest { config ->
                if (config == null) return@flatMapLatest flowOf(null)
                val range = BudgetEngine.statusRange(today, config.weekStart)
                expenses.observeBetween(range.start, range.endInclusive)
                    .map { list -> engine.status(list.inCurrencyOf(config), config, today) }
            }
            .distinctUntilChanged()
}

/**
 * Motor tek para biriminde toplar; farklı birimdeki kayıt toplamayı patlatır.
 * Kur çevirisi gelene kadar yalnız bütçe biriminde olanlar sayılır.
 */
internal fun List<Expense>.inCurrencyOf(config: BudgetConfig): List<Expense> =
    filter { it.grossAmount.currency == config.currency }
