package jonas.tool.saveForOffline;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.preference.PreferenceManager;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;
import java.io.*;
import android.graphics.*;
import java.util.*;
import com.squareup.okhttp.*;

//this is an example of how to take a screenshot of a webpage in a background service
//not very elegant, but it works (for me anyway)
//author: jonasCz (http://github.com/jonasCz)

public class ScreenshotService extends Service {
	private final String TAG = "WebpageScreenshotService";
	private ServiceHandler mServiceHandler;

	private WebView webview;

	// Handler that receives messages from the thread
	private final class ServiceHandler extends Handler {
		private int currentStartId;

		private boolean webviewScreenshotTaken = false;
		private boolean websiteIconTaken = false;

		public ServiceHandler(Looper looper) {
			super(looper);
		}

		@Override
		public void handleMessage(final Message msg) {
			currentStartId = msg.arg1;

			webview = new WebView(ScreenshotService.this);
			Log.i(TAG, "Creating WebView");

			//without this toast message, screenshot will be blank, dont ask me why...
			Toast.makeText(ScreenshotService.this, "Save completed.", Toast.LENGTH_SHORT).show();

			// This is important, so that the webview will render and we don't get blank screenshot
			webview.setDrawingCacheEnabled(true);

			//width and height of your webview and the resulting screenshot
			webview.measure(600, 400);
			webview.layout(0, 0, 600, 400); 

			final Intent intent = (Intent) msg.obj;

			boolean javaScriptEnabled  = PreferenceManager.getDefaultSharedPreferences(ScreenshotService.this).getBoolean("enable_javascript", true);
			webview.getSettings().setJavaScriptEnabled(javaScriptEnabled);

			webview.getSettings().setAllowFileAccessFromFileURLs(true);
			webview.getSettings().setAllowUniversalAccessFromFileURLs(true);

			webview.loadUrl(intent.getStringExtra(Database.FILE_LOCATION));
			Log.i(TAG, "Loading URL: " + intent.getStringExtra(Database.FILE_LOCATION));

			webview.setWebViewClient(new WebViewClient() {

					@Override
					public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
						Log.w(TAG, "Recieved error from WebView, description: " + description + ", Failing url: " + failingUrl);
						//without this method, your app may crash...
					}

					@Override
					public void onPageFinished(WebView view, String url) {
						Log.i(TAG, "Page finished, getting thumbnail");
						takeWebviewScreenshot(intent.getStringExtra(Database.THUMBNAIL));
					
					}
				});
		}

		private void takeWebviewScreenshot(final String outputFileLocation) {
			new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							TimeUnit.MILLISECONDS.sleep(1000);  //allow webview to render, otherwise screenshot may be blank or partial
						} catch (InterruptedException e) {
							//should never happen
							Log.e(TAG, "InterruptedException when taking webview screenshot ", e);
						}
						saveBitmapToFile(webview.getDrawingCache(), new File(outputFileLocation));
						webviewScreenshotTaken = true;
						stopService();
					}	
				}).start();	
		}

		private void saveBitmapToFile(Bitmap bitmap, File outputFile) {
			if (bitmap == null) {
				return;
			}
			outputFile.getParentFile().mkdirs();

			try {
				OutputStream out = new BufferedOutputStream(new FileOutputStream(outputFile));
				bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);

				out.flush();
				out.close();
			} catch (IOException e) {
				Log.e(TAG, "IoException while saving bitmap to file", e);
			}
			Log.i(TAG, "Saved Bitmap to file: " + outputFile.getPath());
		}

		
		private void stopService() {
			if (websiteIconTaken && webviewScreenshotTaken) {
				Log.i(TAG, "Service stopped, with startId " + currentStartId + " completed");
				stopSelf(currentStartId);
			}
		}
	}

	@Override
	public void onCreate() {
		HandlerThread thread = new HandlerThread("WebpageScreenshotService", Process.THREAD_PRIORITY_BACKGROUND);
		thread.start();

		mServiceHandler = new ServiceHandler(thread.getLooper());
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Message msg = mServiceHandler.obtainMessage();
		msg.arg1 = startId;
		msg.obj = intent;
		mServiceHandler.sendMessage(msg);

		return START_NOT_STICKY;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
}
