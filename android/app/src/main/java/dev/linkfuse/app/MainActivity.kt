package dev.linkfuse.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.linkfuse.app.service.MergeController
import dev.linkfuse.app.setup.SetupChecker
import dev.linkfuse.app.setup.SetupStep
import dev.linkfuse.app.ui.MainScreen
import dev.linkfuse.app.ui.OnboardingSheet
import dev.linkfuse.app.ui.theme.LinkFuseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LinkFuseTheme { Root() } }
    }
}

@Composable
private fun Root() {
    val context = LocalContext.current
    val ui by MergeController.ui.collectAsState()

    var refresh by remember { mutableIntStateOf(0) }
    var ackFgs by remember { mutableStateOf(false) }
    var skipped by remember { mutableStateOf(false) }

    val notifLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { refresh++ }
    val vpnLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refresh++ }
    val batteryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refresh++ }

    val pending = remember(refresh, ackFgs) {
        SetupChecker.pending(context).filter { it != SetupStep.FGS || !ackFgs }
    }
    val canStart = remember(refresh) {
        SetupChecker.allRequiredDone(context)
    }

    if (pending.isNotEmpty() && !skipped) {
        OnboardingSheet(
            pending = pending,
            canStart = canStart,
            onAction = { step ->
                when (step) {
                    SetupStep.NOTIFICATIONS ->
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    SetupStep.VPN ->
                        SetupChecker.vpnConsentIntent(context)?.let { vpnLauncher.launch(it) }
                    SetupStep.BATTERY ->
                        runCatching { batteryLauncher.launch(SetupChecker.batteryIntent(context)) }
                    SetupStep.FGS -> ackFgs = true
                }
            },
            onStart = {
                MergeController.start(context)
                skipped = true
            },
            onSkip = { skipped = true },
        )
    } else {
        MainScreen(
            state = ui,
            onStart = {
                if (SetupChecker.vpnConsentIntent(context) != null) {
                    skipped = false   // bounce back to onboarding for the consent dialog
                } else {
                    MergeController.start(context)
                }
            },
            onPause = { MergeController.pause(context) },
            onStop = { MergeController.stop(context) },
        )
    }
}
