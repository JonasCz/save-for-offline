package jonas.tool.saveForOffline;
import java.io.*;
import android.content.*;
import android.util.*;
import java.text.*;
import java.util.*;
import android.os.*;

public class DirectoryHelper {
	
	public static String createUniqueFilename () {
		
		//creates filenames based on the date and time, hopefully unique
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US);
		String filename = sdf.format(new Date());
		return filename;
	}
	
	private static String getStorageDir() {
		//FIXME!!!! replace this with getExternalStorageDir()
		
		String baseDir = Environment.getExternalStorageDirectory().toString();
		String directory = baseDir + "/Android/data/jonas.tool.saveForOffline/files/";
		File file = new File(directory);
		file.mkdirs();
		return directory;
	}
	
	public static String getThumbnailLocation () {
		
		String thumbnailLocation = getStorageDir() + createUniqueFilename() + ".png";
		return thumbnailLocation;
	}
	
	public static String getFileLocation () {

		String fileLocation = getStorageDir() + createUniqueFilename() + ".mht";
		return fileLocation;
	}
	
	public static String getUnpackedDir () {

		String fileLocation = getStorageDir() + createUniqueFilename() + File.separatorChar;
		return fileLocation;
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
