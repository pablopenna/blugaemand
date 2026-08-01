package com.blugaemand.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.blugaemand.input.GamepadLayout
import com.blugaemand.ui.theme.OverlayColors

/** Which page of the menu is showing. */
private enum class MenuPage { Root, Layouts, New }

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
 * Everything about the app that is not about connecting: which layout the pad uses, making new
 * ones, and leaving.
 *
 * The layout list is a second page rather than an inline expansion, so the root stays a short list
 * of destinations however many layouts exist. [MenuPage] lives inside this composable on
 * purpose — the panel is only composed while it is open, so the menu reopens on the root page by
 * itself, without a reset that would visibly flip the page during the closing animation.
 *
 * *Edit layout* appears only when [canEdit], which is how the built-ins stay read-only from here:
 * there is no disabled row to explain, because a layout you cannot edit simply does not offer it.
 * Making a copy is the route to editing one, and it sits on the page where layouts are chosen.
 */
@Composable
fun MenuPanel(
    layouts: List<GamepadLayout>,
    selectedLayoutId: String,
    currentLayoutName: String,
    canEdit: Boolean,
    onSelectLayout: (GamepadLayout) -> Unit,
    onNewEmptyLayout: () -> Unit,
    onCopyCurrentLayout: () -> Unit,
    onEditLayout: () -> Unit,
    onQuit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var page by remember { mutableStateOf(MenuPage.Root) }

    PanelCard(modifier = modifier) {
        when (page) {
            MenuPage.Root -> {
                PanelEntry(label = "Layouts", trailing = "›") { page = MenuPage.Layouts }
                if (canEdit) PanelEntry(label = "Edit layout", onClick = onEditLayout)
                PanelEntry(label = "Quit", onClick = onQuit)
            }

            MenuPage.Layouts -> {
                PanelEntry(label = "Layouts", leading = "‹") { page = MenuPage.Root }
                layouts.forEach { layout ->
                    val active = layout.id == selectedLayoutId
                    PanelEntry(
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
                PanelEntry(label = "New layout", trailing = "›") { page = MenuPage.New }
            }

            MenuPage.New -> {
                PanelEntry(label = "New layout", leading = "‹") { page = MenuPage.Layouts }
                PanelEntry(label = "Empty", onClick = onNewEmptyLayout)
                // Copying names the layout that is showing rather than offering all of them: the
                // list above is how you choose which, and this page stays two rows however many
                // layouts exist.
                PanelEntry(label = "Copy of $currentLayoutName", onClick = onCopyCurrentLayout)
            }
        }
    }
}
