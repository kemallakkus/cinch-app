package com.cinch.widget

import com.cinch.shared.domain.Expense
import com.cinch.shared.domain.repository.CardRepository
import com.cinch.shared.domain.repository.ExpenseRepository
import com.cinch.shared.parser.QuickEntryParser
import com.cinch.shared.time.LocalClock
import com.cinch.ui.common.display
import com.cinch.ui.common.emoji
import com.cinch.ui.common.label

/**
 * Uygulama açılmadan gelen hızlı kayıt (iOS: Siri / Kısayollar "Cinch'e harcama ekle"). Ana ekranın hızlı kayıt
 * alanıyla aynı ayrıştırma: tutar, kategori, tarih, metinde adı geçen kart.
 */
class SaveQuickEntry(
    private val parser: QuickEntryParser,
    private val expenses: ExpenseRepository,
    private val cards: CardRepository,
    private val clock: LocalClock,
) {
    /** Kaydedilen harcama; metinde tutar yoksa null (hiçbir şey kaydedilmez). */
    suspend operator fun invoke(text: String): Expense? {
        val parsed = parser.parse(text.trim(), clock.today(), cards = cards.all())
        val expense = parsed.toExpense()?.takeIf { it.grossAmount.minor > 0 } ?: return null
        val id = expenses.add(expense)
        return expense.copy(id = id)
    }

    companion object {
        /** Siri'nin söyleyeceği / Kısayollar'ın göstereceği kısa cümle. */
        fun message(text: String, saved: Expense?): String =
            if (saved == null) {
                "\"$text\" içinde tutar bulamadım. Örneğin \"195 market\" de."
            } else {
                "Kaydedildi: ${saved.grossAmount.display()} ${saved.category.emoji} ${saved.category.label}"
            }
    }
}
