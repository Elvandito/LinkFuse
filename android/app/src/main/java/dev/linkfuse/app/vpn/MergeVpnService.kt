package dev.linkfuse.app.vpn

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.ServiceCompat
import dev.linkfuse.app.MainActivity
import dev.linkfuse.app.model.MergeStatus
import dev.linkfuse.app.net.LinkInfo
import dev.linkfuse.app.net.NetworkMonitor
import dev.linkfuse.app.service.MergeController
import dev.linkfuse.app.service.NotificationHub
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * The engine. A foreground [VpnService] that owns the TUN interface.
 *
 * Safety contract:
 *  - [onRevoke] and [onDestroy] always close the TUN fd, which hands routing back to the
 *    system default network — a crash, a kill, or another VPN taking over can never leave
 *    the device without internet.
 *  - When no validated link exists we tear the tunnel down instead of black-holing packets.
 */
class MergeVpnService : VpnService() {

    companion object {
        const val ACTION_START = "dev.linkfuse.START"
        const val ACTION_PAUSE = "dev.linkfuse.PAUSE"
        const val ACTION_RESUME = "dev.linkfuse.RESUME"
        const val ACTION_STOP = "dev.linkfuse.STOP"
        private const val NOTIF_ID = 42
        private const val VPN_ADDRESS = "10.10.10.2"
    }

    private var fd: ParcelFileDescriptor? = null
    private var router: TunRouter? = null
    private var udp: UdpRelay? = null
    private var tcp: TcpProxy? = null
    private var monitor: NetworkMonitor? = null
    private var pausedByUser = false

    override fun onCreate() {
        super.onCreate()
        NotificationHub.createChannel(this)
        // Android gives a foreground service ~5 s to post; do it before any work.
        promote(MergeController.ui.value.status)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_STOP -> stopAll()
            else -> {
                pausedByUser = false
                if (monitor == null) bootMonitor()
                if (fd == null) establish()
            }
        }
        return START_STICKY
    }

    private fun bootMonitor() {
        val cm = getSystemService(ConnectivityManager::class.java)
        val mon = NetworkMonitor(cm)
        monitor = mon
        MergeController.attach(this, mon)
        mon.start()
    }

    // ---- tunnel lifecycle ----------------------------------------------------

    private fun establish() {
        val usable = MergeController.usableLinks()
        if (usable.isEmpty()) {
            MergeController.setStatus(MergeStatus.NO_CONNECTIONS, "Waiting for a connection with internet access.")
            promote(MergeStatus.NO_CONNECTIONS)
            return
        }

        try {
            val builder = Builder()
                .setSession("LinkFuse")
                .setMtu(1400)
                .addAddress(VPN_ADDRESS, 32)
                .addRoute("0.0.0.0", 0)

            // DNS comes from the best link, with public fallbacks so name resolution never dies.
            val dns = usable.firstNotNullOfOrNull { link ->
                getSystemService(ConnectivityManager::class.java)
                    .getLinkProperties(link.network)?.dnsServers?.firstOrNull()
            }
            builder.addDnsServer(dns?.hostAddress ?: "1.1.1.1")
            builder.addDnsServer("8.8.8.8")

            builder.setUnderlyingNetworks(preferred(usable))
            builder.setConfigureIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )

            val established = builder.establish() ?: run {
                MergeController.setStatus(MergeStatus.ERROR, "The system refused the tunnel. Re-approve the VPN dialog.")
                promote(MergeStatus.ERROR)
                return
            }
            fd = established

            val tunIn = FileInputStream(established.fileDescriptor)
            val tunOut = FileOutputStream(established.fileDescriptor)
            val picker = WeightedPicker()

            udp = UdpRelay(picker, MergeController::candidates, MergeController::networkFor, { protect(it) }, tunOut)
            tcp = TcpProxy(picker, MergeController::candidates, MergeController::networkFor, { protect(it) }, tunOut)
            router = TunRouter(tunIn, udp!!, tcp!!).also { it.start() }

            MergeController.resetCounters()
            MergeController.setStatus(MergeStatus.MERGING)
            promote(MergeStatus.MERGING)
        } catch (t: Throwable) {
            Log.e("LinkFuse", "establish failed", t)
            tearTunnel()
            MergeController.setStatus(MergeStatus.ERROR, t.message ?: "Unknown tunnel error")
            promote(MergeStatus.ERROR)
        }
    }

    /** Links changed while running: re-hand the preference-ordered list to the system. */
    fun onLinksChanged(usable: List<LinkInfo>) {
        when {
            usable.isEmpty() && fd != null -> {
                tearTunnel()   // fall back to direct routing — never black-hole
                MergeController.setStatus(MergeStatus.NO_CONNECTIONS, "No usable connections right now.")
                promote(MergeStatus.NO_CONNECTIONS)
            }
            usable.isNotEmpty() && fd != null -> {
                runCatching { setUnderlyingNetworks(preferred(usable)) }
                MergeController.setStatus(if (pausedByUser) MergeStatus.PAUSED else MergeStatus.MERGING)
                promote(MergeController.ui.value.status)
            }
            usable.isNotEmpty() && fd == null && !pausedByUser -> establish()
        }
    }

    private fun pause() {
        pausedByUser = true
        tearTunnel()   // closing the fd returns all traffic to the normal default network
        MergeController.setStatus(MergeStatus.PAUSED)
        promote(MergeStatus.PAUSED)
    }

    private fun resume() {
        pausedByUser = false
        establish()
    }

    private fun stopAll() {
        pausedByUser = false
        tearTunnel()
        MergeController.setStatus(MergeStatus.STOPPED)
        MergeController.detach()
        runCatching { monitor?.stop() }
        monitor = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun tearTunnel() {
        router?.stop(); router = null
        udp = null; tcp = null
        runCatching { fd?.close() }
        fd = null
    }

    /** Ethernet first, then Wi-Fi, then cellular — the documented preference order. */
    private fun preferred(usable: List<LinkInfo>): Array<Network> =
        usable.sortedByDescending { it.kind.weight }.map { it.network }.toTypedArray()

    private fun promote(status: MergeStatus) {
        val notification = NotificationHub.build(this, MergeController.ui.value.copy(status = status))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this, NOTIF_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            ServiceCompat.startForeground(this, NOTIF_ID, notification, 0)
        }
    }

    override fun onRevoke() {
        // Another VPN took over, or the system pulled the plug: close everything.
        tearTunnel()
        MergeController.setStatus(MergeStatus.STOPPED)
        stopSelf()
    }

    override fun onDestroy() {
        tearTunnel()
        MergeController.detach()
        super.onDestroy()
    }
}
