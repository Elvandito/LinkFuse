package dev.linkfuse.app.net

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dev.linkfuse.app.model.LinkKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What we currently know about one physical connection. */
data class LinkInfo(
    val network: Network,
    val kind: LinkKind,
    val detail: String,
    val validated: Boolean,      // has real internet (captive portals fail validation)
    val metered: Boolean,
    var rxBytes: Long = 0,       // cumulative interface counters from LinkProperties… not
    var txBytes: Long = 0        // …available per-network; speeds are measured in the relays instead
)

/**
 * Watches every Wi-Fi / cellular / Ethernet (incl. USB tether) connection through
 * [ConnectivityManager.registerNetworkCallback]. Plug or unplug a cable, walk out of
 * Wi-Fi range, toggle the hotspot — we see it within a second and the engine rebalances.
 */
class NetworkMonitor(private val cm: ConnectivityManager) {

    private val mutable = MutableStateFlow<List<LinkInfo>>(emptyList())
    val links: StateFlow<List<LinkInfo>> = mutable.asStateFlow()

    private val known = LinkedHashMap<Network, LinkInfo>()

    private val request = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
        .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
        .build()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            refresh(network, cm.getNetworkCapabilities(network), cm.getLinkProperties(network))
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            refresh(network, caps, cm.getLinkProperties(network))
        }

        override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
            refresh(network, cm.getNetworkCapabilities(network), lp)
        }

        override fun onLost(network: Network) {
            synchronized(known) { known.remove(network) }
            publish()
        }
    }

    fun start() = cm.registerNetworkCallback(request, callback)

    fun stop() = runCatching { cm.unregisterNetworkCallback(callback) }

    /** A link counts once it has a validated, non-captive route to the internet. */
    private fun refresh(network: Network, caps: NetworkCapabilities?, lp: LinkProperties?) {
        if (caps == null) return
        val kind = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> LinkKind.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> LinkKind.CELLULAR
            else -> LinkKind.ETHERNET
        }
        // VALIDATED is the authoritative signal: captive portals and dead links both fail it.
        val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        val detail = lp?.interfaceName?.takeIf { it.isNotBlank() }
            ?: if (kind == LinkKind.CELLULAR) "Cellular" else kind.label

        synchronized(known) {
            known[network] = LinkInfo(network, kind, detail, validated, metered)
        }
        publish()
    }

    private fun publish() {
        val snapshot = synchronized(known) { known.values.toList() }
        mutable.value = snapshot
    }
}
