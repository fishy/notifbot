package com.yhsif.notifbot

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class PkgAdapter(
    var list: MutableList<PkgData>,
    val listener: View.OnClickListener
) : RecyclerView.Adapter<PkgViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, vt: Int): PkgViewHolder {
        val v = LayoutInflater
            .from(parent.getContext())
            .inflate(R.layout.pkg_item, parent, false)
        v.setOnClickListener(listener)
        return PkgViewHolder(v)
    }

    override fun onBindViewHolder(vh: PkgViewHolder, i: Int) {
        vh.setIcon(list.get(i).icon)
        vh.setName(list.get(i).name)
        vh.setBackground(i)
    }

    override fun getItemCount(): Int = list.size

    override fun onAttachedToRecyclerView(rv: RecyclerView) {
        super.onAttachedToRecyclerView(rv)
    }

    fun remove(i: Int) {
        list.removeAt(i)
        notifyDataSetChanged()
    }
}
