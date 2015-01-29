package jonas.tool.saveForOffline;


import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import java.net.*;
import android.webkit.*;
import android.content.*;
import android.os.*;
import android.database.*;
import android.text.*;
import android.text.ClipboardManager;
import android.app.*;
import android.preference.*;
import android.widget.*;
/**
 * activity to get input from user and insert into SQLite database
 * @author ketan(Visit my <a
 *         href="http://androidsolution4u.blogspot.in/">blog</a>)
 */
public class AddActivity extends Activity {
	private Button btn_save;
	private EditText edit_origurl;
	private Intent incomingIntent ;

	private String origurl ;




    @Override
    public void onCreate(Bundle savedInstanceState) {
		incomingIntent = getIntent();

        super.onCreate(savedInstanceState);
		//init prefs
		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        setContentView(R.layout.add_activity);

        btn_save = (Button)findViewById(R.id.save_btn);
		btn_save.setEnabled(false);

        edit_origurl = (EditText)findViewById(R.id.frst_editTxt);
		edit_origurl.setText(incomingIntent.getStringExtra(Intent.EXTRA_TEXT));

		//save directly if activity was tarted via intent
		origurl = edit_origurl.getText().toString().trim();
		if (origurl.length() > 0) {
			startSaveActivity();
		}

		edit_origurl.addTextChangedListener(new TextWatcher(){
				public void afterTextChanged(Editable s) {

					if (edit_origurl.length() == 0) {
						btn_save.setEnabled(false);
					} else {btn_save.setEnabled(true);}
				}
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
				public void onTextChanged(CharSequence s, int start, int before, int count) {}
			}); 
	}

	public void cancelButtonClick(View view) {

		//user clicked the cancel button, quit
		finish();
	}

	public void btn_paste(View view) {
		ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
		edit_origurl.setText(clipboard.getText());
	}

	// saveButton click event 
	public void okButtonClick(View view) {

		origurl = edit_origurl.getText().toString().trim();
		if (origurl.length() > 0 && (origurl.startsWith("http://") || origurl.startsWith("file://"))) {
			startSaveActivity();
		} else if (origurl.length() > 0) {
			origurl = "http://" + origurl;
			startSaveActivity();
		}
	}


	private void startSaveActivity() {
		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
		boolean save_in_background = sharedPref.getBoolean("save_in_background", true);
		if (save_in_background) {
			Intent intent = new Intent(this, SaveService.class);
			intent.putExtra("origurl", origurl);
			startService(intent);
		} else {
			Intent intent = new Intent(this, SaveActivity.class);
			intent.putExtra("origurl", origurl);
			startActivity(intent);
		}

		finish();
	}

}
