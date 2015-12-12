package jonas.tool.saveForOffline;

import org.jsoup.nodes.Document;

import java.io.File;
import com.squareup.okhttp.*;
import org.jsoup.select.*;
import java.util.*;
import org.jsoup.nodes.*;
import java.net.*;
import java.io.*;
import java.util.regex.*;
import android.graphics.*;
import org.jsoup.*;

public class FaviconFetcher {
	private static FaviconFetcher INSTANCE = new FaviconFetcher();
	private OkHttpClient client = new OkHttpClient();
	
	private final String[] htmlIconCssQueries = {
		"meta[property=\"og:image\"]",
		"meta[name=\"msapplication-TileImage\"]",
		"link[rel=\"fluid-icon\"]",
		"link[rel=\"icon\"]",
		"link[rel=\"shortcut icon\"]",
		"link[rel=\"apple-touch-icon\"]",
		"link[rel=\"apple-touch-icon-precomposed\"]",
		"img[alt=\"Logo\"]",
		"img[alt=\"logo\"]"
	};
										   
	private final String[] hardcodedIconPaths = {
		"/favicon.ico",
		"/apple-touch-icon.png",
		"/apple-touch-icon-precomposed.png",
	};
	
	private FaviconFetcher() {}
	
	public static FaviconFetcher getInstance () {
		return INSTANCE;
	}
	
	public String getFaviconUrl (String pageUrl, String userAgent) {
		try {
			Document document = Jsoup.parse(getStringFromUrl(pageUrl, userAgent), pageUrl);
			List <String> potentialIconUrls = getPotentialFaviconUrls(document);
			
			return pickBestIconUrl(potentialIconUrls);
		} catch (IllegalStateException | IOException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public List<String> getPotentialFaviconUrls (Document document) {
		List<String> iconUrls = new ArrayList<String>();
		HttpUrl base = HttpUrl.parse(document.baseUri());
		
		for (String cssQuery : htmlIconCssQueries) {
			for (Element e : document.select(cssQuery)) {
				if (e.hasAttr("href")) {
					iconUrls.add(e.attr("href"));
				}
				
				if (e.hasAttr("content")) {
					iconUrls.add(e.attr("content"));
				}
				
				if (e.hasAttr("src")) {
					iconUrls.add(e.attr("src"));
				}
			}
		}
		
		for (String path : hardcodedIconPaths) {
			HttpUrl	url = HttpUrl.parse("http://" + HttpUrl.parse(document.baseUri()).host() + path);
			iconUrls.add(url.toString());
		}
		
		for (ListIterator<String> i = iconUrls.listIterator(); i.hasNext(); ) {
			HttpUrl httpUrl = base.resolve(i.next());
			if (httpUrl != null) {
				i.set(httpUrl.toString());
			} else {
				i.remove();
			}
		}
		
		return iconUrls;
		
	}
	
	
	public String pickBestIconUrl (List<String> urls) {
		String bestIconUrl = null;
		int currentBestWidth = 0;
		
		for (String url : urls) {
			BitmapFactory.Options options = getBitmapDimensFromUrl(url);
			if (options != null && options.outHeight == options.outHeight) {
				if ((bestIconUrl != null) && (currentBestWidth <= options.outWidth)) {
					bestIconUrl = url;
					currentBestWidth = options.outWidth;
				} else if (bestIconUrl == null) {
					bestIconUrl = url;
					currentBestWidth = options.outWidth;
				}
			}
		}
		
		return bestIconUrl;
	}
	
	private BitmapFactory.Options getBitmapDimensFromUrl (String url) {
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		
		Request request = new Request.Builder()
			.url(url)
			.build();

		try {
			Response response = client.newCall(request).execute();
			InputStream is = response.body().byteStream();

			BitmapFactory.decodeStream(is, null, options);

			response.body().close();
			is.close();

			return options;

		} catch (IllegalArgumentException | IOException | FileNotFoundException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	private String getStringFromUrl(String url, String userAgent) throws IOException, IllegalStateException {
        Request request = new Request.Builder()
			.url(url)
			.addHeader("User-Agent", userAgent)
			.build();
        Response response = client.newCall(request).execute();
        String out = response.body().string();
        response.body().close();
        return out;
    }
}
