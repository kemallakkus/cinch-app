package com.cinch.widget

import com.cinch.shared.budget.FixedMatcher
import com.cinch.shared.budget.FixedPayments
import com.cinch.shared.domain.Expense
import com.cinch.shared.domain.repository.FixedExpenseRepository
import com.cinch.shared.domain.repository.CardRepository
import com.cinch.shared.domain.repository.ExpenseRepository
import com.cinch.shared.parser.QuickEntryParser
import com.cinch.shared.time.LocalClock
import com.cinch.ui.common.display
import com.cinch.ui.common.emoji
import com.cinch.ui.common.label
import kotlinx.datetime.LocalDate

/**
 * Uygulama açılmadan gelen hızlı kayıt (iOS: Siri / Kısayollar "Cinch'e harcama ekle"; Android: bildirimdeki
 * "Harcama yaz" yanıtı). Ana ekranın hızlı kayıt alanıyla aynı ayrıştırma: tutar, kategori, tarih, metinde adı
 * geçen kart.
 */
class SaveQuickEntry(
    private val parser: QuickEntryParser,
    private val expenses: ExpenseRepository,
    private val cards: CardRepository,
    private val clock: LocalClock,
    /** Verilirse adı yazılan sabit gider ("199 netflix") o ayın ödemesi sayılır ([FixedMatcher]). */
    private val fixedExpenses: FixedExpenseRepository? = null,
) {
    /**
     * Kaydedilen harcama ya da sabit gider ödemesi ([Expense.isFixedPayment]); metinde tutar yoksa null (hiçbir şey
     * kaydedilmez).
     *
     * @param fallbackDate metinde tarih yoksa kullanılan gün; null = bugün.
     */
    suspend operator fun invoke(text: String, fallbackDate: LocalDate? = null): Expense? {
        val today = clock.today()
        val parsed = parser.parse(text.trim(), today, cards = cards.all(), fallbackDate = fallbackDate ?: today)
        val expense = parsed.toExpense()?.takeIf { it.grossAmount.minor > 0 } ?: return null
        val fixed = fixedExpenses?.let { repo ->
            FixedMatcher.match(parsed.tokens, repo.all(), expenses.fixedPayments(), expense.date, expense.grossAmount.currency)
        }
        if (fixed != null) {
            val payer = fixed.copy(cardId = expense.cardId ?: fixed.cardId)
            val id = FixedPayments(expenses).markPaid(payer, expense.grossAmount, expense.date)
            return Expense(
                id = id,
                date = expense.date,
                grossAmount = expense.grossAmount,
                category = fixed.kind.category,
                cardId = payer.cardId,
                rawText = fixed.name,
                fixedExpenseId = fixed.id,
            )
        }
        val id = expenses.add(expense)
        return expense.copy(id = id)
    }

    companion object {
        /** Siri'nin söyleyeceği / Kısayollar'ın göstereceği kısa cümle. */
        fun message(text: String, saved: Expense?): String =
            if (saved == null) {
                "\"$text\" içinde tutar bulamadım. Örneğin \"195 market\" de."
            } else if (saved.isFixedPayment) {
                "${saved.rawText} bu ayın ödemesi olarak işaretlendi: ${saved.grossAmount.display()}"
            } else {
                "Kaydedildi: ${saved.grossAmount.display()} ${saved.category.emoji} ${saved.category.label}"
            }
    }
}
