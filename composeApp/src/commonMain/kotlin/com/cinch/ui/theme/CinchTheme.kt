package com.cinch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinch.shared.domain.Category

// Sıcak ve oyunsu: krem zemin, mercan/şeftali vurgu, nane yeşili "iyi gidiyorsun", bal sarısı "dikkat".

private val LightColors = lightColorScheme(
    primary = Color(0xFFC4502F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCCF),
    onPrimaryContainer = Color(0xFF3B0A00),
    secondary = Color(0xFF8A5A00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE3A6),
    onSecondaryContainer = Color(0xFF2B1A00),
    tertiary = Color(0xFF2E7D6B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBDEFE0),
    onTertiaryContainer = Color(0xFF00201A),
    background = Color(0xFFFFF8F2),
    onBackground = Color(0xFF2A1F1B),
    surface = Color(0xFFFFF8F2),
    onSurface = Color(0xFF2A1F1B),
    surfaceVariant = Color(0xFFF5DED4),
    onSurfaceVariant = Color(0xFF6E5A52),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF1E8),
    surfaceContainer = Color(0xFFFBEADF),
    surfaceContainerHigh = Color(0xFFF6E2D6),
    surfaceContainerHighest = Color(0xFFF0DACD),
    outline = Color(0xFFA08C84),
    outlineVariant = Color(0xFFE5D3C9),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB59F),
    onPrimary = Color(0xFF5E1600),
    primaryContainer = Color(0xFF7E2C12),
    onPrimaryContainer = Color(0xFFFFDBD0),
    secondary = Color(0xFFF3C35E),
    onSecondary = Color(0xFF412D00),
    secondaryContainer = Color(0xFF5C4300),
    onSecondaryContainer = Color(0xFFFFDF9E),
    tertiary = Color(0xFF8FD5C1),
    onTertiary = Color(0xFF00382D),
    tertiaryContainer = Color(0xFF00513F),
    onTertiaryContainer = Color(0xFFACF2DD),
    background = Color(0xFF1E1612),
    onBackground = Color(0xFFF1DFD8),
    surface = Color(0xFF1E1612),
    onSurface = Color(0xFFF1DFD8),
    surfaceVariant = Color(0xFF52443E),
    onSurfaceVariant = Color(0xFFD8C2BA),
    surfaceContainerLowest = Color(0xFF180F0C),
    surfaceContainerLow = Color(0xFF251C18),
    surfaceContainer = Color(0xFF2A211C),
    surfaceContainerHigh = Color(0xFF352B26),
    surfaceContainerHighest = Color(0xFF403530),
    outline = Color(0xFFA08C84),
    outlineVariant = Color(0xFF52443E),
)

/** Büyük, yumuşak köşeler: kartlar 24, alanlar 16; düğmeler zaten hap biçiminde. */
private val CinchShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/** Sistem yazı tipi, daha tok ağırlıklarla: büyük rakamlar ve başlıklar dikkat çeker. */
private val CinchTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontWeight = FontWeight.ExtraBold),
        displayMedium = displayMedium.copy(fontWeight = FontWeight.ExtraBold),
        displaySmall = displaySmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Kategori çipi ve rozeti için arka plan / ön plan çifti. */
@Immutable
data class CategoryColors(val container: Color, val content: Color)

/** Material renk şemasında olmayan, uygulamaya özgü renkler. */
@Immutable
data class CinchExtraColors(
    val isDark: Boolean,
    /** Ana kartın degradesi (bütçe içindeyken). */
    val hero: Brush,
    val onHero: Color,
    /** Ana kartın degradesi (pay ya da sınır aşılınca): kırmızı değil, yumuşak gül rengi. */
    val heroOver: Brush,
    val onHeroOver: Color,
    /** "İyi gidiyorsun" (nane) ve "dikkat" (bal) vurguları. */
    val good: Color,
    val caution: Color,
    /** Konfeti parçaları. */
    val confetti: List<Color>,
    /** Kart kutucuklarının degradeleri; karta id'sine göre biri verilir. */
    val cardGradients: List<Brush>,
)

// Dosya düzeyindeki değerler sırayla başlatılır: aşağıdakiler bunu kullandığı için önce gelmeli.
// Beyaz yazı okunacak kadar koyu, yine de canlı tonlar.
private val cardGradients = listOf(
    Brush.linearGradient(listOf(Color(0xFFB3541E), Color(0xFFE07A3F))),
    Brush.linearGradient(listOf(Color(0xFF5B3FA8), Color(0xFF9B4FB0))),
    Brush.linearGradient(listOf(Color(0xFF0F7A63), Color(0xFF2E9E7E))),
    Brush.linearGradient(listOf(Color(0xFF2A5DB8), Color(0xFF3F8FD0))),
    Brush.linearGradient(listOf(Color(0xFFA3456B), Color(0xFFD0687F))),
)

private val LightExtra = CinchExtraColors(
    isDark = false,
    hero = Brush.linearGradient(listOf(Color(0xFFFFC9AE), Color(0xFFFFE6B8))),
    onHero = Color(0xFF3B0A00),
    heroOver = Brush.linearGradient(listOf(Color(0xFFFFC2C7), Color(0xFFFFDCCB))),
    onHeroOver = Color(0xFF5C0F1E),
    good = Color(0xFF2E7D6B),
    caution = Color(0xFFB7791F),
    confetti = listOf(
        Color(0xFFFF8A65), Color(0xFFFFC94D), Color(0xFF5CCB9F), Color(0xFF6FA8FF), Color(0xFFE57BC3),
    ),
    cardGradients = cardGradients,
)

private val DarkExtra = CinchExtraColors(
    isDark = true,
    hero = Brush.linearGradient(listOf(Color(0xFF7E2C12), Color(0xFF5C4300))),
    onHero = Color(0xFFFFE8DF),
    heroOver = Brush.linearGradient(listOf(Color(0xFF7A1F33), Color(0xFF6B2A1B))),
    onHeroOver = Color(0xFFFFE1E4),
    good = Color(0xFF8FD5C1),
    caution = Color(0xFFF3C35E),
    confetti = listOf(
        Color(0xFFFFAB91), Color(0xFFFFD970), Color(0xFF7FE0B8), Color(0xFF9BC2FF), Color(0xFFF19AD4),
    ),
    cardGradients = cardGradients,
)

private val LocalCinchColors = staticCompositionLocalOf { LightExtra }

@Composable
fun CinchTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalCinchColors provides if (darkTheme) DarkExtra else LightExtra) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            shapes = CinchShapes,
            typography = CinchTypography,
            content = content,
        )
    }
}

/** `CinchTheme.colors.hero` gibi erişim için; `MaterialTheme` kalıbıyla aynı. */
object CinchTheme {
    val colors: CinchExtraColors
        @Composable @ReadOnlyComposable get() = LocalCinchColors.current
}

/** Her kategorinin kendi pastel rengi; listede bir bakışta ayırt edilir. */
@Composable
@ReadOnlyComposable
fun Category.colors(): CategoryColors {
    val (light, dark) = when (this) {
        Category.MARKET -> CategoryColors(Color(0xFFD4F1D0), Color(0xFF1F5C2A)) to CategoryColors(Color(0xFF1F4D29), Color(0xFFC8F0C2))
        Category.YEMEK -> CategoryColors(Color(0xFFFFE0C7), Color(0xFF7A3A00)) to CategoryColors(Color(0xFF6B3410), Color(0xFFFFDCC2))
        Category.ULASIM -> CategoryColors(Color(0xFFD3E6FF), Color(0xFF003A73)) to CategoryColors(Color(0xFF173B66), Color(0xFFD3E6FF))
        Category.FATURA -> CategoryColors(Color(0xFFFFF1B8), Color(0xFF5C4A00)) to CategoryColors(Color(0xFF574500), Color(0xFFFFEFA8))
        Category.SAGLIK -> CategoryColors(Color(0xFFFFD9DF), Color(0xFF7A1530)) to CategoryColors(Color(0xFF6B1C2E), Color(0xFFFFD9DF))
        Category.EGLENCE -> CategoryColors(Color(0xFFEADBFF), Color(0xFF4A2180)) to CategoryColors(Color(0xFF412066), Color(0xFFEADBFF))
        Category.GIYIM -> CategoryColors(Color(0xFFFFD8F0), Color(0xFF7A1660)) to CategoryColors(Color(0xFF661A52), Color(0xFFFFD8F0))
        Category.KISISEL -> CategoryColors(Color(0xFFCFF3EE), Color(0xFF005048)) to CategoryColors(Color(0xFF0F4A44), Color(0xFFCFF3EE))
        Category.EV -> CategoryColors(Color(0xFFF3E0CF), Color(0xFF5A3A1E)) to CategoryColors(Color(0xFF55391F), Color(0xFFF3E0CF))
        Category.DIGER -> CategoryColors(Color(0xFFE8E2DE), Color(0xFF4A403B)) to CategoryColors(Color(0xFF433A35), Color(0xFFE8E2DE))
    }
    return if (CinchTheme.colors.isDark) dark else light
}
