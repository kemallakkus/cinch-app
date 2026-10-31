package com.cinch.shared.language

import com.cinch.shared.category.TextNormalizer
import com.cinch.shared.domain.Category
import com.cinch.shared.statement.StatementField
import kotlinx.datetime.DayOfWeek

/**
 * Bir dilin ayrıştırıcılara verdiği kelimeler: hızlı kayıt, kategori sözlüğü, fiş, banka ekranı. Kelimeler
 * [TextNormalizer.normalize] biçiminde yazılır (küçük harf, Türkçe harfler sadeleşmiş: "dün" → "dun").
 *
 * Diller [merge] ile birleştirilir: kullanıcı telefonun dilinde de Türkçe de yazabilir, yurt dışı fişi de
 * okunur. Yeni dil = yeni bir paket ([LanguagePacks]); ayrıştırıcıların koduna dokunulmaz.
 *
 * @param code ISO 639-1 dil kodu ("tr", "en").
 */
data class LanguagePack(
    val code: String,
    val keywords: Map<String, Category>,
    val quickEntry: QuickEntryWords,
    val receipt: ReceiptWords,
    val statement: StatementWords,
    /** Ay adları, Ocak'tan Aralık'a; her ay için tam ad önce, kısaltmalar sonra ("ekim", "eki"). */
    val months: List<List<String>>,
    /** "Nereye gitti?"de en çok para giden kelimelerde sayılmayan bağlaçlar ve ödeme şekilleri. */
    val stopwords: Set<String>,
) {
    init {
        require(months.size == 12) { "$code: 12 ay olmalı" }
    }

    /** Bu paket önce gelir: kelime iki dilde farklı kategorideyse bu dilinki geçerli. */
    fun merge(other: LanguagePack): LanguagePack = LanguagePack(
        code = code,
        keywords = other.keywords + keywords,
        quickEntry = quickEntry.merge(other.quickEntry),
        receipt = receipt.merge(other.receipt),
        statement = statement.merge(other.statement),
        months = months.zip(other.months) { a, b -> (a + b).distinct() },
        stopwords = stopwords + other.stopwords,
    )
}

/**
 * Hızlı kayıt ("dün 120 taksi", "300 yemek yarı yarıya", "625 yemek yarısı geri geldi").
 *
 * @param twoDaysAgoPhrases iki gün önce; çok kelimeliyse kelimeler art arda geçmeli ("onceki gun", "day before yesterday").
 * @param lastWords gün adından önce gelince bugünü atlar ("gecen cuma", "last friday").
 * @param ambiguousWeekdays tek başına tarih sayılmayan gün adları ("pazar" semt pazarı da olabilir): önünde
 *   [lastWords] ya da ardında [dayWords] olmalı ("pazar gunu").
 * @param sharedPhrases paylaşılan harcama: kullanıcının payı yarısı ("yari yariya", "split").
 * @param reimbursePhrases bir kısmı geri gelecek/geldi; ne kadarı [returnedFractions]'tan, bulunamazsa tamamı.
 * @param returnedFractions geri gelen oran, ilk eşleşen geçerli ("yarisi" → 0.5).
 * @param fractionWords pay/iade kelimeleri; kategoriye ve alışkanlık sayımına girmez.
 * @param currencySuffixes sayıya bitişik para birimi ("195tl"): atılır.
 * @param unitSuffixes sayıya bitişik ölçü ("3kg", "2li"): o sayı tutar sayılmaz.
 */
data class QuickEntryWords(
    val todayWords: Set<String>,
    val yesterdayWords: Set<String>,
    val twoDaysAgoPhrases: List<String>,
    val weekdays: Map<String, DayOfWeek>,
    val lastWords: Set<String>,
    val dayWords: Set<String>,
    val ambiguousWeekdays: Set<String>,
    val sharedPhrases: List<String>,
    val reimbursePhrases: List<String>,
    val returnedFractions: List<Pair<String, Double>>,
    val fractionWords: Set<String>,
    val currencySuffixes: Set<String>,
    val unitSuffixes: Set<String>,
) {
    /** Tarih olarak okunan ya da tarih kalıbının parçası olan kelimeler; kategori adayı değildir. */
    val dateWords: Set<String> =
        todayWords + yesterdayWords + twoDaysAgoPhrases.flatMap { it.split(' ') } + lastWords + dayWords

    fun merge(other: QuickEntryWords) = QuickEntryWords(
        todayWords = todayWords + other.todayWords,
        yesterdayWords = yesterdayWords + other.yesterdayWords,
        twoDaysAgoPhrases = (twoDaysAgoPhrases + other.twoDaysAgoPhrases).distinct(),
        weekdays = other.weekdays + weekdays,
        lastWords = lastWords + other.lastWords,
        dayWords = dayWords + other.dayWords,
        ambiguousWeekdays = ambiguousWeekdays + other.ambiguousWeekdays,
        sharedPhrases = (sharedPhrases + other.sharedPhrases).distinct(),
        reimbursePhrases = (reimbursePhrases + other.reimbursePhrases).distinct(),
        returnedFractions = (returnedFractions + other.returnedFractions).distinctBy { it.first },
        fractionWords = fractionWords + other.fractionWords,
        currencySuffixes = currencySuffixes + other.currencySuffixes,
        unitSuffixes = unitSuffixes + other.unitSuffixes,
    )
}

/**
 * Fişteki toplam etiketi ve puanı: en yüksek puanlı etiketin tutarı ödenen toplamdır.
 *
 * @param wholeWord yalnız tam kelimeyse ("TOP" kısaltması; "topuz" değil).
 * @param beatsExcludes satırda dışlanan bir kelime olsa da geçerli ("Ödenecek tutar (KDV dahil)").
 */
data class ReceiptLabel(val phrase: String, val score: Int, val wholeWord: Boolean = false, val beatsExcludes: Boolean = false)

/**
 * Fiş ve fatura.
 *
 * @param totalExcludes toplam olmayan satırlar ("ara toplam", "indirim", "subtotal").
 * @param taxWords vergi satırı; [taxIncludedWords] de geçiyorsa ("KDV dahil toplam") toplamın kendisidir.
 * @param fallbackSkip etiket bulunamayınca tutar aranmayan satırlar (nakit, para üstü, kart).
 * @param dueLabels son ödeme tarihi satırı; [dateLabels] fiş/fatura tarihinin satırı.
 * @param billHints abonelik faturası işaretleri (sabit gidere bağlanması önerilir).
 * @param cardHints kartla ödendiğini gösteren yazılar.
 * @param merchantSkipParts / [merchantSkipWords] mağaza adı aranırken atlanan satırlar; [legalWords] addan silinen
 *   şirket ekleri ("A.Ş.", "Ltd.", "Inc.").
 */
data class ReceiptWords(
    val totalLabels: List<ReceiptLabel>,
    val totalExcludes: List<String>,
    val unitWords: Set<String>,
    val taxWords: Set<String>,
    val taxIncludedWords: Set<String>,
    val fallbackSkip: List<String>,
    val dueLabels: List<String>,
    val dateLabels: List<String>,
    val billHints: List<String>,
    val cardHints: List<String>,
    val merchantSkipParts: List<String>,
    val merchantSkipWords: Set<String>,
    val legalWords: Set<String>,
) {
    fun merge(other: ReceiptWords) = ReceiptWords(
        totalLabels = (totalLabels + other.totalLabels).distinctBy { it.phrase },
        totalExcludes = (totalExcludes + other.totalExcludes).distinct(),
        unitWords = unitWords + other.unitWords,
        taxWords = taxWords + other.taxWords,
        taxIncludedWords = taxIncludedWords + other.taxIncludedWords,
        fallbackSkip = (fallbackSkip + other.fallbackSkip).distinct(),
        dueLabels = (dueLabels + other.dueLabels).distinct(),
        dateLabels = (dateLabels + other.dateLabels).distinct(),
        billHints = (billHints + other.billHints).distinct(),
        cardHints = (cardHints + other.cardHints).distinct(),
        merchantSkipParts = (merchantSkipParts + other.merchantSkipParts).distinct(),
        merchantSkipWords = merchantSkipWords + other.merchantSkipWords,
        legalWords = legalWords + other.legalWords,
    )
}

/**
 * Banka uygulamasının kart ekranı.
 *
 * @param labels her alanın ekranlarda geçen adları; en uzun eşleşen ad kazanır.
 * @param excludes alanı yanlış yakalatan kelimeler ("sanal kart limiti" kart limiti değildir).
 * @param totalWords hareket listesinde hareket olmayan satırlar ("TOPLAM", "kullanılabilir limit").
 * @param installmentWords taksitli hareket ("taksit", "installment").
 */
data class StatementWords(
    val labels: Map<StatementField, List<String>>,
    val excludes: Map<StatementField, List<String>>,
    val totalWords: List<String>,
    val installmentWords: List<String>,
) {
    fun merge(other: StatementWords) = StatementWords(
        labels = StatementField.entries.associateWith { (labels[it].orEmpty() + other.labels[it].orEmpty()).distinct() },
        excludes = StatementField.entries.associateWith { (excludes[it].orEmpty() + other.excludes[it].orEmpty()).distinct() },
        totalWords = (totalWords + other.totalWords).distinct(),
        installmentWords = (installmentWords + other.installmentWords).distinct(),
    )
}
