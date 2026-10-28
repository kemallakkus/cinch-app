package com.cinch.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cinch.shared.budget.CategorySpend
import com.cinch.shared.budget.SpendingBreakdown
import com.cinch.ui.common.CategoryBadge
import com.cinch.ui.common.DonutSlice
import com.cinch.ui.common.display
import com.cinch.ui.common.label
import com.cinch.ui.theme.CinchTheme
import com.cinch.ui.theme.chartColor
import kotlin.math.abs
import kotlin.math.roundToInt

// "Nereye gitti?" ekranı ile "Bu ay" sekmesindeki özet kartının ortak parçaları.

/** Değişimi %5'ten küçükse "aynı" sayarız; kuruş oynamaları haber değildir. */
private const val SAME_THRESHOLD = 0.05

internal fun SpendingBreakdown.donutSlices(): List<DonutSlice> =
    slices.map { DonutSlice(it.share, it.category.chartColor()) }

/** "Geçen ayın aynı günlerine göre %12 daha az 👏" gibi tek cümlelik kıyas. */
internal fun SpendingBreakdown.comparisonSentence(): String {
    val current = period.isCurrentMonth
    val c = change ?: return if (current) "Geçen ay bu günlerde kayıt yoktu." else "Geçen ay kayıt yoktu."
    val base = if (current) "Geçen ayın aynı günlerine göre" else "Geçen aya göre"
    val percent = (abs(c) * 100).roundToInt()
    return when {
        abs(c) < SAME_THRESHOLD -> if (current) "Geçen ayın aynı günleriyle hemen hemen aynı." else "Geçen ayla hemen hemen aynı."
        c < 0 -> "$base %$percent daha az 👏"
        else -> "$base %$percent daha fazla"
    }
}

/** Artış "dikkat" (bal), azalış "iyi" (nane) renginde; aynıysa sönük. */
@Composable
internal fun changeColor(change: Double?): Color = when {
    change == null || abs(change) < SAME_THRESHOLD -> MaterialTheme.colorScheme.onSurfaceVariant
    change > 0 -> CinchTheme.colors.caution
    else -> CinchTheme.colors.good
}

/** Kategori satırındaki kısa değişim: "↑ %30", "↓ %20", "≈ aynı", "yeni", "bu ay hiç". */
internal fun CategorySpend.changeText(): String {
    val c = change ?: return "yeni"
    if (amount.isZero) return "bu ay hiç"
    val percent = (abs(c) * 100).roundToInt()
    return when {
        abs(c) < SAME_THRESHOLD -> "≈ aynı"
        c > 0 -> "↑ %$percent"
        else -> "↓ %$percent"
    }
}

/** Kategori satırı: rozet, ad, payını gösteren ince çubuk, tutar ve geçen döneme göre değişim. */
@Composable
internal fun CategorySpendRow(spend: CategorySpend, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryBadge(spend.category)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(spend.category.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (spend.count == 0) "Geçen ay ${spend.previous.display()}"
                else "${spend.count} kayıt · %${(spend.share * 100).roundToInt()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(spend.share.coerceIn(0f, 1f))
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(spend.category.chartColor()),
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(spend.amount.display(), style = MaterialTheme.typography.titleSmall)
            Text(spend.changeText(), style = MaterialTheme.typography.labelMedium, color = changeColor(spend.change))
        }
    }
}
