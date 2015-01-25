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

public abstract class WebArchiveReader {
    private Document myDoc = null;
    private static boolean myLoadingArchive = false;
    private WebView myWebView = null;
    private ArrayList<String> urlList = new ArrayList<String>();
    private ArrayList<Element> urlNodes = new ArrayList<Element>();

    abstract void onFinished(WebView webView);

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

    public boolean loadToWebView(WebView v) {
        myWebView = v;
        v.setWebViewClient(new WebClient());
        WebSettings webSettings = v.getSettings();
        webSettings.setDefaultTextEncodingName("UTF-8");

        myLoadingArchive = true;
        try {
            // Find the first ArchiveResource in myDoc, should be <ArchiveResource>
            Element ar = (Element) myDoc.getDocumentElement().getFirstChild().getFirstChild();
            byte b[] = getElBytes(ar, "data");

            // Find out the web page charset encoding
            String charset = null;
            String topHtml = new String(b).toLowerCase();
            int n1 = topHtml.indexOf("<meta http-equiv=\"content-type\"");
            if (n1 > -1) {
                int n2 = topHtml.indexOf('>', n1);
                if (n2 > -1) {
                    String tag = topHtml.substring(n1, n2);
                    n1 = tag.indexOf("charset");
                    if (n1 > -1) {
                        tag = tag.substring(n1);
                        n1 = tag.indexOf('=');
                        if (n1 > -1) {
                            tag = tag.substring(n1+1);
                            tag = tag.trim();
                            n1 = tag.indexOf('\"');
                            if (n1 < 0)
                                n1 = tag.indexOf('\'');
                            if (n1 > -1) {
                                charset = tag.substring(0, n1).trim();
                            }
                        }
                    }
                }
            }

            if (charset != null)
                topHtml = new String(b, charset);
            else
                topHtml = new String(b);
            String baseUrl = new String(getElBytes(ar, "url"));
            v.loadDataWithBaseURL(baseUrl, topHtml, "text/html", "UTF-8", null);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private class WebClient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            if (!myLoadingArchive)
                return null;
            int n = urlList.indexOf(url);
            if (n < 0)
                return null;
            Element parentEl = urlNodes.get(n);
            byte [] b = getElBytes(parentEl, "mimeType");
            String mimeType = b == null ? "text/html" : new String(b);
            b = getElBytes(parentEl, "textEncoding");
            String encoding = b == null ? "UTF-8" : new String(b);
            b = getElBytes(parentEl, "data");
            return new WebResourceResponse(mimeType, encoding, new ByteArrayInputStream(b));
        }

        @Override
        public void onPageFinished(WebView view, String url)
        {
            // our WebClient is no longer needed in view
            view.setWebViewClient(null);
            myLoadingArchive = false;
            onFinished(myWebView);
        }
    }
}
