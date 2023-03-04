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

    val prefAutoDismiss: Preference? = findPreference(SettingsActivity.KEY_AUTO_DISMISS)
    if (prefAutoDismiss != null) {
      SettingsActivity.bindPreferenceSummaryToBoolean(
        prefAutoDismiss,
        SettingsActivity.DEFAULT_AUTO_DISMISS,
      )
    }

    val prefSendLabel: Preference? = findPreference(SettingsActivity.KEY_SEND_LABEL)
    if (prefSendLabel != null) {
      SettingsActivity.bindPreferenceSummaryToBoolean(
        prefSendLabel,
        SettingsActivity.DEFAULT_SEND_LABEL,
      )
    }
  }
}
