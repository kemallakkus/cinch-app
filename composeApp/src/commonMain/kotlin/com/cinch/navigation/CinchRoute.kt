package com.cinch.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Uygulamanın ekranları. Sealed olduğu için geri yığını ek serializer kaydı olmadan saklanır. */
@Serializable
sealed interface CinchRoute : NavKey

/**
 * Bütçe formu.
 *
 * @param isOnboarding ilk açılış: kaydedince ana ekran kök olur. Değilse (düzenleme) geri dönülür.
 */
@Serializable
data class BudgetSetupRoute(val isOnboarding: Boolean = false) : CinchRoute

@Serializable
data object HomeRoute : CinchRoute

@Serializable
data object CardsRoute : CinchRoute

/** @param cardId düzenlenecek kart; null ise yeni kart. */
@Serializable
data class CardEditRoute(val cardId: Long? = null) : CinchRoute

@Serializable
data class CardDetailRoute(val cardId: Long) : CinchRoute

@Serializable
data object MonthRoute : CinchRoute

@Serializable
data object SettingsRoute : CinchRoute

/** @param incomeId düzenlenecek gelir; null ise yeni gelir. */
@Serializable
data class IncomeEditRoute(val incomeId: Long? = null) : CinchRoute

/** @param fixedId düzenlenecek sabit gider; null ise yeni. */
@Serializable
data class FixedEditRoute(val fixedId: Long? = null) : CinchRoute

/** Taksitli ya da esnek borcun ilerlemesi ve ödeme geçmişi. */
@Serializable
data class DebtDetailRoute(val fixedId: Long) : CinchRoute

/** Kaydedilmiş tek bir harcamanın düzenlenmesi. */
@Serializable
data class ExpenseEditRoute(val expenseId: Long) : CinchRoute

/**
 * Harcama geçmişi: ay ay liste, arama ve kategori süzgeci.
 *
 * @param categoryName yalnız bu kategori ([com.cinch.shared.domain.Category.name]); null = hepsi.
 * @param year ve [month] açılacak ay (month 1–12); null = bu ay.
 */
@Serializable
data class HistoryRoute(val categoryName: String? = null, val year: Int? = null, val month: Int? = null) : CinchRoute

/**
 * Fişten/faturadan okunanların onayı. [text] okunan yazı (satır satır); rotada durduğu için uygulama arka planda
 * kapatılıp açılsa da kaybolmaz. Fotoğrafın kendisi saklanmaz.
 */
@Serializable
data class ReceiptReviewRoute(val text: String) : CinchRoute

/** "Nereye gitti?": ayın kategori dağılımı ve geçen ayla kıyas. [year]/[month] null = bu ay. */
@Serializable
data class InsightsRoute(val year: Int? = null, val month: Int? = null) : CinchRoute
