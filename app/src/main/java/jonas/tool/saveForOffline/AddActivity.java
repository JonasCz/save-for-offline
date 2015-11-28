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
import android.view.*;

public class AddActivity extends Activity {
	private Button btn_save;
	private EditText edit_origurl;

	private String origurl ;

    @Override
    public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setTitle("Enter URL to save");
		
		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        setContentView(R.layout.add_activity);

        btn_save = (Button)findViewById(R.id.save_btn);
		btn_save.setEnabled(false);

        edit_origurl = (EditText)findViewById(R.id.frst_editTxt);
		edit_origurl.setText(getIntent().getStringExtra(Intent.EXTRA_TEXT));

		//save directly if activity was started via intent
		origurl = edit_origurl.getText().toString().trim();
		if (origurl.length() > 0) {
			startSave();
		}

		edit_origurl.addTextChangedListener(new TextWatcher(){
				public void afterTextChanged(Editable text) {
					if (edit_origurl.length() == 0) {
						btn_save.setEnabled(false);
					} else {
						btn_save.setEnabled(true);
					}
				}
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
				public void onTextChanged(CharSequence s, int start, int before, int count) {}
			}); 
	}

	public void cancelButtonClick(View view) {
		finish();
	}

	public void btn_paste(View view) {
		ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
		edit_origurl.setText(clipboard.getText());
	}

	// saveButton click event
	public void okButtonClick(View view) {
		origurl = edit_origurl.getText().toString().trim();
		if (origurl.length() > 0 && (origurl.startsWith("http"))) {
			startSave();
		} else if (origurl.length() > 0) {
			origurl = "http://" + origurl;
			startSave();
		}
	}


	private void startSave() {
		Intent intent = new Intent(this, SaveService.class);
		intent.putExtra("origurl", origurl);
		startService(intent);
		finish();
	}
}
