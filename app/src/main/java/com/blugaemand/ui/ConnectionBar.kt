package com.blugaemand.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blugaemand.hid.HidStatus
import kotlinx.coroutines.launch

/** A paired host the user can reconnect to. */
data class HostOption(val name: String, val address: String)

/** How long the pill must be held before the panel opens. */
private const val HOLD_TO_OPEN_MS = 600

/**
 * Compact status pill pinned to the top of the pad. It stays small so it does not eat into the
 * play area, and opens on a deliberate hold to reveal the pairing actions — which are only needed
 * occasionally.
 */
@Composable
fun ConnectionBar(
    status: HidStatus,
    expanded: Boolean,
    hosts: List<HostOption>,
    onExpandedChange: (Boolean) -> Unit,
    onFixBlocker: () -> Unit,
    onMakeDiscoverable: () -> Unit,
    onConnect: (HostOption) -> Unit,
    onRetry: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.widthIn(max = 420.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StatusPill(
            status = status,
            expanded = expanded,
            onExpandedChange = onExpandedChange,
        )

        AnimatedVisibility(visible = expanded) {
            Card(
                modifier = Modifier.padding(top = 6.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xF21B1F27)),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    status.detail()?.let {
                        Text(text = it, color = Color(0xFFA8B1C2), fontSize = 11.sp)
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
                            color = Color(0xFFA8B1C2),
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

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onRetry) { Text("Retry", fontSize = 12.sp) }
                        TextButton(onClick = onStop) { Text("Stop gamepad", fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}

/**
 * The pill itself.
 *
 * Opening requires holding rather than tapping: the pill sits along the top edge, right where a
 * thumb travels during play, and a stray tap that threw up the pairing panel mid-game would be
 * worse than a slightly slower deliberate open. A blue bar sweeps left to right while held so the
 * hold is visibly doing something and its length is obvious. Closing is a plain tap — there is
 * nothing to protect against there.
 */
@Composable
private fun StatusPill(
    status: HidStatus,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xCC1B1F27))
            .drawBehind {
                if (progress.value > 0f) {
                    drawRect(
                        color = Color(0xFF4C82F7),
                        size = Size(size.width * progress.value, size.height),
                    )
                }
            }
            .pointerInput(expanded) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)

                    if (expanded) {
                        // Closing needs no ceremony; a plain tap will do.
                        if (waitForUpOrCancellation() != null) onExpandedChange(false)
                        return@awaitEachGesture
                    }

                    val fill = scope.launch {
                        progress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(HOLD_TO_OPEN_MS, easing = LinearEasing),
                        )
                        // Reaching full is the trigger; the buzz confirms it without looking.
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onExpandedChange(true)
                    }

                    // Null means the gesture was cancelled rather than lifted; either way the
                    // hold is over and the bar should drain back to empty.
                    waitForUpOrCancellation()
                    fill.cancel()
                    scope.launch { progress.animateTo(0f, tween(180)) }
                }
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusDot(status)
        Text(
            text = status.label(),
            color = Color(0xFFDCE2EE),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
