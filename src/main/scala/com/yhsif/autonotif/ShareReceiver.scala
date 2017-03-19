package com.yhsif.autonotif

import android.app.Activity
import android.content.Intent
import android.widget.TextView
import android.widget.Toast

import java.net.URL

import scala.collection.JavaConversions
import scala.collection.mutable.Set

class ShareReceiver extends Activity {
  // The URL should be either
  // http(s)://play.google.com/store/apps/details?id=<package_name>
  // or
  // market://details?id=<package_name>
  val PlayHost = "play.google.com"
  val MarketProtocol = "market"
  val HttpProtocol = "http"
  val HttpsProtocol = "https"
  val IdQuery = "id"

  override def onResume(): Unit = {
    Option(getIntent()).foreach { intent =>
      if (intent.getAction() == Intent.ACTION_SEND) {
        Option(intent.getStringExtra(Intent.EXTRA_TEXT)).foreach { text =>
          try {
            val url = new URL(text)
            val valid = url.getProtocol() match {
              case MarketProtocol => true
              case HttpsProtocol | HttpProtocol => url.getHost() == PlayHost
              case _ => false
            }
            if (valid) {
              findPackageName(Option(url.getQuery())) match {
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

  def findPackageName(queryOrNull: Option[String]): Option[String] = {
    val query = queryOrNull getOrElse ""
    for (q <- query.split("&")) {
      val s = q.split("=")
      if (s.size == 2 && s(0) == IdQuery) {
        return Option(s(1))
      }
    }
    return None
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
