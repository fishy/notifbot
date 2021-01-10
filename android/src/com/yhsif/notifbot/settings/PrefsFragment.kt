package com.yhsif.notifbot.settings

import android.os.Bundle
import androidx.preference.Preference
import com.yhsif.notifbot.R

class PrefsFragment : BasePreferenceFragment() {
  companion object {
    const val FRAGMENT_TAG = "prefs_fragment"
  }

  override fun onCreatePreferences(
    savedInstanceState: Bundle?,
    rootKey: String?,
  ) {
    setPreferencesFromResource(R.xml.pref_headers, rootKey)
    setHasOptionsMenu(true)

    val pref: Preference? = findPreference(SettingsActivity.KEY_AUTO_DISMISS)
    if (pref != null) {
      SettingsActivity.bindPreferenceSummaryToBoolean(
        pref,
        SettingsActivity.DEFAULT_AUTO_DISMISS,
      )
    }
  }
}
