package com.cinch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cinch.shared.money.AppLocale
import com.cinch.shared.money.MoneyFormatter
import com.cinch.shared.parser.QuickEntryParser
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.koin.compose.koinInject
import kotlin.time.Clock

/**
 * Hızlı kayıt demosu: domain çekirdeğinin canlı çalıştığını gösterir.
 * UI katmanı sonraki adımlarda genişletilecek (ekranlar, navigasyon, DI).
 */
@Composable
fun App() {
    MaterialTheme {
        // Koin'den gelir: kategori düzeltmeleri veritabanında kalıcı.
        val parser = koinInject<QuickEntryParser>()
        var input by remember { mutableStateOf("195 market") }
        val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
        val parsed = parser.parse(input, today)

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Cinch", style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Hızlı kayıt") },
            )
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val amount = parsed.chargeableAmount
                        ?.let { MoneyFormatter.format(it, AppLocale.TR) } ?: "—"
                    Text("Tutar (sana düşen): $amount")
                    Text("Kategori: ${parsed.category}")
                    Text("Tarih: ${parsed.date}")
                    Text("Pay oranı: ${parsed.chargeableFraction}")
                }
            }
        }
    }
}
