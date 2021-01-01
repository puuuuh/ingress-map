package com.puuuuh.ingressmap.view
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.navigation.fragment.findNavController
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.puuuuh.ingressmap.R
import com.puuuuh.ingressmap.settings.Settings

@Suppress("ControlFlowWithEmptyBody")
class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        val themePref = findPreference<ListPreference>("THEME")!!
        val mapPref = findPreference<ListPreference>("MAP_PROVIDER")!!
        val logoutPref = findPreference<Preference>("LOGOUT")!!
        val iconsPref = findPreference<Preference>("ICONS")!!

        themePref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
            AppCompatDelegate.setDefaultNightMode(Integer.parseInt(value as String))
            true
        }

        mapPref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, _ ->
            requireActivity().recreate()
            true
        }

        logoutPref.onPreferenceClickListener = Preference.OnPreferenceClickListener { _ ->
            Settings.token = ""
            true
        }

        iconsPref.onPreferenceClickListener = Preference.OnPreferenceClickListener { _ ->
            findNavController().navigate(R.id.icons_settings)
            true
        }
    }
}