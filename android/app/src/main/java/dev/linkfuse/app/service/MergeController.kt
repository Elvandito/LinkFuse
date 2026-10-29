package dev.linkfuse.app.service

import android.content.Context
import android.content.Intent
import android.net.Network
import android.os.Build
import dev.linkfuse.app.model.LinkKind
import dev.linkfuse.app.model.LinkUi
import dev.linkfuse.app.model.MergeStatus
import dev.linkfuse.app.model.UiState
import dev.linkfuse.app.net.LinkInfo
import dev.linkfuse.app.net.NetworkMonitor
import dev.linkfuse.app.vpn.CounterBank
import dev.linkfuse.app.vpn.MergeVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The single source of truth the UI, the notification and the QS tile all talk to.
 * Owns link discovery, the merged status, live speeds and the user's metered policy.
 * The actual tunnel lives in [MergeVpnService]; this object holds a handle to it.
 */
object MergeController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val mutable = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = mutable.asStateFlow()

    private val _allowMetered = MutableStateFlow(true)
    val allowMetered: StateFlow<Boolean> = _allowMetered.asStateFlow()

    @Volatile private var service: MergeVpnService? = null
    @Volatile private var monitor: NetworkMonitor? = null
    @Volatile private var links: List<LinkInfo> = emptyList()

    private val lastCounters = HashMap<String, Pair<Long, Long>>()

    // ---- link registry, consumed by the relays -------------------------------

    /** Weighted candidates for new flows, honouring the metered policy with a safe fallback. */
    fun candidates(): List<Pair<String, Int>> {
        val usable = usableLinks()
        val filtered = if (_allowMetered.value) usable else usable.filter { !it.metered }
        val effective = filtered.ifEmpty { usable }   // never strand the device
        return effective.map { it.network.toString() to it.kind.weight }
    }

    fun usableLinks(): List<LinkInfo> = links.filter { it.validated }

    fun networkFor(id: String): Network? = links.firstOrNull { it.network.toString() == id }?.network

    // ---- lifecycle -----------------------------------------------------------

    fun attach(svc: MergeVpnService, mon: NetworkMonitor) {
        service = svc
        monitor = mon
        scope.launch { mon.links.collect { onLinksChanged(it) } }
        startSpeedSampler()
    }

    fun detach() {
        service = null
    }

    /** Called from the Activity / tile / notification when the user wants merging on. */
    fun start(context: Context) {
        val intent = Intent(context, MergeVpnService::class.java).apply {
            action = MergeVpnService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun pause(context: Context) = send(context, MergeVpnService.ACTION_PAUSE)
    fun resume(context: Context) = send(context, MergeVpnService.ACTION_RESUME)
    fun stop(context: Context) = send(context, MergeVpnService.ACTION_STOP)

    private fun send(context: Context, action: String) =
        context.startService(Intent(context, MergeVpnService::class.java).apply { this.action = action })

    fun setAllowMetered(value: Boolean) {
        _allowMetered.value = value
        refreshUi()
    }

    // ---- internals -----------------------------------------------------------

    private fun onLinksChanged(newLinks: List<LinkInfo>) {
        links = newLinks
        service?.onLinksChanged(usableLinks())   // re-hands underlying networks to the system
        refreshUi()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // nudge the QS tile to refresh its label/state
        }
    }

    fun setStatus(status: MergeStatus, message: String? = null) {
        mutable.value = mutable.value.copy(status = status, message = message)
    }

    private fun refreshUi() {
        val state = mutable.value
        val linkUis = usableLinks().map { info ->
            val id = info.network.toString()
            val (down, up) = rateFor(id)
            LinkUi(id, info.kind, info.detail, info.metered, down, up, shareOf(id))
        }
        mutable.value = state.copy(
            links = linkUis,
            totalDown = linkUis.sumOf { it.downRate },
            totalUp = linkUis.sumOf { it.upRate },
        )
    }

    private fun rateFor(id: String): Pair<Long, Long> = synchronized(lastCounters) {
        val (down, up) = CounterBank.snapshot(id)
        val (prevDown, prevUp) = lastCounters[id] ?: (0L to 0L)
        lastCounters[id] = down to up
        maxOf(0L, down - prevDown) to maxOf(0L, up - prevUp)
    }

    private fun shareOf(id: String): Float {
        val c = candidates()
        val total = c.sumOf { it.second }
        if (total <= 0) return 0f
        return ((c.firstOrNull { it.first == id }?.second ?: 0)) / total.toFloat()
    }

    private var samplerStarted = false
    private fun startSpeedSampler() {
        if (samplerStarted) return
        samplerStarted = true
        scope.launch {
            while (true) {
                kotlinx.coroutines.delay(1_000)
                if (service != null) refreshUi()
            }
        }
    }

    fun resetCounters() {
        CounterBank.reset()
        lastCounters.clear()
    }
}
