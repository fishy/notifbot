package com.yhsif.autonatif

import android.app.PendingIntent
import android.app.usage.UsageStatsManager
import android.app.usage.UsageStatsManager.INTERVAL_DAILY
import android.content.Context.USAGE_STATS_SERVICE
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationCompat.CarExtender
import android.support.v4.app.NotificationCompat.CarExtender.UnreadConversation
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.app.RemoteInput

import scala.collection.JavaConverters._
import scala.collection.mutable.Map

class NotificationListener extends NotificationListenerService {
  val Tag = "AutoNotif"

  val PkgSelf = "com.yhsif.autonotif"
  val PkgAndroidAuto = "com.google.android.projection.gearhead"
  val PkgSet = Set("com.smartthings.android")

  val ReplyAction = "com.yhsif.autonotif.ACTION_REPLY" // not used
  val ReplyKey = "com.yhsif.autonorif.KEY_REPLY" // not used

  val UsageTimeframe = 24 * 60 * 60 * 1000 // 24 hours

  var connected = false
  var lastId: Int = 0
  var notifMap: Map[String, Int] = Map()

  override def onListenerConnected(): Unit = {
    connected = true
    for (notif <- getActiveNotifications()) {
      handleNotif(notif)
    }
  }

  override def onNotificationPosted(sbn: StatusBarNotification, rm: NotificationListenerService.RankingMap): Unit = {
    handleNotif(sbn)
  }

  override def onNotificationRemoved(sbn: StatusBarNotification): Unit = {
    cancelNotif(sbn)
  }

  def handleNotif(sbn: StatusBarNotification): Unit = {
    val manager = getPackageManager()
    if (connected && isInAndroidAuto) {
      val pkg = sbn.getPackageName().toLowerCase()
      if (checkPackage(pkg, sbn)) {
        val notif = sbn.getNotification()
        val key = sbn.getKey()
        val appInfo = manager.getApplicationInfo(pkg, 0)
        val label: String = Option(manager.getApplicationLabel(appInfo)) match {
          case Some(s) => s.toString()
          case None => ""
        }
        val text: String = Option(notif.tickerText) match {
          case Some(s) => s.toString()
          case None => ""
        }

        if (text != "") {
          val replyIntent: Intent = new Intent().setAction(ReplyAction)
          val replyPendingIntent: PendingIntent = PendingIntent.getBroadcast(
            this, 0, replyIntent, PendingIntent.FLAG_UPDATE_CURRENT)

          val remoteInput: RemoteInput = new RemoteInput.Builder(ReplyKey)
            .setLabel(getString(R.string.notif_reply))
            .build()

          val convBuilder = new UnreadConversation.Builder(label)
            .setReplyAction(replyPendingIntent, remoteInput)
            .addMessage(text)
            .setLatestTimestamp(System.currentTimeMillis())

          val notifBuilder = new NotificationCompat.Builder(this)
            .setSmallIcon(R.drawable.icon_notif)
            .setContentText(text)
            .extend(new CarExtender().setUnreadConversation(convBuilder.build()))

          getBitmap(Option(notif.getLargeIcon()), Option(notif.getSmallIcon())) match {
            case Some(bitmap) =>
              notifBuilder.setLargeIcon(bitmap)
            case None => ()
          }

          lastId = lastId + 1
          notifMap(key) = lastId
          NotificationManagerCompat.from(this).notify(lastId, notifBuilder.build())
        }
      }
    }
  }

  def cancelNotif(sbn: StatusBarNotification): Unit = {
    val manager = getPackageManager()
    if (connected) {
      val pkg = sbn.getPackageName().toLowerCase()
      if (checkPackage(pkg, sbn)) {
        val key = sbn.getKey()
        notifMap.get(key) match {
          case Some(id) =>
            NotificationManagerCompat.from(this).cancel(id)
          case None => ()
        }
      }
    }
  }

  def checkPackage(pkg: String, sbn: StatusBarNotification): Boolean = {
    // pkg != PkgSelf
    PkgSet(pkg) && !sbn.isOngoing()
  }

  def getBitmap(large: Option[Icon], small: Option[Icon]): Option[Bitmap] = {
    large match {
      case Some(icon) => convertIconToBitmap(icon)
      case None =>
        small match {
          case Some(icon) => convertIconToBitmap(icon)
          case None => None
        }
    }
  }

  def convertIconToBitmap(icon: Icon): Option[Bitmap] = {
    val drawable = icon.loadDrawable(this)

    if (drawable.isInstanceOf[BitmapDrawable]) {
      Option(drawable.asInstanceOf[BitmapDrawable].getBitmap()) match {
        case Some(bitmap) =>
          return Option(bitmap)
        case None => ()
      }
    }

    var bitmap: Option[Bitmap] = None
    if (drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
      bitmap = Option(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
    } else {
      bitmap = Option(Bitmap.createBitmap(
        drawable.getIntrinsicWidth(),
        drawable.getIntrinsicHeight(),
        Bitmap.Config.ARGB_8888))
    }

    bitmap match {
      case Some(bmp) => {
        val canvas = new Canvas(bmp)
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight())
        drawable.draw(canvas)
        return Option(bmp)
      }
      case None => None
    }
  }

  def isInAndroidAuto(): Boolean = {
    // Temporarily disable this check
    return true

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
      case Some(pkg) =>
        return pkg == PkgAndroidAuto
      case None =>
        // This is most likely that we don't have the permission.
        // In this case, always assume we are in Android Auto.
        return true
    }
  }
}
