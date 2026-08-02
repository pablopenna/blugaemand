package com.blugaemand.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.blugaemand.hid.HidStatus
import com.blugaemand.input.GamepadLayout
import com.blugaemand.input.LayoutLibrary

/** The panels the top bar can have open. Exactly one at a time, or none. */
enum class TopPanel { Connection, Menu }

/**
 * The two pills pinned to the top of the pad, and whichever panel is open beneath them.
 *
 * The pills share one row and the panels hang below it, rather than each pill owning a column of
 * its own: a panel opening changes its column's width, and side-by-side columns would slide both
 * pills sideways every time one was opened. Here the row's width never changes, so the pills stay
 * put and only the panel appears.
 *
 * **Both panels are weighted**, which is what bounds their height to the screen. A `Column`
 * measures an unweighted child with an unbounded main axis, so a panel taller than the space under
 * the pills would simply be measured at its full height and have the overflow clipped away —
 * silently, with the missing rows looking like rows that do not exist. `fill = false` keeps a short
 * panel its own size; the weight only ever takes effect as a ceiling. [PanelCard] scrolls whatever
 * does not fit under it.
 */
@Composable
fun TopBar(
    status: HidStatus,
    hosts: List<HostOption>,
    library: LayoutLibrary,
    selectedLayoutId: String,
    currentLayoutName: String,
    openPanel: TopPanel?,
    onOpenPanelChange: (TopPanel?) -> Unit,
    onFixBlocker: () -> Unit,
    onMakeDiscoverable: () -> Unit,
    onConnect: (HostOption) -> Unit,
    onRetry: () -> Unit,
    onSelectLayout: (GamepadLayout) -> Unit,
    onNewEmptyLayout: () -> Unit,
    onCopyCurrentLayout: () -> Unit,
    onEditLayout: () -> Unit,
    onQuit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.widthIn(max = 420.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ConnectionPill(
                expanded = openPanel == TopPanel.Connection,
                status = status,
                onExpandedChange = { open ->
                    onOpenPanelChange(if (open) TopPanel.Connection else null)
                },
            )
            MenuPill(
                expanded = openPanel == TopPanel.Menu,
                onExpandedChange = { open ->
                    onOpenPanelChange(if (open) TopPanel.Menu else null)
                },
            )
        }

        AnimatedVisibility(
            visible = openPanel == TopPanel.Connection,
            modifier = Modifier.weight(1f, fill = false),
        ) {
            ConnectionPanel(
                status = status,
                hosts = hosts,
                onFixBlocker = onFixBlocker,
                onMakeDiscoverable = onMakeDiscoverable,
                onConnect = onConnect,
                onRetry = onRetry,
                onQuit = onQuit,
            )
        }

        // Aligned to the end of the column, which — with only the pill row setting its width —
        // puts the menu directly under the pill that opened it. Sized rather than left to fill,
        // so a two-entry menu does not open as wide as the pairing panel.
        //
        // Swapping panels overlaps the two for a couple of frames, the arriving one landing below
        // the departing one until it collapses. Stacking them in a shared Box fixes that, but
        // needs a composable of its own — AnimatedVisibility resolves to the ColumnScope overload
        // here, which cannot be called inside a Box — and that was not worth the indirection.
        AnimatedVisibility(
            visible = openPanel == TopPanel.Menu,
            modifier = Modifier
                .align(Alignment.End)
                .weight(1f, fill = false),
        ) {
            MenuPanel(
                library = library,
                selectedLayoutId = selectedLayoutId,
                currentLayoutName = currentLayoutName,
                onSelectLayout = { layout ->
                    onSelectLayout(layout)
                    onOpenPanelChange(null)
                },
                // Creating one selects it and opens the editor, so the menu has nothing left to
                // show and closing it is what gets it out of the way of the pad underneath.
                onNewEmptyLayout = {
                    onNewEmptyLayout()
                    onOpenPanelChange(null)
                },
                onCopyCurrentLayout = {
                    onCopyCurrentLayout()
                    onOpenPanelChange(null)
                },
                onEditLayout = {
                    onEditLayout()
                    onOpenPanelChange(null)
                },
                onQuit = onQuit,
                modifier = Modifier.width(180.dp),
            )
        }
    }
}
