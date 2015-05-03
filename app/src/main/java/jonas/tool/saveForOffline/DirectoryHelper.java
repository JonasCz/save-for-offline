package jonas.tool.saveForOffline;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DirectoryHelper {
	
	public static String createUniqueFilename () {
		
		//creates filenames based on the date and time, hopefully unique
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US);
		String filename = sdf.format(new Date());
		return filename;
	}
	
	private static String getStorageDir() {
		//FIXME!!!! replace this with getExternalFilesDir()
		
		String baseDir = Environment.getExternalStorageDirectory().toString();
		String directory = baseDir + "/Android/data/jonas.tool.saveForOffline/files/";
		File file = new File(directory);
		file.mkdirs();
		return directory;
	}

	
	public static String getDestinationDirectory (Context context) {

        String defaultFileLocation = getStorageDir() + createUniqueFilename() + File.separatorChar;

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);

        if (sharedPref.getBoolean("is_custom_storage_dir", false)) {
            return sharedPref.getString("custom_storage_dir" + createUniqueFilename() + File.separatorChar, defaultFileLocation);
        }

		return defaultFileLocation;
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
