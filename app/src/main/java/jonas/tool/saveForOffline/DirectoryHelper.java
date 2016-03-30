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
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DirectoryHelper {
	
	public static String createUniqueFilename () {
		//creates filenames based on the date and time, hopefully unique (TODO: this doesn't work if multiple pages with the same title are saved within 1 sec of each other
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US);
		String filename = sdf.format(new Date());
		return filename;
	}
	
	private static String getStorageDir() {
		String baseDir = Environment.getExternalStorageDirectory().toString();
		String directory = baseDir + "/Android/data/jonas.tool.saveForOffline/files/";
		return directory;
	}

	
	public static String getDestinationDirectory (SharedPreferences sharedPref) {

        String defaultFileLocation = getStorageDir() + createUniqueFilename() + File.separator;

        if (sharedPref.getBoolean("is_custom_storage_dir", false)) {
			String directoryLocation = sharedPref.getString("custom_storage_dir", defaultFileLocation) + createUniqueFilename() + File.separator;
			createNomediaFile(directoryLocation);
			return directoryLocation;
        } else {
            return defaultFileLocation;
        }

	}
	
	private static void createNomediaFile (String directoryLocation) {
		File directory = new File(directoryLocation);
		if (!directory.exists()) {
			if (!directory.mkdirs()) {
				Log.e("SaveService / DirectoryHelper", "Could not create directory " + directoryLocation + ", mkdirs() returned false !");
			}
			try {
				new File(directory.getParent(), ".nomedia").createNewFile();
			} catch (IOException e) {
				Log.e("SaveService / DirectoryHelper", "IOException while creating .nomedia file in " + directory.getParent() +" Is the path writable ?");
			}
		}
	}
	
	public static void deleteDirectory(File directory) {
		
		if (!directory.exists()) {
            return;
		}
		
       if (directory.isDirectory()) {
            for (File f : directory.listFiles()) {
              	 deleteDirectory(f);	 
            }
        }
       directory.delete();
	}
}
