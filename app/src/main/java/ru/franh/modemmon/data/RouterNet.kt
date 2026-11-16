package ru.franh.modemmon.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.net.HttpURLConnection
import java.net.URL

/**
 * Привязка запросов к Wi-Fi роутера.
 * Нужно, когда на телефоне включён VPN (Tailscale): обычный запрос уходит
 * в туннель и до 192.168.2.1 не доходит. С флагом берём именно Wi-Fi-сеть
 * и открываем сокет через неё, остальные приложения не трогаем.
 */
object RouterNet {

    @Volatile
    private var wifiOnly: Boolean = false

    fun setWifiOnly(enabled: Boolean) {
        wifiOnly = enabled
    }

    fun open(context: Context, url: URL): HttpURLConnection {
        if (!wifiOnly) return url.openConnection() as HttpURLConnection
        val wifi = wifi(context)
            ?: throw java.io.IOException("Wi-Fi не подключён — выключите «Только через Wi-Fi роутера»")
        return wifi.openConnection(url) as HttpURLConnection
    }

    private fun wifi(context: Context): Network? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.allNetworks.firstOrNull { net ->
            val caps = cm.getNetworkCapabilities(net) ?: return@firstOrNull false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        }
    }
}
