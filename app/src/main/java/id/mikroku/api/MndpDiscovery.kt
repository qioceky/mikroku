package id.mikroku.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/** Satu router hasil penemuan MNDP di jaringan lokal. */
data class DiscoveredRouter(
    val ipAddress: String,
    val macAddress: String,
    val identity: String,
    val version: String,
    val board: String,
    val interfaceName: String,
    val uptimeSeconds: Long?
) {
    val key: String get() = "$macAddress@$ipAddress"
}

/**
 * Penemuan router MikroTik lewat MNDP (MikroTik Neighbor Discovery
 * Protocol) — protokol yang sama dengan yang dipakai WinBox.
 *
 * UDP port 5678, broadcast 255.255.255.255. Paket: header 4 byte lalu
 * rangkaian TLV (type uint16 big-endian, length uint16 big-endian, value).
 * Tipe penting: 1=MAC, 5=identity, 7=version, 8=platform, 10=uptime
 * (uint32 little-endian), 11=software-id, 12=board, 15=IPv6, 16=interface.
 * Alamat IPv4 diambil dari alamat sumber paket UDP.
 */
object MndpDiscovery {

    private const val MNDP_PORT = 5678

    suspend fun discover(timeoutMs: Int = 4_000): List<DiscoveredRouter> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, DiscoveredRouter>()
        DatagramSocket(MNDP_PORT).use { socket ->
            socket.broadcast = true
            socket.soTimeout = 500
            // Paket refresh 4 byte (header kosong) — semua RouterOS di LAN menjawab.
            val refresh = DatagramPacket(
                ByteArray(4), 4,
                InetAddress.getByName("255.255.255.255"), MNDP_PORT
            )
            repeat(2) {
                try { socket.send(refresh) } catch (_: Exception) {}
            }
            val buf = ByteArray(4096)
            val deadline = System.currentTimeMillis() + timeoutMs
            var resendAt = System.currentTimeMillis() + 1500
            while (System.currentTimeMillis() < deadline) {
                if (System.currentTimeMillis() >= resendAt) {
                    try { socket.send(refresh) } catch (_: Exception) {}
                    resendAt += 1500
                }
                val packet = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(packet)
                } catch (e: java.net.SocketTimeoutException) {
                    continue
                }
                val parsed = parse(packet) ?: continue
                found[parsed.key] = parsed
            }
        }
        found.values.sortedBy { it.identity.lowercase() }
    }

    private fun parse(packet: DatagramPacket): DiscoveredRouter? {
        val data = packet.data
        val len = packet.length
        if (len < 8) return null
        var mac = ""
        var identity = ""
        var version = ""
        var board = ""
        var iface = ""
        var uptime: Long? = null
        var off = 4 // lewati header 4 byte
        while (off + 4 <= len) {
            val type = ((data[off].toInt() and 0xFF) shl 8) or (data[off + 1].toInt() and 0xFF)
            val vlen = ((data[off + 2].toInt() and 0xFF) shl 8) or (data[off + 3].toInt() and 0xFF)
            off += 4
            if (off + vlen > len) break
            when (type) {
                1 -> if (vlen == 6) {
                    mac = (0 until 6).joinToString(":") { "%02X".format(data[off + it]) }
                }
                5 -> identity = String(data, off, vlen, Charsets.UTF_8).trim()
                7 -> version = String(data, off, vlen, Charsets.UTF_8).trim()
                10 -> if (vlen == 4) {
                    uptime = (data[off].toLong() and 0xFF) or
                        ((data[off + 1].toLong() and 0xFF) shl 8) or
                        ((data[off + 2].toLong() and 0xFF) shl 16) or
                        ((data[off + 3].toLong() and 0xFF) shl 24)
                }
                12 -> board = String(data, off, vlen, Charsets.UTF_8).trim()
                16 -> iface = String(data, off, vlen, Charsets.UTF_8).trim()
            }
            off += vlen
        }
        val ip = packet.address?.hostAddress ?: return null
        if (mac.isEmpty() && identity.isEmpty()) return null
        return DiscoveredRouter(
            ipAddress = ip,
            macAddress = mac,
            identity = identity.ifEmpty { ip },
            version = version,
            board = board,
            interfaceName = iface,
            uptimeSeconds = uptime
        )
    }
}
