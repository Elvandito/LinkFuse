package dev.linkfuse.app.vpn

import android.net.Network
import android.util.Log
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * Minimal userspace TCP endpoint — the same idea NetGuard pioneered for rootless VPNs.
 *
 * Why this exists at all: a full-capture TUN receives TCP too, and Android gives us no
 * raw sockets, so to keep TCP alive without root or a relay server we answer the client's
 * handshake ourselves and open a normal socket to the real server on the chosen link.
 *
 * Deliberately small: in-order delivery only, fixed 64 KiB window, no SACK / window scaling.
 * Anything exotic gets a clean RST so apps fall back instead of hanging — the internet is
 * never silently blocked.
 */
class TcpProxy(
    private val picker: WeightedPicker,
    private val candidates: () -> List<Pair<String, Int>>,
    private val networkFor: (String) -> Network?,
    private val protect: (Socket) -> Boolean,
    private val tunOut: OutputStream,
) {
    private companion object {
        const val FIN = 0x01; const val SYN = 0x02; const val RST = 0x04
        const val PSH = 0x08; const val ACK = 0x10
        const val WINDOW = 65535
    }

    private class TcpFlow(
        val clientIp: ByteArray, val clientPort: Int,
        val serverIp: ByteArray, val serverPort: Int,
        val linkId: String,
    ) {
        @Volatile var clientSeq = 0L          // next sequence we expect from the client
        @Volatile var serverSeq = 0L          // next sequence we will send
        @Volatile var upstream: Socket? = null
        @Volatile var failed = false
        val pending = ArrayDeque<ByteArray>()
        val lock = Any()
    }

    private val table = FlowTable<TcpFlow>(idleMs = 120_000) { close(it.value) }
    private val ipId = AtomicInteger(0x8000)

    fun handle(pkt: ByteArray, len: Int) {
        val ihl = (pkt[0].toInt() and 0x0F) * 4
        if (len < ihl + 20) return
        val t = ihl
        val srcIp = pkt.copyOfRange(12, 16)
        val dstIp = pkt.copyOfRange(16, 20)
        val sport = IpPacket.u16(pkt, t)
        val dport = IpPacket.u16(pkt, t + 2)
        val seq = IpPacket.u32(pkt, t + 4)
        val flags = pkt[t + 13].toInt() and 0x3F
        val dataOff = ((pkt[t + 12].toInt() and 0xFF) ushr 4) * 4
        val payloadOff = t + dataOff
        val payloadLen = len - payloadOff

        val key = FlowKey(IpPacket.PROTO_TCP, IpPacket.addrToString(srcIp), sport, IpPacket.addrToString(dstIp), dport)

        // Brand-new connection request
        if (flags and SYN != 0 && flags and ACK == 0) {
            open(key, srcIp, dstIp, sport, dport, seq)
            return
        }

        val entry = table.get(key) ?: return
        val flow = entry.value

        when {
            flags and RST != 0 -> table.remove(key)

            flags and FIN != 0 -> {
                flow.clientSeq = seq + 1
                send(flow, ACK or FIN, null)
                closeUpstream(flow)
            }

            payloadLen > 0 -> {
                if (seq == flow.clientSeq) {
                    flow.clientSeq = seq + payloadLen
                    deliver(flow, pkt, payloadOff, payloadLen, entry.linkId)
                    send(flow, ACK, null)
                } else if (seq < flow.clientSeq) {
                    send(flow, ACK, null)               // duplicate — re-ack
                } else {
                    send(flow, ACK, null)               // gap — ask for resend
                }
            }

            else -> send(flow, ACK, null)               // pure ACK / keepalive
        }
    }

    private fun open(key: FlowKey, srcIp: ByteArray, dstIp: ByteArray, sport: Int, dport: Int, seq: Long) {
        val linkId = picker.pick(candidates()) ?: return
        val network = networkFor(linkId) ?: return

        val flow = TcpFlow(srcIp, sport, dstIp.copyOf(), dport, linkId)
        flow.clientSeq = seq + 1
        flow.serverSeq = Random.nextLong(0, 0xFFFFFFFFL)
        table.put(key, FlowEntry(flow, linkId))

        send(flow, SYN or ACK, null)                    // complete the handshake as the "server"
        flow.serverSeq += 1

        val connector = Thread({
            try {
                val socket = Socket()
                protect(socket)
                network.bindSocket(socket)
                socket.connect(InetSocketAddress(InetAddr(dstIp), dport), 8_000)
                socket.tcpNoDelay = true
                synchronized(flow.lock) {
                    flow.upstream = socket
                    CounterBank.addUp(linkId, 0)
                    flow.pending.forEach { socket.getOutputStream().write(it) }
                    flow.pending.clear()
                }
                readLoop(key, flow)
            } catch (t: Throwable) {
                Log.w("LinkFuse", "tcp upstream failed ${key.dstIp}:${key.dstPort}: ${t.message}")
                flow.failed = true
                send(flow, RST, null)
                table.remove(key)
            }
        }, "lf-tcp-${key.dstPort}")
        connector.isDaemon = true
        connector.start()
    }

    private fun deliver(flow: TcpFlow, pkt: ByteArray, off: Int, len: Int, linkId: String) {
        val socket = flow.upstream
        if (socket == null) {
            synchronized(flow.lock) { flow.pending.addLast(pkt.copyOfRange(off, off + len)) }
        } else {
            try {
                socket.getOutputStream().write(pkt, off, len)
                CounterBank.addUp(linkId, len)
            } catch (_: Throwable) {
                send(flow, RST, null)
                table.remove(flowKey(flow))
            }
        }
    }

    private fun readLoop(key: FlowKey, flow: TcpFlow) {
        val socket = flow.upstream ?: return
        val buf = ByteArray(4096)
        try {
            val input = socket.getInputStream()
            while (true) {
                val n = input.read(buf)
                if (n < 0) { send(flow, ACK or FIN, null); flow.serverSeq += 1; break }
                send(flow, ACK or PSH, buf.copyOfRange(0, n))
                CounterBank.addDown(flow.linkId, n)
            }
        } catch (_: Throwable) {
            send(flow, RST, null)
        } finally {
            table.remove(key)
        }
    }

    private fun send(flow: TcpFlow, flags: Int, payload: ByteArray?) {
        val payloadLen = payload?.size ?: 0
        val seg = ByteArray(20 + payloadLen)
        IpPacket.putU16(seg, 0, flow.serverPort)
        IpPacket.putU16(seg, 2, flow.clientPort)
        IpPacket.putU32(seg, 4, flow.serverSeq)
        IpPacket.putU32(seg, 8, flow.clientSeq)
        seg[12] = ((5 and 0xF) shl 4).toByte()
        seg[13] = flags.toByte()
        IpPacket.putU16(seg, 14, WINDOW)

        // TCP checksum over pseudo-header + segment
        var sum = IpPacket.pseudoSum(flow.serverIp, flow.clientIp, IpPacket.PROTO_TCP, seg.size)
        for (i in seg.indices step 2) {
            val hi = seg[i].toInt() and 0xFF
            val lo = if (i + 1 < seg.size) seg[i + 1].toInt() and 0xFF else 0
            sum += (hi shl 8) or lo
        }
        while ((sum ushr 16) != 0L) sum = (sum and 0xFFFF) + (sum ushr 16)
        IpPacket.putU16(seg, 16, (sum.inv().toInt()) and 0xFFFF)
        if (payload != null) System.arraycopy(payload, 0, seg, 20, payloadLen)

        val out = ByteArray(20 + seg.size)
        val total = IpPacket.writeIpv4(
            out, flow.serverIp, flow.clientIp, IpPacket.PROTO_TCP,
            seg, 0, seg.size, ipId.getAndIncrement()
        )
        synchronized(tunOut) { tunOut.write(out, 0, total) }

        if (payloadLen > 0) flow.serverSeq += payloadLen
    }

    private fun closeUpstream(flow: TcpFlow) {
        runCatching { flow.upstream?.close() }
    }

    private fun close(flow: TcpFlow) = runCatching { flow.upstream?.close() }

    private fun flowKey(f: TcpFlow) = FlowKey(
        IpPacket.PROTO_TCP, IpPacket.addrToString(f.clientIp), f.clientPort,
        IpPacket.addrToString(f.serverIp), f.serverPort
    )

    fun sweep() = table.sweep()
    fun shutdown() = table.closeAll()
}

/** Tiny helper so we don't drag java.net.InetAddress allocation noise into hot paths. */
private fun InetAddr(b: ByteArray): java.net.InetAddress = java.net.InetAddress.getByAddress(b)
