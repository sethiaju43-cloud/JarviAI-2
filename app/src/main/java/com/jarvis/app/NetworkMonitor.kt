package com.jarvis.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

object NetworkMonitor {

    private fun cm(ctx: Context) =
        ctx.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun isOnline(ctx: Context): Boolean {
        return try {
            val c = cm(ctx)
            val net = c.activeNetwork ?: return false
            val caps = c.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (e: Exception) {
            false
        }
    }

    /** Wi-Fi ya Ethernet (bade downloads ke liye mobile data se bachne ke liye) */
    fun isUnmetered(ctx: Context): Boolean {
        return try {
            val c = cm(ctx)
            val net = c.activeNetwork ?: return false
            val caps = c.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        } catch (e: Exception) {
            false
        }
    }

    /** Callback kisi background thread par aata hai. UI update ke liye runOnUiThread use karo. */
    fun register(ctx: Context, onChange: (Boolean) -> Unit): ConnectivityManager.NetworkCallback? {
        return try {
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = onChange(isOnline(ctx))
                override fun onLost(network: Network) = onChange(isOnline(ctx))
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
                    onChange(isOnline(ctx))
            }
            cm(ctx).registerDefaultNetworkCallback(cb)
            cb
        } catch (e: Exception) {
            null
        }
    }

    fun unregister(ctx: Context, cb: ConnectivityManager.NetworkCallback?) {
        if (cb == null) return
        try {
            cm(ctx).unregisterNetworkCallback(cb)
        } catch (e: Exception) {
        }
    }
}
