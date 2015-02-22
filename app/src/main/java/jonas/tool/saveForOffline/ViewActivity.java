package jonas.tool.saveForOffline;
import android.app.*;
import android.os.*;
import android.webkit.*;
import android.view.*;
import android.content.*;
import android.net.*;
import android.widget.*;
import android.database.sqlite.*;
import android.util.*;
import java.io.*;
import android.preference.*;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;

public class ViewActivity extends Activity
{
	private Intent incomingIntent;
	private String title;
	private String fileLocation;
	private WebView webview;
	private WebView.HitTestResult result;
	
	private boolean save_in_background;
	private boolean invertedRendering;
	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.view_activity);
		
		
		
		ActionBar actionBar = getActionBar();
		actionBar.setDisplayHomeAsUpEnabled(true);
		incomingIntent = getIntent();
		actionBar.setSubtitle(incomingIntent.getStringExtra("title"));
		title = incomingIntent.getStringExtra("title");
		fileLocation = incomingIntent.getStringExtra("fileLocation");
		
		setProgressBarIndeterminateVisibility(true);
		
		webview = (WebView) findViewById(R.id.webview);
		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
		String ua = sharedPref.getString("user_agent", "mobile");
		save_in_background = sharedPref.getBoolean("save_in_background", true);
		boolean jsEnabled = sharedPref.getBoolean("viewer_enable_javascript", true);
		invertedRendering = sharedPref.getBoolean("dark_mode", false);
		
		
		registerForContextMenu(webview);
		

		if (ua.equals("desktop")) {
			webview.getSettings().setUserAgentString("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/33.0.1750.517 Safari/537.36");
			
		}
		if (ua.equals("ipad")) {
			webview.getSettings().setUserAgentString("todo:iPad ua");
		}
		
		
		webview.getSettings().setLoadWithOverviewMode(true);
		webview.getSettings().setUseWideViewPort(true);
		webview.getSettings().setJavaScriptEnabled(jsEnabled);
		webview.getSettings().setBuiltInZoomControls(true);
		webview.getSettings().setDisplayZoomControls(false);
		webview.getSettings().setAllowFileAccess(true);
		webview.getSettings().setAllowFileAccessFromFileURLs(true);
		webview.getSettings().setMediaPlaybackRequiresUserGesture(false);
		webview.getSettings().setDefaultTextEncodingName("UTF-8");
		
        
		if (fileLocation.endsWith("html")) {
			webview.loadUrl("file://" + fileLocation);
			webview.setWebViewClient(new WebViewClient() {

					@Override
					public void onPageFinished(WebView view, String url) {
						setProgressBarIndeterminateVisibility(false);

					}
				});
		} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			//kitkat and up saves webarchives in a different format, which we can read easyly
			webview.loadUrl("file://" + fileLocation);
			webview.setWebViewClient(new WebViewClient() {

					@Override
					public void onPageFinished(WebView view, String url) {
						setProgressBarIndeterminateVisibility(false);

					}
				});
			
		} else loadWebView();
    }

	@Override
	protected void onResume() {
		super.onResume();
		//set up inverted rendering, aka. night mode.
		if (invertedRendering) {
			float[] mNegativeColorArray = { 
				-1.0f, 0, 0, 0, 255, // red
				0, -1.0f, 0, 0, 255, // green
				0, 0, -1.0f, 0, 255, // blue
				0, 0, 0, 1.0f, 0 // alpha
			};
			Paint mPaint = new Paint();
			ColorMatrixColorFilter filterInvert = new ColorMatrixColorFilter(mNegativeColorArray);
			mPaint.setColorFilter(filterInvert);
			webview.setLayerType(View.LAYER_TYPE_HARDWARE, mPaint);
		}
	}


	private void loadWebView()
	{
		try {
			FileInputStream is = new FileInputStream(fileLocation);
            //InputStream is = getAssets().open("TestHtmlArchive.xml");
            WebArchiveReader wr = new WebArchiveReader() {
                void onFinished(WebView v) {
                    // we are notified here when the page is fully loaded.
                    continueWhenLoaded(v);
                }
            };
            // To read from a file instead of an asset, use:
            // FileInputStream is = new FileInputStream(fileName);
            if (wr.readWebArchive(is)) {
                wr.loadToWebView(webview);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
		super.onStart();
	}
	
	void continueWhenLoaded(WebView webView) {
        
        // Any other code we need to execute after loading a page from a WebArchive...
		setProgressBarIndeterminateVisibility(false);
    }
	

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.view_activity_actions, menu);
		return super.onCreateOptionsMenu(menu);
	}
	
	public boolean onOptionsItemSelected(MenuItem item)
	{
		// Handle presses on the action bar items
		switch (item.getItemId()) {
			case R.id.ic_action_settings:
				Intent settings = new Intent(getApplicationContext(), Preferences.class);
				startActivityForResult(settings, 1);

				return true;
			
			case R.id.action_save_page_properties:
				
				showPropertiesDialog();

				return true;
			
			case R.id.action_open_in_external:
				
				
				Intent incomingIntent = getIntent();
				
				
				Uri uri = Uri.parse(incomingIntent.getStringExtra("orig_url"));
				Intent startBrowserIntent = new Intent(Intent.ACTION_VIEW, uri);
				startActivity(startBrowserIntent);

				return true;
				
			case R.id.action_open_file_in_external:
				Intent newIntent = new Intent(Intent.ACTION_VIEW);
				newIntent.setDataAndType(Uri.fromFile(new File(fileLocation)),"text/html");
				newIntent.setFlags(newIntent.FLAG_ACTIVITY_NEW_TASK);
				try {
					startActivity(newIntent);
				} catch (android.content.ActivityNotFoundException e) {
					Toast.makeText(this, "No installed app can open HTML files..", Toast.LENGTH_LONG).show();
				}
//
				return true;
				
			case R.id.ic_action_about:
				Intent intent = new Intent(this, FirstRunDialog.class);
				startActivity(intent);
				return true;
				
			case R.id.action_delete:
				
				AlertDialog.Builder build;
				build = new AlertDialog.Builder(ViewActivity.this);
				build.setTitle("Delete ?");
				build.setMessage(title);
				build.setPositiveButton("Delete",
					new DialogInterface.OnClickListener() {

						public void onClick(DialogInterface dialog,
											int which) {
							

							DbHelper mHelper = new DbHelper(ViewActivity.this);
							SQLiteDatabase dataBase = mHelper.getWritableDatabase();
							Intent incomingIntent2 = getIntent();

							dataBase.delete(
								DbHelper.TABLE_NAME,
								DbHelper.KEY_ID + "=" + incomingIntent2.getStringExtra("id"), null);
								
							
							File file = new File(incomingIntent2.getStringExtra("thumbnailLocation"));
								file.delete();

							if (incomingIntent2.getStringExtra("fileLocation").endsWith("mht")) {
								String fileLocation = incomingIntent2.getStringExtra("fileLocation");
								file = new File(fileLocation);
								file.delete();
							} else {
								String fileLocation = incomingIntent2.getStringExtra("fileLocation");
								file = new File(fileLocation);
								file = file.getParentFile();
								DirectoryHelper.deleteDirectory(file);
							}

							Toast.makeText(
								getApplicationContext(),
								"Saved page deleted.", Toast.LENGTH_LONG).show();

							finish();
						}
					});

				build.setNegativeButton("Cancel",
					new DialogInterface.OnClickListener() {

						public void onClick(DialogInterface dialog,
											int which) {
							dialog.cancel();
						}
					});
				AlertDialog alert = build.create();
				alert.show();
				
				

				return true;
				
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	private void showPropertiesDialog() {
		AlertDialog.Builder build = new AlertDialog.Builder(this);
		build.setTitle("Details of saved page");
		View layout = getLayoutInflater().inflate(R.layout.properties_dialog, null);
		build.setView(layout);
		TextView t = (TextView) layout.findViewById(R.id.properties_dialog_text_title);
		t.setText("Title: \r\n" + title);
		t = (TextView) layout.findViewById(R.id.properties_dialog_text_file_location);
		t.setText("File location: \r\n" + fileLocation);
		t = (TextView) layout.findViewById(R.id.properties_dialog_text_orig_url);
		t.setText("Saved from: \r\n" + incomingIntent.getStringExtra("orig_url"));
		build.setPositiveButton("OK",
			new DialogInterface.OnClickListener() {

				public void onClick(DialogInterface dialog, int which) {


				}
			});
		AlertDialog alert = build.create();
		alert.show();
	}
	
	
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo) {

	
		result = webview.getHitTestResult();
		
		if (result.getType() == WebView.HitTestResult.ANCHOR_TYPE || result.getType() == WebView.HitTestResult.SRC_ANCHOR_TYPE || result.getType() == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
			// Menu options for a hyperlink.
			//set the header title to the link url
			menu.setHeaderTitle(result.getExtra());
			menu.add(3, 3, 3, "Save Link");
			menu.add(4, 4, 4, "Share Link");
			menu.add(6, 6, 6, "Copy Link to clipboard");
			menu.add(5, 5, 5, "Open Link");
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		if (item.getItemId() == 5) {
			Uri uri = Uri.parse(result.getExtra());
			Intent startBrowserIntent = new Intent(Intent.ACTION_VIEW, uri);
			startActivity(startBrowserIntent);
		} else if (item.getItemId() == 4) {
			Intent i = new Intent(Intent.ACTION_SEND);
			i.setType("text/plain");
			i.putExtra(Intent.EXTRA_TEXT, result.getExtra());
			startActivity(Intent.createChooser(i, "Share Link via"));
			
		} else if (item.getItemId() == 3) {
			if (save_in_background) {
				Intent intent = new Intent(this, SaveService.class);
				intent.putExtra("origurl", result.getExtra());
				startService(intent);
			} else {
				Intent intent = new Intent(this, SaveActivity.class);
				intent.putExtra("origurl", result.getExtra());
				startActivity(intent);
			}
	
		} else if (item.getItemId() == 6) {
			
			ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE); 
			ClipData clip = ClipData.newPlainText(webview.getTitle(), result.getExtra());
			clipboard.setPrimaryClip(clip);
			Toast.makeText(this, "Link copied to clipboard", Toast.LENGTH_SHORT).show();

		}
		return super.onContextItemSelected(item);
		
	}
	
}
