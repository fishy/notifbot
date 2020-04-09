package com.yhsif.notifbot

import android.app.PendingIntent
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.NameNotFoundException
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

import java.security.SecureRandom
import java.util.concurrent.locks.ReentrantLock

import kotlin.concurrent.withLock
import kotlin.text.Regex

class NotificationListener : NotificationListenerService() {
  companion object {
    private const val PKG_SELF = "com.yhsif.notifbot"
    private const val NOTIF_ID = 0
    private const val NOTIF_TEXT_TEMPLATE = "%s:\n%s"
    private const val PREF_RETRY = "com.yhsif.notifbot.retries"
    private const val PREF_LAST = "com.yhsif.notifbot.last"
    private const val KEY_LABEL = "label"
    private const val KEY_TEXT = "text"
    private const val MAX_RANDOM_INT = 1000000
    private const val CHANNEL_ID = "service_connection_failure"

    private val RE_KEY = Regex("""(\d+)-(.+)-(\d+)""")

    var connected = false
    var startMain = false

    var ctx: Context? = null
    val channelId: String by lazy {
      // Lazy create the notification channel
      ctx?.let { ctx ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          val channel = NotificationChannel(
            CHANNEL_ID,
            ctx.getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
          )
          channel.setDescription(ctx.getString(R.string.channel_desc))
          channel.setShowBadge(false)
          val manager = ctx.getSystemService(
            Context.NOTIFICATION_SERVICE) as NotificationManager
          manager.createNotificationChannel(channel)
        }
      }
      CHANNEL_ID
    }

    fun getPackageName(ctx: Context, pkg: String, empty: Boolean): String {
      val manager = ctx.getPackageManager()
      try {
        manager.getApplicationInfo(pkg, 0).let { appInfo ->
          manager.getApplicationLabel(appInfo).let { s ->
            return s.toString()
          }
        }
      } catch (_: NameNotFoundException) {
        // do nothing
      }
      if (empty) {
        return ""
      } else {
        return pkg
      }
    }

    fun getPkgSet(ctx: Context): Set<String> {
      val pref = ctx.getSharedPreferences(MainActivity.PREF, 0)
      return pref.getStringSet(MainActivity.KEY_PKGS, setOf())!!
    }

    fun cancelTelegramNotif(ctx: Context) {
      NotificationManagerCompat.from(ctx).cancel(NOTIF_ID)
    }

    fun getFirstString(extras: Bundle, vararg keys: String): String {
      for (key in keys) {
        val text = extras.getCharSequence(key)
        if (text != null) {
          return text.toString()
        }
      }
      return ""
    }

    fun getNotifText(notif: Notification): String {
      val title = getFirstString(
        notif.extras,
        Notification.EXTRA_TITLE,
        Notification.EXTRA_TITLE_BIG
      )
      val text = getFirstString(
        notif.extras,
        Notification.EXTRA_BIG_TEXT,
        Notification.EXTRA_TEXT,
        Notification.EXTRA_SUMMARY_TEXT,
        Notification.EXTRA_SUB_TEXT,
        Notification.EXTRA_INFO_TEXT
      )
      if (title == "") {
        return text
      }
      if (text == "") {
        return title
      }
      return NOTIF_TEXT_TEMPLATE.format(title, text)
    }
  }

  val retryQueueLock = ReentrantLock()
  val dupCheckLock = ReentrantLock()
  val rand = SecureRandom()

  val onSuccess = { NotificationListener.cancelTelegramNotif(this) }
  val onFailure = {
    val intent = Intent(Intent.ACTION_VIEW, MainActivity.TELEGRAM_URI)
    ctx = this
    val notifBuilder = NotificationCompat.Builder(this, channelId)
      .setSmallIcon(R.drawable.icon_notif)
      .setCategory(Notification.CATEGORY_ERROR)
      .setContentTitle(getString(R.string.no_service))
      .setContentText(getString(R.string.notif_text))
      .setAutoCancel(true)
      .setContentIntent(PendingIntent.getActivity(this, 0, intent, 0))
      .setVisibility(Notification.VISIBILITY_PUBLIC)
    NotificationManagerCompat.from(this).notify(NOTIF_ID, notifBuilder.build())
  }

  var lastId: Int = 0
  val monitor: NetworkMonitor by lazy {
    val m = NetworkMonitor(this)
    m.enable()
    m
  }

  override fun onListenerConnected() {
    connected = true
    monitor
    if (startMain) {
      startActivity(Intent(this, MainActivity::class.java))
    }
  }

  override fun onListenerDisconnected() {
    connected = false
    monitor
  }

  override fun onNotificationPosted(
    sbn: StatusBarNotification,
    rm: NotificationListenerService.RankingMap
  ) {
    handleNotif(NotificationListener.getPkgSet(this), sbn)
  }

  fun handleNotif(pkgs: Set<String>, sbn: StatusBarNotification) {
    if (!connected) {
      return
    }
    val pkg = sbn.getPackageName().toLowerCase()
    if (checkPackage(pkgs, pkg, sbn)) {
      val notif = sbn.getNotification()
      val label = NotificationListener.getPackageName(this, pkg, true)
      val text = NotificationListener.getNotifText(notif)
      if (label == "" || text == "") {
        return
      }
      if (checkDup(label, text)) {
        return
      }

      val pref = getSharedPreferences(MainActivity.PREF, 0)
      val url = pref.getString(MainActivity.KEY_SERVICE_URL, "")!!
      val onNetFail = {
        addToRetryQueue(System.currentTimeMillis(), label, text)
      }
      // Sanity check
      val uri = Uri.parse(url)
      if (uri.getScheme() == MainActivity.SCHEME_HTTPS &&
          uri.getHost() == MainActivity.SERVICE_HOST) {
        HttpSender.send(url, label, text, onSuccess, onFailure, onNetFail)
      } else {
        onFailure()
      }
    }
  }

  fun checkPackage(
    pkgs: Set<String>,
    pkg: String,
    sbn: StatusBarNotification
  ): Boolean = pkg != PKG_SELF && pkgs.contains(pkg) && !sbn.isOngoing()

  fun checkDup(label: String, text: String): Boolean {
    dupCheckLock.withLock {
      val pref = getSharedPreferences(PREF_LAST, 0)
      val lastLabel = pref.getString(KEY_LABEL, "")
      val lastText = pref.getString(KEY_TEXT, "")
      if (lastLabel == label && lastText == text) {
        return true
      }
      val editor = pref.edit()
      editor.clear()
      editor.putString(KEY_LABEL, label)
      editor.putString(KEY_TEXT, text)
      editor.commit()
    }
    return false
  }

  fun getAndClearRetryQueue(): List<Triple<Long, String, String>> {
    val map = retryQueueLock.withLock {
      val pref = getSharedPreferences(PREF_RETRY, 0)
      val map = pref.getAll()
      val editor = pref.edit()
      editor.clear()
      editor.commit()
      map
    }
    var result: MutableList<Triple<Long, String, String>> = mutableListOf()
    for ((k, v) in map.toSortedMap()) {
      RE_KEY.matchEntire(k)?.groupValues?.let { group ->
        val time = group.get(1)
        val label = group.get(2)
        result.add(Triple(time.toLong(), label, v as String))
      }
    }
    return result
  }

  fun addToRetryQueue(time: Long, label: String, text: String) {
    retryQueueLock.withLock {
      val pref = getSharedPreferences(PREF_RETRY, 0)
      val editor = pref.edit()
      var key = generateKey(time, label)
      while (pref.contains(key)) {
        key = generateKey(time, label)
      }
      editor.putString(key, text)
      editor.commit()
    }
  }

  fun generateKey(time: Long, label: String): String {
    return "%015d-%s-%d".format(time, label, rand.nextInt(MAX_RANDOM_INT))
  }

  fun retry() {
    val pref = getSharedPreferences(MainActivity.PREF, 0)
    val url = pref.getString(MainActivity.KEY_SERVICE_URL, "")!!
    // Sanity check
    val uri = Uri.parse(url)
    if (uri.getScheme() == MainActivity.SCHEME_HTTPS &&
        uri.getHost() == MainActivity.SERVICE_HOST) {
      for (tuple in getAndClearRetryQueue()) {
        val time = tuple.first
        val label = tuple.second
        val text = tuple.third
        val onNetFail = { addToRetryQueue(time, label, text) }
        HttpSender.send(url, label, text, onSuccess, onFailure, onNetFail)
      }
    } else {
      onFailure()
    }
  }
}
