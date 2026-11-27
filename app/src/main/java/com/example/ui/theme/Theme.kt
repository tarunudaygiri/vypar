package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import com.example.data.model.AppLanguage
import com.example.data.model.FontSizeOption
import com.example.i18n.LocalStrings
import com.example.i18n.stringsFor

private val LightColors = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = EmeraldOnPrimary,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = EmeraldOnContainer,
    secondary = SaffronSecondary,
    onSecondary = SaffronOnSecondary,
    secondaryContainer = SaffronContainer,
    onSecondaryContainer = SaffronOnContainer,
    tertiary = SlateTertiary,
    onTertiary = SlateOnTertiary,
    tertiaryContainer = SlateContainer,
    onTertiaryContainer = SlateOnContainer,
    background = LedgerPaperBg,
    onBackground = LedgerInk,
    surface = LedgerSurface,
    onSurface = LedgerInk,
    surfaceVariant = LedgerSurfaceVariant,
    onSurfaceVariant = LedgerInkMuted,
    outline = LedgerOutline,
    error = DebitRed
)

private val DarkColors = darkColorScheme(
    primary = DarkEmeraldPrimary,
    onPrimary = DarkEmeraldOnPrimary,
    primaryContainer = DarkEmeraldContainer,
    onPrimaryContainer = DarkEmeraldOnContainer,
    secondary = DarkSaffronSecondary,
    onSecondary = DarkSaffronOnSecondary,
    secondaryContainer = DarkSaffronContainer,
    onSecondaryContainer = DarkSaffronOnContainer,
    tertiary = SlateContainer,
    onTertiary = SlateOnContainer,
    background = DarkBg,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkInkMuted,
    outline = DarkOutline
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun VyaparDeskTheme(
    language: AppLanguage = AppLanguage.ENGLISH,
    fontSizeOption: FontSizeOption = FontSizeOption.NORMAL,
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val typography = buildAppTypography(language, fontSizeOption.scale)
    val strings = stringsFor(language)

    CompositionLocalProvider(LocalStrings provides strings) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = AppShapes,
            content = content
        )
    }
}
