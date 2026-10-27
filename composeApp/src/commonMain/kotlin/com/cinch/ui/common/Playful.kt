package com.cinch.ui.common

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cinch.shared.domain.Category
import com.cinch.shared.money.Money
import com.cinch.ui.theme.colors
import kotlin.math.roundToLong

/**
 * Tutar değişince eski değerden yenisine sayarak geçer (kayıt eklenince rakam "akar").
 * Animasyon bitince tam tutar gösterilir: Float ara değerleri büyük tutarlarda kuruşu kaydırabilir.
 */
@Composable
fun AnimatedMoneyText(money: Money, style: TextStyle, color: Color = Color.Unspecified, modifier: Modifier = Modifier) {
    val target = money.minor.toFloat()
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "money",
    )
    val shown = if (animated == target) money else Money(animated.roundToLong(), money.currency)
    Text(shown.display(), style = style, color = color, modifier = modifier)
}

/** Kategorinin emojisi, kendi renginde yuvarlak bir rozet içinde. */
@Composable
fun CategoryBadge(category: Category, size: Dp = 44.dp) {
    val colors = category.colors()
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(colors.container),
        contentAlignment = Alignment.Center,
    ) {
        Text(category.emoji, style = MaterialTheme.typography.titleMedium)
    }
}

/** Büyük, yuvarlak köşeli, degrade zeminli kart: ekranın ana rakamı için. */
@Composable
fun HeroCard(brush: Brush, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(brush)
            .padding(24.dp),
        content = content,
    )
}
