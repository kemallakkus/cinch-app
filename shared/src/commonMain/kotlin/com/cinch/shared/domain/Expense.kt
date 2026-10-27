package com.cinch.shared.domain

import com.cinch.shared.money.Money
import kotlinx.datetime.LocalDate

/**
 * Tek bir harcama kaydı.
 *
 * @param grossAmount karta/kasaya giren toplam tutar (mutabakatta bu kullanılır).
 * @param chargeableFraction kullanıcının kendi payı (0.0–1.0). Paylaşılan harcamada
 *   sadece bu pay sınıra sayılır. Örn. "yarısı geri geldi" → 0.5.
 * @param cardId harcamanın bağlı olduğu kart; nakit ise null.
 * @param tokens hızlı kayıttan ayrıştırılmış kelimeler (alışkanlık analizi için).
 * @param rawText kullanıcının yazdığı ham metin (ör. "195 market"); listede gösterilir.
 */
data class Expense(
    val id: Long = 0,
    val date: LocalDate,
    val grossAmount: Money,
    val chargeableFraction: Double = 1.0,
    val category: Category = Category.DIGER,
    val cardId: Long? = null,
    val note: String = "",
    val tokens: List<String> = emptyList(),
    val rawText: String = "",
) {
    /** Kullanıcının sınıra sayılan payı. */
    val chargeableAmount: Money get() = grossAmount * chargeableFraction
}
