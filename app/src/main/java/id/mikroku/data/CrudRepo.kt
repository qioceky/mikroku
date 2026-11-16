package id.mikroku.data

import id.mikroku.api.MikrotikClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Operasi tulis generik (v0.4): tambah / ubah / hapus entri di path API mana pun.
 * [attrs] berisi atribut RouterOS tanpa awalan '=' (mis. "address" to "10.0.0.1/24").
 * Semua aksi ini perlu policy write pada user API.
 */
object CrudRepo {

    suspend fun add(client: MikrotikClient, path: String, attrs: Map<String, String>) =
        withContext(Dispatchers.IO) { synchronized(client) {
            val words = mutableListOf("$path/add")
            for ((k, v) in attrs) words.add("=$k=$v")
            client.runCommand(*words.toTypedArray())
        } }

    suspend fun set(client: MikrotikClient, path: String, id: String, attrs: Map<String, String>) =
        withContext(Dispatchers.IO) { synchronized(client) {
            val words = mutableListOf("$path/set", "=.id=$id")
            for ((k, v) in attrs) words.add("=$k=$v")
            client.runCommand(*words.toTypedArray())
        } }

    suspend fun remove(client: MikrotikClient, path: String, id: String) =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.runCommand("$path/remove", "=.id=$id")
        } }

    /** Jadikan lease DHCP dinamis menjadi statis. */
    suspend fun makeLeaseStatic(client: MikrotikClient, leaseId: String) =
        withContext(Dispatchers.IO) { synchronized(client) {
            client.runCommand("/ip/dhcp-server/lease/make-static", "=.id=$leaseId")
        } }
}
