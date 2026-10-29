package dev.linkfuse.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.linkfuse.app.model.MergeStatus
import dev.linkfuse.app.model.UiState
import dev.linkfuse.app.service.MergeController
import dev.linkfuse.app.ui.theme.Cell
import dev.linkfuse.app.ui.theme.Fuse
import dev.linkfuse.app.ui.theme.Okay

@Composable
fun MainScreen(
    state: UiState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
) {
    val allowMetered by MergeController.allowMetered.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        // Status header — one big honest word instead of jargon.
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(state.status)
            Spacer(Modifier.width(10.dp))
            Text(
                state.status.caption,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = statusColor(state.status),
            )
        }
        Text(
            if (state.status == MergeStatus.MERGING)
                "New connections are spread across your active links."
            else state.message ?: hintFor(state.status),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        Spacer(Modifier.height(22.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TotalBlock("Combined down", state.totalDown, "↓")
                TotalBlock("Combined up", state.totalUp, "↑")
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("Connections", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))

        if (state.links.isEmpty()) {
            Card(shape = RoundedCornerShape(18.dp)) {
                Text(
                    "No usable connections yet. Connect to Wi-Fi or mobile data — LinkFuse picks them up automatically.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            state.links.forEach { link ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            KindBadge(link.kind)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(link.kind.label, fontWeight = FontWeight.SemiBold)
                                Text(
                                    link.detail + if (link.metered) " · metered" else "",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("↓ ${human(link.downRate)}", color = Okay, fontSize = 13.sp)
                                Text("↑ ${human(link.upRate)}", color = Cell, fontSize = 13.sp)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        ShareBar(if (state.status == MergeStatus.MERGING) link.share else 0f, kindColor(link.kind))
                        Text(
                            "${(link.share * 100).toInt()}% of new flows",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Include metered connections", fontWeight = FontWeight.Medium)
                Text(
                    "Off = skip cellular and pay-per-byte networks unless nothing else exists.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = allowMetered, onCheckedChange = MergeController::setAllowMetered)
        }

        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            when (state.status) {
                MergeStatus.MERGING -> {
                    ActionButton("Pause", filled = true, modifier = Modifier.weight(1f), onClick = onPause)
                    ActionButton("Stop", filled = false, modifier = Modifier.weight(1f), onClick = onStop)
                }
                MergeStatus.PAUSED -> {
                    ActionButton("Resume", filled = true, modifier = Modifier.weight(1f), onClick = onStart)
                    ActionButton("Stop", filled = false, modifier = Modifier.weight(1f), onClick = onStop)
                }
                else -> {
                    ActionButton("Start merging", filled = true, modifier = Modifier.weight(1f), onClick = onStart)
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Text(
            "All balancing happens on this device. Nothing is logged or relayed anywhere.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TotalBlock(label: String, bytes: Long, arrow: String) {
    Column {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("$arrow ${human(bytes)}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Fuse)
    }
}

@Composable
private fun ActionButton(
    label: String,
    filled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = if (filled)
            ButtonDefaults.buttonColors(containerColor = Fuse)
        else
            ButtonDefaults.outlinedButtonColors(),
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

private fun hintFor(status: MergeStatus): String = when (status) {
    MergeStatus.STOPPED -> "Tap “Start merging” to bond your connections."
    MergeStatus.PAUSED -> "Traffic is using your normal default network."
    MergeStatus.NO_CONNECTIONS -> "Waiting for a connection with real internet access."
    MergeStatus.ERROR -> "Something went wrong — see the message above."
    MergeStatus.MERGING -> ""
}
