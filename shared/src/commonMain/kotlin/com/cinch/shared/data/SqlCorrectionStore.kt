package com.cinch.shared.data

import com.cinch.shared.category.CorrectionStore
import com.cinch.shared.category.TextNormalizer
import com.cinch.shared.db.CinchDatabase
import com.cinch.shared.domain.Category

/**
 * Kullanıcının kelime→kategori düzeltmelerini veritabanında saklar.
 *
 * [CorrectionStore] senkron bir arayüz ve ayrıştırıcı her tuş vuruşunda çağrılıyor. Bu yüzden
 * tablo ilk erişimde bir kez belleğe alınır, okumalar bellekten yapılır. Yazmalar hem belleğe
 * hem veritabanına gider. Tablo küçük (kullanıcı başına birkaç düzine satır).
 */
class SqlCorrectionStore(private val db: CinchDatabase) : CorrectionStore {

    private var loaded: MutableMap<String, Category>? = null

    private val cache: MutableMap<String, Category>
        get() = loaded ?: db.cinchQueries.allCorrections().executeAsList()
            .associate { it.keyword to categoryOf(it.category) }
            .toMutableMap()
            .also { loaded = it }

    override fun categoryFor(token: String): Category? = cache[TextNormalizer.normalize(token)]

    override fun learn(token: String, category: Category) {
        val key = TextNormalizer.normalize(token)
        db.cinchQueries.upsertCorrection(key, category.name)
        cache[key] = category
    }

    /** Bir sonraki okumada tablo yeniden yüklenir. */
    override fun reload() {
        loaded = null
    }
}
