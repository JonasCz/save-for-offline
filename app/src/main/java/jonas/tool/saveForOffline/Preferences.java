/**
 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 2 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.

 **/

/**
 This file is part of Save For Offline, an Android app which saves / downloads complete webpages for offine reading.
 **/

/**
 If you modify, redistribute, or write something based on this or parts of it, you MUST,
 I repeat, you MUST comply with the GPLv2+ license. This means that if you use or modify
 my code, you MUST release the source code of your modified version, if / when this is
 required under the terms of the license.

 If you cannot / do not want to do this, DO NOT USE MY CODE. Thanks.

 (I've added this message to to the source because it's been used in severeral proprietary
 closed source apps, which I don't want, and which is also a violation of the liense.)
 **/

/**
 Written by Jonas Czech (JonasCz, stackoverflow.com/users/4428462/JonasCz and github.com/JonasCz). (4428462jonascz/eafc4d1afq)
 **/

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
