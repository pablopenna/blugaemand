package com.blugaemand.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blugaemand.hid.HidStatus
import com.blugaemand.ui.theme.OverlayColors

/** A paired host the user can reconnect to. */
data class HostOption(val name: String, val address: String)

/**
 * Stage 0 scratch controls, see TODO.md. Bundled into one object so measuring this costs the
 * component signatures one parameter rather than five, and so deleting it is one line each.
 */
data class SwitchProbe(
    val impersonating: Boolean,
    val scanning: Boolean,
    val found: List<HostOption>,
    val onImpersonateChange: (Boolean) -> Unit,
    val onScan: () -> Unit,
    val onConnect: (HostOption) -> Unit,
)

/**
 * Compact status pill pinned to the top of the pad. It stays small so it does not eat into the
 * play area, and opens on a deliberate hold to reveal the pairing actions — which are only needed
 * occasionally. See [HoldPill] for why opening is a hold and closing a tap.
 */
@Composable
fun ConnectionPill(
    expanded: Boolean,
    status: HidStatus,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    HoldPill(expanded = expanded, onExpandedChange = onExpandedChange, modifier = modifier) {
        StatusDot(status)
        Text(
            text = status.label(),
            color = OverlayColors.Label,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Everything needed to get the session connected, and nothing else. */
@Composable
fun ConnectionPanel(
    status: HidStatus,
    hosts: List<HostOption>,
    onFixBlocker: () -> Unit,
    onMakeDiscoverable: () -> Unit,
    onConnect: (HostOption) -> Unit,
    onRetry: () -> Unit,
    onQuit: () -> Unit,
    modifier: Modifier = Modifier,
    probe: SwitchProbe? = null,
) {
    PanelCard(modifier = modifier) {
        status.detail()?.let {
            Text(text = it, color = OverlayColors.Caption, fontSize = 11.sp)
        }

        // When something is blocking the session, that fix is the only useful action —
        // offering "make discoverable" while Bluetooth is off just wastes a tap.
        val blocker = status.primaryAction()
        if (blocker != null) {
            Button(onClick = onFixBlocker, modifier = Modifier.fillMaxWidth()) {
                Text(blocker, fontSize = 12.sp)
            }
        } else {
            OutlinedButton(
                onClick = onMakeDiscoverable,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Make discoverable to pair", fontSize = 12.sp)
            }
        }

        if (hosts.isNotEmpty()) {
            Text(
                text = "Reconnect to a paired device",
                color = OverlayColors.Caption,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            hosts.forEach { host ->
                TextButton(
                    onClick = { onConnect(host) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = host.name,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        probe?.let { ProbeSection(it) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onRetry) { Text("Retry", fontSize = 12.sp) }
            TextButton(onClick = onQuit) { Text("Stop gamepad", fontSize = 12.sp) }
        }
    }
}

/**
 * Stage 0 scratch, see TODO.md. Two things the shipping app has no business offering: wearing a
 * Pro Controller's SDP identity, and connecting *out* to a host that has never been paired.
 */
@Composable
private fun ProbeSection(probe: SwitchProbe) {
    Text(
        text = "Switch probe — scratch",
        color = OverlayColors.Caption,
        fontSize = 11.sp,
        modifier = Modifier.padding(top = 8.dp),
    )

    OutlinedButton(
        onClick = { probe.onImpersonateChange(!probe.impersonating) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = if (probe.impersonating) {
                "Impersonating Pro Controller — tap to stop"
            } else {
                "Impersonate Pro Controller"
            },
            fontSize = 12.sp,
        )
    }

    if (probe.impersonating) {
        Text(
            text = "The phone's Bluetooth name is now Pro Controller. It goes back when the " +
                "gamepad stops.",
            color = OverlayColors.Caption,
            fontSize = 11.sp,
        )
    }

    OutlinedButton(onClick = probe.onScan, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (probe.scanning) "Scanning…" else "Scan for a host to connect to",
            fontSize = 12.sp,
        )
    }

    probe.found.forEach { host ->
        TextButton(onClick = { probe.onConnect(host) }, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = host.name,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StatusDot(status: HidStatus) {
    val color = when (status) {
        is HidStatus.Connected -> Color(0xFF4ADE80)
        is HidStatus.Connecting -> Color(0xFFFACC15)
        HidStatus.Advertising -> Color(0xFF60A5FA)
        is HidStatus.Error, is HidStatus.Unsupported -> Color(0xFFF87171)
        else -> Color(0xFF6B7280)
    }
    Row(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color),
    ) {}
}

private fun HidStatus.label(): String = when (this) {
    HidStatus.Initializing -> "Starting…"
    HidStatus.PermissionRequired -> "Permission needed"
    HidStatus.BluetoothOff -> "Bluetooth is off"
    is HidStatus.Unsupported -> "Not supported on this phone"
    HidStatus.Idle -> "Not advertising"
    HidStatus.Advertising -> "Ready to pair"
    is HidStatus.Connecting -> "Connecting to $deviceName…"
    is HidStatus.Connected -> "Connected to $deviceName"
    is HidStatus.Error -> "Error"
}

private fun HidStatus.detail(): String? = when (this) {
    is HidStatus.Unsupported -> reason
    is HidStatus.Error -> message
    HidStatus.PermissionRequired ->
        "Blugaemand needs the nearby-devices permission to present itself as a gamepad."
    HidStatus.BluetoothOff ->
        "Bluetooth is switched off. Apps cannot turn it on themselves, so Android will ask you " +
            "to confirm."
    HidStatus.Advertising ->
        "On the host, add a new Bluetooth device and pick this phone."
    else -> null
}

/** The one action that will move this state forward, if there is one. */
private fun HidStatus.primaryAction(): String? = when (this) {
    HidStatus.PermissionRequired -> "Grant permission"
    HidStatus.BluetoothOff -> "Turn on Bluetooth"
    else -> null
}
