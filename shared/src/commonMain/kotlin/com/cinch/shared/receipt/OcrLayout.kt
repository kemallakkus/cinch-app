package com.cinch.shared.receipt

/**
 * Metin tanımanın bulduğu tek satır ve görüntüdeki kutusu (piksel ya da 0–1 arası; birim önemsiz, oran önemli).
 * y aşağı doğru artar.
 */
data class OcrLine(val text: String, val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val centerY: Float get() = (top + bottom) / 2
    val height: Float get() = bottom - top
}

/**
 * Metin tanıma fişi bloklara böler: "TOPLAM" sol sütunda, "*245,90" sağ sütunda ayrı bloklarda gelir ve düz
 * metinde alt alta düşer. Bu nesne satırları görüntüdeki yüksekliklerine göre yeniden dizer: aynı hizadakiler
 * soldan sağa tek satırda birleşir ("TOPLAM *245,90").
 */
object OcrLayout {

    /** İki satır, dikey merkezleri arasındaki fark kısa olanın yüksekliğinin bu oranından azsa aynı hizada. */
    private const val SAME_ROW_RATIO = 0.6f

    fun rows(lines: List<OcrLine>): List<String> {
        val rows = mutableListOf<MutableList<OcrLine>>()
        for (line in lines.filter { it.text.isNotBlank() }.sortedBy { it.centerY }) {
            val row = rows.lastOrNull()
            val anchor = row?.let { r -> r.sumOf { it.centerY.toDouble() }.toFloat() / r.size }
            val tolerance = row?.let { r -> minOf(line.height, r.minOf { it.height }) * SAME_ROW_RATIO }
            if (row != null && anchor != null && tolerance != null && kotlin.math.abs(line.centerY - anchor) <= tolerance) {
                row += line
            } else {
                rows += mutableListOf(line)
            }
        }
        return rows.map { row -> row.sortedBy { it.left }.joinToString(" ") { it.text.trim() } }
    }
}
