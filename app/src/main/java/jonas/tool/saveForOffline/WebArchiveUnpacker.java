package jonas.tool.saveForOffline;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.MalformedURLException;

import com.dd.plist.NSArray;
import com.dd.plist.NSData;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;
import com.dd.plist.NSString;
import com.dd.plist.PropertyListParser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;


// Credit on the WebArchive format: http://microcoder.livejournal.com/14969.html
public class WebArchiveUnpacker {
    private NSDictionary rootDict;
    private NSDictionary page;
    private NSArray resourceList;

    public WebArchiveUnpacker(File archive) throws Exception {
        this.rootDict = (NSDictionary)PropertyListParser.parse(archive);

        this.page = (NSDictionary)rootDict.objectForKey("WebMainResource");
        this.resourceList = (NSArray)rootDict.objectForKey("WebSubresources");
        if(this.page == null) {
            System.err.println("(EE) Failed to get page");
        }
        if(this.resourceList == null) {
            System.err.println("(EE) Failed to get resource list");
        }
    }

    public void extractHtml(String rootFolder, String filename) {
        NSString encodingEntry = (NSString)page.objectForKey("WebResourceTextEncodingName");
        NSString urlEntry = (NSString)page.objectForKey("WebResourceURL");
        NSData htmlBytesEntry = (NSData)page.objectForKey("WebResourceData");
        String encoding;
        String rootUrl = "";

        // Determine the encoding of the file
        if(encodingEntry == null) {
            encoding = "UTF-8";
        } else {
            encoding = encodingEntry.toString();
        }

        // Determine the URL
        if(urlEntry != null) {
            try {
                URL url = new URL(urlEntry.toString());
                rootUrl = url.getProtocol() + "://" + url.getAuthority() + "/";

                if(filename == null) {
                    filename = url.getFile();
                }
            } catch(MalformedURLException exc) {
                if(filename == null) {
                    filename = "index.html";
                }
            }
        }

        // Now extract the HTML file
        File file = new File(rootFolder, filename);
        unpackFile(file, htmlBytesEntry);
        cleanHtml(file, encoding, rootUrl);
    }
    
    public void extractHtml(String rootFolder) {
        extractHtml(rootFolder, null);
    }

    public void extractSubresource(String rootFolder) {
        if(this.resourceList == null) {
            return;
        }

        for(NSObject resourceObj: resourceList.getArray()) {
            NSDictionary resource = (NSDictionary)resourceObj;
            NSString urlEntry = (NSString)resource.objectForKey("WebResourceURL");
            NSString mimeTypeEntry = (NSString)resource.objectForKey("WebResourceMIMEType");
            NSData dataEntry = (NSData)resource.objectForKey("WebResourceData");

            String path;
            String mimeType = "";

            // If there's no data, we just skip past it
            if(dataEntry == null) {
                continue;
            }
            if(urlEntry == null) {
                // We need the URL
                continue;
            }

            path = getPathFromUrl(urlEntry.toString());
            if(path.equals("")) {
                continue;
            }
            File file = new File(rootFolder, path);
            unpackFile(file, dataEntry);
        }
    }

    private String getPathFromUrl(String urlStr) {
        try {
            URL url = new URL(urlStr);
            if(url.getProtocol() == "safari-extension") {
                return "";
            }
            return url.getPath();
        } catch(MalformedURLException exc) {
            return "";
        }
    }

    private void unpackFile(File file, NSData dataEntry) {
        File parentDir = new File(file.getParent());
        try {
            if(! parentDir.exists()) {
                parentDir.mkdirs();
            }
            FileOutputStream fileOutput = new FileOutputStream(file);
            fileOutput.write(dataEntry.bytes());
            fileOutput.close();
        } catch(IOException exc) {
            System.err.println("Cannot extract file " + file.getPath());
        }
    }

    protected void cleanHtml(File htmlFile, String encoding, String rootUrl) {
        System.out.println("root url=" + rootUrl);
        Document doc;
        try {
            // Remove all the Safari extensions
            doc = Jsoup.parse(htmlFile, encoding, rootUrl);
            Elements safariExt = doc.select("script[src^=safari-extension://]");
            safariExt.remove();
        } catch(IOException exc) {
            System.err.println("Fail to parse the HTML file " + htmlFile.getPath());
            return;
        }

        try {
            // Write the file back
            String html = doc.toString();
            FileOutputStream fileWriter = new FileOutputStream(htmlFile);
            fileWriter.write(html.getBytes(encoding));
            fileWriter.close();
        } catch(IOException exc) {
            System.err.println("Fail to rewrite the HTML file " + htmlFile.getPath());
            return;
        }
    }
}
