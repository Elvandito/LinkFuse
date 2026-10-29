package dev.linkfuse.app.vpn

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/** 5-tuple identity of a flow. One entry == one pinned connection. */
data class FlowKey(
    val protocol: Int,
    val srcIp: String,
    val srcPort: Int,
    val dstIp: String,
    val dstPort: Int,
)

/**
 * Weighted random picker — new flows land on a link proportionally to its weight
 * (Ethernet > Wi-Fi > cellular by default). Existing flows never move: stickiness
 * is guaranteed because the table is consulted before the picker.
 */
class WeightedPicker {
    fun pick(candidates: List<Pair<String, Int>>): String? {
        if (candidates.isEmpty()) return null
        val total = candidates.sumOf { it.second }
        if (total <= 0) return candidates.first().first
        var r = Random.nextInt(total)
        for ((id, w) in candidates) {
            r -= w
            if (r < 0) return id
        }
        return candidates.last().first
    }
}

class FlowEntry<T>(val value: T, val linkId: String) {
    @Volatile var lastUsed: Long = System.currentTimeMillis()
}

/**
 * Keeps one resource per 5-tuple and expires idle flows so sockets don't leak.
 * [onExpire] is where relays close their sockets.
 */
class FlowTable<T>(private val idleMs: Long, private val onExpire: (FlowEntry<T>) -> Unit) {

    private val map = ConcurrentHashMap<FlowKey, FlowEntry<T>>()

    fun get(key: FlowKey): FlowEntry<T>? = map[key]?.also { it.lastUsed = System.currentTimeMillis() }

    fun put(key: FlowKey, entry: FlowEntry<T>) {
        map[key] = entry
    }

    fun remove(key: FlowKey) {
        map.remove(key)?.let(onExpire)
    }

    val size: Int get() = map.size

    /** Called from a housekeeping tick; sweeps stale flows and releases their sockets. */
    fun sweep() {
        val now = System.currentTimeMillis()
        val it = map.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (now - e.value.lastUsed > idleMs) {
                it.remove()
                onExpire(e.value)
            }
        }
    }

    fun closeAll() {
        map.values.forEach(onExpire)
        map.clear()
    }
}

/**
 * Per-link byte counters. The relays bump these on every forwarded byte;
 * [dev.linkfuse.app.service.MergeController] samples them once a second to
 * produce the live speeds shown on screen and — nowhere else. Nothing leaves the device.
 */
object CounterBank {
    private val down = ConcurrentHashMap<String, AtomicLong>()
    private val up = ConcurrentHashMap<String, AtomicLong>()

    fun addDown(linkId: String, bytes: Int) {
        down.getOrPut(linkId) { AtomicLong() }.addAndGet(bytes.toLong())
    }

    fun addUp(linkId: String, bytes: Int) {
        up.getOrPut(linkId) { AtomicLong() }.addAndGet(bytes.toLong())
    }

    fun snapshot(linkId: String): Pair<Long, Long> =
        (down[linkId]?.get() ?: 0L) to (up[linkId]?.get() ?: 0L)

    fun reset() {
        down.clear()
        up.clear()
    }
}
