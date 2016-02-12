/**
 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 2 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.

 **/

/**
 This file is part of Save For Offline, an Android app which saves / downloads complete webpages for offine reading.
 **/

/**
 If you modify, redistribute, or write something based on this or parts of it, you MUST,
 I repeat, you MUST comply with the GPLv2+ license. This means that if you use or modify
 my code, you MUST release the source code of your modified version, if / when this is
 required under the terms of the license.

 If you cannot / do not want to do this, DO NOT USE MY CODE. Thanks.

 (I've added this message to to the source because it's been used in severeral proprietary
 closed source apps, which I don't want, and which is also a violation of the liense.)
 **/

/**
 Written by Jonas Czech (JonasCz, stackoverflow.com/users/4428462/JonasCz and github.com/JonasCz). (4428462jonascz/eafc4d1afq)
 **/

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
import android.os.*;

public class FaviconFetcher {
	private static FaviconFetcher INSTANCE = new FaviconFetcher();
	private OkHttpClient client = new OkHttpClient();
	
	private final String[] htmlIconCssQueries = {
		"meta[property=\"og:image\"]",
		"meta[name=\"msapplication-TileImage\"]",
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
	
	public String getFaviconUrl (Document document) {
		List <String> potentialIcons = getPotentialFaviconUrls(document);
		return pickBestIconUrl(potentialIcons);
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

		} catch (IllegalArgumentException | IOException e) {
			e.printStackTrace();
			return null;
		}
	}
}
