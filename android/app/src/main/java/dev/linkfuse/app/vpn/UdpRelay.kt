package dev.linkfuse.app.vpn

import android.net.Network
import android.util.Log
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicInteger

/**
 * Per-flow UDP balancer. Each new 5-tuple is pinned to one link chosen by weight;
 * every datagram of that flow keeps using the same socket, so a session can never
 * jump networks mid-flight. Replies are re-wrapped and injected back into the TUN.
 */
class UdpRelay(
    private val picker: WeightedPicker,
    private val candidates: () -> List<Pair<String, Int>>,
    private val networkFor: (String) -> Network?,
    private val protect: (DatagramSocket) -> Boolean,
    private val tunOut: OutputStream,
) {
    private class UdpFlow(
        val socket: DatagramSocket,
        val clientIp: ByteArray, val clientPort: Int,
        val serverIp: ByteArray, val serverPort: Int,
        val linkId: String,
    )

    private val table = FlowTable<UdpFlow>(idleMs = 60_000) { close(it.value) }
    private val ipId = AtomicInteger(1)

    fun handle(pkt: ByteArray, len: Int) {
        val ihl = (pkt[0].toInt() and 0x0F) * 4
        if (len < ihl + 8) return
        val srcIp = pkt.copyOfRange(12, 16)
        val dstIp = pkt.copyOfRange(16, 20)
        val sport = IpPacket.u16(pkt, ihl)
        val dport = IpPacket.u16(pkt, ihl + 2)
        val payloadOff = ihl + 8
        val payloadLen = len - payloadOff
        if (payloadLen <= 0) return

        val key = FlowKey(IpPacket.PROTO_UDP, IpPacket.addrToString(srcIp), sport, IpPacket.addrToString(dstIp), dport)
        val entry = table.get(key) ?: createFlow(key, srcIp, dstIp, sport, dport) ?: return

        try {
            entry.value.socket.send(DatagramPacket(pkt, payloadOff, payloadLen))
            CounterBank.addUp(entry.linkId, payloadLen)
        } catch (t: Throwable) {
            table.remove(key)
        }
    }

    private fun createFlow(key: FlowKey, srcIp: ByteArray, dstIp: ByteArray, sport: Int, dport: Int): FlowEntry<UdpFlow>? {
        val linkId = picker.pick(candidates()) ?: return null
        val network = networkFor(linkId) ?: return null
        return try {
            val socket = DatagramSocket()
            protect(socket)                       // keep our socket out of the tunnel
            network.bindSocket(socket)            // …and pin it to the chosen link
            socket.connect(InetAddress.getByAddress(dstIp), dport)
            socket.soTimeout = 0

            val flow = UdpFlow(socket, srcIp, sport, dstIp.copyOf(), dport, linkId)
            val reader = Thread({ readLoop(key, flow) }, "lf-udp-${key.dstPort}")
            reader.isDaemon = true
            reader.start()

            val entry = FlowEntry(flow, linkId)
            table.put(key, entry)
            entry
        } catch (t: Throwable) {
            Log.w("LinkFuse", "udp flow setup failed: ${t.message}")
            null
        }
    }

    private fun readLoop(key: FlowKey, flow: UdpFlow) {
        val buf = ByteArray(2048)
        try {
            while (!flow.socket.isClosed) {
                val resp = DatagramPacket(buf, buf.size)
                flow.socket.receive(resp)
                val payloadLen = resp.length
                if (payloadLen <= 0) continue

                val out = ByteArray(20 + 8 + payloadLen)
                // UDP header: server -> client (payload placed first so the checksum covers it)
                IpPacket.putU16(out, 20, flow.serverPort)
                IpPacket.putU16(out, 22, flow.clientPort)
                IpPacket.putU16(out, 24, 8 + payloadLen)
                System.arraycopy(resp.data, 0, out, 28, payloadLen)
                val udpLen = 8 + payloadLen
                var sum = IpPacket.pseudoSum(flow.serverIp, flow.clientIp, IpPacket.PROTO_UDP, udpLen)
                for (i in 20 until 20 + udpLen step 2) {
                    val hi = out[i].toInt() and 0xFF
                    val lo = if (i + 1 < 20 + udpLen) out[i + 1].toInt() and 0xFF else 0
                    sum += (hi shl 8) or lo
                }
                while ((sum ushr 16) != 0L) sum = (sum and 0xFFFF) + (sum ushr 16)
                IpPacket.putU16(out, 26, (sum.inv().toInt()) and 0xFFFF)

                val total = IpPacket.writeIpv4(
                    out, flow.serverIp, flow.clientIp, IpPacket.PROTO_UDP,
                    out, 20, 8 + payloadLen, ipId.getAndIncrement()
                )
                synchronized(tunOut) { tunOut.write(out, 0, total) }
                CounterBank.addDown(flow.linkId, payloadLen)
            }
        } catch (_: Throwable) {
            // socket closed during sweep or link loss — expected
        } finally {
            table.remove(key)
        }
    }

    private fun close(flow: UdpFlow) = runCatching { flow.socket.close() }

    fun sweep() = table.sweep()
    fun shutdown() = table.closeAll()
}
