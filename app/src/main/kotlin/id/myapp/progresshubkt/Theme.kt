package id.myapp.progresshubkt

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

val AccentTeal = Color(0xFF5FB3A3)
val BgDark = Color(0xFF0B0F15)
val BgDark2 = Color(0xFF11161D)
val GlassBorder = Color(0x33FFFFFF)
val GlassFill = Color(0x140C0F14) // subtle translucent panel fill
val TextDim = Color(0xFF9AA5B1)

val AppDarkColorScheme = darkColorScheme(
    primary = AccentTeal,
    background = BgDark,
    surface = BgDark2,
    onBackground = Color.White,
    onSurface = Color.White
)
