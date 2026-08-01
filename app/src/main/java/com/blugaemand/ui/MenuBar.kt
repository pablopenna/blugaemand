package com.blugaemand.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blugaemand.input.GamepadLayout
import com.blugaemand.ui.theme.OverlayColors

/** Which page of the menu is showing. */
private enum class MenuPage { Root, Layouts }

/**
 * The app menu's pill, sitting to the right of [ConnectionPill]. Same shape and same hold to open,
 * because two pills that behave differently would be worse than one.
 */
@Composable
fun MenuPill(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    HoldPill(expanded = expanded, onExpandedChange = onExpandedChange, modifier = modifier) {
        Text(text = "☰", color = OverlayColors.Label, fontSize = 12.sp)
        Text(text = "Menu", color = OverlayColors.Label, fontSize = 12.sp, maxLines = 1)
    }
}

/**
 * Everything about the app that is not about connecting: which layout the pad uses, and leaving.
 *
 * The layout list is a second page rather than an inline expansion, so the root stays a short list
 * of destinations however many layouts exist. [MenuPage] lives inside this composable on
 * purpose — the panel is only composed while it is open, so the menu reopens on the root page by
 * itself, without a reset that would visibly flip the page during the closing animation.
 */
@Composable
fun MenuPanel(
    layouts: List<GamepadLayout>,
    selectedLayoutId: String,
    onSelectLayout: (GamepadLayout) -> Unit,
    onQuit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var page by remember { mutableStateOf(MenuPage.Root) }

    PanelCard(modifier = modifier) {
        when (page) {
            MenuPage.Root -> {
                MenuEntry(label = "Layouts", trailing = "›") { page = MenuPage.Layouts }
                MenuEntry(label = "Quit", onClick = onQuit)
            }

            MenuPage.Layouts -> {
                MenuEntry(label = "Layouts", leading = "‹") { page = MenuPage.Root }
                layouts.forEach { layout ->
                    val active = layout.id == selectedLayoutId
                    MenuEntry(
                        label = layout.name,
                        // A fixed slot for the tick rather than a prefix on the name, so the
                        // names still line up under each other.
                        leading = if (active) "✓" else "",
                        // The tick alone is easy to miss at 12.sp; dimming the rest makes the
                        // active layout the one row that stands out.
                        color = if (active) Color.Unspecified else OverlayColors.Caption,
                        onClick = { onSelectLayout(layout) },
                    )
                }
            }
        }
    }
}

/**
 * One row of the menu: an optional gutter glyph, the label, and an optional trailing glyph.
 * [color] defaults to the button's own, which is the same accent the rest of the panels use.
 */
@Composable
private fun MenuEntry(
    label: String,
    modifier: Modifier = Modifier,
    leading: String? = null,
    trailing: String? = null,
    color: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (leading != null) {
                Text(
                    text = leading,
                    fontSize = 12.sp,
                    color = color,
                    modifier = Modifier.width(12.dp),
                )
            }
            Text(
                text = label,
                fontSize = 12.sp,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                Text(text = trailing, fontSize = 12.sp, color = color)
            }
        }
    }
}
