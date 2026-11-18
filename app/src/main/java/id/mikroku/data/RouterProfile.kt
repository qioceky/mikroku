package id.mikroku.data

import id.mikroku.api.MikrotikClient
import org.json.JSONObject
import java.util.UUID

/** Satu router yang disimpan pengguna. Password hanya ada di penyimpanan terenkripsi. */
data class RouterProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int,
    val useSsl: Boolean,
    val username: String,
    val password: String,
    val note: String = "",
    /** Sidik jari sertifikat api-ssl (TOFU); null bila belum pernah konek SSL. */
    val certFingerprint: String? = null,
    /** Sidik jari kunci host SSH untuk SFTP (TOFU); null bila belum pernah transfer. */
    val sshFingerprint: String? = null
) {
    val defaultPort: Int
        get() = if (useSsl) MikrotikClient.DEFAULT_PORT_SSL else MikrotikClient.DEFAULT_PORT_PLAIN

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("host", host)
        put("port", port)
        put("useSsl", useSsl)
        put("username", username)
        put("password", password)
        put("note", note)
        if (certFingerprint != null) put("certFingerprint", certFingerprint)
        if (sshFingerprint != null) put("sshFingerprint", sshFingerprint)
    }

    companion object {
        fun fromJson(o: JSONObject): RouterProfile = RouterProfile(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name", "Router"),
            host = o.optString("host", ""),
            port = o.optInt("port", MikrotikClient.DEFAULT_PORT_PLAIN),
            useSsl = o.optBoolean("useSsl", false),
            username = o.optString("username", "admin"),
            password = o.optString("password", ""),
            note = o.optString("note", ""),
            certFingerprint = if (o.has("certFingerprint")) o.optString("certFingerprint") else null,
            sshFingerprint = if (o.has("sshFingerprint")) o.optString("sshFingerprint") else null
        )
    }
}
