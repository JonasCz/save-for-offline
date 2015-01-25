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

	private EditTextPreference custom_storage_location;
	private ListPreference storage_location;
	private String list_appearance;
	
	
	@Override
	public void onSharedPreferenceChanged(SharedPreferences preferences, String key)
	{
		if (preferences.getString("storage_dir", "external").equals("custom")) {
            custom_storage_location.setEnabled(true);
		} else { custom_storage_location.setEnabled(false); }
		if (!preferences.getString("layout" , "1").equals(list_appearance)) {
			setResult(RESULT_FIRST_USER);
		} else { setResult(RESULT_OK);}
	}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		ActionBar actionbar = getActionBar();
		actionbar.setDisplayHomeAsUpEnabled(true);
		actionbar.setSubtitle("Settings");

        addPreferencesFromResource(R.xml.preferences);
		
		custom_storage_location = (EditTextPreference) getPreferenceScreen().findPreference("custom_storage_dir");
		storage_location = (ListPreference)getPreferenceScreen().findPreference("storage_dir");
		
		if (getPreferenceScreen().getSharedPreferences().getString("storage_dir", "external").equals("custom")) {
            custom_storage_location.setEnabled(true);
		} else { custom_storage_location.setEnabled(false); }
		
		list_appearance = getPreferenceScreen().getSharedPreferences().getString("layout" , "1");
		
		getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
		
		//DISABLED FOR NOW
		storage_location.setEnabled(false);
    }
	

}
