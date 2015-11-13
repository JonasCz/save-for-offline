package jonas.tool.saveForOffline;
import android.app.*;
import android.os.*;
import android.view.*;
import android.view.WindowManager.LayoutParams;

public class FirstRunDialog extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setTitle("About Save for Offline");

        setContentView(R.layout.firstrundialog);


	}

	public void closeButtonClick(View view) {
		//user clicked the cancel button, quit
		finish();
	}
}
