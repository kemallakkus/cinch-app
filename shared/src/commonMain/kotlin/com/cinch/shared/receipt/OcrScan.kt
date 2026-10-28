package com.cinch.shared.receipt

/**
 * Bir ya da birkaç fotoğrafın okunan satırları, konumlarıyla. Konum, ekran fotoğraflarında etiketin değerini
 * bulmak için gerekir ("Kalan Ekstre Borcu" solda, altında "52.331,00 TL"; yanındaki sütunda "Son Ödeme Tarihi").
 */
data class OcrScan(val pages: List<List<OcrLine>>) {

    /** Fotoğraf fotoğraf, satırlar hizalarına göre birleşmiş düz metin (fiş okuma bunu kullanır). */
    val text: String
        get() = pages.joinToString("\n") { page -> OcrLayout.rows(page).joinToString("\n") }

    val isBlank: Boolean get() = pages.all { page -> page.all { it.text.isBlank() } }
}

/**
 * [OcrScan]'i gezinme rotasında taşınacak düz metne çevirir ve geri okur. Uygulama arka planda kapatılıp açılsa
 * da okunanlar kaybolmasın diye rota içinde durur; fotoğrafın kendisi saklanmaz.
 *
 * Biçim: her satır `sol üst sağ alt<TAB>yazı` (piksel, tam sayı); fotoğraflar arası ayrı bir satırda form feed.
 */
object OcrCodec {

    private const val PAGE_BREAK = "\u000C"

    fun encode(scan: OcrScan): String = scan.pages.joinToString("\n$PAGE_BREAK\n") { page ->
        page.joinToString("\n") { line ->
            val text = line.text.replace('\t', ' ').replace('\n', ' ').replace(PAGE_BREAK, " ")
            "${line.left.toInt()} ${line.top.toInt()} ${line.right.toInt()} ${line.bottom.toInt()}\t$text"
        }
    }

    fun decode(encoded: String): OcrScan {
        if (encoded.isBlank()) return OcrScan(emptyList())
        val pages = encoded.split("\n$PAGE_BREAK\n").map { page ->
            page.lines().mapNotNull { row ->
                val tab = row.indexOf('\t')
                if (tab < 0) return@mapNotNull null
                val box = row.substring(0, tab).split(' ').mapNotNull { it.toFloatOrNull() }
                if (box.size != 4) return@mapNotNull null
                OcrLine(row.substring(tab + 1), box[0], box[1], box[2], box[3])
            }
        }
        return OcrScan(pages)
    }
}
