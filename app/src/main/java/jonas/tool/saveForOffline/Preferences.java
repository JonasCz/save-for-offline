package jonas.tool.saveForOffline;

import android.*;
import android.content.*;
import android.content.res.*;
import android.net.*;
import android.os.*;
import android.preference.*;
import android.app.*;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;

public class Preferences extends PreferenceActivity implements OnSharedPreferenceChangeListener
{
	private String list_appearance;

	@Override
	public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
		if (!preferences.getString("layout" , "1").equals(list_appearance)) {
			setResult(RESULT_FIRST_USER);
		} else if (key.equals("dark_mode")) {
			setResult(RESULT_FIRST_USER);
		} else { setResult(RESULT_OK);}
		disableEnablePreferences();
	}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		ActionBar actionbar = getActionBar();
		actionbar.setDisplayHomeAsUpEnabled(true);
		actionbar.setSubtitle("Preferences");
		
        addPreferencesFromResource(R.xml.preferences);
		
		list_appearance = getPreferenceScreen().getSharedPreferences().getString("layout" , "1");
		disableEnablePreferences();
		
		getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }
	
	private void disableEnablePreferences () {
		//this can probably be done through Preference dependencies, too lazy to figure out how..
		boolean useCustomStorageDirEnabled = getPreferenceScreen().getSharedPreferences().getBoolean("is_custom_storage_dir", true);
		if (useCustomStorageDirEnabled) {
			getPreferenceScreen().findPreference("custom_storage_dir").setEnabled(true);
		} else {
			getPreferenceScreen().findPreference("custom_storage_dir").setEnabled(false);
		}
	}

}
