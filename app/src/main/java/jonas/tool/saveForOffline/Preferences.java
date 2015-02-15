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

	private AlertDialog alert;
	private String list_appearance;

	
	
	@Override
	public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
		if (!preferences.getString("layout" , "1").equals(list_appearance)) {
			setResult(RESULT_FIRST_USER);
		} else { setResult(RESULT_OK);}

		if (key.equals("save_in_background") && preferences.getBoolean("save_in_background" , true) == false) {
			
			Preference advancedSavingOptions = getPreferenceScreen().findPreference("saving_advanced_opts");
			advancedSavingOptions.setEnabled(false);
			advancedSavingOptions.setSummary("Theese options are only available if 'Save in background' is enabled");
			
			AlertDialog.Builder build = new AlertDialog.Builder(Preferences.this);
			build.setTitle("Warning!");
			build.setMessage("You should not turn off this option unless something is not working. Note that this app can only save real HTML files when this option is on.");
			build.setPositiveButton("OK",
				new DialogInterface.OnClickListener() {

					public void onClick(DialogInterface dialog, int which) {
						

					}
				});
			alert = build.create();
			alert.show();
		} else {
			Preference advancedSavingOptions = getPreferenceScreen().findPreference("saving_advanced_opts");
			advancedSavingOptions.setEnabled(true);
			advancedSavingOptions.setSummary("Choose how errors should be handled and what parts of a webpage to save (images, scripts...). Use with care");
		}
		//disabled for now
	}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		ActionBar actionbar = getActionBar();
		actionbar.setDisplayHomeAsUpEnabled(true);
		actionbar.setSubtitle("Settings");

        addPreferencesFromResource(R.xml.preferences);
		
		
		list_appearance = getPreferenceScreen().getSharedPreferences().getString("layout" , "1");
		
		if (getPreferenceScreen().getSharedPreferences().getBoolean("save_in_background" , true) == false) {
			Preference advancedSavingOptions = getPreferenceScreen().findPreference("saving_advanced_opts");
			advancedSavingOptions.setEnabled(false);
			advancedSavingOptions.setSummary("Theese options are only available if 'Save in background' is enabled");
		}
		
		getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
		if (alert != null) alert.cancel();
		alert = null;
		//DISABLED FOR NOW=-O 
    }

	@Override
	protected void onPause() {
		super.onPause();
		if (alert != null) alert.cancel();
	}
	
	

}
