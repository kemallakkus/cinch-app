package com.cinch.ui.common

import com.cinch.shared.domain.Category
import com.cinch.shared.money.AppLocale
import com.cinch.shared.money.Money
import com.cinch.shared.money.MoneyFormatter
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber

// Metinler şimdilik Türkçe ve koda gömülü; çoklu dil gelince Compose resources'a taşınır.

val Category.label: String
    get() = when (this) {
        Category.MARKET -> "Market"
        Category.YEMEK -> "Yemek"
        Category.ULASIM -> "Ulaşım"
        Category.FATURA -> "Fatura"
        Category.SAGLIK -> "Sağlık"
        Category.EGLENCE -> "Eğlence"
        Category.GIYIM -> "Giyim"
        Category.KISISEL -> "Kişisel bakım"
        Category.EV -> "Ev"
        Category.DIGER -> "Diğer"
    }

/** İkon kütüphanesi eklemeden listede kategoriyi bir bakışta ayırt etmek için. */
val Category.emoji: String
    get() = when (this) {
        Category.MARKET -> "🛒"
        Category.YEMEK -> "🍽️"
        Category.ULASIM -> "🚕"
        Category.FATURA -> "💡"
        Category.SAGLIK -> "💊"
        Category.EGLENCE -> "🎬"
        Category.GIYIM -> "👕"
        Category.KISISEL -> "💇"
        Category.EV -> "🏠"
        Category.DIGER -> "📦"
    }

fun Money.display(): String = MoneyFormatter.format(this, AppLocale.TR)

/** Forma geri yazmak için: tam liraysa "1500", kuruşluysa "1.234,56". */
fun Money.toInputText(): String =
    if (minor % currency.minorPerUnit == 0L) (minor / currency.minorPerUnit).toString()
    else MoneyFormatter.format(this, AppLocale.TR, withSymbol = false)

private val shortDays = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")
private val shortMonths = listOf("Oca", "Şub", "Mar", "Nis", "May", "Haz", "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara")

val DayOfWeek.longLabel: String
    get() = when (this) {
        DayOfWeek.MONDAY -> "Pazartesi"
        DayOfWeek.TUESDAY -> "Salı"
        DayOfWeek.WEDNESDAY -> "Çarşamba"
        DayOfWeek.THURSDAY -> "Perşembe"
        DayOfWeek.FRIDAY -> "Cuma"
        DayOfWeek.SATURDAY -> "Cumartesi"
        DayOfWeek.SUNDAY -> "Pazar"
    }

/** "Bugün", "Dün" ya da "Pzt 13 Oca". */
fun LocalDate.relativeLabel(today: LocalDate): String = when (daysUntil(today)) {
    0 -> "Bugün"
    1 -> "Dün"
    else -> "${shortDays[dayOfWeek.isoDayNumber - 1]} $day ${shortMonths[month.ordinal]}"
}

/** "13 Oca". */
fun LocalDate.shortLabel(): String = "$day ${shortMonths[month.ordinal]}"

/** Faiz oranını forma ve ekrana yazmak için: 4.9 → "4,9", 5.0 → "5". */
fun formatPercent(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString().replace('.', ',')

/** "4,9" ya da "4.9" → 4.9. Boş, geçersiz ya da 0–100 dışıysa null. */
fun parsePercent(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.0..100.0 }
