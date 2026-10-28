package com.cinch.shared.backup

import com.cinch.shared.domain.Category
import com.cinch.shared.domain.Expense
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExpenseCsvTest {

    @Test
    fun excelFriendlyRows() {
        val csv = ExpenseCsv.build(
            expenses = listOf(
                Expense(
                    id = 2, date = LocalDate(2026, 10, 21), grossAmount = Money(60_000, Currency.TRY), chargeableFraction = 0.5,
                    category = Category.YEMEK, cardId = 1, rawText = "600 yemek; ikimiz", note = "doğum \"günü\"",
                ),
                Expense(id = 1, date = LocalDate(2026, 10, 20), grossAmount = Money(19_550, Currency.TRY), category = Category.MARKET, rawText = "195,50 market"),
                Expense(id = 3, date = LocalDate(2026, 10, 22), grossAmount = Money(1_500_000, Currency.TRY), category = Category.EV, fixedExpenseId = 7),
            ),
            categoryLabel = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } },
            cardNames = mapOf(1L to "QNB"),
            fixedNames = mapOf(7L to "Kira"),
        )

        assertTrue(csv.startsWith("﻿")) // Excel Türkçe harfleri doğru açsın
        val lines = csv.removePrefix("﻿").trimEnd().split("\r\n")
        assertEquals("Tarih;Tutar;Senin payın;Kategori;Kart;Açıklama;Not;Sabit gider", lines[0])
        assertEquals("2026-10-20;195,50;195,50;Market;Nakit;195,50 market;;", lines[1]) // tarihe göre sıralı
        assertEquals("2026-10-21;600,00;300,00;Yemek;QNB;\"600 yemek; ikimiz\";\"doğum \"\"günü\"\"\";", lines[2])
        assertEquals("2026-10-22;15000,00;15000,00;Ev;Nakit;;;Kira", lines[3])
    }

    @Test
    fun negativeAmount() {
        assertEquals("-2202,86", ExpenseCsv.amount(Money(-220_286, Currency.TRY)))
    }
}
