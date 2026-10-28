package com.cinch.shared.receipt

import com.cinch.shared.category.Categorizer
import com.cinch.shared.category.TextNormalizer
import com.cinch.shared.domain.Category
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.shared.money.MoneyInput
import kotlinx.datetime.LocalDate

/**
 * Fiş ya da faturadan okunan taslak. Hepsi tahmindir; kullanıcı onay ekranında düzeltir.
 *
 * @param amount ödenen toplam; bulunamazsa null.
 * @param date fişin tarihi (faturada düzenleme tarihi); bulunamazsa ya da gelecekteyse null.
 * @param dueDate faturada son ödeme tarihi.
 * @param merchant mağaza ya da kurum adı, okunur biçimde ("Migros Sanal Market").
 * @param category tahmini kategori; faturada [Category.FATURA].
 * @param isBill elektrik, su, doğalgaz, internet gibi abonelik faturası: sabit gidere bağlanması önerilir.
 * @param paidByCard fişte kartla ödendiği yazıyor ("KREDİ KARTI", "**** 1234"); hangi kart olduğu bilinmez.
 * @param rows okunan satırlar; onay ekranında "okunan metin" olarak gösterilir.
 */
data class ReceiptDraft(
    val amount: Money?,
    val date: LocalDate?,
    val dueDate: LocalDate?,
    val merchant: String?,
    val category: Category,
    val isBill: Boolean,
    val paidByCard: Boolean,
    val rows: List<String>,
) {
    /** Ne tutar ne ad okunabildi: bulanık ya da fiş olmayan bir fotoğraf. */
    val isEmpty: Boolean get() = amount == null && merchant == null
}

/**
 * Fiş ve fatura metnini (satır satır, bkz. [OcrLayout]) ayrıştırır. Türkiye'deki yazar kasa fişleri
 * ("TOPLAM *245,90", "TARİH: 15/03/2025") ile e-Arşiv ve abonelik faturaları ("Ödenecek Tutar",
 * "Son Ödeme Tarihi") için yazıldı. Tanıma hatalarına karşı esnek: "245 ,90" gibi boşluklar toparlanır,
 * tarih ve saat tutar sanılmaz.
 */
class ReceiptParser(private val categorizer: Categorizer = Categorizer()) {

    fun parse(text: String, today: LocalDate, currency: Currency = Currency.TRY): ReceiptDraft {
        val rows = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val normalized = rows.map { TextNormalizer.normalize(it) }
        val isBill = BILL_HINTS.any { hint -> normalized.any { hint in it } }
        val merchant = findMerchant(rows, normalized)
        val dates = findDates(rows, normalized, today)
        return ReceiptDraft(
            amount = findAmount(rows, normalized, currency),
            date = dates.first,
            dueDate = dates.second,
            merchant = merchant,
            category = if (isBill) Category.FATURA else guessCategory(merchant, rows),
            isBill = isBill,
            paidByCard = rows.any { "****" in it } || CARD_HINTS.any { hint -> normalized.any { hint in it } },
            rows = rows,
        )
    }

    // ───────────────────────── Tutar ─────────────────────────

    /**
     * Önce etiketli satırlar: "Ödenecek tutar" > "Genel toplam" > "Toplam" > "Tutar". Etiketin tutarı aynı
     * satırda yoksa bir alt satıra bakılır. Etiket yoksa yıldızlı (yazar kasa) tutarların en büyüğü, o da yoksa
     * tüm tutarların en büyüğü; nakit, para üstü ve KDV satırları bu yedek aramaya girmez.
     */
    private fun findAmount(rows: List<String>, normalized: List<String>, currency: Currency): Money? {
        val labelled = rows.indices.mapNotNull { i ->
            val score = labelScore(normalized[i])
            if (score == 0) return@mapNotNull null
            val amounts = amountsIn(rows[i], currency).ifEmpty {
                // Etiket ve tutar ayrı satırlara düştüyse: alttaki satır etiketsiz bir tutarsa onu al.
                rows.getOrNull(i + 1)?.takeIf { labelScore(normalized[i + 1]) == 0 }?.let { amountsIn(it, currency) }.orEmpty()
            }
            amounts.maxOrNull()?.let { score to it }
        }
        labelled.maxOfOrNull { it.first }?.let { best ->
            return labelled.filter { it.first == best }.maxOf { it.second }
        }

        val usable = rows.indices.filter { i -> FALLBACK_SKIP.none { it in normalized[i] } && !isTaxOnly(normalized[i]) }
        val starred = usable.flatMap { i -> amountsIn(rows[i], currency, starredOnly = true) }
        return starred.maxOrNull() ?: usable.flatMap { i -> amountsIn(rows[i], currency) }.maxOrNull()
    }

    private fun labelScore(row: String): Int {
        val wordSet = words(row)
        return when {
            "odenecek" in row -> 5
            "genel toplam" in row -> 4
            excludedLabel(row, wordSet) -> 0
            "toplam" in row || "top" in wordSet -> 3
            "tutar" in row -> 2
            else -> 0
        }
    }

    /** "Ara toplam", "Toplam KDV", "İndirim toplamı", "Toplam tüketim 245,50 kWh" gibi satırlar toplam değildir. */
    private fun excludedLabel(row: String, wordSet: Set<String>): Boolean =
        LABEL_EXCLUDES.any { it in row } || isTaxOnly(row) || wordSet.any { it in UNIT_WORDS }

    /** KDV satırı; "KDV dahil toplam" ise toplamın kendisidir. */
    private fun isTaxOnly(row: String): Boolean = ("kdv" in row || "otv" in row) && "dahil" !in row

    /**
     * Satırdaki tutarlar: kuruşu yazılmış sayılar ("245,90", "1.234,56", "245.90"). Tarih ("15.03.2025"),
     * saat ("14:22"), yüzde ve adet sayıları tutar sayılmaz.
     */
    private fun amountsIn(row: String, currency: Currency, starredOnly: Boolean = false): List<Money> {
        val joined = SEPARATOR_GAP.replace(row) { m -> m.groupValues[1] + m.groupValues[2] + m.groupValues[3] }
        return joined.split(' ', '\t').flatMap { chunk ->
            // "TOPLAM*245,90" gibi bitişik yazımlar; yıldızlı parçayı ayrıca işaretle.
            chunk.split('*').mapIndexed { index, part -> part to (index > 0) }
        }.mapNotNull { (raw, starred) ->
            if (starredOnly && !starred) return@mapNotNull null
            val token = raw.trim { !it.isDigit() }
            if (!AMOUNT_TOKEN.matches(token)) null
            else MoneyInput.parse(token, currency)?.takeIf { !it.isZero && !it.isNegative }
        }
    }

    // ───────────────────────── Tarih ─────────────────────────

    /**
     * (tarih, son ödeme tarihi). "Son ödeme" satırındaki tarih son ödeme tarihidir; "tarih" yazan satırdaki
     * fiş/fatura tarihidir, yoksa bugünden ileri olmayan ilk tarih. Gelecekteki fiş tarihi tanıma hatası sayılır.
     */
    private fun findDates(rows: List<String>, normalized: List<String>, today: LocalDate): Pair<LocalDate?, LocalDate?> {
        var due: LocalDate? = null
        var labelled: LocalDate? = null
        var first: LocalDate? = null
        for (i in rows.indices) {
            val dates = datesIn(rows[i])
            val row = normalized[i]
            // "Son Ödeme Tarihi" etiketi ve tarihi ayrı satırlara düşmüş olabilir.
            val previousIsDueLabel = i > 0 && "son odeme" in normalized[i - 1] && datesIn(rows[i - 1]).isEmpty()
            val isDueRow = "son odeme" in row || (dates.isNotEmpty() && previousIsDueLabel)
            if (isDueRow) {
                if (due == null) due = dates.firstOrNull()
                continue
            }
            val past = dates.filter { it <= today }
            if (labelled == null && "tarih" in row) labelled = past.firstOrNull()
            if (first == null) first = past.firstOrNull()
        }
        return (labelled ?: first) to due
    }

    private fun datesIn(row: String): List<LocalDate> = DATE.findAll(row).mapNotNull { m ->
        val day = m.groupValues[1].toInt()
        val month = m.groupValues[2].toInt()
        val year = m.groupValues[3].toInt().let { if (it < 100) 2000 + it else it }
        if (year !in 2000..2099) null else runCatching { LocalDate(year, month, day) }.getOrNull()
    }.toList()

    // ───────────────────────── Mağaza ─────────────────────────

    /**
     * İlk satırlardan ilk anlamlı olan: fiş başlığı, adres, vergi numarası, "hoş geldiniz" gibi satırlar atlanır;
     * "A.Ş.", "Ltd. Şti.", "Ticaret" gibi ekler silinir, en fazla 3 kelime kalır.
     */
    private fun findMerchant(rows: List<String>, normalized: List<String>): String? {
        for (i in rows.indices.take(MERCHANT_SEARCH_ROWS)) {
            val row = normalized[i]
            if (MERCHANT_SKIP_PARTS.any { it in row }) continue
            if (words(row).any { it in MERCHANT_SKIP_WORDS }) continue
            val letters = rows[i].count { it.isLetter() }
            if (letters < 3 || rows[i].count { it.isDigit() } > letters) continue
            val kept = rows[i].split(' ')
                .filter { it.isNotBlank() }
                .filterNot { word -> stripPunctuation(TextNormalizer.normalize(word)) in LEGAL_WORDS }
                .filterNot { word -> word.none { it.isLetterOrDigit() } }
                .take(3)
            if (kept.isEmpty()) continue
            return kept.joinToString(" ") { titleCaseTr(it.trim { c -> !c.isLetterOrDigit() }) }.take(40)
        }
        return null
    }

    /** Büyük harfli fiş yazısını okunur yapar, Türkçe kurallarıyla: "MİGROS" → "Migros", "ŞOK" → "Şok". */
    private fun titleCaseTr(word: String): String {
        if (word.isEmpty()) return word
        val lower = buildString {
            for (c in word) append(
                when (c) {
                    'I' -> 'ı'
                    'İ' -> 'i'
                    else -> c.lowercaseChar()
                },
            )
        }
        val head = when (val c = lower.first()) {
            'i' -> 'İ'
            'ı' -> 'I'
            else -> c.uppercaseChar()
        }
        return head + lower.drop(1)
    }

    // ───────────────────────── Kategori ─────────────────────────

    /** Önce mağaza adı ("Migros" → Market); bilinmiyorsa fişteki ürün adlarının çoğunluğu. */
    private fun guessCategory(merchant: String?, rows: List<String>): Category {
        merchant?.let { name ->
            val byName = categorizer.categorizeTokens(TextNormalizer.tokenize(name)).category
            if (byName != Category.DIGER) return byName
        }
        val words = rows.flatMap { TextNormalizer.tokenize(it) }.filter { w -> w.length >= 3 && w.none { it.isDigit() } }
        return categorizer.categorizeTokens(words).category
    }

    private fun words(normalizedRow: String): Set<String> =
        normalizedRow.split(' ', '\t', ':').map { stripPunctuation(it) }.filter { it.isNotEmpty() }.toSet()

    private fun stripPunctuation(word: String): String = word.filter { it.isLetterOrDigit() }

    private companion object {
        const val MERCHANT_SEARCH_ROWS = 6

        /** "245 ,90" / "245, 90" → "245,90": tanıma ayırıcının çevresine boşluk koyabiliyor. */
        val SEPARATOR_GAP = Regex("(\\d)\\s*([.,])\\s*(\\d{2}(?!\\d))")

        /** Kuruşlu tutar: 1.234,56 · 245,90 · 245.90 (ABD biçimi "1,234.56" fişlerde yok, kabul edilmez). */
        val AMOUNT_TOKEN = Regex("^(\\d{1,3}(\\.\\d{3})+|\\d+),\\d{2}$|^\\d+\\.\\d{2}$")

        /** gg.aa.yyyy, gg/aa/yyyy, gg-aa-yy. */
        val DATE = Regex("(\\d{1,2})[./-](\\d{1,2})[./-](\\d{4}|\\d{2})(?!\\d)")

        /** Abonelik faturası işaretleri (alışveriş e-Arşiv faturalarında bunlar olmaz). */
        val BILL_HINTS = listOf(
            "son odeme", "abone", "tesisat", "tuketim", "fatura donemi", "sozlesme hesap", "okuma tarihi", "sayac",
        )

        val CARD_HINTS = listOf("kredi karti", "banka karti", "temassiz", "kart ile", "kartla", "kart no")

        val LABEL_EXCLUDES = listOf("ara toplam", "indirim", "iskonto", "para ustu", "puan", "matrah", "tuketim")
        val UNIT_WORDS = setOf("kwh", "m3", "sm3", "kg", "lt", "gb", "dk", "adet")

        /** Yedek tutar aramasında atlanan satırlar: verilen nakit, para üstü, kartla ödenen (aynı tutarın tekrarı). */
        val FALLBACK_SKIP = listOf("nakit", "para ustu", "kart", "puan", "indirim")

        val MERCHANT_SKIP_PARTS = listOf("hos geldiniz", "hosgeldiniz", "e-arsiv", "earsiv", "www", "http", ".com")
        val MERCHANT_SKIP_WORDS = setOf(
            "fis", "fisi", "tarih", "saat", "vkn", "tckn", "vergi", "vd", "tel", "telefon", "adres", "mersis",
            "fatura", "faturasi", "sayin", "ettn", "kasa", "sicil", "no",
        )
        val LEGAL_WORDS = setOf(
            "as", "tas", "ltd", "sti", "ltdsti", "tic", "ticaret", "san", "sanayi", "ve", "limited", "sirketi", "anonim", "sirket",
        )
    }
}
