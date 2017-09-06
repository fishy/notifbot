package com.yhsif.notifbot

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

class NetworkMonitor(
    listener: NotificationListener
) : ConnectivityManager.NetworkCallback() {
  val listener = listener

  val Req = NetworkRequest.Builder()
    .addTransportType(NetworkCapabilities.TRANSPORT_BLUETOOTH)
    .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
    .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
    .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
    .build()

  fun enable() {
    val cm = listener.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    cm.registerNetworkCallback(Req, this)
  }

  fun disable() {
    val cm = listener.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    cm.unregisterNetworkCallback(this)
  }

  override fun onAvailable(network: Network?) {
    listener.retry()
  }
}
