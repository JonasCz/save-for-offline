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

public class SaveService extends Service {
	
	private Looper mServiceLooper;
	private ServiceHandler mServiceHandler;
	private Message msg;
	private Intent intent;
	
	private final class ServiceHandler extends Handler {
		public ServiceHandler(Looper looper) {
			super(looper);
		}

	private WebView webview;
	
	private String filelocation;
	private String destinationDirectory;
	private String thumbnail;
	private String title;
	private String origurl;
	
	private boolean hasTimedOut = true;

	private boolean thumbnailWasSaved = false;
	
	private boolean wasAddedToDb = false;
	private boolean webviewHasLoaded = true;
	
	private boolean errorOccurred = false;
	private String errorDescription = "";

	private int notification_id = 1;
	Notification.Builder mBuilder;
	NotificationManager mNotificationManager;

	@Override
	public void handleMessage(final Message msg) {
		
		errorDescription = "";
		wasAddedToDb = false;
		errorOccurred = false;
		webviewHasLoaded = true;
		thumbnailWasSaved = false;

		mBuilder = new Notification.Builder(SaveService.this)
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
		
		webview = new WebView(SaveService.this);

		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(SaveService.this);
		String ua = sharedPref.getString("user_agent", "mobile");

		if (ua.equals("desktop")) {
			webview.getSettings().setUserAgentString("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/33.0.1750.517 Safari/537.36");

		}
		if (ua.equals("ipad")) {
			webview.getSettings().setUserAgentString("todo:iPad ua");

		}

		DirectoryHelper dh = new DirectoryHelper();
		filelocation = dh.getFileLocation();
		destinationDirectory = dh.getUnpackedDir();
		
		thumbnail = dh.getThumbnailLocation();
		
		origurl = intent.getStringExtra("origurl");


		Toast.makeText(SaveService.this, "Saving page...", Toast.LENGTH_SHORT).show();


		// This is the important code :)  
		// Without it the view will have a dimension of 0,0 and the bitmap will be null       
		webview.setDrawingCacheEnabled(true);
		webview.measure(600, 400);
		webview.layout(0, 0, 600, 400); 
		webview.getSettings().setJavaScriptEnabled(true);
		

		webview.loadUrl(origurl);

		webview.setWebChromeClient(new WebChromeClient() {
				public void onProgressChanged(WebView view, int progress) {
					
					if (progress == 100 && webviewHasLoaded) {
						errorOccurred = false;
						handleSave();
					} else if (webviewHasLoaded) {
						mBuilder.setProgress(100, progress, false);
						mNotificationManager.notify(notification_id, mBuilder.build());
					} else {
						Intent retryIntent = new Intent(SaveService.this, SaveService.class);
						retryIntent.putExtra("origurl", origurl);

						TaskStackBuilder stackBuilder = TaskStackBuilder.create(SaveService.this);

						stackBuilder.addNextIntent(retryIntent);
						PendingIntent resultPendingIntent =
							stackBuilder.getPendingIntent(
							0,
							PendingIntent.FLAG_CANCEL_CURRENT);
						mBuilder.setContentIntent(resultPendingIntent);


						mBuilder.setSmallIcon(android.R.drawable.stat_notify_error);
						mBuilder.setContentText(errorDescription + " Tap to retry.");
						mBuilder.setTicker("Page not saved: " + errorDescription);
						// Removes the progress bar
						mBuilder.setProgress(0, 0, false);
						mBuilder.setOngoing(false);
						mBuilder.setOnlyAlertOnce(false);
						mBuilder.setPriority(Notification.PRIORITY_HIGH);
						mBuilder.setAutoCancel(true);
						mBuilder.setContentTitle("Error: Page not saved.");
						mNotificationManager.notify(notification_id, mBuilder.build());

						stopSelf(msg.arg1);
					}


				}
			});

		webview.setWebViewClient(new WebViewClient() {

				@Override
				public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {

					if (failingUrl.equalsIgnoreCase(origurl)) {
						
						webviewHasLoaded = false;
						errorDescription = description;
						
						Intent retryIntent = new Intent(SaveService.this, SaveService.class);
						retryIntent.putExtra("origurl", origurl);

						TaskStackBuilder stackBuilder = TaskStackBuilder.create(SaveService.this);
						
						stackBuilder.addNextIntent(retryIntent);
						PendingIntent resultPendingIntent =
							stackBuilder.getPendingIntent(
							0,
							PendingIntent.FLAG_CANCEL_CURRENT);
						mBuilder.setContentIntent(resultPendingIntent);


						mBuilder.setSmallIcon(android.R.drawable.stat_notify_error);
						mBuilder.setContentText(description + " Tap to retry.");
						mBuilder.setTicker("Page not saved: " + description);
						// Removes the progress bar
						mBuilder.setProgress(0, 0, false);
						mBuilder.setOngoing(false);
						mBuilder.setOnlyAlertOnce(false);
						mBuilder.setPriority(Notification.PRIORITY_HIGH);
						mBuilder.setAutoCancel(true);
						mBuilder.setContentTitle("Error: Page not saved.");
						mNotificationManager.notify(notification_id, mBuilder.build());

						stopSelf(msg.arg1);

					}



				}

				@Override
				public void onPageFinished(WebView view, String url) {

					if (webviewHasLoaded) {
						hasTimedOut = false;
						errorOccurred = false;

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
						stopSelf(msg.arg1);
					}

				}
			});}

	@Override
	public void onDestroy() {

	}

	private void handleSave() {
		
		if (!webviewHasLoaded || errorOccurred || title.equalsIgnoreCase("webpage not available")) return;

		addToDb();

		webview.saveWebArchive(filelocation);
		
//		Intent unpackerServiceIntent = new Intent(this, webArchiveUnpacker.class);
//		unpackerServiceIntent.putExtra("archivelocation", filelocation);
//		unpackerServiceIntent.putExtra("destdir", destinationDirectory);
//		startService(unpackerServiceIntent);

		new takeScreenshotTask().execute();
		
		stopSelf();

	}



	private void addToDb() {

		//dont want to put it in the database multiple times
		if (wasAddedToDb) return;

		DbHelper mHelper = new DbHelper(SaveService.this);
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
			
			stopSelf();


			return null;
		}



	}




	}	//service related stuff below, its probably easyer to use intentService...

	@Override
	public void onCreate() {

		HandlerThread thread = new HandlerThread("ServiceStartArguments", Process.THREAD_PRIORITY_BACKGROUND);
		thread.start();

		// Get the HandlerThread's Looper and use it for our Handler 
		mServiceLooper = thread.getLooper();
		mServiceHandler = new ServiceHandler(mServiceLooper);
	}

	@Override
	public int onStartCommand(Intent startintent, int flags, int startId) {
		
		intent = startintent;

		// For each start request, send a message to start a job and deliver the
		// start ID so we know which request we're stopping when we finish the job
		msg = mServiceHandler.obtainMessage();
		msg.arg1 = startId;
		mServiceHandler.sendMessage(msg);

		// If we get killed, after returning from here, restart
		return START_REDELIVER_INTENT;
	}

	@Override
	public IBinder onBind(Intent intent) {
		// We don't provide binding, so return null
		return null;
	}

}
