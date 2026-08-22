package com.blugaemand.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blugaemand.BuildConfig
import com.blugaemand.R
import com.blugaemand.input.GamepadLayout
import com.blugaemand.input.LayoutLibrary
import com.blugaemand.motion.MotionSettings
import com.blugaemand.ui.theme.OverlayColors

/** Which page of the menu is showing. */
private enum class MenuPage { Root, Layouts, New, Motion, About }

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
 * *Edit layout* appears only when the selected layout is one of the user's, which is how the
 * built-ins stay read-only from here: there is no disabled row to explain, because a layout you
 * cannot edit simply does not offer it. Making a copy is the route to editing one, and it sits on
 * the page where layouts are chosen.
 *
 * The whole [LayoutLibrary] rather than its list of layouts, because the list on its own cannot say
 * which half a row is in — and that is what the rule between them and the two joystick marks are
 * drawn from. It also means the *Edit layout* test is made here rather than passed in.
 */
@Composable
fun MenuPanel(
    library: LayoutLibrary,
    selectedLayoutId: String,
    currentLayoutName: String,
    onSelectLayout: (GamepadLayout) -> Unit,
    onNewEmptyLayout: () -> Unit,
    onCopyCurrentLayout: () -> Unit,
    onEditLayout: () -> Unit,
    motion: MotionSettings,
    /** Whether this phone has a gyroscope to read at all. */
    motionAvailable: Boolean,
    onMotionChange: (MotionSettings) -> Unit,
    onQuit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var page by remember { mutableStateOf(MenuPage.Root) }

    PanelCard(modifier = modifier) {
        when (page) {
            MenuPage.Root -> {
                PanelEntry(label = "Layouts", trailing = "›") { page = MenuPage.Layouts }
                if (library.isEditable(selectedLayoutId)) {
                    PanelEntry(label = "Edit layout", onClick = onEditLayout)
                }
                // Here rather than in the editor, because it is a setting about the phone and the
                // hands holding it -- it applies whichever layout is on, and a copy of it inside
                // every layout would be one to set again for each.
                PanelEntry(
                    label = "Motion",
                    trailing = if (motion.enabled) "on" else "off",
                ) { page = MenuPage.Motion }
                PanelEntry(label = "About", trailing = "›") { page = MenuPage.About }
                PanelEntry(label = "Quit", onClick = onQuit)
            }

            MenuPage.Layouts -> {
                PanelBack { page = MenuPage.Root }

                LayoutRows(
                    layouts = library.builtIn,
                    icon = R.drawable.generic_joystick,
                    selectedLayoutId = selectedLayoutId,
                    onSelectLayout = onSelectLayout,
                )
                // Suppressed on a fresh install, where there is nothing under it and a rule would
                // read as a row that failed to draw.
                if (library.user.isNotEmpty()) {
                    PanelDivider()
                    LayoutRows(
                        layouts = library.user,
                        icon = R.drawable.generic_joystick_red,
                        selectedLayoutId = selectedLayoutId,
                        onSelectLayout = onSelectLayout,
                    )
                }

                PanelEntry(label = "New layout", trailing = "›") { page = MenuPage.New }
            }

            MenuPage.New -> {
                PanelBack { page = MenuPage.Layouts }
                PanelEntry(label = "Empty", onClick = onNewEmptyLayout)
                // Copying names the layout that is showing rather than offering all of them: the
                // list above is how you choose which, and this page stays two rows however many
                // layouts exist.
                PanelEntry(label = "Copy of $currentLayoutName", onClick = onCopyCurrentLayout)
            }

            MenuPage.Motion -> {
                PanelBack { page = MenuPage.Root }

                if (!motionAvailable) {
                    // Stated rather than hidden: a missing Motion row would read as a build without
                    // the feature, and the reason it does nothing here is worth one line.
                    PanelCaption("This phone has no gyroscope, so there is nothing to read.")
                    return@PanelCard
                }

                PanelEntry(
                    label = "Aim with the phone",
                    trailing = if (motion.enabled) "on" else "off",
                    onClick = { onMotionChange(motion.copy(enabled = !motion.enabled)) },
                )
                PanelEntry(
                    label = "Stick",
                    trailing = motion.target.describe(),
                    onClick = { onMotionChange(motion.copy(target = motion.target.other())) },
                )
                PanelEntry(
                    label = "Sensitivity",
                    trailing = "×${motion.sensitivity}",
                    onClick = { onMotionChange(motion.nextSensitivity()) },
                )
                PanelEntry(
                    label = "Invert vertical",
                    trailing = if (motion.invertY) "on" else "off",
                    onClick = { onMotionChange(motion.copy(invertY = !motion.invertY)) },
                )
                PanelCaption(
                    "Turning the phone pushes the ${motion.target.describe()} stick, on top of " +
                        "whatever a thumb on it is already sending.",
                )
            }

            MenuPage.About -> {
                PanelBack { page = MenuPage.Root }
                AboutCard()
            }
        }
    }
}

/**
 * The app's own page: its icon, its name and the version this build was cut at.
 *
 * The icon is the launcher's foreground layer rather than `@mipmap/ic_launcher`, because that one
 * is an `adaptive-icon` and [painterResource] cannot load it. The layer carries the art's own blue
 * field, and the launcher background sits behind it for the transparent margin the 108dp canvas
 * leaves, so the two together are what the launcher shows -- squared off rather than masked, since
 * the mask is the launcher's choice and not ours to guess.
 */
@Composable
private fun AboutCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colorResource(R.color.ic_launcher_background)),
        )
        Text(text = stringResource(R.string.app_name), fontSize = 14.sp, color = OverlayColors.Label)
        Text(
            text = "Version ${BuildConfig.VERSION_NAME}",
            fontSize = 11.sp,
            color = OverlayColors.Caption,
        )
    }
}

/**
 * One half of the layouts list — the built-ins or the user's own — each row marked with [icon].
 *
 * Its own composable rather than a loop written twice, so the two halves cannot drift into looking
 * like different kinds of row when the only thing that differs between them is the mark on the end.
 */
@Composable
private fun LayoutRows(
    layouts: List<GamepadLayout>,
    @DrawableRes icon: Int,
    selectedLayoutId: String,
    onSelectLayout: (GamepadLayout) -> Unit,
) {
    layouts.forEach { layout ->
        val active = layout.id == selectedLayoutId
        PanelEntry(
            label = layout.name,
            // A fixed slot for the tick rather than a prefix on the name, so the names still line
            // up under each other.
            leading = if (active) "✓" else "",
            trailingIcon = icon,
            // The tick alone is easy to miss at 12.sp; dimming the rest makes the active layout
            // the one row that stands out.
            color = if (active) Color.Unspecified else OverlayColors.Caption,
            onClick = { onSelectLayout(layout) },
        )
    }
}
