package com.yhsif.notifbot

import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PkgViewHolder(val v: View) : RecyclerView.ViewHolder(v) {

  fun setIcon(icon: Drawable) {
    v.findViewById<ImageView>(R.id.icon).setImageDrawable(icon)
  }

  fun setName(name: String) {
    v.findViewById<TextView>(R.id.name).setText(name)
  }

  fun setBackground(i: Int) {
    val tv = v.findViewById<TextView>(R.id.name)
    if (i % 2 == 0) {
      v.setBackgroundColor(v.getContext().getColor(R.color.even_background))
      tv.setTextColor(v.getContext().getColor(R.color.even_text))
    } else {
      v.setBackgroundColor(v.getContext().getColor(R.color.odd_background))
      tv.setTextColor(v.getContext().getColor(R.color.odd_text))
    }
  }
}
