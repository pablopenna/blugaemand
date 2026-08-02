package com.blugaemand.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/** Which of a layout's two colours the picker on the *Appearance* page is aimed at. */
private enum class ColorTarget { Resting, Pressed }

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
    /** Moves the selection one step, in units of -1, 0 or 1 per axis. */
    onNudge: (dx: Int, dy: Int) -> Unit,
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

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TapPill(onClick = { onExpandedChange(!expanded) }) {
                Text("☰", color = OverlayColors.Label, fontSize = 12.sp)
                Text(
                    text = if (expanded) "Close" else "Edit",
                    color = OverlayColors.Label,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }

            // Beside the pill rather than on a panel page, because nudging is something you watch:
            // the panel covers the top middle of the pad, and arrows on it would be moving a control
            // that may well be underneath it. Here they work with the panel shut, which is how the
            // editor sits most of the time. Shown only when there is something to move.
            if (selected != null) NudgePad(onNudge)
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

                // The picker belongs to one of the choices above rather than to the page, so it is
                // shown under the line while that choice is the one in force and absent otherwise —
                // an image layout draws its art's own colours and has none to pick.
                if (colors != null) {
                    PanelDivider()

                    var target by remember { mutableStateOf(ColorTarget.Resting) }
                    ColorTargetRow(
                        label = "At rest",
                        color = colors.resting,
                        selected = target == ColorTarget.Resting,
                        onClick = { target = ColorTarget.Resting },
                    )
                    ColorTargetRow(
                        label = "Held",
                        color = colors.pressed,
                        selected = target == ColorTarget.Pressed,
                        onClick = { target = ColorTarget.Pressed },
                    )

                    // Keyed on the target so that switching between the two rebuilds the picker.
                    // It holds a hue of its own for colours that have none — see ColorPicker — and
                    // that is a fact about one colour, not something to carry to the other.
                    key(target) {
                        ColorPicker(
                            color = when (target) {
                                ColorTarget.Resting -> colors.resting
                                ColorTarget.Pressed -> colors.pressed
                            },
                            onColorChange = { picked ->
                                onLayoutChange(
                                    layout.copy(
                                        style = when (target) {
                                            ColorTarget.Resting -> colors.copy(resting = picked)
                                            ColorTarget.Pressed -> colors.copy(pressed = picked)
                                        },
                                    ),
                                )
                            },
                        )
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
 * Four arrows that move the selection one step at a time.
 *
 * What a drag cannot do is move a control by an amount smaller than the thumb doing the moving —
 * and with the grid on it cannot reliably move one by exactly a cell either, since the finger has to
 * travel more than half a step before anything happens. Both are what the arrows are for, and it is
 * why the step they take is the grid's when snapping is on; see `ResolvedLayout.nudgeStep`.
 *
 * Laid out ◀▲▼▶ in a line rather than as a cross: a cross would be three rows tall next to a pill
 * that is one, and this is the same order the cursor keys on a keyboard sit in.
 */
@Composable
private fun NudgePad(onNudge: (dx: Int, dy: Int) -> Unit) {
    Row(
        modifier = Modifier.pillSurface().padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NudgeArrow("◀", onClick = { onNudge(-1, 0) })
        NudgeArrow("▲", onClick = { onNudge(0, -1) })
        NudgeArrow("▼", onClick = { onNudge(0, 1) })
        NudgeArrow("▶", onClick = { onNudge(1, 0) })
    }
}

/**
 * One arrow. The padding is inside the clickable rather than outside it, so the touch target is the
 * whole cell and not just the glyph — these sit in a pill six millimetres tall.
 */
@Composable
private fun NudgeArrow(glyph: String, onClick: () -> Unit) {
    Text(
        text = glyph,
        color = OverlayColors.Label,
        fontSize = 12.sp,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}
