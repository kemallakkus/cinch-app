package com.cinch.shared.backup

import com.cinch.shared.domain.Category
import com.cinch.shared.domain.Expense
import com.cinch.shared.money.Money

/**
 * Harcamaları Excel'in Türkçe ayarlarıyla doğrudan açılan CSV'ye çevirir: ayraç noktalı virgül (virgül ondalık
 * için), tutarlar "1234,56", tarih "2026-10-28", başta UTF-8 BOM (Türkçe harfler bozulmasın).
 */
object ExpenseCsv {

    private const val SEPARATOR = ';'
    private const val BOM = "﻿"

    private val HEADER = listOf("Tarih", "Tutar", "Senin payın", "Kategori", "Kart", "Açıklama", "Not", "Sabit gider")

    /**
     * @param categoryLabel kategorinin görünen adı ("Market"); dil arayüz katmanında.
     * @param cardNames kart kimliği → adı; nakit harcamada boş.
     * @param fixedNames sabit gider kimliği → adı; sabit gider ödemesi değilse boş.
     */
    fun build(
        expenses: List<Expense>,
        categoryLabel: (Category) -> String,
        cardNames: Map<Long, String> = emptyMap(),
        fixedNames: Map<Long, String> = emptyMap(),
    ): String = buildString {
        append(BOM)
        appendRow(HEADER)
        expenses.sortedWith(compareBy({ it.date }, { it.id })).forEach { e ->
            appendRow(
                listOf(
                    e.date.toString(),
                    amount(e.grossAmount),
                    amount(e.chargeableAmount),
                    categoryLabel(e.category),
                    e.cardId?.let { cardNames[it] } ?: if (e.cardId == null) "Nakit" else "",
                    e.rawText,
                    e.note,
                    e.fixedExpenseId?.let { fixedNames[it] ?: "Sabit gider" }.orEmpty(),
                ),
            )
        }
    }

    /** "1234,56": binlik ayraç yok (Excel sayı olarak okusun), virgül ondalık. */
    internal fun amount(money: Money): String {
        val minor = money.minor
        val sign = if (minor < 0) "-" else ""
        val abs = if (minor < 0) -minor else minor
        val unit = money.currency.minorPerUnit
        val digits = money.currency.fractionDigits
        if (digits == 0) return "$sign${abs / unit}"
        return "$sign${abs / unit},${(abs % unit).toString().padStart(digits, '0')}"
    }

    private fun StringBuilder.appendRow(fields: List<String>) {
        fields.forEachIndexed { i, field ->
            if (i > 0) append(SEPARATOR)
            append(escape(field))
        }
        append("\r\n")
    }

    /** Ayraç, tırnak ya da satır sonu içeren alan tırnağa alınır; içindeki tırnak ikilenir. */
    private fun escape(field: String): String =
        if (field.any { it == SEPARATOR || it == '"' || it == '\n' || it == '\r' }) "\"" + field.replace("\"", "\"\"") + "\""
        else field
}
