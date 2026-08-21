package com.blugaemand.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Surface colours for the gamepad canvas, kept out of the Material scheme so the pad reads the
 * same in either theme — a controller that changes colour with the system theme is just confusing.
 */
object PadColors {
    val Background = Color(0xFF12141A)
    val ControlFill = Color(0xFF262B36)
    val ControlFillPressed = Color(0xFF4C82F7)
    val ControlStroke = Color(0xFF3C4353)
    val Label = Color(0xFFCBD2E0)
    val LabelPressed = Color(0xFFFFFFFF)
    val StickBase = Color(0xFF1D2129)
    val StickKnob = Color(0xFF39414F)
    val StickShaft = Color(0xFFC3CAD8)
    val StickKnobActive = Color(0xFF4C82F7)
}

/**
 * The translucent chrome floating over the pad: the top-bar pills, their panels and the scrim.
 *
 * Named here rather than inlined so a new pill or panel cannot drift from the ones already
 * there — matching by eye across files is how two nearly-identical greys happen.
 */
object OverlayColors {
    val Pill = Color(0xCC1B1F27)
    val Panel = Color(0xF21B1F27)
    val Accent = Color(0xFF4C82F7)
    val Label = Color(0xFFDCE2EE)
    val Caption = Color(0xFFA8B1C2)

    /**
     * The one row that destroys something: *Delete layout*, and the *Delete* that confirms it.
     *
     * Lifted towards the light end rather than a flat red, so it still passes as a label on the
     * panel's near-black instead of reading as an error banner. Nothing else on the chrome is
     * allowed it — a second red row is what stops the first one meaning anything.
     */
    val Destructive = Color(0xFFE8697A)

    /**
     * The one row that finishes something: *Done*, which leaves the editor.
     *
     * Paired with [Destructive] and lifted the same way, so the two colours the chrome spends on
     * meaning read as a pair rather than as two unrelated accidents.
     */
    val Confirm = Color(0xFF63C98E)
    val Scrim = Color(0x59000000)
}

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4C82F7),
    background = PadColors.Background,
    surface = PadColors.ControlFill,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2C5FD0),
)

@Composable
fun BlugaemandTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
