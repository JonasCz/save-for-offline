package jonas.tool.saveForOffline;
import android.content.SharedPreferences;
import android.os.Environment;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.io.*;
import android.util.*;

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
