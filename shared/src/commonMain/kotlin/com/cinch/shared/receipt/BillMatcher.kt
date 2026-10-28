package com.cinch.shared.receipt

import com.cinch.shared.category.TextNormalizer
import com.cinch.shared.domain.FixedExpense
import com.cinch.shared.domain.FixedExpenseKind
import kotlin.math.abs

/**
 * Faturanın hangi sabit gidere ait olduğunu tahmin eder. İki ipucu var:
 * - Sabit giderin adı faturada geçiyor ("Turkcell", "İnternet").
 * - Ad bir hizmet türü ve faturada o hizmetin kurumu yazıyor ("Elektrik" ↔ "Enerjisa", "Su" ↔ "İSKİ").
 *
 * Yalnız abonelik faturalarında ([ReceiptDraft.isBill]) eşleştirilir: market fişindeki "SU 0,5L" "Su" faturasına
 * bağlanmasın. Emin olunamazsa null; kullanıcı onay ekranında elle seçer.
 */
object BillMatcher {

    private const val NAME_HIT = 2
    private const val SERVICE_HIT = 2
    private const val BILL_KIND_BONUS = 1

    fun match(draft: ReceiptDraft, fixed: List<FixedExpense>): FixedExpense? {
        if (!draft.isBill || fixed.isEmpty()) return null
        val textWords = (draft.rows + listOfNotNull(draft.merchant)).flatMap { words(it) }.toSet()
        return fixed
            .map { it to score(it, textWords) }
            .filter { (_, score) -> score >= NAME_HIT }
            .maxWithOrNull(
                compareBy<Pair<FixedExpense, Int>> { it.second }
                    // Eşitlikte tutarı faturaya en yakın olan.
                    .thenByDescending { (f, _) -> draft.amount?.let { abs(it.minor - f.amount.minor) } ?: 0L },
            )
            ?.first
    }

    private fun score(fixed: FixedExpense, textWords: Set<String>): Int {
        val nameWords = words(fixed.name)
        var score = 0
        // "su" gibi kısa adlar her yerde geçer; ad eşleşmesi için en az 3 harf.
        if (nameWords.any { it.length >= 3 && it in textWords }) score += NAME_HIT
        // Kaç ipucu tuttuğu da önemli: "Başkent Doğalgaz" faturasında "başkent" elektrik kurumu adına da benzer,
        // ama "doğalgaz", "gaz", "sm3" gaz tarafını açıkça öne çıkarır.
        val hits = SERVICES.filter { service -> nameWords.any { it in service.names } }
            .maxOfOrNull { service -> service.providers.count { it in textWords } } ?: 0
        if (hits >= 1) score += SERVICE_HIT
        if (hits >= 2) score += 1
        if (score > 0 && fixed.kind == FixedExpenseKind.BILL) score += BILL_KIND_BONUS
        return score
    }

    private fun words(text: String): List<String> =
        TextNormalizer.normalize(text).split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

    /** Hizmet türü: sabit giderin adında geçebilecek kelimeler ve faturada o hizmeti ele veren kelimeler/kurumlar. */
    private class Service(val names: Set<String>, val providers: Set<String>)

    private val SERVICES = listOf(
        Service(
            names = setOf("elektrik", "enerji"),
            providers = setOf(
                // "enerji" yok: EPDK ("Enerji Piyasası...") doğalgaz faturalarında da yazar.
                "elektrik", "enerjisa", "ck", "bogazici", "aydem", "gediz", "uludag", "osmangazi", "toroslar",
                "dicle", "akdeniz", "baskent", "sepas", "meram", "camlibel", "vangolu", "yesilirmak", "trakya", "coruh",
                "firat", "aras", "kwh",
            ),
        ),
        Service(
            names = setOf("dogalgaz", "gaz"),
            providers = setOf(
                "dogalgaz", "gaz", "igdas", "baskentgaz", "aksagaz", "enerya", "palen", "izmirgaz", "bursagaz", "egegaz",
                "kayserigaz", "gazdas", "agdas", "sm3",
            ),
        ),
        Service(
            names = setOf("su"),
            providers = setOf("su", "iski", "aski", "izsu", "buski", "asat", "maski", "saski", "kaski", "deski", "meski", "tiski", "m3"),
        ),
        Service(
            names = setOf("internet", "fiber", "adsl", "vdsl", "wifi", "modem"),
            providers = setOf(
                "internet", "superonline", "turknet", "ttnet", "telekom", "fiber", "millenicom", "kablonet", "turksat",
                "adsl", "vdsl",
            ),
        ),
        Service(
            names = setOf("telefon", "hat", "gsm", "mobil", "cep"),
            providers = setOf("turkcell", "vodafone", "telekom", "gsm", "mobil"),
        ),
        Service(
            names = setOf("tv", "televizyon", "digiturk", "tivibu", "dsmart"),
            providers = setOf("digiturk", "tivibu", "dsmart"),
        ),
    )
}
