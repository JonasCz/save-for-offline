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

public class webArchiveUnpacker extends IntentService {
	
    private Document myDoc = null;
    private static boolean myLoadingArchive = false;
    private ArrayList<String> urlList = new ArrayList<String>();
    private ArrayList<Element> urlNodes = new ArrayList<Element>();
	
	private String destinationDir;
	private String archiveLocation;
	
	public webArchiveUnpacker() {
		super("webArchiveUnpacker");
	}

	@Override
	protected void onHandleIntent(Intent intent) {
		
		archiveLocation = intent.getStringExtra("archivelocation");
		destinationDir = intent.getStringExtra("destdir");
		
		synchronized (this) {try {wait(2500);} catch (InterruptedException e) {}}
		
		unpackWebArchive();

	}
	
	public void unpackWebArchive() {
		Log.w("saving to", destinationDir +"imdex.html");
		try {
			FileInputStream is = new FileInputStream(archiveLocation);
			readWebArchive(is);
			
			File mainDir = new File(destinationDir);
			mainDir.mkdirs();
			
			File mainFile = new File(destinationDir + "index.html");
		
			
			FileOutputStream os = new FileOutputStream(mainFile);
			os.write(getMainHtml().getBytes());
			
			for (int i = 1; i < urlList.size(); i++) {

				String url = urlList.get(i).substring(urlList.get(0).length());
				
				Log.e("Unpacker", url);
				
				File file = new File(destinationDir + url);
				file.mkdirs();
				file.delete();
			
			
				FileOutputStream extraFileOS = new FileOutputStream(destinationDir + url);
				
				extraFileOS.write(getElBytes(urlNodes.get(i), "data"));
				
				
			}
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
				
	}


    public boolean readWebArchive(InputStream is) {
        DocumentBuilderFactory builderFactory =
			DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = null;
        myDoc = null;
        try {
            builder = builderFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        }
        try {
            myDoc = builder.parse(is);
            NodeList nl = myDoc.getElementsByTagName("url");
            for (int i = 0; i < nl.getLength(); i++) {
                Node nd = nl.item(i);
                if(nd instanceof Element) {
                    Element el = (Element) nd;
                    // siblings of el (url) are: mimeType, textEncoding, frameName, data
                    NodeList nodes = el.getChildNodes();
                    for (int j = 0; j < nodes.getLength(); j++) {
                        Node node = nodes.item(j);
                        if (node instanceof Text) {
                            String dt = ((Text)node).getData();
                            byte[] b = Base64.decode(dt, Base64.DEFAULT);
                            dt = new String(b);
                            urlList.add(dt);
                            urlNodes.add((Element) el.getParentNode());
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            myDoc = null;
        }
        return myDoc != null;
    }

    private byte [] getElBytes(Element el, String childName) {
        try {
            Node kid = el.getFirstChild();
            while (kid != null) {
                if (childName.equals(kid.getNodeName())) {
                    Node nn = kid.getFirstChild();
                    if (nn instanceof Text) {
                        String dt = ((Text)nn).getData();
                        return Base64.decode(dt, Base64.DEFAULT);
                    }
                }
                kid = kid.getNextSibling();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public String getMainHtml() {

        myLoadingArchive = true;
		String topHtml;
        try {
            // Find the first ArchiveResource in myDoc, should be <ArchiveResource>
            Element ar = (Element) myDoc.getDocumentElement().getFirstChild().getFirstChild();
            byte b[] = getElBytes(ar, "data");
            
            topHtml = new String(b);
          
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return topHtml;
    }

}
