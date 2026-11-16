package ru.franh.modemmon.data

import org.json.JSONObject

/** Туннели WireGuard и AmneziaWG через объекты luci.wireguard / luci.amneziawg. */
object Vpn {

    data class Peer(
        val name: String,
        val publicKey: String,
        val endpoint: String,
        val allowedIps: List<String>,
        val handshake: Long,
        val rx: Long,
        val tx: Long
    )

    data class Instance(
        val name: String,
        val running: Boolean,
        val listenPort: String,
        val publicKey: String,
        val peers: List<Peer>
    )

    /**
     * @param kind "wg" — WireGuard, "awg" — AmneziaWG
     */
    fun instances(baseUrl: String, token: String, kind: String): Pair<List<Instance>, String?> {
        val obj = if (kind == "awg") "luci.amneziawg" else "luci.wireguard"
        val method = if (kind == "awg") "getAwgInstances" else "getWgInstances"
        val reply = Ubus.call(baseUrl, token, obj, method, JSONObject(), 15_000)
        if (!reply.ok) return emptyList<Instance>() to reply.error
        val d = reply.data ?: return emptyList<Instance>() to "Пустой ответ"

        val list = d.keys().asSequence().mapNotNull { key ->
            val o = d.optJSONObject(key) ?: return@mapNotNull null
            val peersArr = o.optJSONArray("peers")
            val peers = if (peersArr == null) emptyList() else (0 until peersArr.length()).mapNotNull { i ->
                val p = peersArr.optJSONObject(i) ?: return@mapNotNull null
                val ips = p.optJSONArray("allowed_ips")
                Peer(
                    name = p.optString("name").ifBlank { "peer" },
                    publicKey = p.optString("public_key"),
                    endpoint = p.optString("endpoint").ifBlank { "—" },
                    allowedIps = if (ips == null) emptyList()
                    else (0 until ips.length()).map { ips.optString(it) },
                    handshake = p.optString("latest_handshake").toLongOrNull() ?: 0L,
                    rx = p.optString("transfer_rx").toLongOrNull() ?: 0L,
                    tx = p.optString("transfer_tx").toLongOrNull() ?: 0L
                )
            }
            Instance(
                name = o.optString("name").ifBlank { key },
                running = o.optBoolean("running", true),
                listenPort = o.optString("listen_port").ifBlank { "—" },
                publicKey = o.optString("public_key"),
                peers = peers
            )
        }.toList()
        return list to null
    }
}
