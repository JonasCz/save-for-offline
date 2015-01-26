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

public class SaveService extends IntentService {

	private WebView webview;
	private String filelocation;
	private String thumbnail;
	private String title;
	private String origurl;

	private boolean thumbnailWasSaved = false;
	
	private boolean wasAddedToDb = false;
	private boolean webviewHasLoaded = true;

	private int notification_id = 1;
	Notification.Builder mBuilder;
	NotificationManager mNotificationManager;

	public SaveService() {
		super("SaveService");
	}

	@Override
	public void onHandleIntent(Intent intent) {

		mBuilder = new Notification.Builder(this)
			.setContentTitle("Saving webpage...")
			.setOngoing(true)
			.setOnlyAlertOnce(true)
			.setPriority(Notification.PRIORITY_HIGH)
			.setContentText("Save in progress");

		//startForeground(notification_id, mBuilder.build());
		mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		mBuilder.setTicker("Saving webpage...");
		mBuilder.setSmallIcon(android.R.drawable.stat_sys_download);
		mNotificationManager.notify(notification_id, mBuilder.build());
		
		webview = new WebView(this);

		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
		String ua = sharedPref.getString("user_agent", "mobile");

		if (ua.equals("desktop")) {
			webview.getSettings().setUserAgentString("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/33.0.1750.517 Safari/537.36");

		}
		if (ua.equals("ipad")) {
			webview.getSettings().setUserAgentString("todo:iPad ua");

		}

		DirectoryHelper dh = new DirectoryHelper();
		filelocation = dh.getFileLocation();
		thumbnail = dh.getThumbnailLocation();
		origurl = intent.getStringExtra("origurl");


		Toast.makeText(this, "Saving page...", Toast.LENGTH_SHORT).show();


		// This is the important code :)  
		// Without it the view will have a dimension of 0,0 and the bitmap will be null       
		webview.setDrawingCacheEnabled(true);
		webview.measure(600, 400);
		webview.layout(0, 0, 600, 400); 
		webview.getSettings().setJavaScriptEnabled(true);

		webview.loadUrl(origurl);

		webview.setWebChromeClient(new WebChromeClient() {
				public void onProgressChanged(WebView view, int progress) {
					
					if (webviewHasLoaded) {
						mBuilder.setProgress(100, progress, false);
						mNotificationManager.notify(notification_id, mBuilder.build());
					} else {
						mBuilder.setProgress(0, 0, false);
						mBuilder.setSmallIcon(android.R.drawable.stat_sys_warning);
						mNotificationManager.notify(notification_id, mBuilder.build());
					}


				}
			});

		webview.setWebViewClient(new WebViewClient() {

				@Override
				public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
					
					if (failingUrl.equalsIgnoreCase(origurl) || webview.getTitle().equalsIgnoreCase("webpage not available")) {
					webviewHasLoaded = false;
					mBuilder.setSmallIcon(android.R.drawable.stat_notify_error);
					mBuilder.setContentText(description + " Tap to retry.");
					mBuilder.setTicker("Page not saved: " + description);
					// Removes the progress bar
					mBuilder.setProgress(0, 0, false);
					mBuilder.setOngoing(false);
					mBuilder.setOnlyAlertOnce(false);
					mBuilder.setPriority(Notification.PRIORITY_LOW);
					mBuilder.setContentTitle("Error: Page not saved.");
					mNotificationManager.notify(notification_id, mBuilder.build());
					
					}
					


				}

				@Override
				public void onPageFinished(WebView view, String url) {

					if (webviewHasLoaded && !webview.getTitle().equalsIgnoreCase("webpage not available")) {

						title = webview.getTitle();
						mBuilder.setContentText(title);
						mBuilder.setTicker("Saved: " + title);
						// Removes the progress bar
						mBuilder.setProgress(0, 0, false);
						mBuilder.setOngoing(false);
						mBuilder.setOnlyAlertOnce(false);
						mBuilder.setSmallIcon(R.drawable.ic_notify_save);
						mBuilder.setPriority(Notification.PRIORITY_LOW);
						mBuilder.setContentTitle("Savd webpage");
						mNotificationManager.notify(notification_id, mBuilder.build());

						handleSave();
					}

				}
			});}

	@Override
	public void onDestroy() {

	}

	private void handleSave() {

		addToDb();

		webview.saveWebArchive(filelocation);

		new takeScreenshotTask().execute();

	}



	private void addToDb() {

		//dont want to put it in the database multiple times
		if (wasAddedToDb) return;

		DbHelper mHelper = new DbHelper(this);
		SQLiteDatabase dataBase = mHelper.getWritableDatabase();
		ContentValues values=new ContentValues();

		values.put(DbHelper.KEY_FILE_LOCATION, filelocation);
		values.put(DbHelper.KEY_TITLE, title);
		values.put(DbHelper.KEY_THUMBNAIL, thumbnail);
		values.put(DbHelper.KEY_ORIG_URL, origurl);

		//insert data into database
		dataBase.insert(DbHelper.TABLE_NAME, null, values);

		//close database
		dataBase.close();

		wasAddedToDb = true;
	}


	private class takeScreenshotTask extends AsyncTask<Void, Void, Void> {

		@Override
		protected Void doInBackground(Void[] p1) {
			
			//allow the webview to render
			synchronized (this) {try {wait(250);} catch (InterruptedException e) {}}

			Bitmap b = webview.getDrawingCache();
			File file = new File(thumbnail);
			OutputStream out;


			try {
				out = new BufferedOutputStream(new FileOutputStream(file));
				b.compress(Bitmap.CompressFormat.PNG, 100, out);
				out.close();
				thumbnailWasSaved = true;
			} catch (IOException e) {
				Log.e("SaveService", "IOException while trying to save thumbnail, Is /sdcard/ writable?");
				thumbnailWasSaved = false;
				e.printStackTrace();
			}

			//stopForeground(false);

			mBuilder.setContentText(title);
			mBuilder.setTicker("Saved: " + title);
			// Removes the progress bar
			mBuilder.setProgress(0, 0, false);
			mBuilder.setOngoing(false);
			mBuilder.setOnlyAlertOnce(false);
			mBuilder.setPriority(Notification.PRIORITY_LOW);
			mBuilder.setContentTitle("Save completed");

			mNotificationManager.notify(notification_id, mBuilder.build());


			return null;
		}



	}




}
