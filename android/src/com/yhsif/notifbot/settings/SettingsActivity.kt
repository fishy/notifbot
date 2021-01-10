package com.yhsif.notifbot.settings

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.yhsif.notifbot.R

public class SettingsActivity :
  AppCompatActivity(),
  PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

  companion object {
    public const val KEY_AUTO_DISMISS = "auto_dismiss"
    public const val DEFAULT_AUTO_DISMISS = false

    val prefBinder = object : Preference.OnPreferenceChangeListener {
      override fun onPreferenceChange(
        pref: Preference,
        value: Any,
      ): Boolean {
        when (pref.getKey()) {
          KEY_AUTO_DISMISS -> {
            pref.setSummary(
              if (value == true) {
                R.string.pref_desc_auto_dismiss_yes
              } else {
                R.string.pref_desc_auto_dismiss_no
              },
            )
          }
          // For all other preferences, set the summary to the value's
          // simple string representation.
          else -> pref.setSummary(value.toString())
        }
        return true
      }
    }

    fun bindPreferenceSummaryToBoolean(
      preference: Preference,
      defaultValue: Boolean,
    ) {
      preference.setOnPreferenceChangeListener(prefBinder)
      prefBinder.onPreferenceChange(
        preference,
        PreferenceManager
          .getDefaultSharedPreferences(preference.getContext())
          .getBoolean(preference.getKey(), defaultValue),
      )
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setupActionBar()
    setContentView(R.layout.settings)

    if (savedInstanceState == null) {
      var frag = getSupportFragmentManager().findFragmentByTag(
        PrefsFragment.FRAGMENT_TAG,
      )
      if (frag == null) {
        frag = PrefsFragment()
      }

      getSupportFragmentManager().commit {
        replace(R.id.fragment_container, frag, PrefsFragment.FRAGMENT_TAG)
      }
    }
  }

  fun setupActionBar() {
    // Show the Up button in the action bar.
    getSupportActionBar()?.setDisplayHomeAsUpEnabled(true)
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item.getItemId() == android.R.id.home) {
      onBackPressed()
    }
    return super.onOptionsItemSelected(item)
  }

  override fun onPreferenceStartFragment(
    caller: PreferenceFragmentCompat,
    pref: Preference,
  ): Boolean {
    getSupportFragmentManager().commit {
      val key = pref.getKey()
      val args = Bundle()
      args.putString(
        PreferenceFragmentCompat.ARG_PREFERENCE_ROOT,
        key,
      )
      val frag: BasePreferenceFragment? =
        when (key) {
          getString(R.string.pref_tag_about) -> AboutPreferenceFragment()
          else -> null
        }
      if (frag != null) {
        frag.setArguments(args)
        replace(R.id.fragment_container, frag, key)
      }
      addToBackStack(key)
    }
    return true
  }
}
