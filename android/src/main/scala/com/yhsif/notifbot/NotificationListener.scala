package com.yhsif.notifbot

import android.app.PendingIntent
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.NameNotFoundException
import android.net.Uri
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat

import scala.collection.JavaConversions._
import scala.collection.immutable.Set
import scala.concurrent.Lock
import scala.util.Random

object NotificationListener {
  val NotifID = 0
  val NotifTextTemplate = "%s:\n%s"
  val RetryPref = "com.yhsif.notifbot.retries"
  val KeyRegexp = """(\d+)-(.+)-(\d+)""".r
  val MaxRandomInt = 1000000

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
    val javaSet =
      pref.getStringSet(MainActivity.KeyPkgs, setAsJavaSet(Set.empty))
    return Set.empty ++ asScalaSet(javaSet)
  }

  def cancelTelegramNotif(ctx: Context): Unit = {
    NotificationManagerCompat.from(ctx).cancel(NotifID)
  }

  def getFirstString(extras: Bundle, keys: String*): String = {
    keys.foreach { key =>
      {
        val text = Option(extras.getCharSequence(key)) match {
          case Some(s) => s.toString()
          case None => ""
        }
        if (text != "") {
          return text
        }
      }
    }
    return ""
  }

  def getNotifText(notif: Notification): String = {
    val title = getFirstString(
      notif.extras,
      Notification.EXTRA_TITLE,
      Notification.EXTRA_TITLE_BIG)
    val text = getFirstString(
      notif.extras,
      Notification.EXTRA_BIG_TEXT,
      Notification.EXTRA_TEXT,
      Notification.EXTRA_SUMMARY_TEXT,
      Notification.EXTRA_SUB_TEXT,
      Notification.EXTRA_INFO_TEXT)
    if (title == "") {
      return text
    }
    if (text == "") {
      return title
    }
    return String.format(NotifTextTemplate, title, text)
  }
}

class NotificationListener extends NotificationListenerService {
  import NotificationListener.NotifID
  import NotificationListener.RetryPref
  import NotificationListener.KeyRegexp
  import NotificationListener.MaxRandomInt

  import NotificationListener.connected
  import NotificationListener.startMain

  val PkgSelf = "com.yhsif.notifbot"
  val retryQueueLock = new Lock()
  val rand = new Random(System.currentTimeMillis)

  val onSuccess = () => NotificationListener.cancelTelegramNotif(this)
  val onFailure = () => {
    val intent = new Intent(Intent.ACTION_VIEW, MainActivity.TelegramUri)
    val notifBuilder = new NotificationCompat.Builder(this)
      .setSmallIcon(R.drawable.icon_notif)
      .setContentTitle(getString(R.string.no_service))
      .setContentText(getString(R.string.notif_text))
      .setAutoCancel(true)
      .setContentIntent(PendingIntent.getActivity(this, 0, intent, 0))
      .setVisibility(Notification.VISIBILITY_PUBLIC)
    NotificationManagerCompat.from(this).notify(NotifID, notifBuilder.build())
  }

  var lastId: Int = 0
  var monitor: Option[NetworkMonitor] = None

  override def onListenerConnected(): Unit = {
    connected = true
    initMonitor()
    if (startMain) {
      startActivity(new Intent(this, classOf[MainActivity]))
    }
  }

  override def onListenerDisconnected(): Unit = {
    connected = false
    initMonitor()
  }

  override def onNotificationPosted(
      sbn: StatusBarNotification,
      rm: NotificationListenerService.RankingMap): Unit = {
    handleNotif(NotificationListener.getPkgSet(this), sbn)
  }

  def initMonitor(): Unit = {
    this.synchronized {
      if (monitor == None) {
        val m = new NetworkMonitor(this)
        m.enable()
        monitor = Option(m)
      }
    }
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
      val text = NotificationListener.getNotifText(notif)
      if (label == "" || text == "") {
        return
      }

      val pref = getSharedPreferences(MainActivity.Pref, 0)
      val url = pref.getString(MainActivity.KeyServiceURL, "")
      val onNetFail =
        () => addToRetryQueue(System.currentTimeMillis, label, text)
      // Sanity check
      val uri = Uri.parse(url)
      if (uri.getScheme() == MainActivity.HttpsScheme &&
          uri.getHost() == MainActivity.ServiceHost) {
        HttpSender.send(url, label, text, onSuccess, onFailure, onNetFail)
      } else {
        onFailure()
      }
    }
  }

  def checkPackage(
      pkgs: Set[String],
      pkg: String,
      sbn: StatusBarNotification): Boolean =
    pkg != PkgSelf && pkgs(pkg) && !sbn.isOngoing()

  def getAndClearRetryQueue(): Seq[(Long, String, String)] = {
    retryQueueLock.synchronized {
      val pref = getSharedPreferences(RetryPref, 0)
      val javaMap = pref.getAll()
      var result = Vector[(Long, String, String)]()
      mapAsScalaMap(javaMap).toSeq.sortBy(_._1).foreach { kv =>
        kv._1 match {
          case KeyRegexp(time, label, _ *) =>
            result =
              result :+ Tuple3(time.toLong, label, kv._2.asInstanceOf[String])
        }
      }
      val editor = pref.edit()
      editor.clear()
      editor.commit()
      return result
    }
  }

  def addToRetryQueue(time: Long, label: String, text: String): Unit = {
    retryQueueLock.synchronized {
      val pref = getSharedPreferences(RetryPref, 0)
      val editor = pref.edit()
      var key = generateKey(time, label)
      while (pref.contains(key)) {
        key = generateKey(time, label)
      }
      editor.putString(key, text)
      editor.commit()
    }
  }

  def generateKey(time: Long, label: String): String = {
    "%015d-%s-%d".format(time, label, rand.nextInt(MaxRandomInt))
  }

  def retry(): Unit = {
    val pref = getSharedPreferences(MainActivity.Pref, 0)
    val url = pref.getString(MainActivity.KeyServiceURL, "")
    // Sanity check
    val uri = Uri.parse(url)
    if (uri.getScheme() == MainActivity.HttpsScheme &&
        uri.getHost() == MainActivity.ServiceHost) {
      getAndClearRetryQueue().foreach { tuple =>
        val time = tuple._1
        val label = tuple._2
        val text = tuple._3
        val onNetFail = () => addToRetryQueue(time, label, text)
        HttpSender.send(url, label, text, onSuccess, onFailure, onNetFail)
      }
    } else {
      onFailure()
    }
  }
}
