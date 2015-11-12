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

//this is an example of how to take a screenshot of a webpage in a background service
//not very elegant, but it works (for me anyway)
//author: jonasCz (http://github.com/jonasCz)

public class ScreenshotService extends Service {
	private Looper mServiceLooper;
	private ServiceHandler mServiceHandler;
	private Message msg;
	
	private WebView webview;

	// Handler that receives messages from the thread
	private final class ServiceHandler extends Handler {
		public ServiceHandler(Looper looper) {
			super(looper);
		}
		@Override
		public void handleMessage(final Message msg) {
			
			webview = new WebView(ScreenshotService.this);

			//without this toast message, screenshot will be blank, dont ask me why...
			Toast.makeText(ScreenshotService.this, "Save completed.", Toast.LENGTH_SHORT).show();

			SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(ScreenshotService.this);
			boolean js = sharedPref.getBoolean("enable_javascript_screenshot", true);
			webview.getSettings().setJavaScriptEnabled(js);

			// This is the important code :)   
			webview.setDrawingCacheEnabled(true);

			//width x height of your webview and the resulting screenshot
			webview.measure(600, 400);
			webview.layout(0, 0, 600, 400); 

			final Intent intent = (Intent) msg.obj;

			webview.loadUrl(intent.getStringExtra("origurl"));

			webview.setWebViewClient(new WebViewClient() {

					@Override
					public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
						//without this method, your app may crash...
					}

					@Override
					public void onPageFinished(WebView view, String url) {
						new takeScreenshotTask().execute(intent.getStringExtra("thumbnail"));
						stopSelf(msg.arg1);


					}
				});
			
			}		
			
		
	}
	
	private class takeScreenshotTask extends AsyncTask<String, Void, Void> {

		@Override
		protected Void doInBackground(String[] thumblocation) {

			//allow the webview to render
			try {
				TimeUnit.MILLISECONDS.sleep(1000);
			} catch (InterruptedException ex) {
				Log.e("ScreenshotService", "InterruptedException while trying to save thumbnail!");
			}

			//here I save the bitmap to file
			Bitmap b = webview.getDrawingCache();
			File file = new File(thumblocation[0]);

            System.out.println(file.getAbsolutePath());


            try {
				OutputStream out = new BufferedOutputStream(new FileOutputStream(file));
				b.compress(Bitmap.CompressFormat.PNG, 100, out);
				out.flush();
				out.close();

			} catch (IOException e) {
				Log.e("ScreenshotService", "IOException while trying to save thumbnail, Is /sdcard/ writable?");
				e.printStackTrace();
			}

			return null;
		}
	}
	
	//service related stuff below, its probably easier to use intentService...

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

		// For each start request, send a message to start a job and deliver the
		// start ID so we know which request we're stopping when we finish the job
		msg = mServiceHandler.obtainMessage();
		msg.arg1 = startId;
		msg.obj = intent;
		mServiceHandler.sendMessage(msg);
		
		
		// If we get killed, after returning from here, restart
		return START_NOT_STICKY;
	}

	@Override
	public IBinder onBind(Intent intent) {
		// We don't provide binding, so return null
		return null;
	}

	@Override
	public void onDestroy() {
		
	}
	
	
}
