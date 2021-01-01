package com.puuuuh.ingressmap.view

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.puuuuh.ingressmap.R

class IconsSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.icons_preferences, rootKey)
    }
}