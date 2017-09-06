package com.yhsif.notifbot

import android.app.Activity
import android.content.Intent

class ShareReceiver : Activity() {
  override fun onResume() {
    getIntent()?.let { intent ->
      if (intent.getAction() == Intent.ACTION_SEND) {
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
          if (!MainActivity.handleTextPackage(this, text)) {
            MainActivity.illegalText(this, text)
          }
        }
      }
    }
    finish()
    super.onResume()
  }
}
