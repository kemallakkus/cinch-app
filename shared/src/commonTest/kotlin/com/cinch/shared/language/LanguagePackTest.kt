package com.cinch.shared.language

import com.cinch.shared.category.Categorizer
import com.cinch.shared.category.KeywordDictionary
import com.cinch.shared.domain.Category
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.shared.parser.QuickEntryParser
import com.cinch.shared.receipt.OcrLine
import com.cinch.shared.receipt.OcrScan
import com.cinch.shared.receipt.ReceiptParser
import com.cinch.shared.statement.ScreenDateReader
import com.cinch.shared.statement.StatementReader
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LanguagePackTest {

    private val today = LocalDate(2025, 1, 15) // Çarşamba

    /** Telefon İngilizce: önce İngilizce, Türkçe de anlaşılır. */
    private val english = LanguagePacks.active("en-US")
    private val parser = QuickEntryParser(Categorizer(KeywordDictionary.of(english)), language = english)

    @Test
    fun activePack_putsPhoneLanguageFirst_andAlwaysKeepsTurkish() {
        assertEquals("en", LanguagePacks.active("en").code)
        assertEquals("tr", LanguagePacks.active("tr").code)
        assertEquals("tr", LanguagePacks.active("de").code) // bilinmeyen dil: Türkçe önce
        assertTrue("taksi" in LanguagePacks.active("en").keywords)
        assertTrue("taxi" in LanguagePacks.active("tr").keywords)

        // Çakışan kelimede öndeki dil geçerli.
        val a = LanguagePacks.ENGLISH.copy(keywords = mapOf("gas" to Category.ULASIM))
        val b = LanguagePacks.TURKISH.copy(keywords = mapOf("gas" to Category.FATURA))
        assertEquals(Category.ULASIM, a.merge(b).keywords["gas"])
        assertEquals(Category.FATURA, b.merge(a).keywords["gas"])
    }

    @Test
    fun englishQuickEntry_datesAmountsCategories() {
        val coffee = parser.parse("yesterday 12.50 coffee", today)
        assertEquals(1250, coffee.grossAmount?.minor)
        assertEquals(LocalDate(2025, 1, 14), coffee.date)
        assertEquals(Category.YEMEK, coffee.category)
        assertEquals(listOf("coffee"), coffee.tokens)

        // "yesterday" tek başına dün sayılmaz: kalıbın parçası.
        val taxi = parser.parse("day before yesterday 20 taxi", today)
        assertEquals(LocalDate(2025, 1, 13), taxi.date)
        assertEquals(Category.ULASIM, taxi.category)
        assertEquals(listOf("taxi"), taxi.tokens)

        val groceries = parser.parse("last friday 40 groceries", today)
        assertEquals(LocalDate(2025, 1, 10), groceries.date)
        assertEquals(Category.MARKET, groceries.category)
    }

    @Test
    fun englishQuickEntry_sharedAndRefunded() {
        val dinner = parser.parse("60 dinner split", today)
        assertEquals(0.5, dinner.chargeableFraction)
        assertEquals(listOf("dinner"), dinner.tokens)

        val concert = parser.parse("100 concert tickets half refunded", today)
        assertEquals(0.5, concert.chargeableFraction)
        assertEquals(Category.EGLENCE, concert.category)
    }

    @Test
    fun turkishStillWorks_onAnEnglishPhone() {
        val r = parser.parse("dün 120 taksi", today)
        assertEquals(LocalDate(2025, 1, 14), r.date)
        assertEquals(Category.ULASIM, r.category)
        assertEquals(0.5, parser.parse("300 yemek yarı yarıya", today).chargeableFraction)
        assertEquals(19500, parser.parse("195tl market", today).grossAmount?.minor)
    }

    @Test
    fun englishReceipt_totalNotSubtotal_merchantAndCategory() {
        val receipt = ReceiptParser(Categorizer(KeywordDictionary.of(LanguagePacks.ALL)), LanguagePacks.ALL).parse(
            """
            JOE'S COFFEE
            123 Main St
            Date: 15/03/2025
            Latte 4.50
            Muffin 4.50
            Subtotal 9.00
            Tax 0.72
            Total 9.72
            """.trimIndent(),
            today = LocalDate(2025, 3, 20),
        )
        assertEquals(Money(972, Currency.TRY), receipt.amount)
        assertEquals(LocalDate(2025, 3, 15), receipt.date)
        assertEquals("Joe's Coffee", receipt.merchant)
        assertEquals(Category.YEMEK, receipt.category)
    }

    @Test
    fun englishStatementScreen_labelsAndMonthNames() {
        fun l(text: String, x: Int, y: Int) = OcrLine(text, x.toFloat(), y.toFloat(), (x + text.length * 11).toFloat(), (y + 20).toFloat())
        val screen = listOf(
            l("Statement balance", 30, 100), l("1,234.56", 500, 105),
            l("Minimum payment due", 30, 160), l("35.00", 500, 165),
            l("Payment due date", 30, 220), l("6 Oct", 500, 225),
            l("Credit limit", 30, 280), l("5,000.00", 500, 285),
        )
        val draft = StatementReader(language = LanguagePacks.ALL).read(OcrScan(listOf(screen)), LocalDate(2026, 10, 4))

        assertEquals(Money(123_456, Currency.TRY), draft.statementBalance)
        assertEquals(Money(3_500, Currency.TRY), draft.minimumPayment)
        assertEquals(LocalDate(2026, 10, 6), draft.dueDate)
        assertEquals(Money(500_000, Currency.TRY), draft.creditLimit)
    }

    @Test
    fun monthNames_fromAllLanguages() {
        val dates = ScreenDateReader(LanguagePacks.ALL.months)
        val anchor = LocalDate(2026, 10, 4)
        assertEquals(LocalDate(2026, 10, 6), dates.parse("6 Oct", anchor))
        assertEquals(LocalDate(2026, 10, 6), dates.parse("6 Ekim", anchor))
        assertEquals(LocalDate(2026, 9, 28), dates.parse("28 September 2026", anchor))
        assertEquals(LocalDate(2026, 3, 2), dates.parse("2 Mart 2026", anchor)) // "mar" önce eşleşip "t" artmasın
    }
}
