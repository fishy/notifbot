package com.yhsif.notifbot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.yhsif.notifbot.settings.SettingsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    private val RE_KEY4 = Regex("""(\d+?)-(.+?)-(\d+?)-(.+)""")
    private val RE_KEY3 = Regex("""(\d+?)-(.+?)-(\d+?)""")

    data class RetryTuple(
      val time: Long,
      val label: String,
      val key: String?,
      val text: String,
    )

    var connected = false
    var startMain = false

    lateinit var ctx: Context
    val channelId: String by lazy {
      // Lazy create the notification channel
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
          CHANNEL_ID,
          ctx.getString(R.string.channel_name),
          NotificationManager.IMPORTANCE_DEFAULT,
        )
        channel.setDescription(ctx.getString(R.string.channel_desc))
        channel.setShowBadge(false)
        val manager = ctx.getSystemService(
          Context.NOTIFICATION_SERVICE,
        ) as NotificationManager
        manager.createNotificationChannel(channel)
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
        Notification.EXTRA_TITLE_BIG,
      )
      val text = getFirstString(
        notif.extras,
        Notification.EXTRA_BIG_TEXT,
        Notification.EXTRA_TEXT,
        Notification.EXTRA_SUMMARY_TEXT,
        Notification.EXTRA_SUB_TEXT,
        Notification.EXTRA_INFO_TEXT,
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

  var lastId: Int = 0
  val monitor: NetworkMonitor by lazy {
    val m = NetworkMonitor(this)
    m.enable()
    m
  }

  val onFailure: () -> Unit = {
    GlobalScope.launch(Dispatchers.Main) {
      val intent = Intent(Intent.ACTION_VIEW, MainActivity.TELEGRAM_URI)
      ctx = this@NotificationListener
      val notifBuilder = NotificationCompat.Builder(ctx, channelId)
        .setSmallIcon(R.drawable.icon_notif)
        .setCategory(Notification.CATEGORY_ERROR)
        .setContentTitle(getString(R.string.no_service))
        .setContentText(getString(R.string.notif_text))
        .setAutoCancel(true)
        .setContentIntent(PendingIntent.getActivity(ctx, 0, intent, 0))
        .setVisibility(Notification.VISIBILITY_PUBLIC)
      NotificationManagerCompat.from(ctx).notify(NOTIF_ID, notifBuilder.build())
    }
  }

  override fun onListenerConnected() {
    connected = true
    monitor
    if (startMain) {
      val intent = Intent(this, MainActivity::class.java)
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      startActivity(intent)
    }
  }

  override fun onListenerDisconnected() {
    connected = false
    monitor
  }

  override fun onNotificationPosted(
    sbn: StatusBarNotification,
    rm: NotificationListenerService.RankingMap,
  ) {
    HttpSender.initEngine(this)
    handleNotif(NotificationListener.getPkgSet(this), sbn)
  }

  fun handleNotif(pkgs: Set<String>, sbn: StatusBarNotification) {
    if (!connected) {
      return
    }
    GlobalScope.launch(Dispatchers.Default) forReturn@{
      val pkg = sbn.getPackageName().toLowerCase()
      if (checkPackage(pkgs, pkg, sbn)) {
        val notif = sbn.getNotification()
        val label =
          NotificationListener.getPackageName(this@NotificationListener, pkg, true)
        val text = NotificationListener.getNotifText(notif)
        if (label == "" || text == "") {
          return@forReturn
        }
        if (checkDup(label, text)) {
          return@forReturn
        }

        val pref = getSharedPreferences(MainActivity.PREF, 0)
        val url = pref.getString(MainActivity.KEY_SERVICE_URL, "")!!
        val onNetFail = {
          addToRetryQueue(
            RetryTuple(
              System.currentTimeMillis(),
              label,
              sbn.getKey(),
              text,
            ),
          )
        }
        // Sanity check
        val uri = Uri.parse(url)
        if (
          uri.getScheme() == MainActivity.SCHEME_HTTPS &&
          uri.getHost() == MainActivity.SERVICE_HOST
        ) {
          HttpSender.send(
            this@NotificationListener,
            url,
            label,
            text,
            onSuccess(sbn.getKey()),
            onFailure,
            onNetFail,
          )
        }
      }
    }
  }

  fun checkPackage(
    pkgs: Set<String>,
    pkg: String,
    sbn: StatusBarNotification,
  ): Boolean = pkg != PKG_SELF && pkgs.contains(pkg) && !sbn.isOngoing()

  fun checkDup(label: String, text: String): Boolean {
    dupCheckLock.withLock {
      val pref = getSharedPreferences(PREF_LAST, 0)
      val lastLabel = pref.getString(KEY_LABEL, "")
      val lastText = pref.getString(KEY_TEXT, "")
      if (lastLabel == label && lastText == text) {
        return true
      }
      pref.edit {
        clear()
        putString(KEY_LABEL, label)
        putString(KEY_TEXT, text)
      }
    }
    return false
  }

  suspend fun getAndClearRetryQueue(): List<RetryTuple> {
    val map = retryQueueLock.withLock {
      val pref = getSharedPreferences(PREF_RETRY, 0)
      val map = pref.getAll()
      pref.edit {
        clear()
      }
      map
    }
    var result: MutableList<RetryTuple> = mutableListOf()
    for ((k, v) in map.toSortedMap()) {
      val group4 = RE_KEY4.matchEntire(k)?.groupValues
      if (group4 != null) {
        result.add(
          RetryTuple(
            group4.get(1).toLong(), // time
            group4.get(2), // label
            group4.get(4), // key
            v as String, // text
          ),
        )
      } else {
        RE_KEY3.matchEntire(k)?.groupValues?.let { group ->
          result.add(
            RetryTuple(
              group.get(1).toLong(), // time
              group.get(2), // label
              null, // key
              v as String, // text
            ),
          )
        }
      }
    }
    return result
  }

  fun addToRetryQueue(tuple: RetryTuple) {
    GlobalScope.launch(Dispatchers.Default) {
      retryQueueLock.withLock {
        val pref = getSharedPreferences(PREF_RETRY, 0)
        var key = generateKey(tuple.time, tuple.label, tuple.key)
        while (pref.contains(key)) {
          key = generateKey(tuple.time, tuple.label, tuple.key)
        }
        pref.edit {
          putString(key, tuple.text)
        }
      }
    }
  }

  fun generateKey(time: Long, label: String, key: String?): String =
    if (key != null) {
      "%015d-%s-%d-%s".format(time, label, rand.nextInt(MAX_RANDOM_INT), key)
    } else {
      "%015d-%s-%d".format(time, label, rand.nextInt(MAX_RANDOM_INT))
    }

  suspend fun retry() {
    withContext(Dispatchers.Default) {
      val pref = getSharedPreferences(MainActivity.PREF, 0)
      val url = pref.getString(MainActivity.KEY_SERVICE_URL, "")!!
      // Sanity check
      val uri = Uri.parse(url)
      if (
        uri.getScheme() == MainActivity.SCHEME_HTTPS &&
        uri.getHost() == MainActivity.SERVICE_HOST
      ) {
        for (tuple in getAndClearRetryQueue()) {
          val (_, label, key, text) = tuple
          val onNetFail = { addToRetryQueue(tuple) }
          HttpSender.send(
            this@NotificationListener,
            url,
            label,
            text,
            onSuccess(key),
            onFailure,
            onNetFail,
          )
        }
      } else {
        onFailure()
      }
    }
  }

  fun onSuccess(key: String? = null): () -> Unit {
    return {
      GlobalScope.launch(Dispatchers.Main) {
        NotificationListener.cancelTelegramNotif(this@NotificationListener)
        if (key != null && connected && dismissNotification()) {
          cancelNotification(key)
        }
      }
    }
  }

  fun dismissNotification(): Boolean =
    PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
      SettingsActivity.KEY_AUTO_DISMISS,
      SettingsActivity.DEFAULT_AUTO_DISMISS,
    )
}
