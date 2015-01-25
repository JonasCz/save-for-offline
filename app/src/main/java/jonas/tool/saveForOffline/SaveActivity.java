package jonas.tool.saveForOffline;
import android.app.*;
import android.os.*;
import android.webkit.*;
import android.content.*;
import android.graphics.*;
import java.io.*;
import android.widget.*;
import android.view.*;
import android.database.sqlite.*;
import android.transition.*;
import android.preference.*;
import android.util.*;

public class SaveActivity extends Activity
{
	
	private WebView webview;
	private TextView progressTextview;
	private Button reloadButton;
	private Button cancelButton;
	private Button saveAnywayButton;
	
	private boolean hasErrorOccurred = false;
	
	private String origurl;
	private DirectoryHelper dirHelper= new DirectoryHelper();
	private String filelocation = dirHelper.getFileLocation();
	private String thumbnail = dirHelper.getThumbnailLocation();

	
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
		
		//window.setTitle("Save For Offline: Saving...");
		
		
		
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
			Log.d("saveActivity", "using desktop ua");
		}
		if (ua.equals("ipad")) {
			webview.getSettings().setUserAgentString("todo:iPad ua");
			Log.w("saveActivity", "iPad ua not implemented yet");
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

		DbHelper mHelper = new DbHelper(this);
		SQLiteDatabase dataBase = mHelper.getWritableDatabase();
		ContentValues values=new ContentValues();
		
		values.put(DbHelper.KEY_FILE_LOCATION,filelocation );
		values.put(DbHelper.KEY_TITLE,title );
		values.put(DbHelper.KEY_THUMBNAIL,thumbnail );
		values.put(DbHelper.KEY_ORIG_URL,origurl );

		//insert data into database
		dataBase.insert(DbHelper.TABLE_NAME, null, values);

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
			synchronized(this){try{wait(250);}catch(InterruptedException e){}}
			
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

