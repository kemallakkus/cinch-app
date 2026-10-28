package com.cinch.shared.receipt

import kotlin.test.Test
import kotlin.test.assertEquals

class OcrLayoutTest {

    private fun line(text: String, left: Float, top: Float, height: Float = 20f, width: Float = 100f) =
        OcrLine(text, left, top, left + width, top + height)

    @Test
    fun labelAndAmountInSeparateColumns_joinIntoOneRow() {
        // Tanıma sol sütunu ve sağ sütunu ayrı bloklarda verir; sıra da karışık gelebilir.
        val lines = listOf(
            line("*96,15", left = 400f, top = 302f),
            line("TOPLAM", left = 20f, top = 300f),
            line("TOPKDV", left = 20f, top = 270f),
            line("*4,27", left = 400f, top = 268f),
            line("MİGROS", left = 150f, top = 10f),
        )

        assertEquals(listOf("MİGROS", "TOPKDV *4,27", "TOPLAM *96,15"), OcrLayout.rows(lines))
    }

    @Test
    fun slightlyTiltedRow_staysTogether_nextRowStaysApart() {
        val lines = listOf(
            line("EKMEK", left = 20f, top = 100f),
            line("*12,50", left = 400f, top = 108f), // hafif eğik fotoğraf: 8 piksel aşağıda
            line("SÜT", left = 20f, top = 125f), // bir alt satır
        )

        assertEquals(listOf("EKMEK *12,50", "SÜT"), OcrLayout.rows(lines))
    }

    @Test
    fun blankLines_areDropped() {
        assertEquals(listOf("A"), OcrLayout.rows(listOf(line(" ", 0f, 0f), line("A", 0f, 50f))))
    }
}
