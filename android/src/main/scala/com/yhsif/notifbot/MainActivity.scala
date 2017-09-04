package com.yhsif.notifbot

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

import scala.collection.JavaConversions
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Set

object MainActivity {
  val Pref = "com.yhsif.notifbot"
  val KeyPkgs = "packages"
  val KeyServiceURL = "service"
  val ServiceHost = "notification-bot.appspot.com"
  val TelegramUri = Uri.parse("https://t.me/AndroidNotificationBot?start=0")

  val UriRegexp = """(?s).*?([^\s]+://[^\s]+).*""".r
  val AppIdRegexp = """[a-zA-Z0-9\._-]+""".r
  val AppIdPrefix = "market://details?id="

  val HttpScheme = "http"
  val HttpsScheme = "https"

  // For google play url.
  // The URL should be either
  // http(s)://play.google.com/store/apps/details?id=<package_name>
  // or
  // market://details?id=<package_name>
  val PlayHost = "play.google.com"
  val MarketScheme = "market"
  val IdQuery = "id"

  def showToast(ctx: Context, text: String): Unit = {
    val toast = Toast.makeText(ctx, text, Toast.LENGTH_LONG)
    val ov: Option[View] =
      Option(toast.getView().findViewById(android.R.id.message))
    ov.foreach { v =>
      // Put the icon on the right
      val view = v.asInstanceOf[TextView]
      view.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.mipmap.icon, 0)
      view.setCompoundDrawablePadding(
        ctx.getResources().getDimensionPixelSize(R.dimen.toast_padding))
    }
    toast.show()
  }

  def handleTextPackage(ctx: Context, text: String): Boolean = {
    try {
      val uri = Uri.parse(text)
      val valid = uri.getScheme() match {
        case MarketScheme => true
        case HttpsScheme | HttpScheme => uri.getHost() == PlayHost
        case _ => false
      }
      if (!valid) {
        return false
      }
      Option(uri.getQueryParameter(IdQuery)) match {
        case Some(pkg) => {
          addPackage(ctx, pkg)
          return true
        }
        case None => return false
      }
    } catch {
      case _: Throwable => return false
    }
  }

  def addPackage(ctx: Context, pkg: String): Unit = {
    val name = NotificationListener.getPackageName(ctx, pkg, false)
    val pkgSet = Set.empty ++= NotificationListener.getPkgSet(ctx)
    if (pkgSet(pkg)) {
      showToast(ctx, ctx.getString(R.string.receiver_pkg_exists, name))
      return
    }
    pkgSet += pkg
    val editor = ctx.getSharedPreferences(Pref, 0).edit()
    editor
      .putStringSet(KeyPkgs, JavaConversions.setAsJavaSet(pkgSet))
    editor.commit()
    showToast(ctx, ctx.getString(R.string.receiver_added_pkg, name))
  }

  def illegalText(ctx: Context, text: String): Unit = {
    showToast(ctx, ctx.getString(R.string.receiver_wrong_text, text))
  }

  def handleTextService(ctx: Context, uri: Uri): Boolean = {
    if (uri.getHost() != ServiceHost) {
      return false
    }
    val url =
      String.format("%s://%s%s", HttpsScheme, uri.getHost(), uri.getPath())
    HttpSender.send(
      url,
      ctx.getString(R.string.app_name),
      ctx.getString(R.string.service_succeed),
      () => {
        NotificationListener.cancelTelegramNotif(ctx)
        showToast(ctx, ctx.getString(R.string.service_succeed))
        val editor = ctx.getSharedPreferences(Pref, 0).edit()
        editor.putString(KeyServiceURL, url)
        editor.commit()
      },
      () => {
        new AlertDialog.Builder(ctx)
          .setCancelable(true)
          .setIcon(R.mipmap.icon)
          .setTitle(ctx.getString(R.string.service_failed_title))
          .setMessage(ctx.getString(
            R.string.service_failed_text,
            ctx.getString(android.R.string.ok)))
          .setPositiveButton(
            android.R.string.ok,
            new DialogInterface.OnClickListener() {
              override def onClick(dialog: DialogInterface, which: Int): Unit = {
                dialog.dismiss()
                ctx.startActivity(new Intent(Intent.ACTION_VIEW, TelegramUri))
              }
            }
          )
          .create()
          .show()
      },
      () => {
        showToast(ctx, ctx.getString(R.string.service_net_fail))
      }
    )
    return true
  }

  def handleText(ctx: Context, text: String): Boolean = {
    var msg = text
    text match {
      case UriRegexp(uri, _ *) => {
        msg = uri
        if (handleTextPackage(ctx, uri)) {
          return true
        }
        try {
          if (handleTextService(ctx, Uri.parse(uri))) {
            return true
          }
        } catch {
          case _: Throwable =>
        }
      }
      case AppIdRegexp(_ *) => {
        if (handleTextPackage(ctx, AppIdPrefix + text)) {
          return true
        }
      }
      case _ =>
    }
    showToast(ctx, ctx.getString(R.string.magic_invalid_uri, msg))
    return false
  }
}

class MainActivity
    extends AppCompatActivity
    with View.OnClickListener
    with TextView.OnEditorActionListener {

  import MainActivity.Pref
  import MainActivity.KeyPkgs
  import MainActivity.KeyServiceURL
  import MainActivity.ServiceHost
  import MainActivity.TelegramUri
  import MainActivity.HttpsScheme

  // allows accessing `.value` on TR.resource.constants
  implicit val context = this

  var prev: Set[String] = Set.empty
  var adapter: Option[PkgAdapter] = None
  var magicDialog: Option[AlertDialog] = None

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    // type ascription is required due to SCL-10491
    val vh: TypedViewHolder.main =
      TypedViewHolder.setContentView(this, TR.layout.main)
    vh.hint.setText(Html.fromHtml(getString(R.string.main_hint)))
    vh.hint.setMovementMethod(LinkMovementMethod.getInstance())

    adapter = Option(new PkgAdapter(ListBuffer.empty, this))
    adapter.foreach { a =>
      vh.pkg_list.setAdapter(a)
    }
    vh.pkg_list.setLayoutManager(new LinearLayoutManager(this))

    val view = getLayoutInflater().inflate(R.layout.magic, null)
    val tv = view.findViewById(R.id.magic_text).asInstanceOf[TextView]
    tv.setText(Html.fromHtml(getString(R.string.magic_text)))
    tv.setMovementMethod(LinkMovementMethod.getInstance())
    view.findViewById(R.id.go).asInstanceOf[Button].setOnClickListener(this)
    val et = view.findViewById(R.id.magic_url).asInstanceOf[EditText]
    et.setOnEditorActionListener(this)
    et.setImeActionLabel(getString(R.string.go), KeyEvent.KEYCODE_ENTER)
    magicDialog = Option(
      new AlertDialog.Builder(this)
        .setTitle(R.string.magic_box)
        .setView(view)
        .create())

    setSupportActionBar(vh.app_bar)
  }

  override def onResume(): Unit = {
    var checkService = true

    // Handle notification-bot.appspot.com URL
    Option(getIntent()).foreach { intent =>
      if (intent.getAction() == Intent.ACTION_VIEW) {
        Option(intent.getData()).foreach { uri =>
          val valid = MainActivity.handleTextService(this, uri)
          checkService = !valid
          if (!valid) {
            // Pass it along
            startActivity(new Intent(Intent.ACTION_VIEW, uri))
          }
        }
      }
    }

    // Check the listener service status
    if (!NotificationListener.connected) {
      val name = getString(R.string.app_name)
      new AlertDialog.Builder(this)
        .setCancelable(true)
        .setIcon(R.mipmap.icon)
        .setTitle(getString(R.string.perm_title, name))
        .setMessage(getString(R.string.perm_text, name))
        .setNegativeButton(
          R.string.perm_no,
          new DialogInterface.OnClickListener() {
            override def onClick(dialog: DialogInterface, which: Int): Unit = {
              dialog.dismiss()
              NotificationListener.startMain = false
              finish()
            }
          }
        )
        .setPositiveButton(
          R.string.perm_yes,
          new DialogInterface.OnClickListener() {
            override def onClick(dialog: DialogInterface, which: Int): Unit = {
              dialog.dismiss()
              NotificationListener.startMain = true
              startActivity(
                new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
          }
        )
        .setOnCancelListener(new DialogInterface.OnCancelListener() {
          override def onCancel(dialog: DialogInterface): Unit = {
            dialog.dismiss()
            NotificationListener.startMain = false
            finish()
          }
        })
        .create()
        .show()
    } else if (checkService) {
      // Check service url
      val pref = getSharedPreferences(Pref, 0)
      val url = pref.getString(KeyServiceURL, "")
      val onFailure = () => {
        new AlertDialog.Builder(this)
          .setCancelable(true)
          .setIcon(R.mipmap.icon)
          .setTitle(getString(R.string.no_service))
          .setMessage(getString(
            R.string.init_service_text,
            getString(android.R.string.ok)))
          .setPositiveButton(
            android.R.string.ok,
            new DialogInterface.OnClickListener() {
              override def onClick(
                  dialog: DialogInterface, which: Int): Unit = {
                dialog.dismiss()
                startActivity(new Intent(Intent.ACTION_VIEW, TelegramUri))
              }
            }
          )
          .create()
          .show()
      }
      val uri = Uri.parse(url)
      if (uri.getScheme() == HttpsScheme && uri.getHost() == ServiceHost) {
        HttpSender.checkUrl(url, onFailure)
      } else {
        onFailure()
      }
    }

    refreshData()
    super.onResume()
  }

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    getMenuInflater().inflate(R.menu.main, menu)
    return true
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = {
    item.getItemId() match {
      case R.id.action_about => {
        val view = getLayoutInflater().inflate(R.layout.about, null)
        val tv = view.findViewById(R.id.about_text).asInstanceOf[TextView]
        tv.setText(Html.fromHtml(getString(R.string.about_text)))
        tv.setMovementMethod(LinkMovementMethod.getInstance())
        view
          .findViewById(R.id.about_title)
          .asInstanceOf[TextView]
          .setText(
            getString(R.string.about_title, getString(R.string.app_name)))
        new AlertDialog.Builder(this)
          .setTitle(R.string.about)
          .setView(view)
          .create()
          .show()
        return true
      }
      case R.id.action_box => {
        magicDialog.get.show()
        return true
      }
      case _ => {
        return super.onOptionsItemSelected(item)
      }
    }
  }

  // for View.OnClickListener
  override def onClick(v: View): Unit = {
    v.getId() match {
      case R.id.go => {
        doMagicGo()
        return
      }
      case _ =>
    }
    val rv = findViewById(R.id.pkg_list).asInstanceOf[RecyclerView]
    val i = rv.getChildLayoutPosition(v)
    adapter.foreach { a =>
      val data = a.list.apply(i)
      new AlertDialog.Builder(this)
        .setCancelable(true)
        .setIcon(data.icon)
        .setTitle(R.string.dialog_title)
        .setMessage(getString(
          R.string.dialog_text,
          data.name,
          getString(R.string.app_name)))
        .setNegativeButton(
          R.string.dialog_no,
          new DialogInterface.OnClickListener() {
            override def onClick(dialog: DialogInterface, which: Int): Unit = {
              dialog.dismiss()
            }
          })
        .setPositiveButton(
          R.string.dialog_yes,
          new DialogInterface.OnClickListener() {
            override def onClick(dialog: DialogInterface, which: Int): Unit = {
              removePkg(data.pkg)
              a.remove(i)
              dialog.dismiss()
            }
          }
        )
        .create()
        .show()
    }
  }

  // for TextView.OnEditorActionListener
  override def onEditorAction(v: TextView, id: Int, ev: KeyEvent): Boolean = {
    id match {
      case EditorInfo.IME_ACTION_GO =>
        doMagicGo()
        return true
      case _ => return false
    }
  }

  def doMagicGo(): Unit = {
    magicDialog.foreach { d =>
      val text = d.findViewById(R.id.magic_url).asInstanceOf[EditText]
      if (d.isShowing()
          && MainActivity.handleText(this, text.getText().toString())) {
        d.dismiss()
        text.setText("")
        refreshData()
      }
    }
  }

  def removePkg(pkg: String) = {
    val pkgSet = Set.empty ++= NotificationListener.getPkgSet(this)
    pkgSet -= pkg
    val editor = getSharedPreferences(Pref, 0).edit()
    editor.putStringSet(KeyPkgs, JavaConversions.setAsJavaSet(pkgSet))
    editor.commit()
  }

  def refreshData() = {
    val pkgSet = Set.empty ++= NotificationListener.getPkgSet(this)
    if (pkgSet != prev) {
      prev = pkgSet
      val pm = getPackageManager()
      val defIcon = getDrawable(R.mipmap.default_icon)
      adapter.foreach { a =>
        a.list = prev
          .map(x => createPkgData(pm, x, defIcon))
          .to[ListBuffer]
          .sortBy(_.name)
        a.notifyDataSetChanged()
      }
    }
  }

  def createPkgData(
      pm: PackageManager,
      pkg: String,
      defIcon: Drawable): PkgData = {
    var name = pkg
    var icon = defIcon
    try {
      Option(pm.getApplicationInfo(pkg, 0)).foreach { appInfo =>
        Option(pm.getApplicationLabel(appInfo)).foreach { s =>
          name = s.toString()
        }
        icon = pm.getApplicationIcon(appInfo)
      }
    } catch {
      case _: NameNotFoundException =>
    }
    return new PkgData(icon, name, pkg)
  }
}
