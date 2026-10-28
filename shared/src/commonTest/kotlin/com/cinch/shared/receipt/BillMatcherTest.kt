package com.cinch.shared.receipt

import com.cinch.shared.domain.Category
import com.cinch.shared.domain.FixedExpense
import com.cinch.shared.domain.FixedExpenseKind
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BillMatcherTest {

    private fun tl(units: Long) = Money.of(units, Currency.TRY)

    private val rent = FixedExpense(id = 1, name = "Kira", amount = tl(15_000), dayOfMonth = 1, kind = FixedExpenseKind.RENT)
    private val electricity = FixedExpense(id = 2, name = "Elektrik", amount = tl(900), dayOfMonth = 20, kind = FixedExpenseKind.BILL)
    private val gas = FixedExpense(id = 3, name = "Doğalgaz", amount = tl(1_400), dayOfMonth = 22, kind = FixedExpenseKind.BILL)
    private val internet = FixedExpense(id = 4, name = "İnternet", amount = tl(450), dayOfMonth = 5, kind = FixedExpenseKind.BILL)
    private val phone = FixedExpense(id = 5, name = "Telefon", amount = tl(350), dayOfMonth = 12, kind = FixedExpenseKind.BILL)
    private val all = listOf(rent, electricity, gas, internet, phone)

    private fun bill(vararg rows: String, isBill: Boolean = true) = ReceiptDraft(
        amount = null, date = null, dueDate = null, merchant = rows.firstOrNull(),
        category = Category.FATURA, isBill = isBill, paidByCard = false, rows = rows.toList(),
    )

    @Test
    fun providerName_pointsToService() {
        val draft = bill("ENERJİSA BOĞAZİÇİ", "Abone No 123", "Tüketim 245 kWh", "Ödenecek Tutar 912,40")
        assertEquals(electricity, BillMatcher.match(draft, all))
    }

    @Test
    fun gasBill_isNotMistakenForElectricity_evenWithEpdkLine() {
        val draft = bill("İGDAŞ", "Doğal Gaz Faturası", "Enerji Piyasası Düzenleme Kurumu", "Tüketim 120 sm3")
        assertEquals(gas, BillMatcher.match(draft, all))
    }

    @Test
    fun fixedNameWrittenOnBill_matches() {
        val draft = bill("SUPERONLINE", "Fiber İnternet Hizmeti", "Son Ödeme Tarihi 05.04.2025")
        assertEquals(internet, BillMatcher.match(draft, all))
    }

    @Test
    fun mobileOperator_matchesPhone() {
        val draft = bill("TURKCELL", "Fatura Dönemi 03/2025", "Ödenecek Tutar 349,90")
        assertEquals(phone, BillMatcher.match(draft, all))
    }

    @Test
    fun receiptThatIsNotABill_isNeverMatched() {
        // Market fişinde "SU 0,5L" geçiyor: "Su" faturasına bağlanmamalı.
        val water = FixedExpense(id = 6, name = "Su", amount = tl(300), dayOfMonth = 10, kind = FixedExpenseKind.BILL)
        val draft = bill("MİGROS", "SU 0,5L *7,50", "TOPLAM *7,50", isBill = false)
        assertNull(BillMatcher.match(draft, all + water))
    }

    @Test
    fun unknownBill_isLeftToTheUser() {
        val draft = bill("XYZ HİZMETLERİ", "Abone No 123", "Ödenecek Tutar 100,00")
        assertNull(BillMatcher.match(draft, all))
    }
}
