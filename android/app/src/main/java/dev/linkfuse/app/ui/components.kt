package dev.linkfuse.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.linkfuse.app.model.LinkKind
import dev.linkfuse.app.model.MergeStatus
import dev.linkfuse.app.ui.theme.Alert
import dev.linkfuse.app.ui.theme.Cell
import dev.linkfuse.app.ui.theme.Dim
import dev.linkfuse.app.ui.theme.Fuse
import dev.linkfuse.app.ui.theme.Okay
import dev.linkfuse.app.ui.theme.Wifi as WifiColor
import dev.linkfuse.app.ui.theme.Wire

fun kindIcon(kind: LinkKind): ImageVector = when (kind) {
    LinkKind.WIFI -> Icons.Outlined.Wifi
    LinkKind.CELLULAR -> Icons.Outlined.SignalCellularAlt
    LinkKind.ETHERNET -> Icons.Outlined.Cable
}

fun kindColor(kind: LinkKind): Color = when (kind) {
    LinkKind.WIFI -> WifiColor
    LinkKind.CELLULAR -> Cell
    LinkKind.ETHERNET -> Wire
}

fun statusColor(status: MergeStatus): Color = when (status) {
    MergeStatus.MERGING -> Okay
    MergeStatus.PAUSED -> Cell
    MergeStatus.NO_CONNECTIONS -> Dim
    MergeStatus.ERROR -> Alert
    MergeStatus.STOPPED -> Dim
}

fun human(bytesPerSec: Long): String = when {
    bytesPerSec >= 1_000_000 -> "%.1f MB/s".format(bytesPerSec / 1_000_000.0)
    bytesPerSec >= 1_000 -> "${(bytesPerSec / 1_000)} KB/s"
    else -> "$bytesPerSec B/s"
}

@Composable
fun KindBadge(kind: LinkKind) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(kindColor(kind).copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(kindIcon(kind), contentDescription = kind.label, tint = kindColor(kind), modifier = Modifier.size(22.dp))
    }
}

@Composable
fun ShareBar(share: Float, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(share.coerceIn(0f, 1f))
                .height(4.dp)
                .background(color, RoundedCornerShape(2.dp)),
        )
    }
}

@Composable
fun StatusDot(status: MergeStatus) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(statusColor(status), CircleShape),
    )
}
