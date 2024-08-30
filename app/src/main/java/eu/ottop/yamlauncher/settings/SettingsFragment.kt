package eu.ottop.yamlauncher.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import eu.ottop.yamlauncher.R

class SettingsFragment : PreferenceFragmentCompat() {

    private lateinit var sharedPreferenceManager: SharedPreferenceManager

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        sharedPreferenceManager = SharedPreferenceManager(requireContext())

        val homePref = findPreference<Preference?>("defaultHome")

        val homeSettings = findPreference<Preference>("homeSettings")
        val appMenuSettings = findPreference<Preference>("appMenuSettings")

        val hiddenPref = findPreference<Preference?>("hiddenApps")
        val aboutPref = findPreference<Preference?>("aboutPage")

        homePref?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                val intent = Intent(Settings.ACTION_HOME_SETTINGS)
                if (intent.resolveActivity(requireContext().packageManager) != null) {
                    startActivity(intent)
                } else {
                    Toast.makeText(requireContext(), "Unable to launch settings", Toast.LENGTH_SHORT).show()
                }
                true }

        homeSettings?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                requireActivity().supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.settingsLayout, HomeSettingsFragment())
                    .addToBackStack(null)
                    .commit()
                true }

        appMenuSettings?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                requireActivity().supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.settingsLayout, AppMenuSettingsFragment())
                    .addToBackStack(null)
                    .commit()
                true }

        hiddenPref?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                requireActivity().supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.settingsLayout, HiddenAppsFragment())
                    .addToBackStack(null)
                    .commit()
                true }

        aboutPref?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                requireActivity().supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.settingsLayout, AboutFragment())
                    .addToBackStack(null)
                    .commit()
                true }


    }
}