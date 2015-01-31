package jonas.tool.saveForOffline;

import android.util.Base64;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import android.webkit.*;
import java.io.*;
import android.app.*;
import android.content.*;
import android.util.*;

public class webArchiveUnpackerService extends IntentService {
	
    private Document myDoc = null;
    private static boolean myLoadingArchive = false;
    private ArrayList<String> urlList = new ArrayList<String>();
    private ArrayList<Element> urlNodes = new ArrayList<Element>();
	
	private String destinationDir;
	private String archiveLocation;
	
	public webArchiveUnpackerService() {
		super("webArchiveUnpacker");
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		
		archiveLocation = intent.getStringExtra("archivelocation");
		destinationDir = intent.getStringExtra("destdir");
		
		synchronized (this) {try {wait(2500);} catch (InterruptedException e) {}}
		
		try {
		WebArchiveUnpacker reader = new WebArchiveUnpacker(new File(archiveLocation));
		reader.extractHtml(destinationDir);
		reader.extractSubresource(destinationDir);
		} catch(Exception exc) {
		System.err.println("Failed to parse the web archive: " + exc.toString());
	}

}
	
}
