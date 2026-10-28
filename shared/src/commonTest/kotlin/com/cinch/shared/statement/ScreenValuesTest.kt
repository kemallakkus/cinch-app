package com.cinch.shared.statement

import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScreenValuesTest {

    private fun tl(minor: Long) = Money(minor, Currency.TRY)

    @Test
    fun money_bothFormats_sameScreen() {
        // QNB aynı ekranda ikisini birden kullanıyor.
        assertEquals(tl(5_233_100), ScreenMoney.parse("52.331,00 TL"))
        assertEquals(tl(5_233_100), ScreenMoney.parse("52,331.00 TL"))
        assertEquals(tl(130_000), ScreenMoney.parse("1,300.00 TL"))
        assertEquals(tl(1_637_839), ScreenMoney.parse("16.378,39"))
        assertEquals(tl(4_750), ScreenMoney.parse("47,50 TL"))
    }

    @Test
    fun money_splitDecimals_negative_zero() {
        // Kuruş küçük puntoyla yazıldığı için ayrı okunmuş.
        assertEquals(tl(5_233_100), ScreenMoney.parse("52.331 ,00 TL"))
        assertEquals(tl(-29_625), ScreenMoney.parse("Kullanılabilir limit: -296,25 TL"))
        assertEquals(tl(-220_286), ScreenMoney.parse("-2.202,86 TL"))
        assertEquals(tl(0), ScreenMoney.parse("0,00 TL"))
        assertEquals(tl(0), ScreenMoney.parse("O,OO TL")) // tanıma sıfırı O okumuş
        assertEquals(tl(5_233_100), ScreenMoney.parse("52.331 TL")) // kuruş hiç okunmamış
    }

    @Test
    fun money_ignoresDatesInstallmentsAndAccountNumbers() {
        assertNull(ScreenMoney.parse("Son 2 gün! (6 Ekim)"))
        assertNull(ScreenMoney.parse("24.10.2026 - Taksit: 04/06"))
        assertNull(ScreenMoney.parse("SANAYİ/İZMİT 0693-0299906"))
        assertNull(ScreenMoney.parse("SAAT 14:22"))
        assertNull(ScreenMoney.parse("TOPLAM"))
    }

    @Test
    fun dates_numericAndNamed() {
        val anchor = LocalDate(2026, 10, 4)
        assertEquals(LocalDate(2026, 9, 28), ScreenDate.parse("28.09.2026", anchor))
        assertEquals(LocalDate(2026, 10, 6), ScreenDate.parse("6 Ekim", anchor))
        assertEquals(LocalDate(2026, 10, 6), ScreenDate.parse("Son 2 gün! (6 Ekim)", anchor))
        assertEquals(LocalDate(2026, 9, 26), ScreenDate.parse("26 EYL", anchor))
        assertEquals(LocalDate(2026, 10, 6), ScreenDate.parse("6 EKİ", anchor))
        assertEquals(LocalDate(2026, 11, 28), ScreenDate.parse("28 Kasım 2026", anchor))
        assertTrue(ScreenDate.findAll("28 Kasım 2026", anchor).single().hasYear)
    }

    @Test
    fun dates_yearIsTheClosestOne_andWordsAreNotMonths() {
        // Aralık sonunda okunan "6 Ocak" gelecek yılın 6 Ocak'ı.
        assertEquals(LocalDate(2027, 1, 6), ScreenDate.parse("Son ödeme 6 Ocak", LocalDate(2026, 12, 28)))
        assertNull(ScreenDate.parse("2 market", LocalDate(2026, 10, 4)))
        assertNull(ScreenDate.parse("31 Şubat", LocalDate(2026, 10, 4)))
    }
}
