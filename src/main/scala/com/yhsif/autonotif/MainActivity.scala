package com.yhsif.autonotif

import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.support.v7.app.ActionBarActivity
import android.support.v7.widget.LinearLayoutManager
import android.text.Html
import android.text.method.LinkMovementMethod

import scala.collection.JavaConversions
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Set

object MainActivity {
  val Pref = "com.yhsif.autonotif"
  val KeyPkgs = "packages"
}

class MainActivity extends ActionBarActivity {
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

    adapter = Option(new PkgAdapter(ListBuffer.empty))
    adapter.foreach {
      a => vh.pkg_list.setAdapter(a) 
    }
    vh.pkg_list.setLayoutManager(new LinearLayoutManager(this))
    refreshData()
  }

  override def onResume(): Unit = {
    refreshData()
    super.onResume()
  }

  def refreshData() = {
    val pkgSet = Set.empty ++= NotificationListener.getPkgSet(this)
    if (pkgSet != prev) {
      prev = pkgSet
      val pm = getPackageManager()
      val defIcon = getDrawable(R.mipmap.default_icon)
      adapter.foreach { a =>
        a.list =
          prev
            .map(x => createPkgData(pm, x, defIcon))
            .to[ListBuffer]
            .sortBy(_.name)
        a.notifyDataSetChanged()
      }
    }
  }

  def createPkgData(pm: PackageManager, pkg: String, defIcon: Drawable)
  : PkgData = {
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
    return new PkgData(icon, name)
  }
}
