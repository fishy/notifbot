package com.yhsif.notifbot

import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Html
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yhsif.notifbot.settings.SettingsActivity
import kotlin.text.Regex

class MainActivity :
  AppCompatActivity(),
  View.OnClickListener,
  TextView.OnEditorActionListener {

  companion object {
    const val PREF = "com.yhsif.notifbot"
    const val KEY_PKGS = "packages"
    const val KEY_SERVICE_URL = "service"
    const val SERVICE_HOST = "notifbot.fishy.me"
    const val SERVICE_HOST_2 = "notification-bot.appspot.com"
    const val PREFIX_APP_ID = "market://details?id="

    // For google play url.
    // The URL should be either
    // http(s)://play.google.com/store/apps/details?id=<package_name>
    // or
    // market://details?id=<package_name>
    const val PLAY_HOST = "play.google.com"

    const val QUERY_ID = "id"
    const val SCHEME_MARKET = "market"

    const val SCHEME_HTTP = "http"
    const val SCHEME_HTTPS = "https"

    val TELEGRAM_URI = Uri.parse("https://t.me/AndroidNotificationBot?start=0")
    val RE_URI = Regex("""[^\s]+://[^\s]+""")
    val RE_APP_ID = Regex("""[a-zA-Z0-9\._-]+""")

    lateinit var serviceDialog: AlertDialog

    fun showToast(ctx: Context, text: String) {
      val msg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        text
      } else {
        ctx.getString(
          R.string.toast_text_template,
          ctx.getString(R.string.app_name),
          text,
        )
      }
      Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
    }

    fun handleTextPackage(ctx: Context, text: String): Boolean {
      try {
        val uri = Uri.parse(text)
        val valid = when (uri.getScheme()) {
          SCHEME_MARKET -> true
          SCHEME_HTTPS, SCHEME_HTTP -> uri.getHost() == PLAY_HOST
          else -> false
        }
        if (!valid) {
          return false
        }
        val pkg = uri.getQueryParameter(QUERY_ID)
        if (pkg != null) {
          addPackage(ctx, pkg)
          return true
        } else {
          return false
        }
      } catch (_: Throwable) {
        return false
      }
    }

    fun addPackage(ctx: Context, pkg: String) {
      val name = NotificationListener.getPackageName(ctx, pkg, false)
      val pkgSet = NotificationListener.getPkgSet(ctx)

      if (pkgSet.contains(pkg)) {
        showToast(ctx, ctx.getString(R.string.receiver_pkg_exists, name))
        return
      }
      val mutableSet = pkgSet.toMutableSet()
      mutableSet.add(pkg)
      ctx.getSharedPreferences(PREF, 0).edit {
        putStringSet(KEY_PKGS, mutableSet)
      }
      showToast(ctx, ctx.getString(R.string.receiver_added_pkg, name))
    }

    fun illegalText(ctx: Context, text: String) {
      showToast(ctx, ctx.getString(R.string.receiver_wrong_text, text))
    }

    fun handleTextService(ctx: Context, uri: Uri): Boolean {
      if (uri.getHost() != SERVICE_HOST && uri.getHost() != SERVICE_HOST_2) {
        return false
      }
      val url = "$SCHEME_HTTPS://${SERVICE_HOST}${uri.getPath()}"
      HttpSender.send(
        ctx,
        url,
        ctx.getString(R.string.app_name),
        ctx.getString(R.string.service_succeed),
        {
          NotificationListener.cancelTelegramNotif(ctx)
          showToast(ctx, ctx.getString(R.string.service_succeed))
          ctx.getSharedPreferences(PREF, 0).edit {
            putString(KEY_SERVICE_URL, url)
          }
          serviceDialog.let { d ->
            if (d.isShowing()) {
              d.dismiss()
            }
          }
        },
        {
          AlertDialog.Builder(ctx)
            .setCancelable(true)
            .setIcon(R.mipmap.icon)
            .setTitle(ctx.getString(R.string.service_failed_title))
            .setMessage(
              ctx.getString(
                R.string.service_failed_text,
                ctx.getString(android.R.string.ok),
              ),
            )
            .setPositiveButton(
              android.R.string.ok,
              DialogInterface.OnClickListener() { dialog, _ ->
                dialog.dismiss()
                ctx.startActivity(Intent(Intent.ACTION_VIEW, TELEGRAM_URI))
              },
            )
            .create()
            .show()
        },
        { showToast(ctx, ctx.getString(R.string.service_net_fail)) },
      )
      return true
    }

    fun handleText(ctx: Context, text: String?): Boolean {
      if (text == null) {
        return false
      }
      var msg = text
      val uri = RE_URI.find(text)?.value
      if (uri != null) {
        msg = uri
        if (handleTextPackage(ctx, uri)) {
          return true
        }
        try {
          if (handleTextService(ctx, Uri.parse(uri))) {
            return true
          }
        } catch (_: Throwable) {
          // do nothing
        }
      } else if (RE_APP_ID.matches(text)) {
        if (handleTextPackage(ctx, PREFIX_APP_ID + text)) {
          return true
        }
      }
      showToast(ctx, ctx.getString(R.string.magic_invalid_uri, msg))
      return false
    }

    fun fromHtmlWrapper(text: String): Spanned {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        return Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY)
      } else {
        @Suppress("DEPRECATION")
        return Html.fromHtml(text)
      }
    }

    fun tryClip(ctx: Context) {
      val clipboard =
        ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
      clipboard.getPrimaryClip()?.getItemAt(0)?.let { item ->
        val text = item.coerceToText(ctx).toString()
        RE_URI.find(text)?.value?.let { uri ->
          try {
            handleTextService(ctx, Uri.parse(uri))
          } catch (_: Throwable) {
            // do nothing
          }
        }
      }
    }
  }

  var prev: Set<String> = setOf()

  lateinit var adapter: PkgAdapter
  lateinit var magicDialog: AlertDialog

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    HttpSender.initEngine(this)

    setContentView(R.layout.main)
    findViewById<TextView>(R.id.hint).let { tv ->
      tv.setText(fromHtmlWrapper(getString(R.string.main_hint)))
      tv.setMovementMethod(LinkMovementMethod.getInstance())
    }

    adapter = PkgAdapter(mutableListOf(), this)
    findViewById<RecyclerView>(R.id.pkg_list).let { rv ->
      adapter.let { a ->
        rv.setAdapter(a)
      }
      rv.setLayoutManager(LinearLayoutManager(this))
    }

    val view = getLayoutInflater().inflate(R.layout.magic, null)
    view.findViewById<TextView>(R.id.magic_text).let { tv ->
      tv.setText(fromHtmlWrapper(getString(R.string.magic_text)))
      tv.setMovementMethod(LinkMovementMethod.getInstance())
    }
    view.findViewById<View>(R.id.go).setOnClickListener(this)
    view.findViewById<EditText>(R.id.magic_url).let { et ->
      et.setOnEditorActionListener(this)
      et.setImeActionLabel(getString(R.string.go), KeyEvent.KEYCODE_ENTER)
    }

    magicDialog = AlertDialog.Builder(this)
      .setTitle(R.string.magic_box)
      .setView(view)
      .create()

    serviceDialog = AlertDialog.Builder(this)
      .setCancelable(true)
      .setIcon(R.mipmap.icon)
      .setTitle(getString(R.string.no_service))
      .setMessage(
        getString(
          R.string.init_service_text,
          getString(android.R.string.ok),
        ),
      )
      .setPositiveButton(
        android.R.string.ok,
        DialogInterface.OnClickListener() { dialog, _ ->
          dialog.dismiss()
          startActivity(Intent(Intent.ACTION_VIEW, TELEGRAM_URI))
        },
      )
      .create()

    setSupportActionBar(findViewById<Toolbar>(R.id.app_bar))
  }

  override fun onResume() {
    var checkService = true

    // Handle notification-bot.appspot.com URL
    getIntent()?.let { intent ->
      if (intent.getAction() == Intent.ACTION_VIEW) {
        intent.getData()?.let { uri ->
          val valid = handleTextService(this, uri)
          checkService = !valid
          if (!valid) {
            // Pass it along
            startActivity(Intent(Intent.ACTION_VIEW, uri))
          }
        }
      }
    }

    // Check the listener service status
    if (!NotificationListener.connected) {
      val name = getString(R.string.app_name)
      val onCancel: (DialogInterface) -> Unit = { dialog ->
        dialog.dismiss()
        NotificationListener.startMain = false
        finish()
      }
      AlertDialog.Builder(this)
        .setCancelable(true)
        .setIcon(R.mipmap.icon)
        .setTitle(getString(R.string.perm_title, name))
        .setMessage(getString(R.string.perm_text, name))
        .setNegativeButton(
          R.string.perm_no,
          DialogInterface.OnClickListener() { dialog, _ ->
            onCancel(dialog)
          },
        )
        .setPositiveButton(
          R.string.perm_yes,
          DialogInterface.OnClickListener() { dialog, _ ->
            dialog.dismiss()
            NotificationListener.startMain = true
            startActivity(
              Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
            )
          },
        )
        .setOnCancelListener(
          DialogInterface.OnCancelListener() { dialog ->
            onCancel(dialog)
          },
        )
        .create()
        .show()
    } else if (checkService) {
      // Check service url
      val pref = getSharedPreferences(PREF, 0)
      var url = pref.getString(KEY_SERVICE_URL, "")!!
      val onFailure = {
        serviceDialog.show()
        tryClip(this)
      }
      var uri = Uri.parse(url)
      var onSuccess = {}
      if (uri.getHost() == SERVICE_HOST_2) {
        uri = uri.buildUpon().authority(SERVICE_HOST).build()
        url = uri.toString()
        onSuccess = {
          pref.edit {
            putString(KEY_SERVICE_URL, url)
          }
        }
      }
      if (uri.getScheme() == SCHEME_HTTPS && uri.getHost() == SERVICE_HOST) {
        HttpSender.checkUrl(url, onSuccess, onFailure)
      } else {
        onFailure()
      }
    }

    refreshData()
    super.onResume()
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    getMenuInflater().inflate(R.menu.main, menu)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    when (item.getItemId()) {
      R.id.action_settings ->
        startActivity(Intent(this, SettingsActivity::class.java))
      R.id.action_box ->
        magicDialog.show()
      else -> return super.onOptionsItemSelected(item)
    }
    return true
  }

  // for View.OnClickListener
  override fun onClick(v: View) {
    if (v.getId() == R.id.go) {
      doMagicGo()
      return
    }
    findViewById<RecyclerView>(R.id.pkg_list).let { rv ->
      val i = rv.getChildLayoutPosition(v)
      adapter.let { a ->
        val data = a.list.get(i)
        AlertDialog.Builder(this)
          .setCancelable(true)
          .setIcon(data.icon)
          .setTitle(R.string.dialog_title)
          .setMessage(
            getString(
              R.string.dialog_text,
              data.name,
              getString(R.string.app_name),
            ),
          )
          .setNegativeButton(
            R.string.dialog_no,
            DialogInterface.OnClickListener() { dialog, _ ->
              dialog.dismiss()
            },
          )
          .setPositiveButton(
            R.string.dialog_yes,
            DialogInterface.OnClickListener() { dialog, _ ->
              removePkg(data.pkg)
              a.remove(i)
              dialog.dismiss()
            },
          )
          .create()
          .show()
      }
    }
  }

  // for TextView.OnEditorActionListener
  override fun onEditorAction(v: TextView?, id: Int, ev: KeyEvent?): Boolean {
    when (id) {
      EditorInfo.IME_ACTION_GO -> {
        doMagicGo()
        return true
      }
      else -> return false
    }
  }

  fun doMagicGo() {
    magicDialog.let { d ->
      d.findViewById<EditText>(R.id.magic_url).let { text ->
        if (d.isShowing() && handleText(this, text?.getText().toString())) {
          d.dismiss()
          text?.setText("")
          refreshData()
        }
      }
    }
  }

  fun removePkg(pkg: String) {
    val pkgSet = NotificationListener.getPkgSet(this).toMutableSet()
    pkgSet.remove(pkg)
    getSharedPreferences(PREF, 0).edit {
      putStringSet(KEY_PKGS, pkgSet)
    }
  }

  fun refreshData() {
    val pkgSet = NotificationListener.getPkgSet(this)
    if (pkgSet != prev) {
      prev = pkgSet
      val pm = getPackageManager()
      val defIcon = getDrawable(R.mipmap.default_icon)!!
      adapter.let { a ->
        a.list = prev
          .map() { createPkgData(pm, it, defIcon) }
          .sortedBy() { it.name.lowercase() }
          .toMutableList()
        a.notifyDataSetChanged()
      }
    }
  }

  fun createPkgData(
    pm: PackageManager,
    pkg: String,
    defIcon: Drawable,
  ): PkgData {
    var name = pkg
    var icon = defIcon
    try {
      pm.getApplicationInfo(pkg, 0).let { appInfo ->
        pm.getApplicationLabel(appInfo).let { s ->
          name = s.toString()
        }
        icon = pm.getApplicationIcon(appInfo)
      }
    } catch (_: NameNotFoundException) {
      // do nothing
    }
    return PkgData(icon, name, pkg)
  }
}
