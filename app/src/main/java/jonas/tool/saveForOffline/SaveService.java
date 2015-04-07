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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.preference.PreferenceManager;
import android.util.Log;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SaveService extends Service {

    private String destinationDirectory;
    private String thumbnail;
    private String origurl;

    private String uaString;
    private boolean wasAddedToDb = false;

    private int failCount = 0;

    private int notification_id = 1;
    private Notification.Builder mBuilder;
    private NotificationManager mNotificationManager;

    private int waitingIntentCount = 0;

    private boolean userHasCancelled = false;

    private Looper mServiceLooper;
    private ServiceHandler mServiceHandler;
    private Message msg;
    private boolean shouldGoToMainListOnNotificationClick = false;

    @Override
    public void onCreate() {

        HandlerThread thread = new HandlerThread("ServiceStartArguments", Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        // Get the HandlerThread's Looper and use it for our Handler
        mServiceLooper = thread.getLooper();

        mServiceHandler = new ServiceHandler(mServiceLooper);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent.getBooleanExtra("userHasCancelled", false)) {
            userHasCancelled = true;
            mBuilder
                    .setContentText("Please wait...")
                    .setContentTitle("Cancelling...")
                    .setAutoCancel(true)
                    .setOngoing(false)
                    .setProgress(0, 0, false);
            mNotificationManager.notify(notification_id, mBuilder.build());
            return 0;

        } else {

            waitingIntentCount++;

            // For each start request, send a message to start a job and deliver the
            // start ID so we know which request we're stopping when we finish the job
            msg = mServiceHandler.obtainMessage();
            msg.arg1 = startId;
            msg.obj = intent;
            mServiceHandler.sendMessage(msg);

            return START_NOT_STICKY;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding, so return null
        return null;
    }

    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(final Message msg) {
            userHasCancelled = false;
            Intent intent = (Intent) msg.obj;
            if (intent.getBooleanExtra("userHasCancelled", false)) {
                return;
            }
            waitingIntentCount--;
            wasAddedToDb = false;

            Intent notificationIntent = new Intent(SaveService.this, SaveService.class);
            notificationIntent.putExtra("userHasCancelled", true);
            PendingIntent pendingIntent = PendingIntent.getService(SaveService.this, 0, notificationIntent, 0);


            mBuilder = new Notification.Builder(SaveService.this)
                    .setContentTitle("Saving webpage...")
                    .setTicker("Saving webpage...")
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setProgress(0, 0, true)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setPriority(Notification.PRIORITY_HIGH)
                    .setContentText("Save in progress: getting ready...")
                    .addAction(R.drawable.ic_action_discard, "Cancel current", pendingIntent);

            mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.notify(notification_id, mBuilder.build());

            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(SaveService.this);
            String ua = sharedPref.getString("user_agent", "mobile");

            shouldGoToMainListOnNotificationClick = sharedPref.getBoolean("go_to_main_list_on_click", false);

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
            boolean success = grabPage(origurl, destinationDirectory);

            if (userHasCancelled) { //user cancelled, remove the notification, and delete files.
                mNotificationManager.cancelAll();
                File file = new File(destinationDirectory);
                DirectoryHelper.deleteDirectory(file);
                return;
            } else if (!success) { //something went wrong, leave the notification, and delete files.
                File file = new File(destinationDirectory);
                DirectoryHelper.deleteDirectory(file);
                return;
            }
            notifyProgress("Adding to list...", 100, 97, false);

            addToDb();

            Intent i = new Intent(SaveService.this, ScreenshotService.class);
            i.putExtra("origurl", "file://" + destinationDirectory + "index.html");
            i.putExtra("thumbnail", thumbnail);
            startService(i);

            notifyFinished();

            lt.i("Finished");
        }

    }

    private String getLastIdFromDb() {
        DbHelper mHelper = new DbHelper(SaveService.this);
        SQLiteDatabase dataBase = mHelper.getWritableDatabase();
        String sqlStatement = "SELECT * FROM " + DbHelper.TABLE_NAME + " ORDER BY " + DbHelper.KEY_ID + " DESC";
        Cursor cursor = dataBase.rawQuery(sqlStatement, null);
        cursor.moveToFirst();
        return cursor.getString(cursor.getColumnIndexOrThrow(DbHelper.KEY_ID));
    }

    private void addToDb() {

        //dont want to put it in the database multiple times
        if (wasAddedToDb) return;

        lt.i("Adding to db...");

        DbHelper mHelper = new DbHelper(SaveService.this);
        SQLiteDatabase dataBase = mHelper.getWritableDatabase();
        ContentValues values = new ContentValues();


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

    private void notifyFinished() {

        Intent notificationIntent;

        if (shouldGoToMainListOnNotificationClick) {
            notificationIntent = new Intent(this, MainActivity.class);
        } else {
            notificationIntent = new Intent(this, ViewActivity.class);
            notificationIntent.putExtra("orig_url", origurl);
            notificationIntent.putExtra("title", GrabUtility.title);
            notificationIntent.putExtra("id", getLastIdFromDb());
            notificationIntent.putExtra("thumbnailLocation", "file://" + destinationDirectory + "index.html");
            notificationIntent.putExtra("fileLocation", destinationDirectory + "index.html");
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        mBuilder
                .setContentTitle("Save completed.")
                .setTicker("Saved: " + GrabUtility.title)
                .setSmallIcon(R.drawable.ic_notify_save)
                .setOngoing(false)
                .setProgress(0, 0, false)
                .setOnlyAlertOnce(false)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_LOW)
                .setContentText(GrabUtility.title);
        mNotificationManager.notify(notification_id, mBuilder.build());
    }


    private void notifyProgress(String filename, int maxProgress, int progress, boolean indeterminate) {
        //progress updates are sent here
        if (waitingIntentCount == 0) {
            mBuilder
                    .setContentTitle("Saving webpage...")
                    .setContentText(filename)
                    .setProgress(maxProgress, progress, indeterminate);
        } else {
            int intentCount = waitingIntentCount + 1;
            mBuilder
                    .setContentTitle("Saving " + intentCount + " webpages...")
                    .setContentText(filename)
                    .setProgress(maxProgress, progress, indeterminate);
        }

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
                    .setProgress(0, 0, false);

        } else {
            mBuilder
                    .setContentText(extraMessage);
        }
        mNotificationManager.notify(notification_id, mBuilder.build());


    }

    private static class errorMessage {
        public static final String badUrlFileProtocol = "Bad url, Cannot save local files. URL must not start with file://";
        public static final String badUrlNotHttp = "URL to save must start with http:// or https://";
        public static final String storageNotWritable = "SD Card / Storage not writable";
    }

    //returns false if the page cannot be saved for some reason, usually network or disk error.
    private boolean grabPage(String url, String outputDirPath) {

        GrabUtility.filesToGrab.clear();
        GrabUtility.framesToGrab.clear();
        GrabUtility.cssToGrab.clear();
        GrabUtility.extraCssToGrab.clear();
        GrabUtility.title = "";


        if (!url.startsWith("http")) {
            if (url.startsWith("file://")) {
                notifyError("Bad URL", errorMessage.badUrlFileProtocol);
            } else {
                notifyError("Bad url", errorMessage.badUrlNotHttp);
            }
            return false;
        }

        File outputDir = new File(outputDirPath);

        if (outputDir.mkdirs() == false) {
            notifyError("Can't save", errorMessage.storageNotWritable);
            return false;
        }


        //download main html and parse -- isExtra parameter should be false
        boolean success = downloadHtmlAndParseLinks(url, outputDirPath, false);
        if (userHasCancelled) return false;

        //download and parse html frames
        //don't change this! Enhanced for loop breaks it!
        for (int i = 0; i < GrabUtility.framesToGrab.size(); i++) {
            downloadHtmlAndParseLinks(GrabUtility.framesToGrab.get(i), outputDirPath, true);
            if (userHasCancelled) return true;

        }

        //download and parse css files
        for (String urlToDownload : GrabUtility.cssToGrab) {
            if (userHasCancelled) return true;
            downloadCssAndParse(urlToDownload, outputDirPath);
        }

        //download and parse extra css files
        //todo : make this recursive
        for (String urlToDownload : GrabUtility.extraCssToGrab) {
            if (userHasCancelled) return true;
            downloadCssAndParse(urlToDownload, outputDirPath);
        }

        //download extra files, such as images / scripts
        ExecutorService pool = Executors.newFixedThreadPool(10);

        for (String urlToDownload : GrabUtility.filesToGrab) {
            if (userHasCancelled) return true;
            notifyProgress("Saving file: " + urlToDownload.substring(urlToDownload.lastIndexOf("/") + 1), GrabUtility.filesToGrab.size(), GrabUtility.filesToGrab.indexOf(urlToDownload), false);
            pool.submit(new DownloadTask(urlToDownload, outputDir));
        }

        pool.shutdown();

        try {
            pool.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return true;
    }

    private boolean downloadHtmlAndParseLinks(final String url, final String outputDir, final boolean isExtra) {
        //isExtra should be true when saving a html frame file.

        String filename;

        if (isExtra) {
            filename = GrabUtility.getFileName(url);
            notifyProgress("Downloading extra HTML file", 100, 5, true);
        } else {
            filename = "index.html";
            notifyProgress("Downloading main HTML file", 100, 5, true);
        }

        String baseUrl = url;
        if (url.endsWith("/")) {
            baseUrl = url + filename;
        }

        try {
            String htmlContent = getStringFromUrl(url);
            htmlContent = GrabUtility.parseHtmlForLinks(htmlContent, baseUrl);

            File outputFile = new File(outputDir, filename);
            saveStringToFile(htmlContent, outputFile);
            return true;

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void downloadCssAndParse(final String url, final String outputDir) {

        FileOutputStream fos = null;

        String filename = GrabUtility.getFileName(url);
        File outputFile = new File(outputDir, filename);

        try {

            String cssContent = getStringFromUrl(url);
            cssContent = GrabUtility.parseCssForLinks(cssContent, url);
            saveStringToFile(cssContent, outputFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    //HttpURLConnection sucks, this is much better.
    //todo: put this somewhere else.
    private static OkHttpClient client = new OkHttpClient();

    //url: the URL to download.
    //outputDir: the directory to place the downloaded file into
    private static class DownloadTask implements Runnable {

        private String url;
        private File outputDir;

        public DownloadTask(String url, File toPath) {
            this.url = url;
            this.outputDir = toPath;
        }

        @Override
        public void run() {
            String filename = GrabUtility.getFileName(url);
            File outputFile = new File(outputDir, filename);
            if (outputFile.exists()) {
                lt.e(lt.COMPONENT_EXTRA_FILE_DOWNLOADER, "File already exists, skipping: " + filename);
                return;
            }

            Request request = new Request.Builder()
                    .url(url)
                    .build();

            try {
                Response response = client.newCall(request).execute();
                InputStream is = response.body().byteStream();

                FileOutputStream fos = new FileOutputStream(outputFile);
                final byte[] buffer = new byte[1024 * 16]; // read in batches of 16K
                int length;
                while ((length = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, length);
                }

                response.body().close();
                fos.flush();
                fos.close();
                is.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    private String getStringFromUrl (String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .build();

        Response response = client.newCall(request).execute();
        String out = response.body().string();
        response.body().close();
        return out;
    }

    private void saveStringToFile (String ToSave, File outputFile) throws IOException {

        if (outputFile.exists()) {
            return;
        }

        outputFile.createNewFile();

        FileOutputStream fos = new FileOutputStream(outputFile);
        fos.write(ToSave.getBytes());

        fos.flush();
        fos.close();


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
	
	public static final Pattern fileNameReplacementPattern = Pattern.compile("[^a-zA-Z0-9-_\\.]");


	public static String parseHtmlForLinks(String htmlToParse, String baseUrl) {
		//get all links from this webpage and add them to Frontier List i.e. LinksToVisit ArrayList
		Document parsedHtml = null;
		URL fromHTMLPageUrl;
		boolean noBaseUrl = false;
		try {
            //todo: fixme
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
			
			parsedHtml.outputSettings().escapeMode(Entities.EscapeMode.extended);

			if (title.equals("")) {
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
	
	public static final String parseCssForLinks(String cssToParse, String baseUrl) {
		
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
        //todo: test this
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

    //todo:make sure this is not used inapropriately.
	public static final void addLinkToList(String link) {
		if (!filesToGrab.contains(link)) {
			filesToGrab.add(link);		
		}
	}
	
	public static final String makeLinkAbsolute(String link, String baseurl) {
		
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
	
	public static final String getFileName(String url) {
		
		String filename = url.substring(url.lastIndexOf('/')+1);

		if (filename.contains("?")) {
			filename = filename.substring(0, filename.indexOf("?"));
		}

		filename = fileNameReplacementPattern.matcher(filename).replaceAll("_");
		
		return filename;
	}
}

class tools {
	public static final void waitForInternet () {
		try {
			TimeUnit.SECONDS.sleep(3);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}

class lt {
	//log messages are sent here
	public static boolean shouldLogDebug = false;
	public static boolean shouldLogErrors = true;
	
	public static final String COMPONENT_SAVER_MAIN = "HTML Saver main";
	public static final String COMPONENT_PARSER = "Parser";
	public static final String COMPONENT_CSS_PARSER = "CSS Parser";
	public static final String COMPONENT_HTML_PARSER = "HTML Parser";
	public static final String COMPONENT_EXTRA_FILE_DOWNLOADER = "Extra file downloader";
	public static final String COMPONENT_CSS_FILE_DOWNLOADER = "CSS file downloader";
	public static final String COMPONENT_HTML_FILE_DOWNLOADER = "HTML file downloader";
	
	public static final void e (String message) {
		if (shouldLogErrors) {
			Log.e("SaveService", message);
		}	
	}
	
	public static final void e (String component, String message) {
		if (shouldLogErrors) {
			Log.e("SaveService: " + component, message);
		}	
	}
	
	public static final void i (String message) {	
		if (shouldLogDebug) {
			Log.i("SaveService", message);
		}
	}
	
	public static final void i (String component, String message) {
		if (shouldLogDebug) {
			Log.i("SaveService: " + component, message);
		}		
	}
}
