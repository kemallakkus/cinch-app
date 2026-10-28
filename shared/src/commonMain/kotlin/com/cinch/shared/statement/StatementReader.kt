package com.cinch.shared.statement

import com.cinch.shared.category.TextNormalizer
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.shared.receipt.OcrLine
import com.cinch.shared.receipt.OcrScan
import kotlinx.datetime.LocalDate
import kotlin.math.abs

/** "TRENDYOL.COM (04/06)": 6 taksitin 4.'sü. */
data class Installment(val number: Int, val total: Int)

/**
 * Kart hareketi. [amount] eksiyse ödeme ya da iade ("Ödemeniz için Teşekkürler -2.202,86 TL"): harcama değil.
 */
data class StatementTransaction(
    val date: LocalDate,
    val description: String,
    val amount: Money,
    val installment: Installment? = null,
) {
    val isPaymentOrRefund: Boolean get() = amount.isNegative
}

/**
 * Banka uygulamasındaki kart ekranının fotoğraf(lar)ından okunanlar. Okunamayan alan null; kullanıcı onay
 * ekranında düzeltir.
 *
 * @param last4 tek bir kart numarası görüldüyse son 4 hanesi ("•••• 1243").
 * @param last4Seen ekranda görülen tüm son 4 haneler (kart listesi ekranında birden çok olur).
 * @param statementBalance ödenecek ekstre borcu ("Kalan Ekstre Borcu", "Dönem Borcu", Akbank'ta "Son gün" altı).
 * @param availableLimit eksi olabilir: limit aşılmış.
 */
data class StatementDraft(
    val last4: String?,
    val last4Seen: List<String>,
    val statementBalance: Money?,
    val minimumPayment: Money?,
    val dueDate: LocalDate?,
    val statementDate: LocalDate?,
    val nextStatementDate: LocalDate?,
    val nextDueDate: LocalDate?,
    val periodSpending: Money?,
    val futureInstallments: Money?,
    val totalDebt: Money?,
    val creditLimit: Money?,
    val availableLimit: Money?,
    val transactions: List<StatementTransaction>,
    val rows: List<String>,
) {
    /** Hiçbir şey okunamadı: kart ekranı değil ya da fotoğraf çok bulanık. */
    val isEmpty: Boolean
        get() = listOf(
            statementBalance, minimumPayment, dueDate, statementDate, periodSpending, totalDebt, creditLimit, availableLimit,
        ).all { it == null } && transactions.isEmpty()
}

/**
 * Bankaya özel şablon kullanmadan kart ekranı okur. İki fikir:
 *
 * 1. **Ortak etiketler.** Son ödeme tarihi, hesap kesim tarihi, ekstre borcu, asgari ödeme ekstrede bulunması
 *    gereken kalemler; bankalar bunlara benzer adlar verir. Eş anlamlılar [Field] listesinde; yeni banka
 *    çoğunlukla buraya bir kelime demektir.
 * 2. **Değer etiketin yanında ya da altında.** Önce etiketin kendi satırına ("Ekstre kesim tarihi: 28.09.2026"),
 *    sonra aynı hizada sağına ("Toplam Borç ....... 62,310.69 TL"), sonra hemen altına, aynı sütunda bakılır
 *    (QNB: "Kalan Ekstre Borcu" ve "Son Ödeme Tarihi" yan yana, değerleri altlarında).
 *
 * Girdi başka bir telefonla çekilmiş ekran fotoğrafıdır (banka uygulamaları ekran görüntüsünü engeller): arkadaki
 * monitör ya da klavyedeki yazılar etikete bağlanmadıkça yok sayılır, hafif eğiklik tolere edilir.
 */
class StatementReader(private val currency: Currency = Currency.TRY) {

    fun read(scan: OcrScan, today: LocalDate): StatementDraft {
        val money = mutableMapOf<Field, MutableList<Money>>()
        val dates = mutableMapOf<Field, MutableList<LocalDate>>()
        val last4 = linkedSetOf<String>()
        val transactions = mutableListOf<StatementTransaction>()

        for (page in scan.pages) {
            val lines = page.filter { it.text.isNotBlank() }.sortedWith(compareBy({ it.centerY }, { it.left }))
            val labels = lines.indices.mapNotNull { i -> labelOf(lines[i])?.let { i to it } }.toMap()
            val used = labels.keys.toMutableSet()

            for ((i, label) in labels) {
                when (label.field.kind) {
                    Kind.MONEY -> findValue(lines, i, label, labels, Direction.ANY) { parseMoney(it) }?.let { (value, at) ->
                        money.getOrPut(label.field) { mutableListOf() } += value
                        used += at
                    }
                    Kind.DATE -> findValue(lines, i, label, labels, Direction.ANY, joinBelow = true) { ScreenDate.parse(it, today) }
                        ?.let { (value, at) ->
                            dates.getOrPut(label.field) { mutableListOf() } += value
                            used += at
                        }
                    // Akbank: "Son gün: 8 Ekim" ve altında ödenecek tutar.
                    Kind.DATE_THEN_MONEY -> {
                        findValue(lines, i, label, labels, Direction.SAME_ROW) { ScreenDate.parse(it, today) }?.let { (value, at) ->
                            dates.getOrPut(Field.DUE_DATE) { mutableListOf() } += value
                            used += at
                        }
                        findValue(lines, i, label, labels, Direction.BELOW) { parseMoney(it) }?.let { (value, at) ->
                            money.getOrPut(Field.STATEMENT_BALANCE) { mutableListOf() } += value
                            used += at
                        }
                    }
                }
            }
            lines.forEach { line -> LAST4.findAll(line.text).forEach { last4 += it.groupValues[1] } }
            transactions += readTransactions(lines, used, today)
        }

        val due = dates[Field.DUE_DATE]?.firstOrNull()
        // QNB'nin tarih şeridinde iki "Ekstre Kesim Tarihi" var (geçen ve gelecek): son ödemeden öncesi bu ekstre.
        val cuts = dates[Field.STATEMENT_DATE].orEmpty().distinct().sorted()
        val statementDate = if (due != null) cuts.lastOrNull { it < due } ?: cuts.firstOrNull() else cuts.lastOrNull { it <= today } ?: cuts.firstOrNull()
        val nextStatement = dates[Field.NEXT_STATEMENT]?.firstOrNull()
            ?: cuts.firstOrNull { statementDate != null && it > statementDate }

        return StatementDraft(
            last4 = last4.singleOrNull(),
            last4Seen = last4.toList(),
            statementBalance = money[Field.STATEMENT_BALANCE]?.firstOrNull(),
            minimumPayment = money[Field.MINIMUM_PAYMENT]?.firstOrNull(),
            dueDate = due,
            statementDate = statementDate,
            nextStatementDate = nextStatement,
            nextDueDate = dates[Field.NEXT_DUE]?.firstOrNull(),
            periodSpending = money[Field.PERIOD_SPENDING]?.firstOrNull(),
            futureInstallments = money[Field.FUTURE_INSTALLMENTS]?.firstOrNull(),
            totalDebt = money[Field.TOTAL_DEBT]?.firstOrNull(),
            creditLimit = money[Field.CREDIT_LIMIT]?.firstOrNull(),
            availableLimit = money[Field.AVAILABLE_LIMIT]?.firstOrNull(),
            transactions = transactions.distinctBy { Triple(it.date, it.amount, TextNormalizer.normalize(it.description)) },
            rows = scan.text.lines().filter { it.isNotBlank() },
        )
    }

    private fun parseMoney(text: String): Money? = ScreenMoney.parse(text, currency)

    // ───────────────────────── Etiketler ─────────────────────────

    private enum class Kind { MONEY, DATE, DATE_THEN_MONEY }

    /** Okunan alanlar ve ekranlarda geçen adları (Türkçe harfler indirgenmiş, küçük harf). */
    private enum class Field(val kind: Kind, val phrases: List<String>, val excludes: List<String> = emptyList()) {
        STATEMENT_BALANCE(
            Kind.MONEY,
            listOf(
                "kalan ekstre borcu", "ekstre borcu", "kalan donem borcu", "donem borcu", "hesap ozeti borcu",
                "ekstreye yansiyan islemler", "ekstre tutari", "odenecek tutar",
            ),
        ),
        MINIMUM_PAYMENT(Kind.MONEY, listOf("asgari odeme tutari", "kalan asgari odeme", "asgari odeme", "asgari tutar")),
        DUE_DATE(Kind.DATE, listOf("son odeme tarihi", "son odeme gunu", "son odeme")),
        SON_GUN(Kind.DATE_THEN_MONEY, listOf("son gun")),
        NEXT_DUE(Kind.DATE, listOf("sonraki son odeme")),
        STATEMENT_DATE(Kind.DATE, listOf("ekstre kesim tarihi", "hesap kesim tarihi", "ekstre kesim", "hesap kesim", "kesim tarihi")),
        NEXT_STATEMENT(Kind.DATE, listOf("sonraki ekstre kesim", "sonraki hesap kesim", "sonraki kesim")),
        PERIOD_SPENDING(Kind.MONEY, listOf("donem ici harcama", "donem ici islemler", "guncel donem harcama")),
        FUTURE_INSTALLMENTS(Kind.MONEY, listOf("gelecek donem taksit", "kalan taksitler")),
        TOTAL_DEBT(Kind.MONEY, listOf("toplam borc", "guncel borc", "toplam kart borcu")),
        CREDIT_LIMIT(Kind.MONEY, listOf("kart limiti", "toplam limit", "kredi limiti"), excludes = listOf("sanal", "internet")),
        AVAILABLE_LIMIT(Kind.MONEY, listOf("kullanilabilir limit"), excludes = listOf("sanal", "internet")),
    }

    /** @param end etiketin satırda bittiği yer; değer aynı satırdaysa buradan sonrası. */
    private class Label(val field: Field, val end: Int)

    /**
     * En uzun eşleşen ad kazanır: "Sonraki son ödeme" bir "son ödeme" değildir. Uzun cümleler etiket sayılmaz
     * ("Toplam borcunuz, bu karta ait kalan ekstreniz…" açıklamasındaki "toplam borç" gibi).
     */
    private fun labelOf(line: OcrLine): Label? {
        val folded = TextNormalizer.fold(line.text)
        var best: Label? = null
        var bestLength = 0
        for (field in Field.entries) {
            if (field.excludes.any { it in folded }) continue
            for (phrase in field.phrases) {
                val at = folded.indexOf(phrase)
                if (at < 0 || phrase.length <= bestLength) continue
                if (folded.trim().length > phrase.length + MAX_LABEL_EXTRA) continue
                best = Label(field, at + phrase.length)
                bestLength = phrase.length
            }
        }
        return best
    }

    // ───────────────────────── Değer arama ─────────────────────────

    private enum class Direction { ANY, SAME_ROW, BELOW }

    /**
     * Etiketin değeri ve bulunduğu satır(lar). Sıra: etiketin kendi satırı, aynı hizada sağı, altı (aynı sütun).
     * [joinBelow]: tarih şeridinde gün ve ay alt alta yazılabilir ("26" / "EYL").
     */
    private fun <T> findValue(
        lines: List<OcrLine>,
        index: Int,
        label: Label,
        labels: Map<Int, Label>,
        direction: Direction,
        joinBelow: Boolean = false,
        parse: (String) -> T?,
    ): Pair<T, Set<Int>>? {
        val line = lines[index]
        if (direction != Direction.BELOW) {
            parse(line.text.substring(minOf(label.end, line.text.length)))?.let { return it to emptySet() }

            // Aynı hizada sağdakiler; başka bir etikete gelince durulur (yan sütunun etiketi).
            val right = lines.indices
                .filter { it != index && sameRow(lines[it], line) && lines[it].left >= line.right - line.height }
                .sortedBy { lines[it].left }
                .takeWhile { it !in labels }
            if (right.isNotEmpty()) {
                parse(right.joinToString(" ") { lines[it].text })?.let { return it to right.toSet() }
            }
        }
        if (direction == Direction.SAME_ROW) return null

        val spanLeft = line.left - line.width * COLUMN_SLACK
        val spanRight = line.right + line.width * COLUMN_SLACK
        val below = lines.indices
            .filter { j ->
                val other = lines[j]
                j != index && other.centerY > line.bottom &&
                    other.top - line.bottom <= line.height * MAX_GAP_BELOW &&
                    other.right >= spanLeft && other.left <= spanRight
            }
            .sortedBy { lines[it].top }
        for ((k, j) in below.withIndex()) {
            if (j in labels) break // altta başka bir etiket: bu etiketin değeri yok
            val row = rowAround(lines, j, spanLeft, spanRight, labels)
            parse(row.joinToString(" ") { lines[it].text })?.let { return it to row.toSet() }
            if (joinBelow) {
                val next = below.getOrNull(k + 1)?.takeIf { it !in labels } ?: continue
                val joined = row + rowAround(lines, next, spanLeft, spanRight, labels)
                parse(joined.joinToString(" ") { lines[it].text })?.let { return it to joined.toSet() }
            }
        }
        return null
    }

    /** [index] satırıyla aynı hizada ve aynı sütundaki parçalar ("52.331" ve ",00 TL" ayrı okunmuş olabilir). */
    private fun rowAround(lines: List<OcrLine>, index: Int, spanLeft: Float, spanRight: Float, labels: Map<Int, Label>): List<Int> =
        lines.indices
            .filter { it == index || (it !in labels && sameRow(lines[it], lines[index]) && lines[it].right >= spanLeft && lines[it].left <= spanRight) }
            .sortedBy { lines[it].left }

    private fun sameRow(a: OcrLine, b: OcrLine): Boolean =
        abs(a.centerY - b.centerY) <= minOf(a.height, b.height) * SAME_ROW_RATIO

    // ───────────────────────── Hareketler ─────────────────────────

    /**
     * Hareket satırı: tam tarihli bir satır (yılıyla: "24.10.2026"), hemen üstünde açıklama ("TRENDYOL.COM (04/06)"),
     * sağ tarafta tutar. Gelecek dönem listesindeki "TOPLAM" ve açıklamasız satırlar alınmaz.
     */
    private fun readTransactions(lines: List<OcrLine>, used: Set<Int>, today: LocalDate): List<StatementTransaction> =
        lines.indices.mapNotNull { j ->
            if (j in used) return@mapNotNull null
            val dateLine = lines[j]
            val date = ScreenDate.findAll(dateLine.text, today).firstOrNull { it.hasYear }?.date ?: return@mapNotNull null

            val descIndex = lines.indices
                .filter { k ->
                    val d = lines[k]
                    k != j && k !in used && d.bottom <= dateLine.top + dateLine.height * 0.5f &&
                        dateLine.top - d.bottom <= dateLine.height * 2 &&
                        d.right >= dateLine.left && d.left <= dateLine.right &&
                        d.text.count { it.isLetter() } >= 3 && ScreenDate.findAll(d.text, today).isEmpty()
                }
                .maxByOrNull { lines[it].bottom } ?: return@mapNotNull null
            val desc = lines[descIndex]
            if (TOTAL_WORDS.any { it in TextNormalizer.fold(desc.text) }) return@mapNotNull null

            // Tutar sağ sütunda, açıklama ile tarih satırlarının hizasında.
            val top = desc.top - desc.height * 0.5f
            val bottom = dateLine.bottom + dateLine.height * 0.5f
            val amount = lines.indices
                .filter { m ->
                    m != j && m != descIndex && m !in used &&
                        lines[m].centerY in top..bottom && lines[m].left >= minOf(desc.right, dateLine.right) - desc.height
                }
                .sortedBy { abs(lines[it].centerY - desc.centerY) }
                .firstNotNullOfOrNull { parseMoney(lines[it].text) }
                ?: parseMoney(desc.text.substringAfterLast(')', ""))
                ?: return@mapNotNull null

            StatementTransaction(
                date = date,
                description = desc.text.replace(INSTALLMENT_IN_PARENS, "").trim(),
                amount = amount,
                installment = installmentIn(desc.text) ?: installmentIn(dateLine.text),
            )
        }

    private fun installmentIn(text: String): Installment? {
        val folded = TextNormalizer.fold(text)
        val match = INSTALLMENT.find(folded) ?: return null
        if ("taksit" !in folded && !INSTALLMENT_IN_PARENS.containsMatchIn(text)) return null
        val number = match.groupValues[1].toInt()
        val total = match.groupValues[2].toInt()
        return if (total in 2..60 && number in 1..total) Installment(number, total) else null
    }

    private companion object {
        /** Etiket satırında etiketten sonra en fazla bu kadar karakter olabilir (değer dahil); fazlası cümledir. */
        const val MAX_LABEL_EXTRA = 30
        const val SAME_ROW_RATIO = 0.6f

        /**
         * Alttaki değer, etiketin altında en fazla bu kadar satır yüksekliği uzakta olabilir. QNB'nin tarih şeridinde
         * küçük puntolu etiketle yuvarlak içindeki tarih ("26 EYL") arası 4–5 satır.
         */
        const val MAX_GAP_BELOW = 6f

        /** Sütun genişliği payı: değer etiketten biraz kaymış ya da taşmış olabilir. */
        const val COLUMN_SLACK = 0.5f

        /** "•••• 1243", "**** 1234", "....1243". */
        val LAST4 = Regex("(?:[•*·∙●.]{3,}|[xX]{3,})\\s*(\\d{4})(?!\\d)")
        val INSTALLMENT = Regex("(\\d{1,2})\\s*/\\s*(\\d{1,2})")
        val INSTALLMENT_IN_PARENS = Regex("\\(\\s*\\d{1,2}\\s*/\\s*\\d{1,2}\\s*\\)")
        val TOTAL_WORDS = listOf("toplam", "kullanilabilir", "limit")
    }
}

private val OcrLine.width: Float get() = right - left
