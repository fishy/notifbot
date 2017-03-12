package com.yhsif.autonatif

import android.app.usage.UsageStatsManager
import android.app.usage.UsageStatsManager.INTERVAL_DAILY
import android.content.Context.USAGE_STATS_SERVICE
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationCompat.CarExtender
import android.support.v4.app.NotificationCompat.CarExtender.UnreadConversation
import android.support.v4.app.NotificationManagerCompat
import android.util.Log

import scala.collection.JavaConverters._

class NotificationListener extends NotificationListenerService {
  val Tag = "AutoNotif"

  val PkgSelf = "com.yhsif.autonotif"
  val PkgAndroidAuto = "com.google.android.projection.gearhead"
  val PkgSet = Set("com.smartthings.android")

  val UsageTimeframe = 24 * 60 * 60 * 1000 // 24 hours

  var connected = false

  override def onListenerConnected(): Unit = {
    connected = true
    for (notif <- getActiveNotifications()) {
      handleNotif(notif)
    }
  }

  override def onNotificationPosted(sbn: StatusBarNotification, rm: NotificationListenerService.RankingMap): Unit = {
    handleNotif(sbn)
  }

  def handleNotif(sbn: StatusBarNotification): Unit = {
    val manager = getPackageManager()
    if (connected && isInAndroidAuto) {
      val pkg = sbn.getPackageName().toLowerCase()
      Log.d(Tag, s"notification package name ${pkg}")
      if (pkg != PkgSelf) {
        if (PkgSet(pkg) && !sbn.isOngoing()) {
          val notif = sbn.getNotification()
          val key = sbn.getKey()
          val appInfo = manager.getApplicationInfo(pkg, 0)
          val label: String = Option(manager.getApplicationLabel(appInfo)) match {
            case Some(s) => s.toString()
            case None => ""
          }
          val text = notif.tickerText.toString()
          Log.d(Tag, s"key = ${key}, label = ${label}, text = ${text}")

          if (text != "") {
            val convBuilder = new UnreadConversation.Builder(label)
              .addMessage(notif.tickerText.toString())
              .setLatestTimestamp(System.currentTimeMillis())

            val notifBuilder = new NotificationCompat.Builder(this)
              .setSmallIcon(R.drawable.icon_notif)
              .setContentText(notif.tickerText.toString())
              .extend(new CarExtender().setUnreadConversation(convBuilder.build()))

            NotificationManagerCompat.from(this).notify(key, 0, notifBuilder.build())
          }
        }
      }
    }
  }

  def isInAndroidAuto(): Boolean = {
    val manager =
      getSystemService(USAGE_STATS_SERVICE).asInstanceOf[UsageStatsManager]
    val time = System.currentTimeMillis()
    val apps =
      manager.queryUsageStats(INTERVAL_DAILY, time - UsageTimeframe, time)
    var max: Long = 0
    var result: Option[String] = None
    if (apps != null) {
      for (app <- apps.asScala) {
        val pkg = app.getPackageName().toLowerCase()
        val timestamp = app.getLastTimeUsed()
        if ((pkg != PkgSelf) && (timestamp > max)) {
          max = timestamp
          result = Some(pkg)
        }
      }
    }
    result match {
      case Some(pkg) => {
        Log.d(Tag, s"Foreground app is ${pkg}")
        return pkg == PkgAndroidAuto
      }
      case None =>
        // This is most likely that we don't have the permission.
        // In this case, always assume we are in Android Auto.
        return true
    }
  }
}
