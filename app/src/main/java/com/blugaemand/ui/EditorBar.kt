package com.blugaemand.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blugaemand.input.ControlId
import com.blugaemand.input.GamepadLayout
import com.blugaemand.input.LayoutStyle
import com.blugaemand.input.describe
import com.blugaemand.input.missingButtons
import com.blugaemand.ui.theme.OverlayColors

/** Which page of the editor is showing. */
private enum class EditorPage { Root, Add, Colours, Rename, ConfirmDelete }

/**
 * The editor's controls, on the same card every other panel uses.
 *
 * Deliberately **not** a [HoldPill]: the hold exists so that a stray tap cannot throw a panel up
 * mid-game, and in here there is no game to protect — nothing being edited is connected to
 * anything. Plain taps are right.
 *
 * Everything destructive is one step away from where a thumb rests. *Delete layout* asks first,
 * because a layout is the only copy of work someone did and there is no undo for it; removing a
 * single control does not, because adding it back puts it exactly where it was.
 */
@Composable
fun EditorBar(
    layout: GamepadLayout,
    /** Index of the control being edited, in [GamepadLayout.controls]. */
    selected: Int?,
    snapToGrid: Boolean,
    onSnapToGridChange: (Boolean) -> Unit,
    onLayoutChange: (GamepadLayout) -> Unit,
    onAddControl: (ControlId) -> Unit,
    onRemoveSelected: () -> Unit,
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
                val selectedSpec = selected?.let { layout.controls.getOrNull(it) }
                PanelEntry(
                    label = selectedSpec?.let { "Remove ${it.id.describe()}" } ?: "Remove control",
                    // Nothing selected means nothing to remove, and a row that acted on whichever
                    // control happened to be last would be worse than one that waits.
                    enabled = selectedSpec != null,
                    color = if (selectedSpec != null) Color.Unspecified else OverlayColors.Caption,
                    onClick = onRemoveSelected,
                )
                PanelEntry(
                    label = "Grid",
                    trailing = if (snapToGrid) "on" else "off",
                    onClick = { onSnapToGridChange(!snapToGrid) },
                )
                // Only a colours layout has colours to pick. An images layout draws its art's own,
                // so the row is absent rather than present and inert.
                if (layout.style is LayoutStyle.Colors) {
                    PanelEntry(label = "Colours", trailing = "›") { page = EditorPage.Colours }
                }
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
                        onAddControl(id)
                        page = EditorPage.Root
                    }
                }
            }

            EditorPage.Colours -> {
                PanelEntry(label = "Colours", leading = "‹") { page = EditorPage.Root }
                val colors = layout.style as LayoutStyle.Colors

                PanelCaption("At rest")
                Swatches(selected = colors.resting) {
                    onLayoutChange(layout.copy(style = colors.copy(resting = it)))
                }
                PanelCaption("Held")
                Swatches(selected = colors.pressed) {
                    onLayoutChange(layout.copy(style = colors.copy(pressed = it)))
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
