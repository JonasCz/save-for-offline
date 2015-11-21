package jonas.tool.saveForOffline;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Picture;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

public class SaveActivity extends Activity
{
	
	private WebView webview;
	private TextView progressTextview;
	private Button reloadButton;
	private Button cancelButton;
	private Button saveAnywayButton;
	
	private boolean hasErrorOccurred = false;
	
	private String origurl;
	private String filelocation = null;
	private String thumbnail = null;

	
	private boolean getHasErrorOccurred() {
		return hasErrorOccurred;
	}
	private void setHasErrorOcurred(boolean error) {
		hasErrorOccurred = error;
	}
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		//requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		//requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.save_activity);
		//Window window = getWindow();
		
		getWindow().setTitle("Save For Offline: Saving...");
		
		
		
		progressTextview = (TextView) findViewById(R.id.saveProgressText);
		reloadButton = (Button) findViewById(R.id.saveactivityReloadButton);
		cancelButton = (Button) findViewById(R.id.saveactivityCancelButton);
		saveAnywayButton = (Button) findViewById(R.id.saveAnyWayButton);
		
		//setting variables
		Intent intent = getIntent();
		
		origurl = intent.getStringExtra("origurl");
	
		//setProgressBarIndeterminateVisibility(true);
		//configuring the webview
		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
		String ua = sharedPref.getString("user_agent", "mobile");
		webview = (WebView) findViewById(R.id.SaveActivityWebView);

		if (ua.equals("desktop")) {
			webview.getSettings().setUserAgentString("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/33.0.1750.517 Safari/537.36");
		}
		if (ua.equals("ipad")) {
			webview.getSettings().setUserAgentString("todo:iPad ua");
			
		}
		
		
		
		webview.getSettings().setJavaScriptEnabled(true);
		webview.getSettings().setLoadWithOverviewMode(true);
		webview.getSettings().setUseWideViewPort(true);
		webview.setInitialScale(30);
		
		
		webview.loadUrl(origurl);
		
		webview.setWebChromeClient(new WebChromeClient() {
			@Override
			public void onProgressChanged(WebView view, int progress) {
				if (progress == 100) { progressTextview.setText("Could not fully load page");
				}
				else {progressTextview.setText("Saving webpage... (" + progress +"% )");
				}
					
			}});
		
		webview.setWebViewClient(new WebViewClient() {
			boolean error = getHasErrorOccurred();
			@Override
			public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
				setHasErrorOcurred(true);
				progressTextview.setText(description);
				reloadButton.setVisibility(View.VISIBLE);
				saveAnywayButton.setVisibility(View.VISIBLE);
				//setProgressBarIndeterminateVisibility(false);
					
			}
			@Override
			public void onPageFinished (WebView view, String url) {
				if (getHasErrorOccurred() == false) {
				handleSave();
				}
			}
		});
		}
	
	private boolean isCancelled = false;
	public void cancelButtonClick (View view) {
		isCancelled = true;
		setResult(RESULT_CANCELED);
		finish();
		
	}
	
	public void reloadButtonClick (View view) {
		hasErrorOccurred = false;
		webview.reload();	
	}
	
	public void saveAnywayButtonClick (View view) {
		handleSave();
	}
	
	private void handleSave () {
		if (isCancelled == false) {
			progressTextview.setText("Saving page...    ");
		cancelButton.setEnabled(false);
		reloadButton.setEnabled(false);
		new takeScreenshotTask().execute();
		webview.saveWebArchive(filelocation);
		addToDb();
		Toast.makeText(SaveActivity.this, "Saved " + webview.getTitle(), Toast.LENGTH_SHORT).show();
		
		setResult(RESULT_OK);
		
		}
	}
	
	private void addToDb () {
		String title = webview.getTitle();

		Database mHelper = new Database(this);
		SQLiteDatabase dataBase = mHelper.getWritableDatabase();
		ContentValues values=new ContentValues();
		
		values.put(Database.FILE_LOCATION,filelocation );
		values.put(Database.TITLE,title );
		values.put(Database.THUMBNAIL,thumbnail );
		values.put(Database.ORIGINAL_URL,origurl );

		//insert data into database
		dataBase.insert(Database.TABLE_NAME, null, values);

		//close database
		dataBase.close();
		}
	
	private class takeScreenshotTask extends AsyncTask<Void, Void, Void>
	{

		@Override
		protected Void doInBackground(Void[] p1)
		{
			
		   /*sometimes the webview tells us that it is fully loaded,
			*even though it is not. So wait some time to let it
			*load and do this in an asyncTask in order not to freeze the ui
			*/
			try {
				TimeUnit.MILLISECONDS.sleep(300);
			} catch (InterruptedException e) {

			}
			
			Picture mPicture = webview.capturePicture();
			Bitmap b = Bitmap.createBitmap(webview.getWidth(), webview.getHeight(), Bitmap.Config.ARGB_8888);
			Canvas c = new Canvas(b);
			mPicture.draw(c);
			

			Bitmap resizedBitmap = Bitmap.createScaledBitmap(b, 600, 400, false);


			File file = new File (thumbnail);
			OutputStream out;

			try
			{
				out = new BufferedOutputStream(new FileOutputStream(file));
				resizedBitmap.compress(Bitmap.CompressFormat.PNG,100, out);
				out.close();
			}
			catch (IOException cuteKitten)
			{cuteKitten.printStackTrace();}
			
			finish();
			
			return null;
		}
			

		
	}
	
	
}

