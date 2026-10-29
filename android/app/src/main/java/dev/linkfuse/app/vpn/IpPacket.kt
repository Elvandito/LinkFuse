package dev.linkfuse.app.vpn

/**
 * Minimal IPv4 / TCP / UDP header helpers. We terminate flows in userspace, so we only
 * ever *build* headers for packets we inject back into the TUN — never rewrite in place.
 */
internal object IpPacket {

    const val PROTO_TCP = 6
    const val PROTO_UDP = 17

    fun u16(b: ByteArray, off: Int): Int = ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)

    fun putU16(b: ByteArray, off: Int, v: Int) {
        b[off] = ((v ushr 8) and 0xFF).toByte()
        b[off + 1] = (v and 0xFF).toByte()
    }

    fun putU32(b: ByteArray, off: Int, v: Long) {
        b[off] = ((v ushr 24) and 0xFF).toByte()
        b[off + 1] = ((v ushr 16) and 0xFF).toByte()
        b[off + 2] = ((v ushr 8) and 0xFF).toByte()
        b[off + 3] = (v and 0xFF).toByte()
    }

    fun u32(b: ByteArray, off: Int): Long =
        ((b[off].toLong() and 0xFF) shl 24) or
            ((b[off + 1].toLong() and 0xFF) shl 16) or
            ((b[off + 2].toLong() and 0xFF) shl 8) or
            (b[off + 3].toLong() and 0xFF)

    /** Standard Internet checksum (RFC 1071). */
    fun checksum(buf: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        var remaining = length
        while (remaining > 1) {
            sum += ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
            i += 2
            remaining -= 2
        }
        if (remaining > 0) sum += (buf[i].toInt() and 0xFF) shl 8
        while ((sum ushr 16) != 0L) sum = (sum and 0xFFFF) + (sum ushr 16)
        return (sum.inv().toInt()) and 0xFFFF
    }

    fun addrToString(b: ByteArray, off: Int = 0): String =
        "${b[off].toInt() and 0xFF}.${b[off + 1].toInt() and 0xFF}.${b[off + 2].toInt() and 0xFF}.${b[off + 3].toInt() and 0xFF}"

    fun addrToBytes(ip: String): ByteArray {
        val parts = ip.split('.')
        return byteArrayOf(
            parts[0].toInt().toByte(), parts[1].toInt().toByte(),
            parts[2].toInt().toByte(), parts[3].toInt().toByte()
        )
    }

    /**
     * Builds a fresh IPv4 header + payload into [out]. Returns total length.
     * [src]/[dst] are the addresses *as the app should see them* (i.e. the real remote
     * server is the source for inbound packets).
     */
    fun writeIpv4(
        out: ByteArray, src: ByteArray, dst: ByteArray, protocol: Int,
        payload: ByteArray, payloadOff: Int, payloadLen: Int, id: Int
    ): Int {
        val total = 20 + payloadLen
        out[0] = 0x45 // v4, IHL 5
        out[1] = 0
        putU16(out, 2, total)
        putU16(out, 4, id and 0xFFFF)
        putU16(out, 6, 0x4000) // don't fragment
        out[8] = 64 // TTL
        out[9] = protocol.toByte()
        putU16(out, 10, 0)
        System.arraycopy(src, 0, out, 12, 4)
        System.arraycopy(dst, 0, out, 16, 4)
        putU16(out, 10, checksum(out, 0, 20))
        System.arraycopy(payload, payloadOff, out, 20, payloadLen)
        return total
    }

    /** Pseudo-header sum shared by TCP and UDP checksums. */
    fun pseudoSum(src: ByteArray, dst: ByteArray, protocol: Int, length: Int): Long {
        var sum = 0L
        for (i in 0..3) sum += (src[i].toInt() and 0xFF)
        for (i in 0..3) sum += (dst[i].toInt() and 0xFF)
        sum += protocol
        sum += length
        return sum
    }
}
