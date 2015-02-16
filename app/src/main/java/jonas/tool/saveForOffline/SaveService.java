/**

This file is part of saveForOffline, an app which saves / downloads complete webpages.
Copyright (C) 2015  Jonas Czech

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
 This was originally based on getMeThatPage (https://github.com/PramodKhare/GetMeThatPage/),
 with lots of improvements.
**/

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
import java.util.ArrayList;
import java.util.Iterator;
import java.net.HttpURLConnection;
import org.jsoup.nodes.Document;
import org.jsoup.Jsoup;
import org.jsoup.select.Elements;
import org.jsoup.nodes.Element;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.net.MalformedURLException;

public class SaveService extends IntentService {

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
		GrabUtility.makeLinksAbsolute = sharedPref.getBoolean("make_links_absolute", true);
		GrabUtility.maxRetryCount = Integer.parseInt(sharedPref.getString("max_number_of_retries", "5"));
		GrabUtility.saveFrames = sharedPref.getBoolean("save_frames", true);
		GrabUtility.saveImages = sharedPref.getBoolean("save_images", true);
		GrabUtility.saveOther = sharedPref.getBoolean("save_other", true);
		GrabUtility.saveScripts = sharedPref.getBoolean("save_scripts", true);
		GrabUtility.saveVideo = sharedPref.getBoolean("save_video", true);

		if (ua.equals("desktop")) {
			uaString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/33.0.1750.517 Safari/537.36";

		} else if (ua.equals("ipad")) {
			uaString = "iPad ipad safari";

		} else {
			uaString = "Mozilla/5.0 (Linux; U; Android 4.2.2; en-us; Phone Build/IML74K) AppleWebkit/534.30 (KHTML, like Gecko) Version/4.0 Mobile Safari/534.30";
		}

		
	
		destinationDirectory = DirectoryHelper.getUnpackedDir();
		thumbnail = destinationDirectory + "saveForOffline_thumbnail.png";
		
		origurl = intent.getStringExtra("origurl");
		
		try {
			grabPage(origurl, destinationDirectory);
		} catch (Exception e) {
			e.printStackTrace();
			
			//if we crash, delete all files saved so far
			File file = new File(destinationDirectory);
			DirectoryHelper.deleteDirectory(file);
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
	
	
	private void notifyProgress(String filename, int maxProgress, int progress, boolean indeterminate) {
		//progress updates are sent here
		mBuilder
			.setContentText(filename)
			.setProgress(maxProgress, progress, indeterminate);
		mNotificationManager.notify(notification_id, mBuilder.build());
	}


	private void notifyError(String message, String extraMessage) {
		//if message == null, we can't continue
		//otherwise, the error is not fatal and we can continue anyway
		//this tells the user if this is so
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
		GrabUtility.cssToGrab.clear();
		GrabUtility.extraCssToGrab.clear();
		GrabUtility.title = "";
		
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
		
		//download main html and parse -- isExtra parameter should be false
		downloadHtmlAndParseLinks(url, outputDirPath, false);
		failCount = 0;
		
		//download and parse html frames
		for (String urlToDownload: GrabUtility.framesToGrab) {
			downloadHtmlAndParseLinks(urlToDownload, outputDirPath, true);
			failCount = 0;
		}
		
		//download and parse css files
		for (String urlToDownload: GrabUtility.cssToGrab) {
			downloadCssAndParseLinks(urlToDownload, outputDirPath);
			failCount = 0;
			
		}
		
		//download and parse extra css files
		for (String urlToDownload: GrabUtility.extraCssToGrab) {
			downloadCssAndParseLinks(urlToDownload, outputDirPath);
			failCount = 0;

		}

		//download extra files, such as images / scripts
		for (String urlToDownload: GrabUtility.filesToGrab) {
			
			getExtraFile(urlToDownload, outputDir);
			lt.d("Prepare to download file: " + urlToDownload);
			notifyProgress("Saving file: " + urlToDownload.substring(urlToDownload.lastIndexOf("/") + 1), GrabUtility.filesToGrab.size(), GrabUtility.filesToGrab.indexOf(urlToDownload), false);	
			failCount = 0;
	
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
			filename = url.substring(url.lastIndexOf("/") + 1).replaceAll("[^a-zA-Z0-9-_\\.]", "_");
		} else {
			filename = "index.html";
		}
		
		if (isExtra) {
			notifyProgress("Downloading extra HTML file", 100, 5, true);
		} else {
			notifyProgress("Downloading main HTML file", 100, 5, true);
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
			System.out.println(status);
			if (status != HttpURLConnection.HTTP_OK) {
				if (status == HttpURLConnection.HTTP_MOVED_TEMP
					|| status == HttpURLConnection.HTTP_MOVED_PERM
					|| status == HttpURLConnection.HTTP_SEE_OTHER){
					redirect = true;
				} else if (status == HttpURLConnection.HTTP_UNAUTHORIZED ||
						   status == HttpURLConnection.HTTP_ACCEPTED ||
						   status == HttpURLConnection.HTTP_CREATED ||
						   status == HttpURLConnection.HTTP_NOT_FOUND)
							{
								System.out.println("error");
								notifyError(null, "Possibe error. HTTP status code: " + status);
							   
				} else {
					if (isExtra) {
						notifyError(null, "Failed to download extra HTML file. HTTP status code: " + status);
						return;
					} else {
						notifyError("Could not save page", "Failed to download main HTML file. HTTP status code: " + status);
						throw new IOException("HTTP status not ok. status: " + status);
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
					throw new IOException("Cannont write to file");
				}
			}
			
			in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			
			String inputLine;
			StringBuilder strResponse = new StringBuilder();
			// append whole response into single string, save it into a file on storage
			// if its of type html then parse it and get all css and images and javascript files
			// and add them to filesToGrab list
			while ((inputLine = in.readLine()) != null) {
				strResponse.append(inputLine+"\r\n");
			}
			
			if (isExtra) {
				notifyProgress("Processing extra HTML file", 100, 5, true);
			} else {
				notifyProgress("Processing main HTML file", 100, 5, true);
			}
			
			String htmlContent = strResponse.toString();
		
			htmlContent = GrabUtility.parseHtmlForLinks(htmlContent, baseUrl);

			outputFile = new File(outputDir, filename);

		
				// clear previous files contents
				fop = new FileOutputStream(outputFile);
				fop.write(htmlContent.getBytes());
				fop.flush();
		
			
			failCount = 0;
		} catch (Exception e) {
			e.printStackTrace();
			failCount++;

			if (isExtra) {
				if (GrabUtility.maxRetryCount >= failCount) {
					notifyError(null, "Failed to download extra HTML file, retrying. Fail count: " + failCount );
					synchronized (this) {try {wait(2500);} catch (InterruptedException ex) {}}
					downloadHtmlAndParseLinks(url, outputDir, isExtra);
				} else {
					notifyError(null, "Failed to download extra HTML file.");
					return;
				}
			} else {
				if (GrabUtility.maxRetryCount >= failCount) {
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
	
	private void downloadCssAndParseLinks (String url, String outputDir) throws IOException {
		//todo: one method for saving & parsing both html & css, as they are very similar
		FileOutputStream fop = null;
		BufferedReader in = null;
		HttpURLConnection conn = null;
		File outputFile = null;
		InputStream is = null;
		String filename = url.substring(url.lastIndexOf("/") + 1).replaceAll("[^a-zA-Z0-9-_\\.]", "_");
		System.out.println(filename);
	
		notifyProgress("Downloading CSS file", 100, 5, true);

		try {
			URL obj = new URL(url);

			//Output file
			outputFile = new File(outputDir, filename);

			conn = (HttpURLConnection) obj.openConnection();
			conn.setReadTimeout(5000);
			conn.addRequestProperty("Accept-Language", "en-US,en;q=0.8");
			conn.addRequestProperty("User-Agent", uaString);
			conn.addRequestProperty("Referer", "google.com");

			boolean redirect = false;

			//catch possible redirect, normally, 3xx is redirect
			int status = conn.getResponseCode();
			if (status != HttpURLConnection.HTTP_OK) {
				if (status == HttpURLConnection.HTTP_MOVED_TEMP
					|| status == HttpURLConnection.HTTP_MOVED_PERM
					|| status == HttpURLConnection.HTTP_SEE_OTHER){
					redirect = true;
				}else{
					notifyError(null, "Failed to download CSS file. HTTP status code: " + status);
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
				conn.addRequestProperty("User-Agent", uaString);
				conn.addRequestProperty("Referer", "google.com");
			}

			// if file doesn't exists, then create it
			if (!outputFile.exists()) {
				outputFile.createNewFile();
			}
			// can we write this file
			if(!outputFile.canWrite()){
				
				notifyError("Could not save page", "Cannot write to file - "+outputFile.getAbsolutePath());
				System.out.println("Cannot write to file - "+outputFile.getAbsolutePath());
				return;
			
			}

			in = new BufferedReader(new InputStreamReader(conn.getInputStream()));

			String inputLine;
			StringBuilder strResponse = new StringBuilder();
			
			while ((inputLine = in.readLine()) != null) {
				strResponse.append(inputLine+"\r\n");
			}

			
			notifyProgress("Processing CSS file", 100, 5, true);

			String cssContent = strResponse.toString();

			//parse for links and convert to relative links
			System.out.println("Pre parse css ");
			cssContent = GrabUtility.parseCssForLinks(cssContent, url);
			System.out.println("Post parse css ");

			outputFile = new File(outputDir, filename);
			System.out.println("Create file parse css ");

				// clear previous files contents
				System.out.println("pre write file parse css ");
				fop = new FileOutputStream(outputFile);
				fop.write(cssContent.getBytes());
				fop.flush();
		

			failCount = 0;
		
		
		} catch (Exception e) {

			failCount++;
			e.printStackTrace();
			
			if (GrabUtility.maxRetryCount >= failCount) {
				notifyError(null, "Failed to download CSS file: " + filename+", retrying. Fail count: " + failCount);
				synchronized (this) {try {wait(2500);} catch (InterruptedException ex) {}}
				downloadCssAndParseLinks(url, outputDir);
			} else {
				notifyError(null, "Failed to download CSS file: " + filename);
				return;
			}
		} finally {
			try {
				if(conn != null) {
					conn.disconnect();
				}
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

	private void getExtraFile(String urlToDownload, File outputDir) {
		FileOutputStream fop = null;
		BufferedReader in = null;
		HttpURLConnection conn = null;
		File outputFile = null;
		InputStream is = null;
		URL obj;
		
		String filename = urlToDownload.substring(urlToDownload.lastIndexOf('/')+1).replaceAll("[^a-zA-Z0-9-_\\.]", "_");
	
		try {
			obj  = new URL(urlToDownload);
			
			if(filename.equals("/") || filename.equals("")){
				return;
			}

			//Output file name
			outputFile = new File(outputDir, filename);
			if (outputFile.exists()) {
				lt.e("File already exists, skipping: " + filename);
				return;
			}
			lt.d("Opening connection: " + urlToDownload);
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
				} else {
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

				lt.d("Redirect to URL : " + newUrl);
			}

			
			// if file doesn't exists, then create it
			if (!outputFile.exists()) {
				lt.d("Create file: " + filename);
				outputFile.createNewFile();
			}
			// can we write this file
			if(!outputFile.canWrite()){
				lt.e("Cannot write to file - "+outputFile.getAbsolutePath());
				return;
			}
			
			
				fop = new FileOutputStream(outputFile);
				is = conn.getInputStream();
				byte[] buffer = new byte[1024*32]; // read in batches of 32K
		        int length;
		        while ((length = is.read(buffer)) > 0) {
		            fop.write(buffer, 0, length);
	            }
					fop.flush();
				
			
		failCount = 0;
		
		} catch (Exception e) {
			failCount++;
			lt.e(e + "while getting extra file, printing stack trace...");
			e.printStackTrace();
			if (GrabUtility.maxRetryCount >= failCount) {
				notifyError(null, "Failed to download: " + filename + ", retrying. Fail count: " + failCount );
				synchronized (this) {try {wait(2500);} catch (InterruptedException ex) {}}
				getExtraFile(urlToDownload, outputDir);
				return;
			} else {
				notifyError(null, "Failed to download: " + filename);
				return;
			}
			
			
		} finally {
			try {
				if(conn != null) {
					conn.disconnect();
				}
				if(is != null){
					is.close();
				}
				if(in != null){
					in.close();
				}
				if(fop != null) {
					fop.close();
				}
			} catch (IOException e) {
				lt.e(e + "while closing streams");
				e.printStackTrace();
			}
		}
	}
}

/**
 * @author Pramod Khare & improved by Jonas Czech
 * Contains all the utility methods used in above class
 */
class GrabUtility{
	// filesToGrab - maintains all the links to files which we are going to grab/download
	public static List<String> filesToGrab = new ArrayList<String>();
	//framesToGrab - list of html frame files to download
	public static List<String> framesToGrab = new ArrayList<String>();
	//cssToGrab - list of all css files to download an parse
	public static List<String> cssToGrab = new ArrayList<String>();
	//we do another pass for this one
	public static List<String> extraCssToGrab = new ArrayList<String>();
	
	public static String title = "";
	public static int maxRetryCount = 5;
	public static boolean makeLinksAbsolute = true;
	
	public static boolean saveImages = true;
	public static boolean saveFrames = true;
	public static boolean saveOther = true;
	public static boolean saveScripts = true;
	public static boolean saveVideo = false;


	public static String parseHtmlForLinks(String htmlToParse, String baseUrl) {
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
		if (!htmlToParse.trim().equals("")) {

			if (noBaseUrl) {
				parsedHtml = Jsoup.parse(htmlToParse);
			} else {
				parsedHtml = Jsoup.parse(htmlToParse, baseUrl);
			}

			if (title == "") {
				title = parsedHtml.title();
			}
			
			Elements links;

			if (saveFrames) {
				links = parsedHtml.select("frame[src]");
				for (Element link: links) {
					urlToGrab = link.attr("abs:src");

					synchronized (framesToGrab) {
						if (!framesToGrab.contains(urlToGrab)) {
							framesToGrab.add(urlToGrab);
						}
					}

					String replacedURL = urlToGrab.substring(urlToGrab.lastIndexOf("/") + 1).replaceAll("[^a-zA-Z0-9-_\\.]", "_");
					link.attr("src", replacedURL);
				}

				links = parsedHtml.select("iframe[src]");
				for (Element link: links) {
					urlToGrab = link.attr("abs:src");

					synchronized (framesToGrab) {
						if (!framesToGrab.contains(urlToGrab)) {
							framesToGrab.add(urlToGrab);
						}
					}

					String replacedURL = urlToGrab.substring(urlToGrab.lastIndexOf("/") + 1).replaceAll("[^a-zA-Z0-9-_\\.]", "_");
					link.attr("src", replacedURL);


				}
			}

			if (saveOther) {
				// Get all the links
				links = parsedHtml.select("link[href]");
				for (Element link: links) {
					urlToGrab = link.attr("abs:href");
					
					//if it is css, parse it later to extract urls (images referenced from "background" attributes for example)
					if (link.attr("rel").equals("stylesheet")) {
						cssToGrab.add(link.attr("abs:href"));
					} else {
						addLinkToList(urlToGrab);
					}
					
					String replacedURL = urlToGrab.substring(urlToGrab.lastIndexOf("/") + 1).replaceAll("[^a-zA-Z0-9-_\\.]", "_");
					link.attr("href", replacedURL);

				}	
				//get links in embedded css also, and modify the links to point to local files
				links = parsedHtml.select("style[type=text/css]");
				for (Element link: links) {
					String cssToParse = link.data();
					String parsedCss = parseCssForLinks(cssToParse, baseUrl);
					if (link.dataNodes().size() != 0){
						link.dataNodes().get(0).setWholeData(parsedCss);
					}
				}
				
				links = parsedHtml.select("[style]");
				for (Element link: links) {
					String cssToParse = link.attr("style");
					String parsedCss = parseCssForLinks(cssToParse, baseUrl);
					link.attr("style", parsedCss);
				}
				
			}

			if (saveScripts) {
				links = parsedHtml.select("script[src]");
				for (Element link: links) {
					urlToGrab = link.attr("abs:src");
					addLinkToList(urlToGrab);
					String replacedURL = urlToGrab.substring(urlToGrab.lastIndexOf("/") + 1).replaceAll("[^a-zA-Z0-9-_\\.]", "_");
					link.attr("src", replacedURL);
				}
			}

			if (saveImages) {
				links = parsedHtml.select("img[src]");
				for (Element link: links) {
					urlToGrab = link.attr("abs:src");
					addLinkToList(urlToGrab);

					String replacedURL = urlToGrab.substring(urlToGrab.lastIndexOf("/") + 1).replaceAll("[^a-zA-Z0-9-_\\.]", "_");
					link.attr("src", replacedURL);
				}
			}
			
			if (saveVideo) {
				//video src is sometimes in a child element
				links = parsedHtml.select("video:not([src])");
				for (Element  link: links.select("[src]")){
					urlToGrab = link.attr("abs:src");
					addLinkToList(urlToGrab);

					String replacedURL = urlToGrab.substring(urlToGrab.lastIndexOf("/") + 1).replaceAll("[^a-zA-Z0-9-_\\.]", "_");
					link.attr("src", replacedURL);
				}
				
				links = parsedHtml.select("video[src]");
				for (Element  link: links){
					urlToGrab = link.attr("abs:src");
					addLinkToList(urlToGrab);

					String replacedURL = urlToGrab.substring(urlToGrab.lastIndexOf("/") + 1).replaceAll("[^a-zA-Z0-9-_\\.]", "_");
					link.attr("src", replacedURL);
				}
			}

			if (makeLinksAbsolute) {
				//make links absolute, so they are not broken
				links = parsedHtml.select("a[href]");
				for (Element link: links) {
					String absUrl = link.attr("abs:href");
					link.attr("href", absUrl);
				}
			}
		}
		return parsedHtml.outerHtml();
	}
	
	public static String parseCssForLinks(String cssToParse, String baseUrl) {
		
		String patternString = "url(\\s*\\(\\s*['\"]*\\s*)(.*?)\\s*['\"]*\\s*\\)"; //I hate regexes...
	
		Pattern pattern = Pattern.compile(patternString); 
		Matcher matcher = pattern.matcher(cssToParse); 
		
		System.out.println(patternString + "Parsing: css");
		
		//find everything inside url(" ... ")
		while (matcher.find()) {
		//	System.out.println("Original url:" + matcher.group().replaceAll(patternString, "$2"));

			if (matcher.group().replaceAll(patternString, "$2").contains("/")) {
				//System.out.println("Replaced url:" + matcher.group().replaceAll(patternString, "$2").substring(matcher.group().replaceAll(patternString, "$2").lastIndexOf("/") + 1).replaceAll("[^a-zA-Z0-9-_\\.]", "_"));
				cssToParse = cssToParse.replace(matcher.group().replaceAll(patternString, "$2"), matcher.group().replaceAll(patternString, "$2").substring(matcher.group().replaceAll(patternString, "$2").lastIndexOf("/") + 1).replaceAll("[^a-zA-Z0-9-_\\.]", "_"));
				
			}
			
			addLinkToList(makeLinkAbsolute(matcher.group().replaceAll(patternString, "$2").trim(), baseUrl));
		}
		
		// find css linked with @import  -  needs testing
		String importString = "@(import\\s*['\"])()([^ '\"]*)";
		pattern = Pattern.compile(importString); 
		matcher = pattern.matcher(cssToParse);
		matcher.reset();
		while (matcher.find()) {
			//	System.out.println("Original url:" + matcher.group().replaceAll(patternString, "$2"));

			if (matcher.group().replaceAll(patternString, "$2").contains("/")) {
				//System.out.println("Replaced url:" + matcher.group().replaceAll(patternString, "$2").substring(matcher.group().replaceAll(patternString, "$2").lastIndexOf("/") + 1).replaceAll("[^a-zA-Z0-9-_\\.]", "_"));
				cssToParse = cssToParse.replace(matcher.group().replaceAll(patternString, "$2"), matcher.group().replaceAll(patternString, "$2").substring(matcher.group().replaceAll(patternString, "$2").lastIndexOf("/") + 1).replaceAll("[^a-zA-Z0-9-_\\.]", "_"));

			}

			extraCssToGrab.add(makeLinkAbsolute(matcher.group().replaceAll(patternString, "$2").trim(), baseUrl));
		}
		
		return cssToParse;
	}

	public static void addLinkToList(String link) {
		//no multithreading for now
		synchronized (filesToGrab) {
			if (!filesToGrab.contains(link)) {
				filesToGrab.add(link);
			}
		}
	}
	
	public static String makeLinkAbsolute(String link, String baseurl) {
		
		try {
			URL u = new URL(new URL(baseurl), link);
			return u.toString();
		} catch (MalformedURLException e) {
			lt.e("MalformedURLException while making url absolute");
			lt.e("Link: " + link);
			lt.e("BaseURL: " + baseurl);
			return null;
		}

	}
}

class lt {
	//log messages are sent here
	public static void e (String message) {
		Log.e("SaveService", message);
	}
	public static void d (String message) {
		Log.d("SaveService", message);
	}
}
