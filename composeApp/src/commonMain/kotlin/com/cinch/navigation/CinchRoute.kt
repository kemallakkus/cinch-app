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
