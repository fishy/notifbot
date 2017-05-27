package com.yhsif.notifbot

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.TextView
import android.widget.Toast

import scala.collection.JavaConversions
import scala.collection.mutable.Set

class ShareReceiver extends Activity {
  // The URL should be either
  // http(s)://play.google.com/store/apps/details?id=<package_name>
  // or
  // market://details?id=<package_name>
  val PlayHost = "play.google.com"
  val MarketScheme = "market"
  val HttpScheme = "http"
  val HttpsScheme = "https"
  val IdQuery = "id"

  override def onResume(): Unit = {
    Option(getIntent()).foreach { intent =>
      if (intent.getAction() == Intent.ACTION_SEND) {
        Option(intent.getStringExtra(Intent.EXTRA_TEXT)).foreach { text =>
          try {
            val uri = Uri.parse(text)
            val valid = uri.getScheme() match {
              case MarketScheme => true
              case HttpsScheme | HttpScheme => uri.getHost() == PlayHost
              case _ => false
            }
            if (valid) {
              Option(uri.getQueryParameter(IdQuery)) match {
                case Some(pkg) => addPackage(pkg)
                case None => illegalText(text)
              }
            } else illegalText(text)
          } catch {
            case _: Throwable => illegalText(text)
          }
        }
      }
    }
    finish()
    super.onResume()
  }

  def addPackage(pkg: String): Unit = {
    val name = NotificationListener.getPackageName(this, pkg, false)
    val pkgSet = Set.empty ++= NotificationListener.getPkgSet(this)
    if (pkgSet(pkg)) {
      showToast(getString(R.string.receiver_pkg_exists, name))
      return
    }
    pkgSet += pkg
    val editor = getSharedPreferences(MainActivity.Pref, 0).edit()
    editor
      .putStringSet(MainActivity.KeyPkgs, JavaConversions.setAsJavaSet(pkgSet))
    editor.commit()
    showToast(getString(R.string.receiver_added_pkg, name))
  }

  def illegalText(text: String): Unit = {
    showToast(getString(R.string.receiver_wrong_text, text))
  }

  def showToast(text: String): Unit = {
    val toast = Toast.makeText(this, text, Toast.LENGTH_LONG)
    Option(toast.getView().findViewById(android.R.id.message)).foreach { v =>
      // Put the icon on the right
      val view = v.asInstanceOf[TextView]
      view.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.mipmap.icon, 0)
      view.setCompoundDrawablePadding(
        getResources().getDimensionPixelSize(R.dimen.toast_padding))
    }
    toast.show()
  }
}
