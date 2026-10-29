package dev.linkfuse.app.vpn

import java.io.InputStream

/**
 * Reads raw IPv4 packets out of the TUN and hands each one to the right relay.
 * Runs on its own daemon thread; the TUN fd is non-blocking, so an empty read
 * just yields a brief sleep instead of burning a core.
 */
class TunRouter(
    private val tunIn: InputStream,
    private val udp: UdpRelay,
    private val tcp: TcpProxy,
) {
    @Volatile var running = false
        private set

    private var loopThread: Thread? = null

    fun start() {
        if (running) return
        running = true
        loopThread = Thread({
            val buf = ByteArray(32_767)
            while (running) {
                val n = try {
                    tunIn.read(buf)
                } catch (_: Throwable) {
                    -1
                }
                if (n < 20) {
                    if (n == -1) Thread.sleep(2)
                    continue
                }
                // IPv4 only; the version nibble must be 4.
                if (((buf[0].toInt() ushr 4) and 0xF) != 4) continue
                when (buf[9].toInt() and 0xFF) {
                    IpPacket.PROTO_UDP -> udp.handle(buf.copyOfRange(0, n), n)
                    IpPacket.PROTO_TCP -> tcp.handle(buf.copyOfRange(0, n), n)
                    // ICMP and the rest: dropped. Browsers and apps ride TCP/UDP,
                    // and dropping the rest can never wedge a session.
                }
            }
        }, "lf-router").apply {
            isDaemon = true
            start()
        }

        Thread({
            while (running) {
                Thread.sleep(5_000)
                udp.sweep()
                tcp.sweep()
            }
        }, "lf-sweep").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        udp.shutdown()
        tcp.shutdown()
        runCatching { loopThread?.interrupt() }
    }
}
