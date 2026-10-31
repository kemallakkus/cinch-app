package com.cinch.ui.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cinch.shared.domain.Category
import com.cinch.shared.money.Currency
import com.cinch.shared.money.Money
import com.cinch.ui.common.AnimatedMoneyText
import com.cinch.ui.common.CategoryBadge
import com.cinch.ui.common.HeroCard
import com.cinch.ui.common.display
import com.cinch.ui.theme.CinchTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class IntroPage(
    val title: String,
    val body: String,
    val illustration: @Composable (active: Boolean) -> Unit
)

private val pages = listOf(
    IntroPage(
        title = "Cebindeki bütçe koçu",
        body = "Haftalık bir harcama sınırı koy; Cinch onu günlere böler. Her gün \"bugün ne kadar harcayabilirim\" " +
                "sorusunun cevabı hazır. Az harcadığın gün, yarınki payın büyür.",
        illustration = { DailyShareDemo(it) },
    ),
    IntroPage(
        title = "Yaz, gerisini Cinch anlasın",
        body = "Form yok. \"195 market\" yazman yeter: tutar, kategori ve tarih kendiliğinden anlaşılır. " +
                "Fişi, faturayı ya da banka ekranını fotoğraftan da okur.",
        illustration = { QuickEntryDemo(it) },
    ),
    IntroPage(
        title = "Sınırda kal, serini büyüt",
        body = "Payının içinde kaldığın her gün serin uzar, rozet kazanırsın. Haftadan artanı tek dokunuşla " +
                "kumbaraya at. Koç, sınıra yaklaşınca ve ödeme günlerinde haber verir.",
        illustration = { ProgressDemo(it) },
    ),
    IntroPage(
        title = "Verilerin yalnız bu telefonda",
        body = "Hesap açmak yok, banka şifresi yok. Her şey bu telefonda kalır; istediğinde tek dosyalık yedek alırsın.",
        illustration = { PrivacyDemo() },
    ),
)

/**
 * İlk açılışta, bütçe kurulumundan önce gösterilen tanıtım: dört sayfa, kaydırarak ya da "İleri" ile geçilir.
 * "Geç" de son düğme de [onFinish]'i çağırır; tanıtım bir daha gösterilmez.
 */
@Composable
fun IntroScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == pages.lastIndex

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Cinch",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (!isLast) TextButton(onClick = onFinish) { Text("Geç") }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) { index ->
                val page = pages[index]
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        page.illustration(pagerState.currentPage == index)
                    }
                    Spacer(Modifier.height(32.dp))
                    Text(
                        page.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        page.body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            PageDots(
                count = pages.size,
                current = pagerState.currentPage,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    if (isLast) onFinish() else scope.launch {
                        pagerState.animateScrollToPage(
                            pagerState.currentPage + 1
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(
                    if (isLast) "Bütçemi kuralım" else "İleri",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == current) 24.dp else 8.dp, label = "dot")
            Box(
                Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

// ───────────────────────── Sayfa çizimleri ─────────────────────────

private fun tl(units: Long) = Money.of(units, Currency.TRY)

/** Sayfa açılınca günlük pay 0'dan sayarak gelir. */
@Composable
private fun DailyShareDemo(active: Boolean) {
    val colors = CinchTheme.colors
    HeroCard(brush = colors.hero, modifier = Modifier.width(300.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Bugün harcayabileceğin",
                style = MaterialTheme.typography.labelLarge,
                color = colors.onHero
            )
            AnimatedMoneyText(
                money = if (active) tl(640) else tl(0),
                style = MaterialTheme.typography.displaySmall,
                color = colors.onHero,
            )
            Text(
                "Haftalık 4.500 ₺ · 7 güne bölündü",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onHero
            )
            Spacer(Modifier.height(12.dp))
            // Haftanın günleri: harcanan / günlük pay.
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                listOf(0.6f, 0.9f, 0.4f, 0.75f, 0.2f, 0f, 0f).forEachIndexed { i, fill ->
                    val height by animateDpAsState(
                        if (active) (6 + 42 * fill).dp else 6.dp,
                        label = "bar$i"
                    )
                    Box(
                        Modifier
                            .width(28.dp)
                            .height(height)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.onHero.copy(alpha = if (fill > 0f) 0.55f else 0.18f)),
                    )
                }
            }
        }
    }
}

private data class EntryExample(
    val text: String,
    val category: Category,
    val amount: Long,
    val note: String
)

private val entryExamples = listOf(
    EntryExample("195 market", Category.MARKET, 195, "Bugün"),
    EntryExample("dün 120 taksi", Category.ULASIM, 120, "Dün"),
    EntryExample("300 yemek yarı yarıya", Category.YEMEK, 150, "Senin payın: yarısı"),
)

/** Yazılan cümle harf harf belirir, altında anlaşılan kayıt çıkar. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickEntryDemo(active: Boolean) {
    var exampleIndex by remember { mutableIntStateOf(0) }
    var typed by remember { mutableStateOf("") }
    var showResult by remember { mutableStateOf(false) }

    LaunchedEffect(active) {
        if (active) {
            while (true) {
                val example = entryExamples[exampleIndex]
                showResult = false
                for (n in 0..example.text.length) {
                    typed = example.text.take(n)
                    delay(65)
                }
                showResult = true
                delay(1_800)
                exampleIndex = (exampleIndex + 1) % entryExamples.size
            }
        }
    }

    val example = entryExamples[exampleIndex]
    Column(Modifier.width(300.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "$typed|",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }
        Box(Modifier.fillMaxWidth().height(64.dp)) {
            // Paket adıyla: yoksa dıştaki Column'un ColumnScope.AnimatedVisibility'sine bağlanıp derlenmiyor.
            androidx.compose.animation.AnimatedVisibility(
                visible = showResult,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CategoryBadge(example.category, size = 40.dp)
                        Column(Modifier.weight(1f)) {
                            Text(
                                tl(example.amount).display(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                example.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            "✓",
                            style = MaterialTheme.typography.titleLarge,
                            color = CinchTheme.colors.good
                        )
                    }
                }
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth()
        ) {
            listOf("📷 Fiş", "🧾 Fatura", "📸 Banka ekranı").forEach { DemoChip(it) }
        }
    }
}

/** Seri, rozetler ve dolan bir kumbara. */
@Composable
private fun ProgressDemo(active: Boolean) {
    val fill by animateFloatAsState(if (active) 0.62f else 0f, label = "goal")
    Column(
        Modifier.width(300.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Text(
                "🔥 7 gün sınırda",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("🌱", "🔥", "🏅", "🎯", "💰").forEach { emoji ->
                Box(
                    Modifier.size(48.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) { Text(emoji, style = MaterialTheme.typography.titleLarge) }
            }
        }
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row {
                    Text(
                        "🏖️ Tatil",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${tl((20_000 * fill).toLong()).display()} / ${tl(20_000).display()}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                LinearProgressIndicator(
                    progress = { fill },
                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
                    color = CinchTheme.colors.good,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
        }
    }
}

@Composable
private fun PrivacyDemo() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            Modifier.size(120.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center,
        ) { Text("🔒", style = MaterialTheme.typography.displayMedium) }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                "Hesap ve şifre yok",
                "İnternete gönderilmez",
                "Fotoğraflar telefonda okunur, saklanmaz",
            ).forEach { line ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("✓", color = CinchTheme.colors.good, fontWeight = FontWeight.Bold)
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun DemoChip(text: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
