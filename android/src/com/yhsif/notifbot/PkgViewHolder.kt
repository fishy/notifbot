package com.yhsif.notifbot

import android.graphics.drawable.Drawable
import android.support.v7.widget.RecyclerView
import android.view.View
import android.widget.ImageView
import android.widget.TextView

class PkgViewHolder(v: View) : RecyclerView.ViewHolder(v) {
  val v = v

  fun setIcon(icon: Drawable) {
    (v.findViewById(R.id.icon) as ImageView).setImageDrawable(icon)
  }

  fun setName(name: String) {
    (v.findViewById(R.id.name) as TextView).setText(name)
  }

  fun setBackground(i: Int) {
    if (i % 2 == 0) {
      v.setBackgroundColor(v.getContext().getColor(R.color.even_background))
    } else {
      v.setBackgroundColor(v.getContext().getColor(R.color.odd_background))
    }
  }
}
