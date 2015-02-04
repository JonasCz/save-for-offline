/**
 * @author Pramod Khare
 * GrabWebPage using HttpURLConnection
 * @Purpose This program will grab the whole web page including all its images, css and js files
 * and stores them in a single output directory with all urls in html page modified to point 
 * to this directory itself.
 * @date: 12-Dec-2013
 * 
 * So when you open downloaded HTML in browser it will open with proper css and images applied,
 * even though in original webpage all css and js files were from different folder hierarchy.
 */

package jonas.tool.saveForOffline;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * @author Pramod Khare
 * Main class where it all starts
 */
public class saveCompleteWebpage {
	/**
	 * Default Constructor
	 */
	public saveCompleteWebpage() {}

	/**
	 * @param args - The inline Command parameters. 
	 * accepts only 2 parameters, first is url and second is output directory location
	 * @throws Exception 
	 * @exception In case url is broken or invalid - program will throw an exception
	 *            In case of user doesn't have proper privileges over output directory
	 *            or directory already exists, or unable to access output directory
	 *            location, etc - raises invalid output directory location
	 */
	public void grabPage(String url, String outputDirPath) throws Exception {

		int i = 0;
        if(url == null || outputDirPath == null){
        	System.out.println(GrabUtility.usageMessage);
	        throw new Exception("Invalid input parameters");
        }

		// in case no http protocol specified then add protocol part 
		// to form proper url
		if(!url.startsWith("http://") && !url.startsWith("https://")){
			throw new Exception("url does not have protocol part. Must start with http:// or https://");
			
		}

		URL obj = new URL(url);
		File outputDir = new File(outputDirPath);
		if(outputDir.exists() && outputDir.isFile()){
			System.out.println("output directory path is wrong, please provide some directory path");
			return;
		} else if (!outputDir.exists()){
			outputDir.mkdirs();
			System.out.println("Directory created!!");
		}
		saveCompleteWebpage.getWebPage(obj, outputDir);
		System.out.println("First Page grabbed successfully!!");

		//Links to visit ->
		String tempEntry = null;
		int linksToGrabSize;
		synchronized (GrabUtility.filesToGrab) {
			linksToGrabSize = GrabUtility.filesToGrab.size();
			System.out.println("Total filesToGrab - "+linksToGrabSize);
		}

		for (i=0; i<linksToGrabSize; i++) {
			System.out.println("Value of i - "+i);
			tempEntry = null;

			synchronized (GrabUtility.filesToGrab) {
				if(GrabUtility.filesToGrab.size() > i){
					tempEntry = GrabUtility.filesToGrab.get(i);
					obj = new URL(tempEntry);
					if(!GrabUtility.isURLAlreadyGrabbed(tempEntry)){
						saveCompleteWebpage.getWebPage(obj, outputDir);
					}
					System.out.println("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
					System.out.println("Grabbing Page "+tempEntry);
				}
			}
			linksToGrabSize = GrabUtility.filesToGrab.size();
		}

		System.out.println("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");

		Iterator<String> filesToGrab = GrabUtility.filesToGrab.iterator();
		System.out.println("URLs to Grab - ");
		while (filesToGrab.hasNext()) {
			tempEntry = filesToGrab.next();
			System.out.println(tempEntry);
		}
		System.out.println("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");

		//Total Links Visited:-
		Iterator<String> grabbedFiles = GrabUtility.grabbedFiles.iterator();
		System.out.println("Grabbeded URLs - ");
		while (grabbedFiles.hasNext()) {
			tempEntry = grabbedFiles.next();
			System.out.println(tempEntry);
		}
		System.out.println("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
	}

	public static void getWebPage(URL obj, File outputDir) {
		FileOutputStream fop = null;
		BufferedReader in = null;
		HttpURLConnection conn = null;
		File outputFile = null;
		InputStream is = null;
		try {
			String path = obj.getPath();
			String filename = path.substring(path.lastIndexOf('/')+1);
			if(filename.equals("/") || filename.equals("")){
				filename = "index.html";
			}
			System.out.println(filename);

			//Output file name
			outputFile = new File(outputDir, filename);

			conn = (HttpURLConnection) obj.openConnection();
			conn.setReadTimeout(5000);
			conn.addRequestProperty("Accept-Language", "en-US,en;q=0.8");
			conn.addRequestProperty("User-Agent", "Java");
			conn.addRequestProperty("Referer", "google.com");

			boolean redirect = false;

			// normally, 3xx is redirect
			int status = conn.getResponseCode();
			if (status != HttpURLConnection.HTTP_OK) {
				if (status == HttpURLConnection.HTTP_MOVED_TEMP
					|| status == HttpURLConnection.HTTP_MOVED_PERM
					|| status == HttpURLConnection.HTTP_SEE_OTHER){
					redirect = true;
				}else{
					System.out.println("Unable to get resource mostly 404 "+status);
					return;
				}
			}

			System.out.println("Response Code ... " + status);

			if (redirect) {
				// get redirect url from "location" header field
				String newUrl = conn.getHeaderField("Location");

				// get the cookie if need, for login
				String cookies = conn.getHeaderField("Set-Cookie");

				// open the new connnection again
				obj =  new URL(newUrl);
				conn = (HttpURLConnection) obj.openConnection();
				conn.setRequestProperty("Cookie", cookies);
				conn.addRequestProperty("Accept-Language", "en-US,en;q=0.8");
				conn.addRequestProperty("User-Agent", "Mozilla");
				conn.addRequestProperty("Referer", "google.com");

				System.out.println("Redirect to URL : " + newUrl);
			}

			// if file doesn't exists, then create it
			if (!outputFile.exists()) {
				outputFile.createNewFile();
			}
			// can we write this file
			if(!outputFile.canWrite()){
				System.out.println("Cannot write to file - "+outputFile.getAbsolutePath());
				return;
			}
			//parse only HTML type page response, others just write them to their respective files
			//in given output directory
			if(filename.endsWith("html") || filename.endsWith("htm")
			   || filename.endsWith("asp") || filename.endsWith("aspx")
			   || filename.endsWith("php") || filename.endsWith("php")
			   || filename.endsWith("net")){
				in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
				//Convert parse only if its html, others leave it as is
				String inputLine;
				StringBuffer strResponse = new StringBuffer();
				// append whole response into single string, save it into a file on storage
				// if its of type html then parse it and get all css and images and javascript files
				// and add them to filesToGrab list
				while ((inputLine = in.readLine()) != null) {
					strResponse.append(inputLine+"\r\n");
				}
				String htmlContent = strResponse.toString();
				htmlContent = GrabUtility.searchForNewFilesToGrab(htmlContent, obj);

				outputFile = new File(outputDir, "index.html");
				
				try {
					// clear previous files contents
					fop = new FileOutputStream(outputFile);
					fop.write(htmlContent.getBytes());
					fop.flush();
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else {
				// In case if file is not HTML type then directly store it to 
				// output directory
				try {
					fop = new FileOutputStream(outputFile);
					is = conn.getInputStream();
					// clear previous files contents
					byte[] buffer = new byte[1024*16]; // read in batches of 16K
		            int length;
		            while ((length = is.read(buffer)) > 0) {
		                fop.write(buffer, 0, length);
		            }
					fop.flush();					
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		} catch (Exception e) {
			System.out.println("Excpetion in getting webpage - "+obj);
			e.printStackTrace();
		} finally {
			try {
				if(is != null){
					is.close();
				}
				if(in != null){
					in.close();
				}
				if (fop != null) {
					fop.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}

/**
 * @author Pramod Khare
 * Contains all the utility methods used in above GrabWebPage class
 */
class GrabUtility{
	// filesToGrab - maintains all the links to files which we are going to grab/download
	public static List<String> filesToGrab = new ArrayList<String>();
	// grabbedFiles - links/urls to files which we have already downloaded
	public static HashSet<String> grabbedFiles = new HashSet<String>();

	public static final String usageMessage = 
	"Usage: GrabWebPage [--url url-to-grab] [--out output-directory-full-path]"
	+ 	"  		--url url-to-grab\turl of webpage to grab"
	+ 	"  		--out output-directory-full-path where to store grabbed files"
	+  	"For example -->  " 
	+	" GrabWebPage --url \"http://www.google.com\" --out \"F:/New folder\"";

	public static void addLinkGrabbedFilesList(String url){
		synchronized (grabbedFiles) {
			grabbedFiles.add(url);
		}
	}

	public static String getMovedUrlLocation(String responseHeader){
		//handle HTTP Response
		StringTokenizer stk = new StringTokenizer(responseHeader.toString(), "\n", false);
		//check the new URL from response's location field
		String newUrl = null;
		while(stk.hasMoreTokens()){
			String tmp = stk.nextToken();
			if(tmp.toLowerCase().startsWith("location:") && 
			   tmp.split(" ")[1] != null && !tmp.split(" ")[1].trim().equals("")){
				newUrl = tmp.split(" ")[1];
				break;
			}
		}
		return newUrl;
	}

	public static String searchForNewFilesToGrab(String htmlContent, URL fromHTMLPageUrl){
		//get all links from this webpage and add them to Frontier List i.e. LinksToVisit ArrayList
		Document responseHTMLDoc = null;
		String urlToGrab = null;
		if(!htmlContent.trim().equals("")){
			responseHTMLDoc = Jsoup.parse(htmlContent);
			// GrabUtility.searchNewLinksForCrawling(responseHTMLDoc, url);
			// Get all the links
			System.out.println("All Links - ");
			Elements links = responseHTMLDoc.select("link[href]");
			for(Element link: links){
				urlToGrab = link.attr("href");
				addLinkToFrontier(urlToGrab, fromHTMLPageUrl);
				System.out.println("Actual URL - "+urlToGrab);
				String replacedURL = urlToGrab.substring(urlToGrab.lastIndexOf("/")+1);
				htmlContent = htmlContent.replaceAll(urlToGrab, replacedURL);
				System.out.println("Replaced URL - "+replacedURL);
			}

			System.out.println("All external scripts - ");
			Elements links2 = responseHTMLDoc.select("script[src]");
			for(Element link: links2){
				urlToGrab = link.attr("src");
				addLinkToFrontier(urlToGrab, fromHTMLPageUrl);
				System.out.println("Actual URL - "+urlToGrab);
				String replacedURL = urlToGrab.substring(urlToGrab.lastIndexOf("/")+1);
				htmlContent = htmlContent.replaceAll(urlToGrab, replacedURL);
				System.out.println("Replaced URL - "+replacedURL);
			}

			System.out.println("All images - ");
			Elements links3 = responseHTMLDoc.select("img[src]");
			for(Element link: links3){
				urlToGrab = link.attr("src");
				addLinkToFrontier(urlToGrab, fromHTMLPageUrl);
				System.out.println("Actual URL - "+urlToGrab);
				String replacedURL = urlToGrab.substring(urlToGrab.lastIndexOf("/")+1);
				htmlContent = htmlContent.replaceAll(urlToGrab, replacedURL);
				System.out.println("Replaced URL - "+replacedURL);
			}
		}
		return htmlContent;
	}

	public static void addLinkToFrontier(String link, URL fromHTMLPageUrl) {
		synchronized (filesToGrab) {
			if(link.startsWith("/")){
				// meaning absolute url from root
				System.out.println("Absolute Link - "+getRootUrlString(fromHTMLPageUrl)+link);
				filesToGrab.add(getRootUrlString(fromHTMLPageUrl)+link);
			} else if(link.startsWith("http://") && !filesToGrab.contains(link)){
				System.out.println("Full Doamin Link - "+link);
				URL url;
				try {
					url = new URL(link);
					if(isValidlink(url, fromHTMLPageUrl))	//if link from different domain
						filesToGrab.add(link);
				} catch (MalformedURLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				// meaning relative url from current directory
				System.out.println("Relative Link - "+getCurrentFolder(fromHTMLPageUrl)+link);
				filesToGrab.add(getCurrentFolder(fromHTMLPageUrl)+link);
			}
		}
	}

	public static String getCurrentFolder(URL url){
		String port = (url.getPort() == -1)? "" :(":"+String.valueOf(url.getPort()));
		String path = url.getPath();
		String currentFolderPath = path.substring(0, path.lastIndexOf("/") + 1);
		return url.getProtocol() +"://" + url.getHost()+ port + currentFolderPath;
	}

	public static String getRootUrlString(URL url){
		String port = (url.getPort() == -1)? "" :(":"+String.valueOf(url.getPort()));
		return url.getProtocol() +"://" + url.getHost()+ port;
	}

	//links like mailto, .pdf, or any file downloads, are not to be crawled
	public static boolean isValidlink(URL link, URL fromHTMLPageUrl) {
		//if link is from same domain
		if (getRootUrlString(link).equalsIgnoreCase(getRootUrlString(fromHTMLPageUrl))){
			return true;
		} else {
			return false;
		}
	}

	public static boolean isURLAlreadyGrabbed(String url){
		synchronized (grabbedFiles) {
			return grabbedFiles.contains(url);
		}
	}
}

