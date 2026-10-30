package org.trfl.app

import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

enum class ChanTheme(val label: String) {
    Automatic("Automatic (by board)"), Yotsuba("Yotsuba"), YotsubaB("Yotsuba B"),
    Futaba("Futaba"), Burichan("Burichan"), Tomorrow("Tomorrow"), Photon("Photon");

    companion object {
        fun fromSaved(value: String?) = entries.firstOrNull { it.name == value } ?: Automatic
    }
}

fun chanColors(theme: ChanTheme, safe: Boolean, dark: Boolean): ColorScheme {
    when (theme) {
        ChanTheme.Automatic -> return chanColors(safe, dark)
        ChanTheme.Yotsuba -> return chanColors(false, dark)
        ChanTheme.YotsubaB -> return chanColors(true, dark)
        else -> Unit
    }
    // Tomorrow is intrinsically dark; the other classic styles have dark variants.
    val isDark = dark || theme == ChanTheme.Tomorrow
    val (background, reply, accent) = when (theme) {
        ChanTheme.Futaba -> if (isDark) Triple(0xFF231A17, 0xFF392923, 0xFFFFB49C) else Triple(0xFFFFFFEE, 0xFFF0E0D6, 0xFFCC1105)
        ChanTheme.Burichan -> if (isDark) Triple(0xFF171923, 0xFF252A3D, 0xFFB8C4FF) else Triple(0xFFEEF2FF, 0xFFD6DAF0, 0xFF0000EE)
        ChanTheme.Tomorrow -> Triple(0xFF1D1F21, 0xFF282A2E, 0xFF81A2BE)
        else -> if (isDark) Triple(0xFF1C1C1C, 0xFF2C2C2C, 0xFFC5C5C5) else Triple(0xFFFFFFFF, 0xFFEEEEEE, 0xFF333333)
    }
    val bg = Color(background)
    val card = Color(reply)
    val primary = Color(accent)
    val foreground = Color(if (isDark) 0xFFC5C8C6 else 0xFF202020)
    return chanColors(safe, isDark).copy(
        primary = primary, onPrimary = if (isDark) bg else Color.White,
        primaryContainer = card, onPrimaryContainer = primary,
        secondary = primary, onSecondary = if (isDark) bg else Color.White,
        secondaryContainer = card, onSecondaryContainer = primary,
        background = bg, onBackground = foreground, surface = bg, onSurface = foreground,
        surfaceVariant = card, onSurfaceVariant = foreground,
        surfaceContainerLowest = bg, surfaceContainerLow = card,
        surfaceContainer = bg, surfaceContainerHigh = card, surfaceContainerHighest = card,
        surfaceTint = primary, outline = primary, outlineVariant = card
    )
}

/** 4chan's Yotsuba B (SFW) and Yotsuba (NSFW) background/reply colors. */
fun chanColors(safe: Boolean, dark: Boolean = false): ColorScheme {
    if (dark) {
        val background = Color(if (safe) 0xFF171923 else 0xFF231A17)
        val reply = Color(if (safe) 0xFF252A3D else 0xFF392923)
        val accent = Color(if (safe) 0xFFB8C4FF else 0xFFFFB49C)
        val foreground = Color(if (safe) 0xFFE5E7F2 else 0xFFF4E4DC)
        return darkColorScheme(
            primary = accent, onPrimary = background,
            primaryContainer = reply, onPrimaryContainer = accent,
            secondary = accent, onSecondary = background,
            secondaryContainer = reply, onSecondaryContainer = accent,
            background = background, onBackground = foreground,
            surface = background, onSurface = foreground,
            surfaceVariant = reply, onSurfaceVariant = Color(if (safe) 0xFFBFC3D6 else 0xFFD5BFB4),
            surfaceContainerLowest = background, surfaceContainerLow = reply,
            surfaceContainer = background, surfaceContainerHigh = reply,
            surfaceContainerHighest = reply, surfaceTint = accent,
            outline = Color(if (safe) 0xFF9299B2 else 0xFFB09A90), outlineVariant = reply
        )
    }
    val background = Color(if (safe) 0xFFEEF2FF else 0xFFFFFFEE)
    val reply = Color(if (safe) 0xFFD6DAF0 else 0xFFF0E0D6)
    val accent = Color(if (safe) 0xFF34345C else 0xFF800000)
    return lightColorScheme(
        primary = accent, onPrimary = Color.White,
        primaryContainer = reply, onPrimaryContainer = accent,
        secondary = accent, onSecondary = Color.White,
        secondaryContainer = reply, onSecondaryContainer = accent,
        background = background, onBackground = Color(0xFF202020),
        surface = background, onSurface = Color(0xFF202020),
        surfaceVariant = reply, onSurfaceVariant = Color(0xFF454545),
        surfaceContainerLowest = background, surfaceContainerLow = reply,
        surfaceContainer = background, surfaceContainerHigh = reply,
        surfaceContainerHighest = reply, surfaceTint = accent,
        outline = accent, outlineVariant = reply
    )
}

@Composable
fun SettingsToggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        modifier = Modifier.clickable { onChange(!checked) }
    )
}
