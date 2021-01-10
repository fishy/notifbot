package com.yhsif.notifbot.settings

import android.view.MenuItem
import androidx.preference.PreferenceFragmentCompat

abstract class BasePreferenceFragment : PreferenceFragmentCompat() {
  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item.getItemId() == android.R.id.home) {
      getParentFragmentManager().popBackStack()
    }
    return super.onOptionsItemSelected(item)
  }
}
