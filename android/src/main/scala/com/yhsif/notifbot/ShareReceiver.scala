package com.yhsif.notifbot

import android.app.Activity
import android.content.Intent

class ShareReceiver extends Activity {
  override def onResume(): Unit = {
    Option(getIntent()).foreach { intent =>
      if (intent.getAction() == Intent.ACTION_SEND) {
        Option(intent.getStringExtra(Intent.EXTRA_TEXT)).foreach { text =>
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
