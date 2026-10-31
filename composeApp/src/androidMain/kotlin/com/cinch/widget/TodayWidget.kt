package com.cinch.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.cinch.app.MainActivity
import com.cinch.shared.budget.ObserveBudgetStatus
import com.cinch.shared.domain.repository.BudgetRepository
import com.cinch.shared.domain.repository.ExpenseRepository
import com.cinch.shared.gamification.ObserveGamification
import com.cinch.shared.time.LocalClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named

/**
 * Ana ekran widget'ı: bugün harcayabileceğin, haftanın durumu, sınır serisi; "+ Harcama" ile uygulamayı açmadan
 * küçük bir kayıt penceresi. Veriler telefondaki veritabanından okunur.
 */
class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = load()
        provideContent { Content(data) }
    }

    private suspend fun load(): TodaySnapshot {
        val koin = GlobalContext.getOrNull() ?: return TodaySnapshot.NO_BUDGET
        return LoadTodaySnapshot(koin.get<ObserveBudgetStatus>(), koin.get<ObserveGamification>(), koin.get<LocalClock>()).invoke()
    }

    @Composable
    private fun Content(data: TodaySnapshot) {
        val background = if (data.over) Color(0xFFFFD6DA) else Color(0xFFFFE3CC)
        val ink = ColorProvider(if (data.over) Color(0xFF5C0F1E) else Color(0xFF3B0A00))
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(background)
                .cornerRadius(24.dp)
                .padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                if (!data.hasBudget) {
                    Text("Cinch", style = TextStyle(color = ink, fontWeight = FontWeight.Bold, fontSize = 16.sp))
                    Spacer(GlanceModifier.height(6.dp))
                    Text("Haftalık bütçeni kurmak için dokun", style = TextStyle(color = ink, fontSize = 14.sp))
                } else {
                    Text(
                        if (data.over) "Bugünkü payını aştın" else "Bugün harcayabileceğin",
                        style = TextStyle(color = ink, fontSize = 12.sp),
                    )
                    Text(data.todayText, style = TextStyle(color = ink, fontWeight = FontWeight.Bold, fontSize = 28.sp))
                    Text(data.weekText, style = TextStyle(color = ink, fontSize = 12.sp))
                    Spacer(GlanceModifier.defaultWeight())
                    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (data.streak > 0) "🔥 ${data.streak} gün" else "",
                            style = TextStyle(color = ink, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                            modifier = GlanceModifier.defaultWeight(),
                        )
                        Box(
                            modifier = GlanceModifier
                                .background(Color(0xFFC4502F))
                                .cornerRadius(18.dp)
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .clickable(actionStartActivity<QuickAddActivity>()),
                        ) {
                            Text("+ Harcama", style = TextStyle(color = ColorProvider(Color.White), fontWeight = FontWeight.Bold, fontSize = 14.sp))
                        }
                    }
                }
            }
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

/**
 * Bütçe ya da harcamalar değişince widget'ı yeniler (uygulamadan, hızlı kayıt penceresinden ya da fişten eklenince).
 * Uygulama açılışında bir kez kurulur; gün değişimi için widget yarım saatte bir kendiliğinden de güncellenir.
 */
object TodayWidgetUpdater {

    private const val DEBOUNCE_MS = 1_000L

    @OptIn(FlowPreview::class)
    fun start(context: Context) {
        val koin = GlobalContext.getOrNull() ?: return
        val app = context.applicationContext
        val scope = koin.get<CoroutineScope>(named("appScope"))
        val changes = widgetChanges(koin.get<BudgetRepository>(), koin.get<ExpenseRepository>())
        scope.launch {
            changes.debounce(DEBOUNCE_MS).collect { TodayWidget().updateAll(app) }
        }
    }
}
