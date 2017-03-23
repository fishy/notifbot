package com.yhsif.notifbot

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.NameNotFoundException
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.support.v4.app.RemoteInput

import scala.collection.JavaConversions
import scala.collection.immutable.Set

object NotificationListener {
  val ReplyAction = "com.yhsif.notifbot.ACTION_REPLY" // not really used
  val ReplyKey = "com.yhsif.notifbot.KEY_REPLY" // not really used

  var connected = false
  var startMain = false

  def getPackageName(ctx: Context, pkg: String, empty: Boolean): String = {
    val manager = ctx.getPackageManager()
    try {
      Option(manager.getApplicationInfo(pkg, 0)).foreach { appInfo =>
        Option(manager.getApplicationLabel(appInfo)).foreach { s =>
          return s.toString()
        }
      }
    } catch {
      case _: NameNotFoundException =>
    }
    if (empty) {
      ""
    } else {
      pkg
    }
  }

  def getPkgSet(ctx: Context): Set[String] = {
    val pref = ctx.getSharedPreferences(MainActivity.Pref, 0)
    val javaSet = pref.getStringSet(
      MainActivity.KeyPkgs,
      JavaConversions.setAsJavaSet(Set.empty))
    return Set.empty ++ JavaConversions.asScalaSet(javaSet)
  }
}

class NotificationListener extends NotificationListenerService {
  import NotificationListener.ReplyAction
  import NotificationListener.ReplyKey
  import NotificationListener.connected
  import NotificationListener.startMain

  val PkgSelf = "com.yhsif.notifbot"

  var lastId: Int = 0

  override def onListenerConnected(): Unit = {
    connected = true
    if (startMain) {
      startActivity(new Intent(this, classOf[MainActivity]))
    }
  }

  override def onListenerDisconnected(): Unit = {
    connected = false
  }

  override def onNotificationPosted(
      sbn: StatusBarNotification,
      rm: NotificationListenerService.RankingMap): Unit = {
    handleNotif(NotificationListener.getPkgSet(this), sbn)
  }

  def handleNotif(pkgs: Set[String], sbn: StatusBarNotification): Unit = {
    if (!connected) {
      return
    }
    val pkg = sbn.getPackageName().toLowerCase()
    if (checkPackage(pkgs, pkg, sbn)) {
      val notif = sbn.getNotification()
      val key = sbn.getKey()
      val label = NotificationListener.getPackageName(this, pkg, true)
      val text = Option(notif.tickerText) match {
        case Some(s) => s.toString()
        case None => ""
      }

      // TODO
    }
  }

  def checkPackage(
      pkgs: Set[String],
      pkg: String,
      sbn: StatusBarNotification): Boolean =
    pkg != PkgSelf && pkgs(pkg) && !sbn.isOngoing()
}
