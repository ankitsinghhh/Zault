package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.material3.Shapes
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = PixelPrimaryDark,
    onPrimary = PixelOnPrimaryDark,
    primaryContainer = PixelPrimaryContainerDark,
    onPrimaryContainer = PixelOnPrimaryContainerDark,
    secondary = PixelSecondaryDark,
    onSecondary = PixelOnSecondaryDark,
    secondaryContainer = PixelSecondaryContainerDark,
    onSecondaryContainer = PixelOnSecondaryContainerDark,
    tertiary = PixelTertiaryDark,
    onTertiary = PixelOnTertiaryDark,
    background = PixelBackgroundDark,
    onBackground = PixelOnBackgroundDark,
    surface = PixelSurfaceDark,
    onSurface = PixelOnSurfaceDark,
    surfaceVariant = PixelSurfaceVariantDark,
    onSurfaceVariant = PixelOnSurfaceVariantDark,
    error = PixelError
)

private val LightColorScheme = lightColorScheme(
    primary = PixelPrimaryLight,
    onPrimary = PixelOnPrimaryLight,
    primaryContainer = PixelPrimaryContainerLight,
    onPrimaryContainer = PixelOnPrimaryContainerLight,
    secondary = PixelSecondaryLight,
    onSecondary = PixelOnSecondaryLight,
    secondaryContainer = PixelSecondaryContainerLight,
    onSecondaryContainer = PixelOnSecondaryContainerLight,
    tertiary = PixelTertiaryLight,
    onTertiary = PixelOnTertiaryLight,
    background = PixelBackgroundLight,
    onBackground = PixelOnBackgroundLight,
    surface = PixelSurfaceLight,
    onSurface = PixelOnSurfaceLight,
    surfaceVariant = PixelSurfaceVariantLight,
    onSurfaceVariant = PixelOnSurfaceVariantLight,
    error = PixelError
)

@Composable
fun PrivateGalleryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    paletteId: Int = 0,
    content: @Composable () -> Unit
) {
    val colorScheme = galleryColorScheme(paletteId, darkTheme)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(26.dp),
            extraLarge = RoundedCornerShape(32.dp)
        ),
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    paletteId: Int = 0,
    content: @Composable () -> Unit
) {
    PrivateGalleryTheme(darkTheme = darkTheme, paletteId = paletteId, content = content)
}

data class GalleryPalette(val id: Int, val name: String, val preview: androidx.compose.ui.graphics.Color)

val GalleryPalettes = listOf(
    GalleryPalette(0, "Ocean", androidx.compose.ui.graphics.Color(0xFF1972E8)),
    GalleryPalette(1, "Graphite", androidx.compose.ui.graphics.Color(0xFF59636E)),
    GalleryPalette(2, "Lavender", androidx.compose.ui.graphics.Color(0xFF7656C9)),
    GalleryPalette(3, "Rose", androidx.compose.ui.graphics.Color(0xFFB24C70)),
    GalleryPalette(4, "Forest", androidx.compose.ui.graphics.Color(0xFF28775A)),
    GalleryPalette(5, "Sunset", androidx.compose.ui.graphics.Color(0xFFC45D32)),
    GalleryPalette(6, "Teal", androidx.compose.ui.graphics.Color(0xFF34736F)),
    GalleryPalette(7, "Indigo", androidx.compose.ui.graphics.Color(0xFF5369A8)),
    GalleryPalette(8, "Sky", androidx.compose.ui.graphics.Color(0xFF39749A)),
    GalleryPalette(9, "Mint", androidx.compose.ui.graphics.Color(0xFF4F7161)),
    GalleryPalette(10, "Coral", androidx.compose.ui.graphics.Color(0xFF9A5D52)),
    GalleryPalette(11, "Plum", androidx.compose.ui.graphics.Color(0xFF76566F))
)

private fun galleryColorScheme(id: Int, dark: Boolean): androidx.compose.material3.ColorScheme {
    val palette = when (id.coerceIn(0, 11)) {
        1 -> AccentColors(0xFF56616D, 0xFFDDE3E9, 0xFFC0C8D0, 0xFF3C4650)
        2 -> AccentColors(0xFF66558A, 0xFFE9E1F5, 0xFFCFBDF3, 0xFF4B3C68)
        3 -> AccentColors(0xFF875568, 0xFFF4E0E7, 0xFFEABDD0, 0xFF663C4C)
        4 -> AccentColors(0xFF4C6759, 0xFFDFEBE4, 0xFFBBD8C8, 0xFF354E42)
        5 -> AccentColors(0xFF78603A, 0xFFF1E6D3, 0xFFE5C995, 0xFF584624)
        6 -> AccentColors(0xFF3C6966, 0xFFDCEBE9, 0xFFB5D8D4, 0xFF31504E)
        7 -> AccentColors(0xFF53669A, 0xFFE0E5F4, 0xFFBDC8EA, 0xFF3B4A73)
        8 -> AccentColors(0xFF426C86, 0xFFDCE9F0, 0xFFB5D4E4, 0xFF315064)
        9 -> AccentColors(0xFF526C60, 0xFFE0EAE5, 0xFFC0D7CB, 0xFF3C5147)
        10 -> AccentColors(0xFF875D55, 0xFFF1E3DF, 0xFFE4C3BB, 0xFF65443E)
        11 -> AccentColors(0xFF755B70, 0xFFEEE2EB, 0xFFDDBFD6, 0xFF584254)
        else -> AccentColors(0xFF1967D2, 0xFFDCE8FA, 0xFFA8C7FA, 0xFF084B9B)
    }
    val base = if (dark) DarkColorScheme else LightColorScheme
    return base.copy(
        primary = androidx.compose.ui.graphics.Color(if (dark) palette.darkPrimary else palette.primary),
        onPrimary = if (dark) androidx.compose.ui.graphics.Color(0xFF202124) else androidx.compose.ui.graphics.Color.White,
        primaryContainer = androidx.compose.ui.graphics.Color(if (dark) palette.darkContainer else palette.container),
        onPrimaryContainer = if (dark) androidx.compose.ui.graphics.Color(0xFFF1F3F4) else androidx.compose.ui.graphics.Color(0xFF202124),
        secondary = androidx.compose.ui.graphics.Color(if (dark) palette.darkPrimary else palette.primary),
        secondaryContainer = androidx.compose.ui.graphics.Color(if (dark) 0xFF34373B else 0xFFF0F2F5),
        surfaceVariant = androidx.compose.ui.graphics.Color(if (dark) 0xFF303236 else 0xFFF0F2F5)
    )
}

private data class AccentColors(
    val primary: Long,
    val container: Long,
    val darkPrimary: Long,
    val darkContainer: Long
)
