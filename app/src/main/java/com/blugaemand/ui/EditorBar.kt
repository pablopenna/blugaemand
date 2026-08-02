package com.blugaemand.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blugaemand.input.ControlGroups
import com.blugaemand.input.ControlId
import com.blugaemand.input.ControlSpec
import com.blugaemand.input.GamepadLayout
import com.blugaemand.input.LayoutStyle
import com.blugaemand.input.Placement
import com.blugaemand.input.art.ArtPacks
import com.blugaemand.input.describe
import com.blugaemand.input.missingButtons
import com.blugaemand.ui.theme.OverlayColors

/** Which page of the editor is showing. */
private enum class EditorPage { Root, Add, AddGroup, Appearance, Rename, ConfirmDelete }

/**
 * The editor's controls: a pill, and its panel when opened.
 *
 * **Collapsed by default**, because the panel covers the middle of the top edge — which is where
 * the centre cluster sits, and where anything else may be put. A permanently open panel makes those
 * controls unselectable, and an editor that cannot reach part of the pad is not much of an editor.
 *
 * The pill opens on a plain tap rather than [HoldPill]'s 600 ms hold; see [TapPill] for why the
 * hold has nothing to buy in here.
 *
 * Everything destructive is one step away from where a thumb rests. *Delete layout* asks first,
 * because a layout is the only copy of work someone did and there is no undo for it; removing a
 * single control does not, because adding it back is one tap and a place to put it.
 */
@Composable
fun EditorBar(
    layout: GamepadLayout,
    /** Index of the control being edited, in [GamepadLayout.controls]. */
    selected: Int?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    snapToGrid: Boolean,
    onSnapToGridChange: (Boolean) -> Unit,
    /** Whether a control group goes down as one control. */
    asOneControl: Boolean,
    onAsOneControlChange: (Boolean) -> Unit,
    /** The colours to go back to when leaving an art pack; see [EditorPage.Appearance]. */
    lastColors: LayoutStyle.Colors,
    onLayoutChange: (GamepadLayout) -> Unit,
    /** Waiting to be dropped, if anything is. */
    pending: Placement?,
    onStartPlacing: (Placement) -> Unit,
    onCancelPlacing: () -> Unit,
    onRemoveSelected: () -> Unit,
    onUngroupSelected: () -> Unit,
    onDeleteLayout: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // While something is waiting to be dropped the pill says so and offers the way out, because
        // the pad underneath is what is being aimed at and the menu would only be in the way of it.
        if (pending != null) {
            TapPill(onClick = onCancelPlacing) {
                Text("✕", color = OverlayColors.Label, fontSize = 12.sp)
                Text(
                    text = "Tap the pad to place ${pending.name.lowercase()}",
                    color = OverlayColors.Label,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
            return@Column
        }

        TapPill(onClick = { onExpandedChange(!expanded) }) {
            Text("☰", color = OverlayColors.Label, fontSize = 12.sp)
            Text(
                text = if (expanded) "Close" else "Edit",
                color = OverlayColors.Label,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }

        AnimatedVisibility(
            visible = expanded,
            // Weighted for the same reason the top bar's panels are: a Column measures an
            // unweighted child with an unbounded main axis, so a long page -- and the add-control
            // page is twenty-two rows -- would be measured full height and silently clipped.
            modifier = Modifier.weight(1f, fill = false),
        ) {
            EditorPanel(
                layout = layout,
                selected = selected,
                snapToGrid = snapToGrid,
                onSnapToGridChange = onSnapToGridChange,
                asOneControl = asOneControl,
                onAsOneControlChange = onAsOneControlChange,
                lastColors = lastColors,
                onLayoutChange = onLayoutChange,
                onStartPlacing = onStartPlacing,
                onRemoveSelected = onRemoveSelected,
                onUngroupSelected = onUngroupSelected,
                onDeleteLayout = onDeleteLayout,
                onDone = onDone,
                modifier = Modifier.width(240.dp),
            )
        }
    }
}

/**
 * The pages themselves. Split from the pill so that its state lives in a composable that only
 * exists while open — the panel therefore reopens on its root page by itself, without a reset that
 * would visibly flip pages during the closing animation. Same trick as `MenuPanel`.
 */
@Composable
private fun EditorPanel(
    layout: GamepadLayout,
    selected: Int?,
    snapToGrid: Boolean,
    onSnapToGridChange: (Boolean) -> Unit,
    asOneControl: Boolean,
    onAsOneControlChange: (Boolean) -> Unit,
    /** The colours to go back to when leaving an art pack; see [EditorPage.Appearance]. */
    lastColors: LayoutStyle.Colors,
    onLayoutChange: (GamepadLayout) -> Unit,
    onStartPlacing: (Placement) -> Unit,
    onRemoveSelected: () -> Unit,
    onUngroupSelected: () -> Unit,
    onDeleteLayout: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var page by remember { mutableStateOf(EditorPage.Root) }

    PanelCard(modifier = modifier) {
        when (page) {
            EditorPage.Root -> {
                PanelEntry(label = "Done", leading = "✓", onClick = onDone)
                PanelEntry(label = "Add control", trailing = "›") { page = EditorPage.Add }
                PanelEntry(label = "Add control group", trailing = "›") {
                    page = EditorPage.AddGroup
                }
                val selectedSpec = selected?.let { layout.controls.getOrNull(it) }
                PanelEntry(
                    label = selectedSpec?.let { "Remove ${it.describe()}" } ?: "Remove control",
                    // Nothing selected means nothing to remove, and a row that acted on whichever
                    // control happened to be last would be worse than one that waits.
                    enabled = selectedSpec != null,
                    color = if (selectedSpec != null) Color.Unspecified else OverlayColors.Caption,
                    onClick = onRemoveSelected,
                )
                // Only shown for a plate, because for anything else there is nothing to break up.
                // This is the way back out of one: a member is not separately selectable, by
                // design, so ungrouping is how a single button in a group gets tuned.
                if (selectedSpec?.shape is ControlSpec.Shape.Cluster) {
                    PanelEntry(label = "Ungroup", onClick = onUngroupSelected)
                }
                PanelEntry(
                    label = "Grid",
                    trailing = if (snapToGrid) "on" else "off",
                    onClick = { onSnapToGridChange(!snapToGrid) },
                )
                // One row for the whole of how a pad looks, and unconditional. A row that vanished
                // the moment the layout took an art pack would be missing at exactly the point
                // someone went looking for the way back off it.
                PanelEntry(label = "Appearance", trailing = "›") { page = EditorPage.Appearance }
                PanelEntry(label = "Rename", trailing = "›") { page = EditorPage.Rename }
                PanelEntry(
                    label = "Delete layout",
                    color = OverlayColors.Caption,
                ) { page = EditorPage.ConfirmDelete }

                val missing = layout.missingButtons()
                if (missing.isNotEmpty()) {
                    // A warning, not an error: a pad with no Start button is a strange pad, but it
                    // is allowed to be one. Naming the buttons is more use than counting them.
                    PanelCaption("No control for ${missing.joinToString { it.name }}")
                }
            }

            EditorPage.Add -> {
                PanelEntry(label = "Add control", leading = "‹") { page = EditorPage.Root }
                // Everything, every time. A control may appear on a layout more than once -- two A
                // buttons, one under each thumb -- so there is nothing to filter out.
                ControlId.ALL.forEach { id ->
                    PanelEntry(label = id.describe()) {
                        onStartPlacing(Placement.of(id))
                        page = EditorPage.Root
                    }
                }
            }

            EditorPage.AddGroup -> {
                PanelEntry(label = "Add control group", leading = "‹") { page = EditorPage.Root }
                // A switch rather than a second list of the same seven arrangements: which way a
                // group goes down is one decision about it, not a different thing to place. Held
                // by the caller and not here, alongside the grid: this panel is destroyed every
                // time it closes -- which placing something does -- and someone laying out a pad
                // of plates should not have to set it again for each one.
                PanelEntry(
                    label = "As one control",
                    trailing = if (asOneControl) "on" else "off",
                    onClick = { onAsOneControlChange(!asOneControl) },
                )
                PanelCaption(
                    if (asOneControl) {
                        "One control. Which button it sends depends on where it is touched."
                    } else {
                        "Placed together, then moved separately."
                    },
                )
                // The switch is a setting about the list below it, not the first thing in it.
                PanelDivider()
                ControlGroups.ALL.forEach { group ->
                    PanelEntry(label = group.name) {
                        onStartPlacing(if (asOneControl) ControlGroups.clustered(group) else group)
                        page = EditorPage.Root
                    }
                }
            }

            EditorPage.Appearance -> {
                PanelEntry(label = "Appearance", leading = "‹") { page = EditorPage.Root }

                val colors = layout.style as? LayoutStyle.Colors
                PanelEntry(
                    label = "Shapes and colours",
                    // Not the layout's own colours but the ones it last had, so a look at a pack
                    // and back does not quietly hand someone the defaults instead of their choice.
                    trailing = if (colors != null) "✓" else null,
                    onClick = { onLayoutChange(layout.copy(style = lastColors)) },
                )
                ArtPacks.ALL.forEach { pack ->
                    val current = (layout.style as? LayoutStyle.Images)?.pack
                    PanelEntry(
                        label = pack.name,
                        trailing = if (pack == current) "✓" else null,
                        onClick = { onLayoutChange(layout.copy(style = LayoutStyle.Images(pack))) },
                    )
                }

                // The swatches belong to one of the choices above rather than to the page, so they
                // are shown under the line while that choice is the one in force and absent
                // otherwise -- an image layout draws its art's colours and has none to pick.
                if (colors != null) {
                    PanelDivider()
                    PanelCaption("At rest")
                    Swatches(selected = colors.resting) {
                        onLayoutChange(layout.copy(style = colors.copy(resting = it)))
                    }
                    PanelCaption("Held")
                    Swatches(selected = colors.pressed) {
                        onLayoutChange(layout.copy(style = colors.copy(pressed = it)))
                    }
                }
            }

            EditorPage.Rename -> {
                PanelEntry(label = "Rename", leading = "‹") { page = EditorPage.Root }
                OutlinedTextField(
                    value = layout.name,
                    onValueChange = { onLayoutChange(layout.copy(name = it)) },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 12.sp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                )
            }

            EditorPage.ConfirmDelete -> {
                PanelEntry(label = "Delete layout", leading = "‹") { page = EditorPage.Root }
                PanelCaption("\"${layout.name}\" will be gone for good.")
                PanelEntry(label = "Delete", onClick = onDeleteLayout)
                PanelEntry(label = "Keep it") { page = EditorPage.Root }
            }
        }
    }
}

/**
 * A row of preset colours, the current one ringed.
 *
 * Presets rather than a full picker: the pad's whole chrome is two pills and a card, and a hue
 * wheel in the middle of it would be the largest thing in the app. What lands in the layout is a
 * plain ARGB [Int] either way, so nothing outside this file knows the difference.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Swatches(selected: Int, onPick: (Int) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (color in SWATCHES) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(
                        width = if (color == selected) 2.dp else 1.dp,
                        color = if (color == selected) {
                            OverlayColors.Accent
                        } else {
                            OverlayColors.Caption
                        },
                        shape = CircleShape,
                    )
                    .clickable { onPick(color) },
            )
        }
    }
}

/**
 * Six muted fills and six vivid ones. Resting and pressed pick from the same list rather than from
 * two curated halves — which of the two a colour suits is obvious on sight, and splitting them
 * would rule out the dark-on-darker pads that some people want.
 */
private val SWATCHES: List<Int> = listOf(
    0xFF262B36.toInt(), // the default resting slate
    0xFF1D2129.toInt(),
    0xFF2E2A33.toInt(),
    0xFF23302A.toInt(),
    0xFF33291F.toInt(),
    0xFF2B2B2B.toInt(),
    0xFF4C82F7.toInt(), // the default pressed blue
    0xFF3DC98A.toInt(),
    0xFFF7B84C.toInt(),
    0xFFF76C6C.toInt(),
    0xFFB07CF7.toInt(),
    0xFF4CD3F7.toInt(),
)
