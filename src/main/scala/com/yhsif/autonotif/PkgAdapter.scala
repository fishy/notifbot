package com.yhsif.autonotif

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup

import scala.collection.mutable.ListBuffer

class PkgAdapter(var list: ListBuffer[PkgData])
    extends RecyclerView.Adapter[PkgViewHolder] {

  override def onCreateViewHolder(parent: ViewGroup, vt: Int): PkgViewHolder = {
    val v =
      LayoutInflater
        .from(parent.getContext())
        .inflate(R.layout.pkg_item, parent, false)
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

  def insert(i: Int, data: PkgData) = {
    list.insert(i, data)
    notifyItemInserted(i)
  }

  def remove(data: PkgData) = {
    val i = list.indexOf(data)
    if (i >= 0) {
      list.remove(i)
      notifyItemRemoved(i)
    }
  }
}
