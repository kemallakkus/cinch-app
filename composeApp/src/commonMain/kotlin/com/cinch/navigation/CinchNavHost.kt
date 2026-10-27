package com.cinch.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.navigation3.ui.NavDisplay
import com.cinch.ui.budget.BudgetSetupWrapper
import com.cinch.ui.home.HomeWrapper

/**
 * Tek geri yığınlı navigasyon. Ekranlar arası geçişler burada; ekranlar yalnız callback alır.
 * ViewModel'ler giriş (entry) ömrüne bağlıdır: ekran yığından çıkınca temizlenir.
 */
@Composable
fun CinchNavHost(startRoute: CinchRoute) {
    val backStack = rememberSerializable(serializer = NavBackStackSerializer(CinchRoute.serializer())) {
        NavBackStack(startRoute)
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.pop() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<HomeRoute> {
                HomeWrapper(onOpenBudget = { backStack.add(BudgetSetupRoute()) })
            }
            entry<BudgetSetupRoute> { route ->
                BudgetSetupWrapper(
                    route = route,
                    onDone = {
                        if (route.isOnboarding) backStack.setAsRoot(HomeRoute) else backStack.pop()
                    },
                    onBack = { backStack.pop() },
                )
            }
        },
    )
}

/** Kök ekranda geri tuşu yığını boşaltmaz; sistem uygulamayı kapatır. */
private fun NavBackStack<CinchRoute>.pop() {
    if (size > 1) removeAt(lastIndex)
}

private fun NavBackStack<CinchRoute>.setAsRoot(route: CinchRoute) {
    clear()
    add(route)
}
