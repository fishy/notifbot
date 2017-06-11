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
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast

import scala.collection.JavaConversions
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Set

object MainActivity {
  val Pref = "com.yhsif.notifbot"
  val KeyPkgs = "packages"
  val KeyServiceURL = "service"
  val HttpsScheme = "https"
  val ServiceHost = "notification-bot.appspot.com"
  val TelegramUri = Uri.parse("https://t.me/AndroidNotificationBot?start=0")

  def showToast(ctx: Context, text: String): Unit = {
    val toast = Toast.makeText(ctx, text, Toast.LENGTH_LONG)
    Option(toast.getView().findViewById(android.R.id.message)).foreach { v =>
      // Put the icon on the right
      val view = v.asInstanceOf[TextView]
      view.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.mipmap.icon, 0)
      view.setCompoundDrawablePadding(
        ctx.getResources().getDimensionPixelSize(R.dimen.toast_padding))
    }
    toast.show()
  }
}

class MainActivity extends AppCompatActivity with View.OnClickListener {
  // allows accessing `.value` on TR.resource.constants
  implicit val context = this

  var prev: Set[String] = Set.empty
  var adapter: Option[PkgAdapter] = None

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

    //vh.app_bar.inflateMenu(R.menu.main)
    setSupportActionBar(vh.app_bar)
  }

  override def onResume(): Unit = {
    var checkService = true

    // Handle notification-bot.appspot.com URL
    Option(getIntent()).foreach { intent =>
      if (intent.getAction() == Intent.ACTION_VIEW) {
        Option(intent.getData()).foreach { uri =>
          val valid = uri.getHost() match {
            case MainActivity.ServiceHost => true
            case _ => false
          }
          checkService = !valid
          if (valid) {
            val url = String.format(
              "%s://%s%s",
              MainActivity.HttpsScheme, uri.getHost(), uri.getPath())
            HttpSender.send(
              url,
              getString(R.string.app_name),
              getString(R.string.service_succeed),
              () => {
                NotificationListener.cancelTelegramNotif(this)
                MainActivity.showToast(
                  this, getString(R.string.service_succeed))
                val editor = getSharedPreferences(MainActivity.Pref, 0).edit()
                editor.putString(MainActivity.KeyServiceURL, url)
                editor.commit()
              },
              () => {
                new AlertDialog.Builder(this)
                  .setCancelable(true)
                  .setIcon(R.mipmap.icon)
                  .setTitle(getString(R.string.service_failed_title))
                  .setMessage(getString(
                    R.string.service_failed_text,
                    getString(android.R.string.ok)))
                  .setPositiveButton(
                    android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                      override def onClick(
                          dialog: DialogInterface, which: Int): Unit = {
                        dialog.dismiss()
                        startActivity(new Intent(
                          Intent.ACTION_VIEW, MainActivity.TelegramUri))
                      }
                    }
                  )
                  .create()
                  .show()
              },
              () => {
                // Do nothing on network failure here.
              })
          } else {
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
      val pref = getSharedPreferences(MainActivity.Pref, 0)
      val url = pref.getString(MainActivity.KeyServiceURL, "")
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
                startActivity(new Intent(
                  Intent.ACTION_VIEW, MainActivity.TelegramUri))
              }
            }
          )
          .create()
          .show()
      }
      val uri = Uri.parse(url)
      if (uri.getScheme() == MainActivity.HttpsScheme &&
          uri.getHost() == MainActivity.ServiceHost) {
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
      case _ => {
        return super.onOptionsItemSelected(item)
      }
    }
  }

  // for View.OnClickListener
  override def onClick(v: View): Unit = {
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

  def removePkg(pkg: String) = {
    val pkgSet = Set.empty ++= NotificationListener.getPkgSet(this)
    pkgSet -= pkg
    val editor = getSharedPreferences(MainActivity.Pref, 0).edit()
    editor
      .putStringSet(MainActivity.KeyPkgs, JavaConversions.setAsJavaSet(pkgSet))
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
