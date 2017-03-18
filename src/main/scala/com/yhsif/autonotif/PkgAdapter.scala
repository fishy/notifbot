package com.yhsif.autonotif

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View

import scala.collection.mutable.ListBuffer

class PkgAdapter(
  var list: ListBuffer[PkgData], val listener: View.OnClickListener)
    extends RecyclerView.Adapter[PkgViewHolder] {

  override def onCreateViewHolder(parent: ViewGroup, vt: Int): PkgViewHolder = {
    val v =
      LayoutInflater
        .from(parent.getContext())
        .inflate(R.layout.pkg_item, parent, false)
    v.setOnClickListener(listener)
    return new PkgViewHolder(v)
  }

  override def onBindViewHolder(vh: PkgViewHolder, i: Int): Unit = {
    vh.setIcon(list.apply(i).icon)
    vh.setName(list.apply(i).name)
    vh.setBackground(i)
  }

  override def getItemCount(): Int = list.length

  override def onAttachedToRecyclerView(rv: RecyclerView): Unit = {
    super.onAttachedToRecyclerView(rv)
  }

  def remove(i: Int) = {
    list.remove(i)
    notifyDataSetChanged()
  }
}
