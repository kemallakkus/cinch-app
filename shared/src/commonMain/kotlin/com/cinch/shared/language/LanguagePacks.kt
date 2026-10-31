package com.cinch.shared.language

import com.cinch.shared.domain.Category
import com.cinch.shared.statement.StatementField
import kotlinx.datetime.DayOfWeek

/** Uygulamayla gelen diller ve telefonun diline göre seçim. */
object LanguagePacks {

    private fun keywords(block: MutableMap<String, Category>.() -> Unit): Map<String, Category> = buildMap(block)

    private fun MutableMap<String, Category>.put(category: Category, vararg words: String) =
        words.forEach { put(it, category) }

    // ───────────────────────── Türkçe ─────────────────────────

    val TURKISH = LanguagePack(
        code = "tr",
        keywords = keywords {
            put(
                Category.MARKET,
                "market", "bakkal", "migros", "bim", "a101", "sok", "şok", "carrefour",
                "tarım", "manav", "kasap", "alışveriş",
            )
            put(
                Category.YEMEK,
                "yemek", "restoran", "restaurant", "lokanta", "kafe", "cafe", "kahve",
                "çay", "su", "ekmek", "büfe", "bufe", "fast", "food", "döner", "doner",
                "pizza", "burger", "tatlı", "pastane", "simit", "yemeksepeti", "getir",
            )
            put(
                Category.ULASIM,
                "taksi", "otobüs", "otobus", "metro", "metrobüs", "metrobus", "dolmuş",
                "dolmus", "benzin", "mazot", "yakıt", "yakit", "otopark", "köprü", "kopru",
                "hgs", "ogs", "bilet", "uber",
            )
            put(Category.FATURA, "fatura", "elektrik", "doğalgaz", "dogalgaz", "internet", "telefon", "abonelik", "aidat")
            put(Category.SAGLIK, "eczane", "ilaç", "ilac", "doktor", "hastane", "muayene", "diş", "dis", "gözlük", "gozluk")
            put(Category.EGLENCE, "sinema", "tiyatro", "konser", "oyun", "netflix", "spotify", "youtube", "steam", "bar", "maç", "mac")
            put(
                Category.GIYIM,
                "giyim", "ayakkabı", "ayakkabi", "tişört", "tisort", "pantolon", "ceket", "elbise", "defacto", "lcw", "zara",
            )
            put(Category.KISISEL, "kuaför", "kuafor", "berber", "kozmetik", "parfüm", "parfum", "makyaj")
            put(Category.EV, "kira", "mobilya", "ikea", "deterjan", "nalbur", "züccaciye", "zuccaciye")
        },
        quickEntry = QuickEntryWords(
            todayWords = setOf("bugun"),
            yesterdayWords = setOf("dun"),
            twoDaysAgoPhrases = listOf("onceki gun", "evvelki gun", "evvelsi"),
            weekdays = mapOf(
                "pazartesi" to DayOfWeek.MONDAY,
                "sali" to DayOfWeek.TUESDAY,
                "carsamba" to DayOfWeek.WEDNESDAY,
                "persembe" to DayOfWeek.THURSDAY,
                "cuma" to DayOfWeek.FRIDAY,
                "cumartesi" to DayOfWeek.SATURDAY,
                "pazar" to DayOfWeek.SUNDAY,
            ),
            lastWords = setOf("gecen"),
            dayWords = setOf("gun", "gunu"),
            ambiguousWeekdays = setOf("pazar"),
            sharedPhrases = listOf("yari yariya", "bolustuk", "paylastik", "ortak"),
            reimbursePhrases = listOf("geri geldi", "geri gelecek", "geri gelir", "iade"),
            returnedFractions = listOf(
                "yarisi" to 0.5, "yarim" to 0.5, "ucte biri" to 1.0 / 3.0, "ceyregi" to 0.25, "tamami" to 1.0, "hepsi" to 1.0,
            ),
            fractionWords = setOf(
                "geri", "geldi", "gelecek", "gelir", "iade", "yarisi", "yarim", "ucte", "biri",
                "ceyregi", "tamami", "hepsi", "yari", "yariya", "bolustuk", "paylastik", "ortak",
            ),
            currencySuffixes = setOf("tl", "₺", "lira", "try"),
            unitSuffixes = setOf(
                "kg", "gr", "g", "gram", "lt", "l", "litre", "ml", "cl", "km", "cm", "mm", "m", "adet", "x",
                "li", "lu", "lik", "luk", "lk",
            ),
        ),
        receipt = ReceiptWords(
            totalLabels = listOf(
                ReceiptLabel("odenecek", 5, beatsExcludes = true),
                ReceiptLabel("genel toplam", 4, beatsExcludes = true),
                ReceiptLabel("toplam", 3),
                ReceiptLabel("top", 3, wholeWord = true),
                ReceiptLabel("tutar", 2),
            ),
            totalExcludes = listOf("ara toplam", "indirim", "iskonto", "para ustu", "puan", "matrah", "tuketim"),
            unitWords = setOf("kwh", "m3", "sm3", "kg", "lt", "gb", "dk", "adet"),
            taxWords = setOf("kdv", "otv"),
            taxIncludedWords = setOf("dahil"),
            fallbackSkip = listOf("nakit", "para ustu", "kart", "puan", "indirim"),
            dueLabels = listOf("son odeme"),
            dateLabels = listOf("tarih"),
            billHints = listOf("son odeme", "abone", "tesisat", "tuketim", "fatura donemi", "sozlesme hesap", "okuma tarihi", "sayac"),
            cardHints = listOf("kredi karti", "banka karti", "temassiz", "kart ile", "kartla", "kart no"),
            merchantSkipParts = listOf("hos geldiniz", "hosgeldiniz", "e-arsiv", "earsiv", "www", "http", ".com"),
            merchantSkipWords = setOf(
                "fis", "fisi", "tarih", "saat", "vkn", "tckn", "vergi", "vd", "tel", "telefon", "adres", "mersis",
                "fatura", "faturasi", "sayin", "ettn", "kasa", "sicil", "no",
            ),
            legalWords = setOf(
                "as", "tas", "ltd", "sti", "ltdsti", "tic", "ticaret", "san", "sanayi", "ve", "limited", "sirketi", "anonim", "sirket",
            ),
        ),
        statement = StatementWords(
            labels = mapOf(
                StatementField.STATEMENT_BALANCE to listOf(
                    "kalan ekstre borcu", "ekstre borcu", "kalan donem borcu", "donem borcu", "hesap ozeti borcu",
                    "ekstreye yansiyan islemler", "ekstre tutari", "odenecek tutar",
                ),
                StatementField.MINIMUM_PAYMENT to listOf("asgari odeme tutari", "kalan asgari odeme", "asgari odeme", "asgari tutar"),
                StatementField.DUE_DATE to listOf("son odeme tarihi", "son odeme gunu", "son odeme"),
                StatementField.LAST_DAY to listOf("son gun"),
                StatementField.NEXT_DUE to listOf("sonraki son odeme"),
                StatementField.STATEMENT_DATE to listOf(
                    "ekstre kesim tarihi", "hesap kesim tarihi", "ekstre kesim", "hesap kesim", "kesim tarihi",
                ),
                StatementField.NEXT_STATEMENT to listOf("sonraki ekstre kesim", "sonraki hesap kesim", "sonraki kesim"),
                StatementField.PERIOD_SPENDING to listOf("donem ici harcama", "donem ici islemler", "guncel donem harcama"),
                StatementField.FUTURE_INSTALLMENTS to listOf("gelecek donem taksit", "kalan taksitler"),
                StatementField.TOTAL_DEBT to listOf("toplam borc", "guncel borc", "toplam kart borcu"),
                StatementField.CREDIT_LIMIT to listOf("kart limiti", "toplam limit", "kredi limiti"),
                StatementField.AVAILABLE_LIMIT to listOf("kullanilabilir limit"),
            ),
            excludes = mapOf(
                StatementField.CREDIT_LIMIT to listOf("sanal", "internet"),
                StatementField.AVAILABLE_LIMIT to listOf("sanal", "internet"),
            ),
            totalWords = listOf("toplam", "kullanilabilir", "limit"),
            installmentWords = listOf("taksit"),
        ),
        months = listOf(
            listOf("ocak", "oca"), listOf("subat", "sub"), listOf("mart", "mar"), listOf("nisan", "nis"),
            listOf("mayis", "may"), listOf("haziran", "haz"), listOf("temmuz", "tem"), listOf("agustos", "agu"),
            listOf("eylul", "eyl"), listOf("ekim", "eki"), listOf("kasim", "kas"), listOf("aralik", "ara"),
        ),
        stopwords = setOf("ile", "icin", "bir", "iki", "uc", "ve", "de", "da", "kart", "nakit"),
    )

    // ───────────────────────── İngilizce ─────────────────────────

    val ENGLISH = LanguagePack(
        code = "en",
        keywords = keywords {
            put(
                Category.MARKET,
                "market", "supermarket", "grocery", "groceries", "walmart", "tesco", "aldi", "lidl", "costco",
                "sainsburys", "asda", "butcher", "greengrocer",
            )
            put(
                Category.YEMEK,
                "food", "lunch", "dinner", "breakfast", "brunch", "restaurant", "cafe", "coffee", "tea", "bakery",
                "snack", "pizza", "burger", "takeaway", "takeout", "starbucks", "mcdonalds", "kebab",
            )
            put(
                Category.ULASIM,
                "taxi", "cab", "uber", "lyft", "bus", "metro", "subway", "tube", "train", "tram", "fuel", "petrol",
                "diesel", "parking", "toll", "ticket", "flight",
            )
            put(Category.FATURA, "bill", "electricity", "utilities", "internet", "broadband", "phone", "mobile", "subscription")
            put(Category.SAGLIK, "pharmacy", "medicine", "doctor", "hospital", "dentist", "clinic", "glasses")
            put(Category.EGLENCE, "cinema", "movie", "theatre", "theater", "concert", "game", "netflix", "spotify", "bar", "pub")
            put(Category.GIYIM, "clothes", "clothing", "shoes", "shirt", "jeans", "jacket", "dress", "zara")
            put(Category.KISISEL, "haircut", "barber", "hairdresser", "cosmetics", "perfume", "makeup")
            put(Category.EV, "rent", "furniture", "ikea", "detergent", "hardware")
        },
        quickEntry = QuickEntryWords(
            todayWords = setOf("today"),
            yesterdayWords = setOf("yesterday"),
            twoDaysAgoPhrases = listOf("day before yesterday"),
            weekdays = mapOf(
                "monday" to DayOfWeek.MONDAY, "mon" to DayOfWeek.MONDAY,
                "tuesday" to DayOfWeek.TUESDAY, "tue" to DayOfWeek.TUESDAY,
                "wednesday" to DayOfWeek.WEDNESDAY, "wed" to DayOfWeek.WEDNESDAY,
                "thursday" to DayOfWeek.THURSDAY, "thu" to DayOfWeek.THURSDAY,
                "friday" to DayOfWeek.FRIDAY, "fri" to DayOfWeek.FRIDAY,
                // "sat" ve "sun" yok: Türkçe yazarken ("telefonu sat") tarih sanılmasın.
                "saturday" to DayOfWeek.SATURDAY,
                "sunday" to DayOfWeek.SUNDAY,
            ),
            lastWords = setOf("last"),
            dayWords = setOf("day"),
            ambiguousWeekdays = emptySet(),
            sharedPhrases = listOf("split", "shared", "went halves", "halves"),
            reimbursePhrases = listOf("refund", "paid back", "pay me back", "got back", "reimbursed"),
            returnedFractions = listOf("half" to 0.5, "third" to 1.0 / 3.0, "quarter" to 0.25, "full" to 1.0),
            fractionWords = setOf(
                "split", "shared", "went", "halves", "refund", "refunded", "paid", "pay", "me", "back", "got",
                "reimbursed", "half", "third", "quarter", "full", "fully",
            ),
            currencySuffixes = setOf("usd", "eur", "gbp", "$", "€", "£", "dollar", "dollars", "euro", "euros"),
            unitSuffixes = setOf("kg", "g", "lb", "lbs", "oz", "l", "ml", "pcs", "pc", "x", "km", "mi"),
        ),
        receipt = ReceiptWords(
            totalLabels = listOf(
                ReceiptLabel("amount due", 5, beatsExcludes = true),
                ReceiptLabel("balance due", 5, beatsExcludes = true),
                ReceiptLabel("grand total", 4, beatsExcludes = true),
                ReceiptLabel("total", 3),
                ReceiptLabel("amount", 2),
            ),
            totalExcludes = listOf("subtotal", "sub total", "discount", "savings", "change", "points"),
            unitWords = setOf("kwh", "kg", "lb", "gal", "gb", "min"),
            taxWords = setOf("tax", "vat"),
            taxIncludedWords = setOf("incl", "including", "included"),
            fallbackSkip = listOf("cash", "change", "card", "points", "discount"),
            dueLabels = listOf("due date", "payment due", "pay by"),
            dateLabels = listOf("date"),
            billHints = listOf("due date", "account number", "billing period", "meter", "usage", "service period"),
            cardHints = listOf("credit card", "debit card", "contactless", "visa", "mastercard", "card no"),
            merchantSkipParts = listOf("welcome", "thank you", "www", "http", ".com"),
            merchantSkipWords = setOf(
                "receipt", "invoice", "date", "time", "tel", "phone", "address", "tax", "vat", "no", "cashier",
            ),
            legalWords = setOf("inc", "llc", "ltd", "limited", "co", "corp", "plc", "gmbh", "and"),
        ),
        statement = StatementWords(
            labels = mapOf(
                StatementField.STATEMENT_BALANCE to listOf("statement balance", "new balance"),
                StatementField.MINIMUM_PAYMENT to listOf("minimum payment due", "minimum payment", "min payment"),
                StatementField.DUE_DATE to listOf("payment due date", "due date"),
                StatementField.NEXT_DUE to listOf("next payment due", "next due date"),
                StatementField.STATEMENT_DATE to listOf("statement closing date", "statement date", "closing date"),
                StatementField.NEXT_STATEMENT to listOf("next statement date", "next closing date"),
                StatementField.PERIOD_SPENDING to listOf("new purchases", "current period purchases"),
                StatementField.FUTURE_INSTALLMENTS to listOf("future installments", "remaining installments"),
                StatementField.TOTAL_DEBT to listOf("current balance", "total balance"),
                StatementField.CREDIT_LIMIT to listOf("credit limit", "total credit line"),
                StatementField.AVAILABLE_LIMIT to listOf("available credit", "available limit"),
            ),
            excludes = mapOf(
                StatementField.CREDIT_LIMIT to listOf("virtual", "cash advance"),
                StatementField.AVAILABLE_LIMIT to listOf("virtual", "cash advance"),
            ),
            totalWords = listOf("total", "available", "limit"),
            installmentWords = listOf("installment", "instalment"),
        ),
        months = listOf(
            listOf("january", "jan"), listOf("february", "feb"), listOf("march", "mar"), listOf("april", "apr"),
            listOf("may"), listOf("june", "jun"), listOf("july", "jul"), listOf("august", "aug"),
            listOf("september", "sept", "sep"), listOf("october", "oct"), listOf("november", "nov"), listOf("december", "dec"),
        ),
        stopwords = setOf("the", "and", "for", "with", "from", "card", "cash"),
    )

    private val KNOWN = listOf(TURKISH, ENGLISH)

    /** Bilinen bütün diller birleşik (Türkçe önce): dilin bilinmediği yerler için (ör. metindeki tutarı bulma). */
    val ALL: LanguagePack = KNOWN.reduce { acc, pack -> acc.merge(pack) }

    /**
     * Telefonun diline göre: o dil önce, ardından Türkçe ve İngilizce. Kullanıcı hangisiyle yazarsa yazsın
     * anlaşılır; iki dilde çakışan kelimede telefonun dili geçerli. Bilinmeyen dilde Türkçe önce gelir.
     */
    fun active(languageCode: String): LanguagePack {
        val primary = KNOWN.firstOrNull { it.code == languageCode.lowercase().take(2) } ?: TURKISH
        return (listOf(primary) + KNOWN).distinctBy { it.code }.reduce { acc, pack -> acc.merge(pack) }
    }
}
