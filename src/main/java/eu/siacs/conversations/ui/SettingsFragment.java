package eu.siacs.conversations.ui;

import info.upperechelon.chat.R;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;

public class SettingsFragment extends PreferenceFragment {
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Load the preferences from an XML resource
		addPreferencesFromResource(R.xml.preferences);

        ((PreferenceGroup) findPreference("pref_ui_options")).removePreference(findPreference("send_button_status"));
        ((PreferenceGroup) findPreference("pref_ui_options")).removePreference(findPreference("show_dynamic_tags"));
        ((PreferenceGroup) findPreference("pref_ui_options")).removePreference(findPreference("use_subject"));

        ((PreferenceGroup) findPreference("pref_ui_options")).removePreference(findPreference("keep_foreground_service"));
        ((PreferenceGroup) findPreference("pref_ui_options")).removePreference(findPreference("grant_new_contacts"));
        ((PreferenceGroup) findPreference("pref_ui_options")).removePreference(findPreference("confirm_messages"));
        ((PreferenceGroup) findPreference("pref_ui_options")).removePreference(findPreference("indicate_received"));
        ((PreferenceGroup) findPreference("pref_ui_options")).removePreference(findPreference("auto_accept_file_size"));

        ((PreferenceGroup) findPreference("pref_ui_options")).removePreference(findPreference("force_encryption"));
        ((PreferenceGroup) findPreference("pref_ui_options")).removePreference(findPreference("minimum_encryption"));
        ((PreferenceGroup) findPreference("pref_ui_options")).removePreference(findPreference("minimum_ciphers"));

        ((PreferenceGroup) findPreference("pref_ui_options")).removePreference(findPreference("never_send"));
        ((PreferenceGroup) findPreference("pref_ui_options")).removePreference(findPreference("about"));

    }
}
