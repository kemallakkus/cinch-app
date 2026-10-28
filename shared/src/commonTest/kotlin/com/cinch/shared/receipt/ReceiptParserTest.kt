package com.cinch.shared.receipt

import com.cinch.shared.domain.Category
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReceiptParserTest {

    private val parser = ReceiptParser()
    private val today = LocalDate(2025, 3, 15)
    private fun tl(minor: Long) = Money(minor, Currency.TRY)

    /** Yazar kasa fişi, satırlar [OcrLayout]'tan çıktığı gibi (etiket ve tutar aynı satırda). */
    private val migros = """
        MİGROS TİCARET A.Ş.
        ATAŞEHİR ŞUBESİ
        ATATÜRK MAH. TURGUT ÖZAL BLV. NO:12
        ÜMRANİYE V.D. VKN: 6220529513
        TARİH : 15/03/2025 SAAT : 14:22
        FİŞ NO : 0042
        EKMEK *12,50
        SÜT 1L *34,90
        DOMATES KG *48,75
        TOPKDV *4,27
        TOPLAM *96,15
        KREDİ KARTI *96,15
        **** **** **** 1234
    """.trimIndent()

    @Test
    fun cashRegisterReceipt() {
        val r = parser.parse(migros, today)

        assertEquals(tl(9_615), r.amount)
        assertEquals(LocalDate(2025, 3, 15), r.date)
        assertEquals("Migros", r.merchant)
        assertEquals(Category.MARKET, r.category)
        assertFalse(r.isBill)
        assertTrue(r.paidByCard)
        assertNull(r.dueDate)
    }

    @Test
    fun cashAndChange_doNotBeatTheTotal() {
        val r = parser.parse(
            """
            ŞOK MARKETLER T.A.Ş.
            TARİH: 14.03.2025
            TOPLAM *96,15
            NAKİT *100,00
            PARA ÜSTÜ *3,85
            """.trimIndent(),
            today,
        )

        assertEquals(tl(9_615), r.amount)
        assertEquals("Şok Marketler", r.merchant) // "T.A.Ş." atılır
        assertFalse(r.paidByCard)
    }

    @Test
    fun labelAndAmountOnSeparateRows_andOcrSpaces() {
        val separate = parser.parse("BİM\nTOPLAM\n*96,15", today)
        assertEquals(tl(9_615), separate.amount)

        val spaced = parser.parse("BİM\nTOPLAM *96 ,15", today)
        assertEquals(tl(9_615), spaced.amount)
    }

    @Test
    fun datesAndTimes_areNotAmounts() {
        val r = parser.parse("KAFE\nTARİH 15.03.2025 SAAT 14:22\nTOPLAM 1.234,56 TL", today)
        assertEquals(tl(123_456), r.amount)
    }

    @Test
    fun withoutTotalLabel_largestStarredAmountWins_ignoringCash() {
        val r = parser.parse("KAFE\nÇAY *15,00\nTOST *85,00\nNAKİT *200,00", today)
        assertEquals(tl(8_500), r.amount)
    }

    @Test
    fun futureDate_isTreatedAsMisread() {
        val r = parser.parse("KAFE\nTARİH 15.08.2025\nTOPLAM *50,00", today)
        assertNull(r.date)
    }

    @Test
    fun electricityBill() {
        val r = parser.parse(
            """
            ENERJİSA İSTANBUL ANADOLU YAKASI
            ELEKTRİK PERAKENDE SATIŞ A.Ş.
            e-Arşiv Fatura
            Sayın KEMAL A.
            Fatura Tarihi: 05.03.2025
            Abone No: 4001234567
            Tüketim (kWh) 245,500
            Enerji Bedeli 610,20
            Dağıtım Bedeli 250,30
            KDV (%20) 172,10
            Ödenecek Tutar 1.032,60 TL
            Son Ödeme Tarihi 20.03.2025
            """.trimIndent(),
            today,
        )

        assertTrue(r.isBill)
        assertEquals(tl(103_260), r.amount)
        assertEquals(LocalDate(2025, 3, 5), r.date)
        assertEquals(LocalDate(2025, 3, 20), r.dueDate) // son ödeme gelecekte olabilir
        assertEquals("Enerjisa İstanbul Anadolu", r.merchant)
        assertEquals(Category.FATURA, r.category)
    }

    @Test
    fun dueDateOnTheRowBelowItsLabel() {
        val r = parser.parse("TÜRK TELEKOM\nAbone No 123\nSon Ödeme Tarihi\n28.03.2025\nÖdenecek Tutar 449,90", today)
        assertEquals(LocalDate(2025, 3, 28), r.dueDate)
        assertNull(r.date)
    }

    @Test
    fun merchant_skipsHeaderRows_andCleansLegalWords() {
        val r = parser.parse("e-ARŞİV FATURA\nHOŞGELDİNİZ\nBİM BİRLEŞİK MAĞAZALAR A.Ş.\nTOPLAM *10,00", today)
        assertEquals("Bim Birleşik Mağazalar", r.merchant)
    }

    @Test
    fun category_fromMerchantName_elseFromItems() {
        assertEquals(Category.SAGLIK, parser.parse("ECZANE GÜLER\nTOPLAM *120,00", today).category)
    }

    @Test
    fun unreadablePhoto_isEmpty() {
        val r = parser.parse("~~ ..\n12", today)
        assertTrue(r.isEmpty)
        assertNull(r.amount)
    }
}
