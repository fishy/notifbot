package com.yhsif.notifbot

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

import scala.collection.immutable.Set

class NetworkMonitor(val listener: NotificationListener)
    extends ConnectivityManager.NetworkCallback {
  val Req = new NetworkRequest.Builder()
    .addTransportType(NetworkCapabilities.TRANSPORT_BLUETOOTH)
    .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
    .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
    .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
    .build()

  def enable(): Unit = {
    listener
      .getSystemService(Context.CONNECTIVITY_SERVICE)
      .asInstanceOf[ConnectivityManager]
      .registerNetworkCallback(Req, this)
  }

  def disable(): Unit = {
    listener
      .getSystemService(Context.CONNECTIVITY_SERVICE)
      .asInstanceOf[ConnectivityManager]
      .unregisterNetworkCallback(this)
  }

  override def onAvailable(network: Network): Unit = {
    listener.retry()
  }
}
