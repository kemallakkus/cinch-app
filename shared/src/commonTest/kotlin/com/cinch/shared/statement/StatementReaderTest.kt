package com.cinch.shared.statement

import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.shared.receipt.OcrLine
import com.cinch.shared.receipt.OcrScan
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Gerçek Akbank ve QNB ekranlarından (başka telefonla çekilmiş fotoğraflar) çıkarılan satırlar ve yaklaşık
 * konumları. Kart ve hesap numaraları değiştirildi; etiketler, tutarlar ve yerleşim aynı.
 */
class StatementReaderTest {

    private val today = LocalDate(2026, 10, 4)
    private fun tl(minor: Long) = Money(minor, Currency.TRY)

    /** Satır: metin, sol, üst; genişlik yazının uzunluğundan. */
    private fun l(text: String, x: Int, y: Int, h: Int = 20, w: Int = text.length * 11) =
        OcrLine(text, x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat())

    private fun read(vararg pages: List<OcrLine>) = StatementReader().read(OcrScan(pages.toList()), today)

    /** QNB kart ekranı: üstte iki sütun (etiketler bir satırda, değerler altında), "Daha fazla" listesi, tarih şeridi. */
    private val qnb = listOf(
        l("QNB Kartlarım", 120, 260), l("Diğer Banka Kartlarım", 450, 260),
        l("Kalan Ekstre Borcu", 30, 340), l("Son Ödeme Tarihi", 610, 345),
        // Kuruş küçük puntolu: ayrı okunmuş.
        l("52.331", 30, 398, h = 32, w = 140), l(",00 TL", 172, 406, h = 22, w = 70), l("6 Ekim", 700, 400, h = 28),
        l("Daha fazla göster", 30, 480),
        l("Kalan Ekstre Borcu", 30, 560), l("52,331.00 TL", 560, 565),
        l("Dönem İçi İşlemler", 30, 620), l("5,764.34 TL", 570, 625),
        l("Gelecek Dönem Taksitler", 30, 680), l("4,195.35 TL", 570, 685),
        l("Toplam Borç", 30, 740), l("62,310.69 TL", 560, 745),
        l("Toplam borcunuz, bu karta ait kalan ekstreniz, dönem içi", 30, 790),
        l("işlemleriniz, gelecek dönem taksitleriniz ve provizyonda bekleyen", 30, 815),
        l("işlemleriniz dahil tüm borçlarınızdır.", 30, 840),
        l("Limit Bilgileri", 30, 930),
        l("Kart Limiti", 30, 1000), l("95,600.00 TL", 560, 1005),
        l("Kullanılabilir Limit", 30, 1045), l("33,313.31 TL", 560, 1050),
        l("Kart Tarih Bilgileri", 30, 1140),
        // Tarih şeridi: küçük etiketler iki satır, tarihler yuvarlak içinde gün ve ay alt alta.
        l("Ekstre Kesim", 30, 1210, h = 16), l("Tarihi", 30, 1228, h = 16), l("26", 40, 1290, h = 18), l("EYL", 35, 1310, h = 16),
        l("Son Ödeme", 270, 1215, h = 16), l("Tarihi", 280, 1233, h = 16), l("6", 300, 1295, h = 18), l("EKİ", 290, 1312, h = 16),
        l("Ekstre Kesim", 600, 1225, h = 16), l("Tarihi", 640, 1243, h = 16), l("26", 650, 1300, h = 18), l("EKİ", 645, 1318, h = 16),
    )

    @Test
    fun qnb_twoColumnHeader_mixedNumberFormats_dateStrip() {
        val d = read(qnb)

        assertEquals(tl(5_233_100), d.statementBalance)
        assertEquals(LocalDate(2026, 10, 6), d.dueDate)
        assertEquals(tl(576_434), d.periodSpending)
        assertEquals(tl(419_535), d.futureInstallments)
        assertEquals(tl(6_231_069), d.totalDebt)
        assertEquals(tl(9_560_000), d.creditLimit)
        assertEquals(tl(3_331_331), d.availableLimit)
        // Şeritte iki kesim tarihi: son ödemeden önceki bu ekstre, sonraki bir sonraki.
        assertEquals(LocalDate(2026, 9, 26), d.statementDate)
        assertEquals(LocalDate(2026, 10, 26), d.nextStatementDate)
        assertTrue(d.transactions.isEmpty())
        assertNull(d.last4)
    }

    @Test
    fun qnb_lastDaysBanner() {
        val d = read(
            listOf(
                l("Kalan Ekstre Borcu", 165, 300), l("Son Ödeme Tarihi", 660, 305),
                l("31.393,55 TL", 165, 355, h = 30), l("Son 2 gün! (6 Ekim)", 527, 355, h = 30),
            ),
        )
        assertEquals(tl(3_139_355), d.statementBalance)
        assertEquals(LocalDate(2026, 10, 6), d.dueDate)
    }

    /** Akbank Axess kart ekranı: "Etiket: değer" satırları, "Son gün" altında ödenecek tutar, dönem içi hareket. */
    private val akbank = listOf(
        l("•••• 1243", 120, 160),
        l("Dönem içi harcama", 120, 240),
        l("1.455,00 TL", 120, 268, h = 30),
        l("Kullanılabilir limit: -296,25 TL", 120, 345),
        l("Toplam limit: 22.650,00 TL", 120, 372),
        l("Kazanılan Chip-Para:", 160, 460), l("0,00 TL", 680, 460),
        l("Ekstre kesim tarihi:", 120, 715), l("28.09.2026", 640, 715),
        l("Son ödeme tarihi:", 120, 760), l("08.10.2026", 640, 760),
        l("Sonraki ekstre kesimi:", 120, 805), l("30.10.2026", 640, 805),
        l("Sonraki son ödeme:", 120, 850), l("09.11.2026", 640, 850),
        l("Son gün: 8 Ekim", 225, 935), l("Ekstreni öde", 600, 962),
        l("16.378,39 TL", 225, 975, h = 24),
        l("Ekstre", 250, 1110), l("Harcamalar", 560, 1110),
        l("Dönem içi", 175, 1225), l("Bekleyen", 380, 1225), l("Gelecek dönem", 550, 1225),
        l("TRENDYOL.COM (04/06)", 80, 1300), l("1.455,00 TL", 680, 1310),
        l("24.10.2026 - Taksit: 04/06", 80, 1332, h = 18),
    )

    @Test
    fun akbank_labelValueRows_sonGun_installmentTransaction() {
        val d = read(akbank)

        assertEquals("1243", d.last4)
        assertEquals(tl(1_637_839), d.statementBalance) // "Son gün: 8 Ekim" altındaki
        assertEquals(LocalDate(2026, 10, 8), d.dueDate)
        assertEquals(LocalDate(2026, 9, 28), d.statementDate)
        assertEquals(LocalDate(2026, 10, 30), d.nextStatementDate) // "Sonraki ekstre kesimi", "ekstre kesim" değil
        assertEquals(LocalDate(2026, 11, 9), d.nextDueDate)
        assertEquals(tl(145_500), d.periodSpending)
        assertEquals(tl(2_265_000), d.creditLimit)
        assertEquals(tl(-29_625), d.availableLimit) // limit aşılmış

        val tx = d.transactions.single()
        assertEquals("TRENDYOL.COM", tx.description)
        assertEquals(tl(145_500), tx.amount)
        assertEquals(LocalDate(2026, 10, 24), tx.date)
        assertEquals(Installment(4, 6), tx.installment)
    }

    @Test
    fun akbank_paymentRow_isNegative() {
        val d = read(
            listOf(
                l("•••• 8804", 165, 250),
                l("İNTERNET Şb-Ödemeniz için Teşekkürler", 146, 1120), l("-2.202,86 TL", 580, 1130),
                l("30.09.2026", 146, 1142),
            ),
        )
        val tx = d.transactions.single()
        assertTrue(tx.isPaymentOrRefund)
        assertEquals(tl(-220_286), tx.amount)
    }

    @Test
    fun cardListScreen_withTwoCards_leavesCardChoiceToUser() {
        val d = read(
            listOf(
                l("Kullanılabilir limit", 250, 1280), l("10.154,00 TL", 250, 1300),
                l("Axess Platinum Troy", 250, 1330), l("•••• 1243", 250, 1355),
                l("Kullanılabilir limit", 250, 1430), l("10.154,00 TL", 250, 1452),
                l("Axess Platin MC D", 250, 1482), l("•••• 8804", 250, 1507),
            ),
        )
        assertNull(d.last4)
        assertEquals(listOf("1243", "8804"), d.last4Seen)
    }

    @Test
    fun futureInstallmentsTab_isNotReadAsTransactions() {
        val d = read(
            listOf(
                l("TOPLAM", 130, 900), l("2.910,00 TL", 590, 900),
                l("28 Kasım 2026", 130, 935), l("1.455,00 TL", 130, 958),
                l("28 Aralık 2026", 130, 1012), l("1.455,00 TL", 130, 1035),
            ),
        )
        assertTrue(d.transactions.isEmpty())
    }

    @Test
    fun severalPhotos_areMerged() {
        val header = qnb.take(7)
        val strip = qnb.drop(qnb.indexOfFirst { it.text == "Kart Tarih Bilgileri" })
        val d = read(header, strip)

        assertEquals(tl(5_233_100), d.statementBalance)
        assertEquals(LocalDate(2026, 10, 6), d.dueDate)
        assertEquals(LocalDate(2026, 9, 26), d.statementDate)
    }

    @Test
    fun unrelatedPhoto_isEmpty() {
        val d = read(listOf(l("fun main() {", 10, 10), l("println(\"merhaba\")", 30, 40), l("}", 10, 70)))
        assertTrue(d.isEmpty)
    }
}
