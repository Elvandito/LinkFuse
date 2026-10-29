package dev.linkfuse.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.linkfuse.app.setup.SetupStep
import dev.linkfuse.app.ui.theme.Fuse
import dev.linkfuse.app.ui.theme.Okay
import dev.linkfuse.app.ui.theme.Wifi as WifiColor

private data class StepCopy(val title: String, val caption: String, val cta: String)

private fun copy(step: SetupStep) = when (step) {
    SetupStep.NOTIFICATIONS -> StepCopy(
        "Allow notifications",
        "Let LinkFuse post its status notification — on Android 13 and newer this needs your approval.",
        "Allow",
    )
    SetupStep.VPN -> StepCopy(
        "Approve the tunnel",
        "Approve the system VPN dialog so LinkFuse can create its local tunnel interface.",
        "Approve",
    )
    SetupStep.BATTERY -> StepCopy(
        "Skip battery optimization",
        "Turn off battery optimization for LinkFuse so Android doesn't freeze the merge while the screen is off.",
        "Open settings",
    )
    SetupStep.FGS -> StepCopy(
        "Foreground service",
        "The merge runs as a foreground service — the persistent notification is what keeps Android from killing it.",
        "Got it",
    )
}

@Composable
fun OnboardingSheet(
    pending: List<SetupStep>,
    canStart: Boolean,
    onAction: (SetupStep) -> Unit,
    onStart: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
        Text("One-time setup", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(
            "Four quick approvals and LinkFuse can safely bond your connections.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 18.dp),
        )

        pending.forEachIndexed { i, step ->
            val c = copy(step)
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(34.dp).background(WifiColor.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${i + 1}", fontWeight = FontWeight.Bold, color = WifiColor)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.title, fontWeight = FontWeight.SemiBold)
                        Text(c.caption, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(10.dp))
                    OutlinedButton(onClick = { onAction(step) }) { Text(c.cta) }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onStart,
            enabled = canStart,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Fuse),
        ) {
            Text("Start merging", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        if (!canStart) {
            Text(
                "Finish the steps above first — each one protects the merge from being killed.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text("Do it later") }
    }
}
