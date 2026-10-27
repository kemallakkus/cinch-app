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
