package id.mikroku

import id.mikroku.data.RouterProfile
import id.mikroku.data.RouterQr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uji urai/bangun QR tambah router (fungsi murni, tanpa Android). */
class RouterQrTest {

    private fun profile(
        name: String = "Router Sekolah",
        host: String = "10.10.10.1",
        port: Int = 8729,
        useSsl: Boolean = true,
        username: String = "admin"
    ) = RouterProfile(
        name = name, host = host, port = port,
        useSsl = useSsl, username = username, password = "rahasia"
    )

    @Test
    fun roundtripFormatMikroKu() {
        val content = RouterQr.buildContent(profile())
        assertTrue(content.startsWith("mikroku://add?"))
        assertTrue("QR tidak boleh berisi password", !content.contains("rahasia"))
        val parsed = RouterQr.parse(content)!!
        assertEquals("Router Sekolah", parsed.name)
        assertEquals("10.10.10.1", parsed.host)
        assertEquals(8729, parsed.port)
        assertEquals("admin", parsed.username)
        assertEquals(true, parsed.useSsl)
    }

    @Test
    fun roundtripSpasiDanKarakterKhusus() {
        val content = RouterQr.buildContent(
            profile(name = "Router A&B =C", username = "ad min")
        )
        val parsed = RouterQr.parse(content)!!
        assertEquals("Router A&B =C", parsed.name)
        assertEquals("ad min", parsed.username)
    }

    @Test
    fun formatWinboxDiterima() {
        val parsed = RouterQr.parse("winbox://192.168.88.1/admin")!!
        assertEquals("192.168.88.1", parsed.host)
        assertEquals("admin", parsed.username)
        assertNull(parsed.port)
        assertNull(parsed.useSsl)
    }

    @Test
    fun formatWinboxDenganPort() {
        val parsed = RouterQr.parse("winbox://10.0.0.2:8729/operator")!!
        assertEquals("10.0.0.2", parsed.host)
        assertEquals(8729, parsed.port)
        assertEquals("operator", parsed.username)
    }

    @Test
    fun teksAsingDitolak() {
        assertNull(RouterQr.parse("https://example.com"))
        assertNull(RouterQr.parse("halo dunia"))
        assertNull(RouterQr.parse(""))
        assertNull(RouterQr.parse("mikroku://add?user=admin"))
    }
}
