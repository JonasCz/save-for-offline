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
	private boolean oldSaveInBackground;
	
	
	@Override
	public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
		if (!preferences.getString("layout" , "1").equals(list_appearance)) {
			setResult(RESULT_FIRST_USER);
		} else { setResult(RESULT_OK);}

		if (key.equals("save_in_background") && preferences.getBoolean("save_in_background" , true) == false) {
			AlertDialog.Builder build;
			build = new AlertDialog.Builder(Preferences.this);

			build.setTitle("Warning!");

			build.setMessage("You should not turn off this option unless something is not working, or you need to login to website before saving. Note that this app can only save HTML files when this option is on.");


			build.setPositiveButton("OK",
				new DialogInterface.OnClickListener() {

					public void onClick(DialogInterface dialog, int which) {
						

					}
				});

			build.setNegativeButton("Cancel",
				new DialogInterface.OnClickListener() {

					public void onClick(DialogInterface dialog, int which) {
						SharedPreferences.Editor edit = getPreferenceScreen().getEditor();
						edit.putBoolean("save_in_background", true);
						edit.commit();
						
					}
				});
			alert = build.create();
			alert.show();
		}
	}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		ActionBar actionbar = getActionBar();
		actionbar.setDisplayHomeAsUpEnabled(true);
		actionbar.setSubtitle("Settings");

        addPreferencesFromResource(R.xml.preferences);
		
		
		list_appearance = getPreferenceScreen().getSharedPreferences().getString("layout" , "1");
		
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
