package dev.linkfuse.app.setup

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

enum class SetupStep { NOTIFICATIONS, VPN, BATTERY, FGS }

/**
 * Computes which first-run steps are still open and builds the exact intents that
 * resolve them. The Activity owns the result callbacks.
 */
object SetupChecker {

    fun needsNotification(ctx: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED

    /** Non-null result means the system VPN consent dialog still has to be shown. */
    fun vpnConsentIntent(ctx: Context): Intent? = VpnService.prepare(ctx)

    @SuppressLint("BatteryLife")
    fun needsBattery(ctx: Context): Boolean {
        val pm = ctx.getSystemService(PowerManager::class.java)
        return !pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    fun batteryIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${ctx.packageName}")
        }

    /** Steps the onboarding card should show, in order. FGS is informational only. */
    fun pending(ctx: Context): List<SetupStep> = buildList {
        if (needsNotification(ctx)) add(SetupStep.NOTIFICATIONS)
        if (vpnConsentIntent(ctx) != null) add(SetupStep.VPN)
        if (needsBattery(ctx)) add(SetupStep.BATTERY)
        add(SetupStep.FGS)   // no runtime gate — we just explain the persistent notification
    }

    fun allRequiredDone(ctx: Context): Boolean =
        !needsNotification(ctx) && vpnConsentIntent(ctx) == null && !needsBattery(ctx)
}
