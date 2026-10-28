package com.cinch.shared.receipt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OcrCodecTest {

    @Test
    fun roundTrip_keepsPagesLinesAndPositions() {
        val scan = OcrScan(
            listOf(
                listOf(OcrLine("Kalan Ekstre Borcu", 30f, 340f, 228f, 360f), OcrLine("52.331,00 TL", 30f, 398f, 170f, 430f)),
                listOf(OcrLine("Son Ödeme Tarihi", 610f, 345f, 786f, 365f)),
            ),
        )

        assertEquals(scan, OcrCodec.decode(OcrCodec.encode(scan)))
    }

    @Test
    fun tabsInText_doNotBreakTheFormat() {
        val scan = OcrScan(listOf(listOf(OcrLine("A\tB", 1f, 2f, 3f, 4f))))
        assertEquals("A B", OcrCodec.decode(OcrCodec.encode(scan)).pages.single().single().text)
    }

    @Test
    fun emptyText_decodesToNoPages() {
        assertTrue(OcrCodec.decode("").pages.isEmpty())
    }
}
