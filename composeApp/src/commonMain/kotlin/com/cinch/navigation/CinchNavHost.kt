package com.cinch.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.navigation3.ui.NavDisplay
import com.cinch.ui.budget.BudgetSetupWrapper
import com.cinch.ui.cards.CardDetailWrapper
import com.cinch.ui.cards.CardEditWrapper
import com.cinch.ui.cards.CardsWrapper
import com.cinch.ui.history.ExpenseEditWrapper
import com.cinch.ui.history.HistoryWrapper
import com.cinch.ui.home.HomeWrapper
import com.cinch.ui.insights.InsightsWrapper
import com.cinch.ui.month.DebtDetailWrapper
import com.cinch.ui.month.FixedEditWrapper
import com.cinch.ui.month.IncomeEditWrapper
import com.cinch.ui.month.MonthWrapper
import com.cinch.ui.receipt.ReceiptReviewWrapper
import com.cinch.ui.settings.SettingsWrapper
import kotlinx.datetime.number

/** Alt menünün sekmeleri; her biri kendi geri yığınının köküdür. */
enum class TopTab(val label: String, val root: CinchRoute) {
    TODAY("Bugün", HomeRoute),
    MONTH("Bu ay", MonthRoute),
    CARDS("Kartlar", CardsRoute),
    SETTINGS("Ayarlar", SettingsRoute),
}

/**
 * Uygulamanın gezinme kökü. İlk açılışta (bütçe yoksa) yalnız bütçe kurulumu gösterilir; sonra alt menülü
 * ana yapıya geçilir.
 */
@Composable
fun CinchNavHost(startWithOnboarding: Boolean) {
    var onboarding by rememberSaveable { mutableStateOf(startWithOnboarding) }
    if (onboarding) {
        BudgetSetupWrapper(
            route = BudgetSetupRoute(isOnboarding = true),
            onDone = { onboarding = false },
            onBack = {},
        )
    } else {
        MainTabs()
    }
}

/**
 * Sekme başına ayrı geri yığını (Navigation 3 "multiple back stacks" tarifi). Her yığının girişleri ayrı
 * süslenir; böylece sekme değişince ekran durumu ve ViewModel'ler kaybolmaz. Ekranda "Bugün" yığını ile
 * seçili sekmenin yığını üst üste durur: başka bir sekmenin kökünde geri tuşu "Bugün"e döner.
 */
@Composable
private fun MainTabs() {
    var currentIndex by rememberSaveable { mutableIntStateOf(0) }
    val current = TopTab.entries[currentIndex]
    val stacks: Map<TopTab, NavBackStack<CinchRoute>> = TopTab.entries.associateWith { tab ->
        key(tab) { rememberCinchBackStack(tab.root) }
    }
    val stack = stacks.getValue(current)

    // Girişler bir kez oluşturulup saklanır; bu yüzden hedef yığın çağrı anında okunur, yakalanmaz.
    // Yoksa "Kartlar" kökünden açılan detay, giriş oluşturulurken seçili olan "Bugün" yığınına düşerdi.
    val navigate: (CinchRoute) -> Unit = { route -> stacks.getValue(TopTab.entries[currentIndex]).add(route) }
    val goBack: () -> Unit = {
        val active = stacks.getValue(TopTab.entries[currentIndex])
        when {
            active.size > 1 -> active.removeAt(active.lastIndex)
            currentIndex != TopTab.TODAY.ordinal -> currentIndex = TopTab.TODAY.ordinal
        }
    }

    val entryProvider = cinchEntries(
        navigate = navigate,
        goBack = goBack,
        removeCardScreens = { id ->
            stacks.values.forEach { s ->
                s.removeAll { (it is CardDetailRoute && it.cardId == id) || (it is CardEditRoute && it.cardId == id) }
            }
        },
    )
    val decorated: Map<TopTab, List<NavEntry<CinchRoute>>> = TopTab.entries.associateWith { tab ->
        key(tab) {
            rememberDecoratedNavEntries(
                backStack = stacks.getValue(tab),
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                entryProvider = entryProvider,
            )
        }
    }
    val visibleEntries = if (current == TopTab.TODAY) {
        decorated.getValue(TopTab.TODAY)
    } else {
        decorated.getValue(TopTab.TODAY) + decorated.getValue(current)
    }

    Scaffold(
        // Kenar boşluklarını içerideki ekranların kendi Scaffold'ları yönetir.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            // Alt menü yalnız sekme köklerinde: detay ve form ekranları tüm alanı kullanır.
            AnimatedVisibility(visible = stack.size == 1, enter = expandVertically(), exit = shrinkVertically()) {
                NavigationBar {
                    TopTab.entries.forEach { tab ->
                        val selected = tab == current
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (selected) {
                                    // Seçili sekmeye tekrar dokununca köke dönülür.
                                    val s = stacks.getValue(tab)
                                    while (s.size > 1) s.removeAt(s.lastIndex)
                                } else {
                                    currentIndex = tab.ordinal
                                }
                            },
                            icon = { Icon(tab.icon(selected), contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavDisplay(
            entries = visibleEntries,
            onBack = goBack,
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
        )
    }
}

private fun TopTab.icon(selected: Boolean): ImageVector = when (this) {
    TopTab.TODAY -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
    TopTab.MONTH -> if (selected) Icons.Filled.CalendarMonth else Icons.Outlined.CalendarMonth
    TopTab.CARDS -> if (selected) Icons.Filled.CreditCard else Icons.Outlined.CreditCard
    TopTab.SETTINGS -> if (selected) Icons.Filled.Settings else Icons.Outlined.Settings
}

@Composable
private fun rememberCinchBackStack(root: CinchRoute): NavBackStack<CinchRoute> =
    rememberSerializable(serializer = NavBackStackSerializer(CinchRoute.serializer())) { NavBackStack(root) }

/** Tüm ekranların giriş tanımları. Ekranlar yalnız callback alır; gezinme kararı burada. */
private fun cinchEntries(
    navigate: (CinchRoute) -> Unit,
    goBack: () -> Unit,
    removeCardScreens: (Long) -> Unit,
): (CinchRoute) -> NavEntry<CinchRoute> = entryProvider {
    entry<HomeRoute> {
        HomeWrapper(
            onOpenExpense = { id -> navigate(ExpenseEditRoute(id)) },
            onOpenHistory = { navigate(HistoryRoute()) },
            onReceiptRead = { text -> navigate(ReceiptReviewRoute(text)) },
        )
    }
    entry<ReceiptReviewRoute> { route -> ReceiptReviewWrapper(route = route, onDone = { goBack() }, onBack = goBack) }
    entry<HistoryRoute> { route ->
        HistoryWrapper(route = route, onOpenExpense = { id -> navigate(ExpenseEditRoute(id)) }, onBack = goBack)
    }
    entry<ExpenseEditRoute> { route -> ExpenseEditWrapper(route = route, onDone = goBack, onBack = goBack) }
    entry<InsightsRoute> { route ->
        InsightsWrapper(
            route = route,
            // Kategoriye dokununca o ayın o kategorideki kayıtları açılır.
            onOpenCategory = { category, month ->
                navigate(HistoryRoute(categoryName = category.name, year = month.year, month = month.month.number))
            },
            onBack = goBack,
        )
    }
    entry<MonthRoute> {
        MonthWrapper(
            onAddIncome = { navigate(IncomeEditRoute()) },
            onOpenIncome = { id -> navigate(IncomeEditRoute(id)) },
            onAddFixed = { navigate(FixedEditRoute()) },
            onOpenFixed = { id -> navigate(FixedEditRoute(id)) },
            onOpenDebt = { id -> navigate(DebtDetailRoute(id)) },
            onOpenInsights = { navigate(InsightsRoute()) },
            onReceiptRead = { text -> navigate(ReceiptReviewRoute(text)) },
        )
    }
    entry<DebtDetailRoute> { route ->
        DebtDetailWrapper(route = route, onBack = goBack, onEdit = { navigate(FixedEditRoute(route.fixedId)) })
    }
    entry<FixedEditRoute> { route -> FixedEditWrapper(route = route, onDone = goBack, onBack = goBack) }
    entry<IncomeEditRoute> { route -> IncomeEditWrapper(route = route, onDone = goBack, onBack = goBack) }
    entry<CardsRoute> {
        CardsWrapper(
            onAdd = { navigate(CardEditRoute()) },
            onOpen = { id -> navigate(CardDetailRoute(id)) },
        )
    }
    entry<CardDetailRoute> { route ->
        CardDetailWrapper(route = route, onBack = goBack, onEdit = { navigate(CardEditRoute(route.cardId)) })
    }
    entry<CardEditRoute> { route ->
        CardEditWrapper(route = route, onDone = goBack, onDeleted = removeCardScreens, onBack = goBack)
    }
    entry<SettingsRoute> { SettingsWrapper(onOpenBudget = { navigate(BudgetSetupRoute()) }) }
    entry<BudgetSetupRoute> { route -> BudgetSetupWrapper(route = route, onDone = goBack, onBack = goBack) }
}
