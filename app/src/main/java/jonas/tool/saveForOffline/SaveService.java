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
 The code for actually saving pages is further down in this file.
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
import org.jsoup.nodes.Entities;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.Channels;
import java.util.concurrent.TimeUnit;

public class SaveService extends IntentService {

	private String destinationDirectory;
	private String thumbnail;
	private String origurl;
	
	private String uaString;
	private boolean wasAddedToDb = false;
	
	private int failCount = 0;

	private int notification_id = 1;
	private Notification.Builder mBuilder;
	private NotificationManager mNotificationManager;
	
	public SaveService () {
		super("SaveService");
	}

	@Override
	public void onHandleIntent(Intent intent) {
		
		wasAddedToDb = false;
		
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
		
		lt.shouldLogDebug = sharedPref.getBoolean("enable_logging", false);
		lt.shouldLogErrors = sharedPref.getBoolean("enable_logging_error", true);
		
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
		
		notifyProgress("Adding to list...", 100, 97, false);
		
		addToDb();
		
		Intent i = new Intent(this, ScreenshotService.class);
		i.putExtra("origurl", "file://" + destinationDirectory + "index.html");
		i.putExtra("thumbnail", thumbnail);
		startService(i);
		
		notifyFinished();
		
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
	
	private void notifyFinished () {
		Intent notificationIntent = new Intent(this, MainActivity.class);
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
		
		mBuilder
			.setContentTitle("Save completed.")
			.setTicker("Saved: " + GrabUtility.title)
			.setSmallIcon(R.drawable.ic_notify_save)
			.setOngoing(false)
			.setProgress(0,0,false)
			.setOnlyAlertOnce(false)
			.setContentIntent(pendingIntent)
			.setAutoCancel(true)
			.setPriority(Notification.PRIORITY_LOW)
			.setContentText(GrabUtility.title);
		mNotificationManager.notify(notification_id, mBuilder.build());
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
			Intent notificationIntent = new Intent(this, SaveService.class);
			notificationIntent.putExtra("origurl", origurl);
			PendingIntent pendingIntent = PendingIntent.getService(this, 0, notificationIntent, 0);
			
			mBuilder
				.setContentText(extraMessage)
				.setOnlyAlertOnce(false)
				.setTicker("Could not save page!")
				.setSmallIcon(android.R.drawable.stat_sys_warning)
				.setContentTitle(message + " Tap to retry.")
				.setContentIntent(pendingIntent)
				.setAutoCancel(true)
				.setOngoing(false)
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
		lt.i("Download of main HTML...");
		downloadHtmlAndParseLinks(url, outputDirPath, false);
		failCount = 0;
		
		//download and parse html frames
		lt.i("Number of HTML frames to download: " + GrabUtility.extraCssToGrab.size());
		for (String urlToDownload: GrabUtility.framesToGrab) {
			downloadHtmlAndParseLinks(urlToDownload, outputDirPath, true);
			lt.i("--");
			failCount = 0;
		}
		lt.i("Finished download of HTML frames.");
		
		//download and parse css files
		lt.i("Number of CSS files to download: " + GrabUtility.extraCssToGrab.size());
		for (String urlToDownload: GrabUtility.cssToGrab) {
			downloadCssAndParseLinks(urlToDownload, outputDirPath);
			lt.i("--");
			failCount = 0;	
		}
		lt.i("Finished download of CSS files.");
		
		//download and parse extra css files
		//todo : make this recursive
		lt.i("Number of extra CSS files to download: " + GrabUtility.extraCssToGrab.size());
		for (String urlToDownload: GrabUtility.extraCssToGrab) {
			downloadCssAndParseLinks(urlToDownload, outputDirPath);
			lt.i("--");
			failCount = 0;
		}
		lt.i("Finished download of extra CSS files.");

		//download extra files, such as images / scripts
		lt.i("Number of files to download: " + GrabUtility.filesToGrab.size());
		for (String urlToDownload: GrabUtility.filesToGrab) {
			notifyProgress("Saving file: " + urlToDownload.substring(urlToDownload.lastIndexOf("/") + 1), GrabUtility.filesToGrab.size(), GrabUtility.filesToGrab.indexOf(urlToDownload), false);
			getExtraFile(urlToDownload, outputDir);
			lt.i("--");		
			failCount = 0;
	
		}
		
		lt.i("Finished download of extra files.");
		lt.i("Finished downloading HTML page.");

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
			filename = GrabUtility.getFileName(url);

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
		
		String filename = GrabUtility.getFileName(url);
		
		notifyProgress("Downloading CSS file", 100, 5, true);

		try {
			lt.i(lt.COMPONENT_CSS_FILE_DOWNLOADER, "Preparing to download css file");
			URL obj = new URL(url);

			//Output file
			outputFile = new File(outputDir, filename);

			conn = (HttpURLConnection) obj.openConnection();
			conn.setReadTimeout(5000);
			conn.addRequestProperty("User-Agent", uaString);
			boolean redirect = false;

			//catch possible redirect, normally, 3xx is redirect
			int status = conn.getResponseCode();
			lt.i(lt.COMPONENT_CSS_FILE_DOWNLOADER, "HTTP status: " + status);
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
				conn.addRequestProperty("User-Agent", uaString);
				
				lt.i(lt.COMPONENT_CSS_FILE_DOWNLOADER, "Redirect to URL: " + newUrl);
			}

			// if file doesn't exists, then create it
			if (!outputFile.exists()) {
				outputFile.createNewFile();
			}
			// can we write this file
			if(!outputFile.canWrite()){
				
				notifyError("Could not save page", "Cannot write to file - "+outputFile.getAbsolutePath());
				lt.i(lt.COMPONENT_CSS_FILE_DOWNLOADER, "Cannot write to file - "+outputFile.getAbsolutePath());
				return;
			
			}

			lt.i(lt.COMPONENT_CSS_FILE_DOWNLOADER, "Getting input stream...");
			in = new BufferedReader(new InputStreamReader(conn.getInputStream()));

			String inputLine;
			StringBuilder strResponse = new StringBuilder();
			
			while ((inputLine = in.readLine()) != null) {
				strResponse.append(inputLine+"\r\n");
			}

			
			notifyProgress("Processing CSS file", 100, 5, true);

			String cssContent = strResponse.toString();

			//parse for links and convert to relative links
			lt.i(lt.COMPONENT_CSS_FILE_DOWNLOADER, "Will now parse CSS");
			cssContent = GrabUtility.parseCssForLinks(cssContent, url);

			outputFile = new File(outputDir, filename);
			
			lt.i(lt.COMPONENT_CSS_FILE_DOWNLOADER, "Writing CSS file: " + filename);
			fop = new FileOutputStream(outputFile);
			fop.write(cssContent.getBytes());
			fop.flush();
			lt.i(lt.COMPONENT_CSS_FILE_DOWNLOADER, "CSS file downloaded.");
			failCount = 0;
		
		
		} catch (Exception e) {

			failCount++;
			e.printStackTrace();
			
			if (GrabUtility.maxRetryCount >= failCount) {
				notifyError(null, "Failed to download CSS file: " + filename+", retrying. Fail count: " + failCount);
				lt.e(lt.COMPONENT_CSS_FILE_DOWNLOADER, "Failed to download CSS file, retrying: " + filename);
				synchronized (this) {try {wait(2500);} catch (InterruptedException ex) {}}
				downloadCssAndParseLinks(url, outputDir);
			} else {
				notifyError(null, "Failed to download CSS file: " + filename);
				lt.e(lt.COMPONENT_CSS_FILE_DOWNLOADER, "Failed to download CSS file: " + filename);
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
				lt.e(lt.COMPONENT_CSS_FILE_DOWNLOADER, "Exception while closing streams!");
			}
		}
	}

	private void getExtraFile(String urlToDownload, File outputDir) {
		FileOutputStream fos = null;
		BufferedReader in = null;
		HttpURLConnection conn = null;
		File outputFile = null;
		InputStream is = null;
		URL obj;
		
		String filename = GrabUtility.getFileName(urlToDownload);
		
		try {
			lt.i(lt.COMPONENT_EXTRA_FILE_DOWNLOADER, "Preparing to download file: " + filename);
			obj  = new URL(urlToDownload);
			
			if(filename.equals("/") || filename.equals("")){
				return;
			}

			//Output file name
			outputFile = new File(outputDir, filename);
			if (outputFile.exists()) {
				lt.e(lt.COMPONENT_EXTRA_FILE_DOWNLOADER,"File already exists, skipping: " + filename);
				return;
			}
			lt.i(lt.COMPONENT_EXTRA_FILE_DOWNLOADER,"Opening connection: " + urlToDownload);
			conn = (HttpURLConnection) obj.openConnection();
			conn.setReadTimeout(5000);
			conn.addRequestProperty("User-Agent", uaString);

			boolean redirect = false;

			// normally, 3xx is redirect
			int status = conn.getResponseCode();
			lt.i(lt.COMPONENT_EXTRA_FILE_DOWNLOADER, "HTTP response status: " + status);
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
				conn.addRequestProperty("User-Agent", uaString);

				lt.i("Redirect to URL : " + newUrl);
			}

			
			// if file doesn't exists, then create it
			if (!outputFile.exists()) {
				lt.i(lt.COMPONENT_EXTRA_FILE_DOWNLOADER,"Create file: " + filename);
				outputFile.createNewFile();
			}
			// can we write this file
			if(!outputFile.canWrite()){
				lt.e(lt.COMPONENT_EXTRA_FILE_DOWNLOADER,"Cannot write to file - "+outputFile.getAbsolutePath());
				return;
			}
			
			lt.i(lt.COMPONENT_EXTRA_FILE_DOWNLOADER, "Downloading file...");
			
			ReadableByteChannel rbc = Channels.newChannel(conn.getInputStream());
			fos = new FileOutputStream(outputFile);
			fos.getChannel().transferFrom(rbc, 0, 1024*32);
			fos.flush();
			
//			is = conn.getInputStream();
//			byte[] buffer = new byte[1024*16]; // read in batches of 16K
//	        int length;
//	        while ((length = is.read(buffer)) > 0) {
//		       fop.write(buffer, 0, length);
//	        }
//			
//			fop.flush();
			
			lt.i(lt.COMPONENT_EXTRA_FILE_DOWNLOADER, "Extra file downloaded.");
				
			
		failCount = 0;
		
		} catch (Exception e) {
			failCount++;
			lt.e(lt.COMPONENT_EXTRA_FILE_DOWNLOADER, e + "while getting extra file, printing stack trace...");
			e.printStackTrace();
			if (GrabUtility.maxRetryCount >= failCount) {
				notifyError(null, "Failed to download: " + filename + ", retrying. Fail count: " + failCount );
				lt.e(lt.COMPONENT_EXTRA_FILE_DOWNLOADER, "Error while getting extra file, waiting and retrying...");
				try {
					TimeUnit.SECONDS.sleep(3);
				} catch (InterruptedException ex) {}
				
				getExtraFile(urlToDownload, outputDir);
				return;
			} else {
				notifyError(null, "Failed to download: " + filename);
				lt.e(lt.COMPONENT_EXTRA_FILE_DOWNLOADER, "Error while getting extra file!");
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
				if(fos != null) {
					fos.close();
				}
			} catch (IOException e) {
				lt.e(lt.COMPONENT_EXTRA_FILE_DOWNLOADER,e + "while closing streams!");
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
		boolean noBaseUrl = false;
		try {
			fromHTMLPageUrl = new URL(baseUrl);
			noBaseUrl = false;
			lt.e(lt.COMPONENT_HTML_PARSER, "Setting base url");
		} catch (MalformedURLException e) {
			fromHTMLPageUrl = null;
			noBaseUrl = true;
			lt.e(lt.COMPONENT_HTML_PARSER, "Error while setting base url!");
			
		}

		String urlToGrab = null;
		if (!htmlToParse.trim().equals("")) {

			if (noBaseUrl) {
				parsedHtml = Jsoup.parse(htmlToParse);
				lt.e(lt.COMPONENT_HTML_PARSER, "Parsing without base url!");
			} else {
				parsedHtml = Jsoup.parse(htmlToParse, baseUrl);
			}
			
			parsedHtml.outputSettings().escapeMode(Entities.EscapeMode.xhtml);

			if (title == "") {
				title = parsedHtml.title();
				lt.i(lt.COMPONENT_HTML_PARSER, "Got title");
			}
			
			Elements links;

			if (saveFrames) {
				links = parsedHtml.select("frame[src]");
				lt.i(lt.COMPONENT_HTML_PARSER, "Got " + links.size() + " frames");
				for (Element link: links) {
					urlToGrab = link.attr("abs:src");

					
					if (!framesToGrab.contains(urlToGrab)) {
						framesToGrab.add(urlToGrab);
					}

					String replacedURL = GrabUtility.getFileName(urlToGrab);
					link.attr("src", replacedURL);
				}

				links = parsedHtml.select("iframe[src]");
				lt.i(lt.COMPONENT_HTML_PARSER, "Got " + links.size() + " iframes");
				for (Element link: links) {
					urlToGrab = link.attr("abs:src");

					synchronized (framesToGrab) {
						if (!framesToGrab.contains(urlToGrab)) {
							framesToGrab.add(urlToGrab);
						}
					}

					String replacedURL = GrabUtility.getFileName(urlToGrab);
					link.attr("src", replacedURL);


				}
			}

			if (saveOther) {
				// Get all the links
				links = parsedHtml.select("link[href]");
				lt.i(lt.COMPONENT_HTML_PARSER, "Got " + links.size() + " link elements with a href attribute");
				for (Element link: links) {
					urlToGrab = link.attr("abs:href");
					
					//if it is css, parse it later to extract urls (images referenced from "background" attributes for example)
					if (link.attr("rel").equals("stylesheet")) {
						cssToGrab.add(link.attr("abs:href"));
					} else {
						addLinkToList(urlToGrab);
					}
					
					String replacedURL = GrabUtility.getFileName(urlToGrab);
					link.attr("href", replacedURL);

				}
				
				//get links in embedded css also, and modify the links to point to local files
				links = parsedHtml.select("style[type=text/css]");
				lt.i(lt.COMPONENT_HTML_PARSER, "Got " + links.size() + " embedded stylesheets, parsing CSS");
				for (Element link: links) {
					String cssToParse = link.data();
					String parsedCss = parseCssForLinks(cssToParse, baseUrl);
					if (link.dataNodes().size() != 0){
						link.dataNodes().get(0).setWholeData(parsedCss);
					}
				}
				
				links = parsedHtml.select("[style]");
				lt.i(lt.COMPONENT_HTML_PARSER, "Got " + links.size() + " elements with a style attribute, parsing CSS");
				for (Element link: links) {
					String cssToParse = link.attr("style");
					String parsedCss = parseCssForLinks(cssToParse, baseUrl);
					link.attr("style", parsedCss);
				}
				
			}

			if (saveScripts) {
				links = parsedHtml.select("script[src]");
				lt.i(lt.COMPONENT_HTML_PARSER, "Got " + links.size() + " script elements");
				for (Element link: links) {
					urlToGrab = link.attr("abs:src");
					addLinkToList(urlToGrab);
					String replacedURL = GrabUtility.getFileName(urlToGrab);
					link.attr("src", replacedURL);
				}
			}

			if (saveImages) {
				links = parsedHtml.select("img[src]");
				lt.i(lt.COMPONENT_HTML_PARSER, "Got " + links.size() + " image elements");
				for (Element link: links) {
					urlToGrab = link.attr("abs:src");
					addLinkToList(urlToGrab);

					String replacedURL = GrabUtility.getFileName(urlToGrab);
					link.attr("src", replacedURL);
				}
			}
			
			if (saveVideo) {
				//video src is sometimes in a child element
				links = parsedHtml.select("video:not([src])");
				lt.i(lt.COMPONENT_HTML_PARSER, "Got " + links.size() + " video elements without src attribute");
				for (Element  link: links.select("[src]")){
					urlToGrab = link.attr("abs:src");
					addLinkToList(urlToGrab);

					String replacedURL = GrabUtility.getFileName(urlToGrab);
					link.attr("src", replacedURL);
				}
				
				links = parsedHtml.select("video[src]");
				lt.i(lt.COMPONENT_HTML_PARSER, "Got " + links.size() + " video elements");
				for (Element  link: links){
					urlToGrab = link.attr("abs:src");
					addLinkToList(urlToGrab);

					String replacedURL = GrabUtility.getFileName(urlToGrab);
					link.attr("src", replacedURL);
				}
			}

			if (makeLinksAbsolute) {
				//make links absolute, so they are not broken
				links = parsedHtml.select("a[href]");
				lt.i(lt.COMPONENT_HTML_PARSER, "Making " + links.size() + " links absolute");
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
		
		lt.i(lt.COMPONENT_CSS_PARSER, "Parsing CSS");
		
		int count = 0;
		//find everything inside url(" ... ")
		while (matcher.find()) {
			count++;
			lt.i(lt.COMPONENT_CSS_PARSER,"Original url:" + matcher.group().replaceAll(patternString, "$2"));

			if (matcher.group().replaceAll(patternString, "$2").contains("/")) {
				lt.i(lt.COMPONENT_CSS_PARSER, "Replaced url:" + getFileName(matcher.group().replaceAll(patternString, "$2")));
				cssToParse = cssToParse.replace(matcher.group().replaceAll(patternString, "$2"), getFileName(matcher.group().replaceAll(patternString, "$2")));
				
			}
			
			addLinkToList(makeLinkAbsolute(matcher.group().replaceAll(patternString, "$2").trim(), baseUrl));
		}
		
		lt.i(lt.COMPONENT_CSS_PARSER, "Found " + count + " URLs in CSS");
		
		// find css linked with @import  -  needs testing
		String importString = "@(import\\s*['\"])()([^ '\"]*)";
		pattern = Pattern.compile(importString); 
		matcher = pattern.matcher(cssToParse);
		matcher.reset();
		count = 0;
		while (matcher.find()) {
			lt.i(lt.COMPONENT_CSS_PARSER, "Original url from @import: " + matcher.group().replaceAll(patternString, "$2"));
			count++;
			if (matcher.group().replaceAll(patternString, "$2").contains("/")) {
				lt.i(lt.COMPONENT_CSS_PARSER, "Replaced url:" + getFileName(matcher.group().replaceAll(patternString, "$2")));
				cssToParse = cssToParse.replace(matcher.group().replaceAll(patternString, "$2"), getFileName(matcher.group().replaceAll(patternString, "$2")));
				
			}

			extraCssToGrab.add(makeLinkAbsolute(matcher.group().replaceAll(patternString, "$2").trim(), baseUrl));
			lt.i(lt.COMPONENT_CSS_PARSER, "Found " + count + " @import liked stylesheets in CSS");
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
			lt.e(lt.COMPONENT_CSS_PARSER, "MalformedURLException while making url absolute");
			lt.e(lt.COMPONENT_CSS_PARSER, "Link: " + link);
			lt.e(lt.COMPONENT_CSS_PARSER, "BaseURL: " + baseurl);
			return null;
		}

	}
	
	public static String getFileName(String url) {
		
		String filename = url.substring(url.lastIndexOf('/')+1);

		if (filename.contains("?")) {
			filename = filename.substring(0, filename.indexOf("?"));
		}

		filename = filename.replaceAll("[^a-zA-Z0-9-_\\.]", "_");
		
		return filename;
	}
}

class lt {
	//log messages are sent here
	public static boolean shouldLogDebug = false;
	public static boolean shouldLogErrors = true;
	
	public static final String COMPONENT_PARSER = "Parser";
	public static final String COMPONENT_CSS_PARSER = "CSS Parser";
	public static final String COMPONENT_HTML_PARSER = "HTML Parser";
	public static final String COMPONENT_EXTRA_FILE_DOWNLOADER = "Extra file downloader";
	public static final String COMPONENT_CSS_FILE_DOWNLOADER = "CSS file downloader";
	public static final String COMPONENT_HTML_FILE_DOWNLOADER = "HTML file downloader";
	
	public static void e (String message) {
		if (shouldLogErrors) {
			Log.e("SaveService", message);
		}	
	}
	
	public static void e (String component, String message) {
		if (shouldLogErrors) {
			Log.e("SaveService: " + component, message);
		}	
	}
	
	public static void i (String message) {	
		if (shouldLogDebug) {
			Log.i("SaveService", message);
		}
	}
	
	public static void i (String component, String message) {
		if (shouldLogDebug) {
			Log.i("SaveService: " + component, message);
		}		
	}
}
