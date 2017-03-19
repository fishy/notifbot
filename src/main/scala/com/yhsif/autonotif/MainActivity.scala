package com.yhsif.autonotif

import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.Menu
import android.view.MenuItem
import android.view.View

import scala.collection.JavaConversions
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Set

object MainActivity {
  val Pref = "com.yhsif.autonotif"
  val KeyPkgs = "packages"
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
    if (!NotificationListener.connected) {
      val name = getString(R.string.app_name)
      val builder = new AlertDialog.Builder(this)
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
      builder.create().show()
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
        val builder = new AlertDialog.Builder(this)
          .setIcon(R.mipmap.icon)
          .setTitle(getString(R.string.about_title))
          .setMessage(Html.fromHtml(getString(R.string.about_text)))
        builder.create().show()
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
      val builder = new AlertDialog.Builder(this)
        .setCancelable(true)
        .setIcon(data.icon)
        .setTitle(R.string.dialog_title)
        .setMessage(
          getString(
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
      builder.create().show()
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
