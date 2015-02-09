package jonas.tool.saveForOffline;

import android.app.*;
import android.content.*;
import android.widget.*;
import android.util.*;
import android.webkit.*;
import android.graphics.*;
import java.io.*;
import android.database.sqlite.*;
import android.view.View.*;
import android.os.*;
import android.preference.*;
import android.os.Process;
import java.net.URL;
import java.util.List;
import java.util.StringTokenizer;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.net.HttpURLConnection;
import org.jsoup.nodes.Document;
import org.jsoup.Jsoup;
import org.jsoup.select.Elements;
import org.jsoup.nodes.Element;
import java.net.MalformedURLException;

public class SaveService extends IntentService {

	
	private String filelocation;
	private String destinationDirectory;
	private String thumbnail;
	private String origurl;
	
	private String uaString;

	private boolean thumbnailWasSaved = false;
	
	private boolean wasAddedToDb = false;
	
	private boolean errorOccurred = false;
	private String errorDescription = "";
	
	private int failCount = 0;

	private int notification_id = 1;
	private Notification.Builder mBuilder;
	private NotificationManager mNotificationManager;
	
	public SaveService () {
		super("SaveService");
	}

	@Override
	public void onHandleIntent(Intent intent) {
		
		errorDescription = "";
		wasAddedToDb = false;
		errorOccurred = false;
		
		thumbnailWasSaved = false;
		

		mBuilder = new Notification.Builder(SaveService.this)
			.setContentTitle("Saving webpage...")
			.setTicker("Saving webpage...")
			.setSmallIcon(android.R.drawable.stat_sys_download)
			.setProgress(0,0, true)
			.setOngoing(true)
			.setOnlyAlertOnce(true)
			.setPriority(Notification.PRIORITY_HIGH)
			.setContentText("Save in progress: getting ready...");
			
		mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		mNotificationManager.notify(notification_id, mBuilder.build());
		

		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(SaveService.this);
		String ua = sharedPref.getString("user_agent", "mobile");

		if (ua.equals("desktop")) {
			uaString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/33.0.1750.517 Safari/537.36";

		} else if (ua.equals("ipad")) {
			uaString = "iPad ipad safari";

		} else {
			uaString = "android";
		}

		DirectoryHelper dh = new DirectoryHelper();
		filelocation = dh.getFileLocation();
		destinationDirectory = dh.getUnpackedDir();
		thumbnail = dh.getThumbnailLocation();
		
		origurl = intent.getStringExtra("origurl");
		
		try {
			grabPage(origurl, destinationDirectory);
		} catch (Exception e) {
			return;
		}
		
		addToDb();
		
		Intent i = new Intent(this, ScreenshotService.class);
		i.putExtra("origurl", "file://" + destinationDirectory + "index.html");
		i.putExtra("thumbnail", thumbnail);
		startService(i);
		
		mBuilder
			.setContentTitle("Save completed.")
			.setTicker("Saved: " + GrabUtility.title)
			.setSmallIcon(R.drawable.ic_notify_save)
			.setOngoing(false)
			.setProgress(0,0,false)
			.setOnlyAlertOnce(false)
			.setPriority(Notification.PRIORITY_LOW)
			.setContentText(GrabUtility.title);
		mNotificationManager.notify(notification_id, mBuilder.build());
	}

	private void addToDb() {

		//dont want to put it in the database multiple times
		if (wasAddedToDb) return;

		DbHelper mHelper = new DbHelper(SaveService.this);
		SQLiteDatabase dataBase = mHelper.getWritableDatabase();
		ContentValues values=new ContentValues();

		
		values.put(DbHelper.KEY_FILE_LOCATION, destinationDirectory + "index.html");

		values.put(DbHelper.KEY_TITLE, GrabUtility.title);
		values.put(DbHelper.KEY_THUMBNAIL, thumbnail);
		values.put(DbHelper.KEY_ORIG_URL, origurl);

		//insert data into database
		dataBase.insert(DbHelper.TABLE_NAME, null, values);

		//close database
		dataBase.close();

		wasAddedToDb = true;
	}
	
	private void notifyProgress(String filename, int maxProgress, int progress) {
		mBuilder
			.setContentText(filename)
			.setProgress(maxProgress, progress, false);
		mNotificationManager.notify(notification_id, mBuilder.build());
	}


	private void notifyError(String message, String extraMessage) {
		if (message != null) {
			mBuilder
				.setContentText(extraMessage)
				.setOnlyAlertOnce(false)
				.setTicker("Could not save page!")
				.setSmallIcon(android.R.drawable.stat_sys_warning)
				.setContentTitle(message)
				.setProgress(0,0,false);

		} else {
			mBuilder
				.setContentText(extraMessage);
		}
		mNotificationManager.notify(notification_id, mBuilder.build());


	}
	
	private void grabPage(String url, String outputDirPath) throws Exception {

		GrabUtility.filesToGrab.clear();
		GrabUtility.framesToGrab.clear();
		GrabUtility.title = null;
		
        if(url == null || outputDirPath == null){
        	notifyError("Page not saved", "There was an internal error, this is a bug, so please report it.");
	        throw new IllegalArgumentException();
		}
		if(!url.startsWith("http")){
			if (url.startsWith("file://")) {
				notifyError("Bad url","Cannot save local files. URL must not start with file://");
			} else {
				notifyError("Bad url","URL to save must start with http:// or https://");
			}
			throw new IllegalArgumentException("URL does not have valid protocol part. Must start with http:// or https://");
		}
		
		File outputDir = new File(outputDirPath);
		
		if(outputDir.exists() && outputDir.isFile()){
			System.out.println("output directory path is wrong, please provide some directory path");
			return;
		} else if (!outputDir.exists()){
			outputDir.mkdirs();
		}
		
		//download main html
		downloadHtmlAndParseLinks(url, outputDirPath, false);
		
		//download and parse html frames
		for (String urlToDownload: GrabUtility.framesToGrab) {

			downloadHtmlAndParseLinks(urlToDownload, outputDirPath, true);

		}

		//download extra files

		for (String urlToDownload: GrabUtility.filesToGrab) {
			
			getExtraFile(urlToDownload, outputDir);
			
			synchronized (GrabUtility.filesToGrab) {		
				notifyProgress("Saving file: " + urlToDownload.substring(urlToDownload.lastIndexOf("/") + 1), GrabUtility.filesToGrab.size(), GrabUtility.filesToGrab.indexOf(urlToDownload));	
				
			}
			
		}

	}
	
	private void downloadHtmlAndParseLinks (String url, String outputDir, boolean isExtra) throws IOException {
		//isExtra should be true when saving a html frame file.
		FileOutputStream fop = null;
		BufferedReader in = null;
		HttpURLConnection conn = null;
		File outputFile = null;
		InputStream is = null;
		String filename;
		
		if (isExtra) {
			filename = url.substring(url.lastIndexOf("/") + 1);
		} else {
			filename = "index.html";
		}
		
		if (isExtra) {
			notifyProgress("Downloading extra HTML file", 100, 5);
		} else {
			notifyProgress("Downloading main HTML file", 100, 1);
		}
	
		try {
			
			String baseUrl;
			if(url.endsWith("/")){
				baseUrl = url + "index.html";
			} else {
				baseUrl = url;
			}
			
			URL obj = new URL(url);
			

			//Output file name
			outputFile = new File(outputDir, filename);

			conn = (HttpURLConnection) obj.openConnection();
			conn.setReadTimeout(5000);
			conn.addRequestProperty("Accept-Language", "en-US,en;q=0.8");
			conn.addRequestProperty("User-Agent", uaString);
			conn.addRequestProperty("Referer", "google.com");

			boolean redirect = false;

			// normally, 3xx is redirect
			int status = conn.getResponseCode();
			if (status != HttpURLConnection.HTTP_OK && status != HttpURLConnection.HTTP_NOT_FOUND) {
				if (status == HttpURLConnection.HTTP_MOVED_TEMP
					|| status == HttpURLConnection.HTTP_MOVED_PERM
					|| status == HttpURLConnection.HTTP_SEE_OTHER){
					redirect = true;
				}else{
					if (isExtra) {
						notifyError(null, "Failed to download extra HTML file. HTTP status code: " + status);
						return;
					} else {
						notifyError("Could not save page", "Failed to download main HTML file. HTTP status code: " + status);
						throw new IOException();
					}
					
				}
			}

			if (redirect) {
				// get redirect url from "location" header field
				String newUrl = conn.getHeaderField("Location");
				
				// get the cookie if need, for login
				String cookies = conn.getHeaderField("Set-Cookie");

				// open the new connnection again
				obj =  new URL(newUrl);
				conn = (HttpURLConnection) obj.openConnection();
				conn.setRequestProperty("Cookie", cookies);
				conn.addRequestProperty("Accept-Language", "en-US,en;q=0.8");
				conn.addRequestProperty("User-Agent", uaString);
				conn.addRequestProperty("Referer", "google.com");
			}

			// if file doesn't exists, then create it
			if (!outputFile.exists()) {
				outputFile.createNewFile();
			}
			// can we write this file
			if(!outputFile.canWrite()){
				if (isExtra) {
					notifyError("Could not save page", "Cannot write to file - "+outputFile.getAbsolutePath());
					System.out.println("Cannot write to file - "+outputFile.getAbsolutePath());
					return;
				} else {
					notifyError("Could not save page", "Cannot write to file - "+outputFile.getAbsolutePath());
					System.out.println("Cannot write to file - "+outputFile.getAbsolutePath());
					throw new IOException();
				}
			}
			
			in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			
			String inputLine;
			StringBuffer strResponse = new StringBuffer();
			// append whole response into single string, save it into a file on storage
			// if its of type html then parse it and get all css and images and javascript files
			// and add them to filesToGrab list
			while ((inputLine = in.readLine()) != null) {
				strResponse.append(inputLine+"\r\n");
			}
			
			if (isExtra) {
				notifyProgress("Processing extra HTML file", 100, 10);
			} else {
				notifyProgress("Processing main HTML file", 100, 5);
			}
			
			String htmlContent = strResponse.toString();
		
			htmlContent = GrabUtility.searchForNewFilesToGrab(htmlContent, baseUrl);

			outputFile = new File(outputDir, filename);

			try {
				// clear previous files contents
				fop = new FileOutputStream(outputFile);
				fop.write(htmlContent.getBytes());
				fop.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			failCount = 0;
		} catch (Exception e) {
			
			failCount++;

			if (isExtra) {
				if (failCount <= 5) {
					notifyError(null, "Failed to download extra HTML file, retrying. Fail count: " + failCount );
					synchronized (this) {try {wait(2500);} catch (InterruptedException ex) {}}
					downloadHtmlAndParseLinks(url, outputDir, isExtra);
				} else {
					notifyError(null, "Failed to download extra HTML file: " + filename);
					return;
				}
			} else {
				if (failCount <= 5) {
					notifyError(null, "Failed to download main HTML file, retrying. Fail count: " + failCount );
					synchronized (this) {try {wait(2500);} catch (InterruptedException ex) {}}
					downloadHtmlAndParseLinks(url, outputDir, isExtra);
				} else {
					notifyError("Could not save page", "Failed to download main HTML file.");
					throw new IOException();
				}
			}
			

		} finally {
			try {
				if(is != null){
					is.close();
				}
				if(in != null){
					in.close();
				}
				if (fop != null) {
					fop.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void getExtraFile(String urlToDownload, File outputDir) throws IOException {
		FileOutputStream fop = null;
		BufferedReader in = null;
		HttpURLConnection conn = null;
		File outputFile = null;
		InputStream is = null;
		try {
			URL obj = new URL(urlToDownload);
			String path = obj.getPath();
			String filename = path.substring(path.lastIndexOf('/')+1);
			if(filename.equals("/") || filename.equals("")){
				return;
			}

			//Output file name
			outputFile = new File(outputDir, filename);
			if (outputFile.exists()) {
				return;
			}

			conn = (HttpURLConnection) obj.openConnection();
			conn.setReadTimeout(5000);
			conn.addRequestProperty("Accept-Language", "en-US,en;q=0.8");
			conn.addRequestProperty("User-Agent", uaString);
			conn.addRequestProperty("Referer", "google.com");

			boolean redirect = false;

			// normally, 3xx is redirect
			int status = conn.getResponseCode();
			if (status != HttpURLConnection.HTTP_OK) {
				if (status == HttpURLConnection.HTTP_MOVED_TEMP
					|| status == HttpURLConnection.HTTP_MOVED_PERM
					|| status == HttpURLConnection.HTTP_SEE_OTHER){
					redirect = true;
				}else{
					
					return;
				}
			}

			if (redirect) {
				// get redirect url from "location" header field
				String newUrl = conn.getHeaderField("Location");

				// get the cookie if need, for login
				String cookies = conn.getHeaderField("Set-Cookie");

				// open the new connnection again
				obj =  new URL(newUrl);
				conn = (HttpURLConnection) obj.openConnection();
				conn.setRequestProperty("Cookie", cookies);
				conn.addRequestProperty("Accept-Language", "en-US,en;q=0.8");
				conn.addRequestProperty("User-Agent", "Mozilla");
				conn.addRequestProperty("Referer", "google.com");

				System.out.println("Redirect to URL : " + newUrl);
			}

			// if file doesn't exists, then create it
			if (!outputFile.exists()) {
				outputFile.createNewFile();
			}
			// can we write this file
			if(!outputFile.canWrite()){
				System.out.println("Cannot write to file - "+outputFile.getAbsolutePath());
				return;
			}
			
			
			try {
				fop = new FileOutputStream(outputFile);
				is = conn.getInputStream();
				byte[] buffer = new byte[1024*32]; // read in batches of 32K
		        int length;
		        while ((length = is.read(buffer)) > 0) {
		            fop.write(buffer, 0, length);
	            }
					fop.flush();
					failCount = 0;					
				} catch (IOException e) {
					failCount++;

					if (failCount <= 5) {
						notifyError(null, "Failed to download: " + outputFile.getName() + ", retrying. Fail count: " + failCount );
						synchronized (this) {try {wait(2500);} catch (InterruptedException ex) {}}
						getExtraFile(urlToDownload, outputDir);
					} else {
						notifyError(null, "Failed to download: " + outputFile.getName());
						//handle it properly
					}
				
			}
		failCount = 0;
		} catch (Exception e) {
			failCount++;
			
			if (failCount <= 5) {
				notifyError(null, "Failed to download: " + outputFile.getName() + ", retrying. Fail count: " + failCount );
				synchronized (this) {try {wait(2500);} catch (InterruptedException ex) {}}
				getExtraFile(urlToDownload, outputDir);
			} else {
				notifyError(null, "Failed to download: " + outputFile.getName());
				
			}
			
			
		} finally {
			try {
				if(is != null){
					is.close();
				}
				if(in != null){
					in.close();
				}
				if (fop != null) {
					fop.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}

/**
 * @author Pramod Khare
 * Contains all the utility methods used in above GrabWebPage class
 */
class GrabUtility{
	// filesToGrab - maintains all the links to files which we are going to grab/download
	public static List<String> filesToGrab = new ArrayList<String>();
	//framesToGrab - list of html frame files to download
	public static List<String> framesToGrab = new ArrayList<String>();
	
	public static String title;


	public static String searchForNewFilesToGrab(String htmlToParse, String baseUrl){
		//get all links from this webpage and add them to Frontier List i.e. LinksToVisit ArrayList
		Document parsedHtml = null;
		URL fromHTMLPageUrl;
		System.out.println(baseUrl);
		boolean noBaseUrl = false;
		try {
			fromHTMLPageUrl = new URL(baseUrl);
			noBaseUrl = false;
		} catch (MalformedURLException e) {
			fromHTMLPageUrl = null;
			noBaseUrl = true;
		}
		
		String urlToGrab = null;
		if(!htmlToParse.trim().equals("")){
			
			if (noBaseUrl) {
				parsedHtml = Jsoup.parse(htmlToParse);
			} else {
				parsedHtml = Jsoup.parse(htmlToParse, baseUrl);
			}
			
			if (parsedHtml.title() != "") {
				title = parsedHtml.title();
			}
			
			
			Elements links = parsedHtml.select("frame[src]");
			for(Element link: links){
				urlToGrab = link.attr("abs:src");
				synchronized (framesToGrab) {
					if (!framesToGrab.contains(urlToGrab)) {
						framesToGrab.add(urlToGrab);
					}
				}
				String replacedURL = urlToGrab.substring(urlToGrab.lastIndexOf("/")+1);
				link.attr("src", replacedURL);
			}
			
			links = parsedHtml.select("iframe[src]");
			for(Element link: links){
				urlToGrab = link.attr("abs:src");
				synchronized (framesToGrab) {
					if (!framesToGrab.contains(urlToGrab)) {
						framesToGrab.add(urlToGrab);
					}
				}
				String replacedURL = urlToGrab.substring(urlToGrab.lastIndexOf("/")+1);
				link.attr("src", replacedURL);


			}
			
			// Get all the links
			links = parsedHtml.select("link[href]");
			for(Element link: links){
				urlToGrab = link.attr("abs:href");
				addLinkToList(urlToGrab,fromHTMLPageUrl);
				String replacedURL = urlToGrab.substring(urlToGrab.lastIndexOf("/")+1);
				link.attr("href", replacedURL);
		
				
			}

		
			links = parsedHtml.select("script[src]");
			for(Element link: links){
				urlToGrab = link.attr("abs:src");
				addLinkToList(urlToGrab, fromHTMLPageUrl);
				String replacedURL = urlToGrab.substring(urlToGrab.lastIndexOf("/")+1);
				link.attr("src", replacedURL);
			}
			
		
			links = parsedHtml.select("img[src]");
			for(Element link: links){
				urlToGrab = link.attr("abs:src");
				addLinkToList(urlToGrab, fromHTMLPageUrl);
				
				String replacedURL = urlToGrab.substring(urlToGrab.lastIndexOf("/")+1);
				link.attr("src", replacedURL);
			}
		}
		return parsedHtml.toString();
	}

	public static void addLinkToList(String link, URL fromHTMLPageUrl) {
		synchronized (filesToGrab) {
			if (!filesToGrab.contains(link)) {
				filesToGrab.add(link);
			}
		}
	}
}
