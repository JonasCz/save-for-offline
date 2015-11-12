/**

 This file is part of saveForOffline, an app which saves / downloads complete webpages.

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
 This was originally based on https://github.com/PramodKhare/GetMeThatPage/
 with lots of improvements.
 **/

package jonas.tool.saveForOffline;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class PageSaver {
    private EventCallback eventCallback;

    private OkHttpClient client = new OkHttpClient();
    private final String HTTP_REQUEST_TAG = "TAG";

    private boolean isCancelled = false;
    private Options options = new Options();


    // filesToGrab - maintains all the links to files which we are going to grab/download
    private List<String> filesToGrab = new ArrayList<String>();
    //framesToGrab - list of html frame files to download
    private List<String> framesToGrab = new ArrayList<String>();
    //cssToGrab - list of all css files to download an parse
    private List<String> cssToGrab = new ArrayList<String>();
    //we do another pass for this one
    private List<String> extraCssToGrab = new ArrayList<String>();

    private String title = "";

    private String indexFileName = "index.html";

    private final Pattern fileNameReplacementPattern = Pattern.compile("[^a-zA-Z0-9-_\\.]");
    private final Pattern urlValidatingPattern = Pattern.compile("^(http(s)?:\\/\\/[a-zA-Z0-9\\-_]+\\.[a-zA-Z]+(.)+)+");

    public Options getOptions() {
        return this.options;
    }

    public String getPageTitle () {
        return this.title;
    }

    public PageSaver(EventCallback callback) {
        this.eventCallback = callback;
    }

    public void cancel() {
        this.isCancelled = true;
        client.cancel(HTTP_REQUEST_TAG);

    }

    public boolean isCancelled () {
        return this.isCancelled;
    }

    public boolean getPage(String url, String outputDirPath, String indexFilename) {

        this.indexFileName = indexFilename;

        File outputDir = new File(outputDirPath);

        if (outputDir.mkdirs() == false) {
            eventCallback.onError("Can't save, storage not available");
            return false;
        }

        //download main html and parse -- isExtra parameter should be false
        boolean success = downloadHtmlAndParseLinks(url, outputDirPath, false);
        if (isCancelled) return false;

        //download and parse html frames
        //don't change this! Enhanced for loop breaks it!
        for (int i = 0; i < framesToGrab.size(); i++) {
            downloadHtmlAndParseLinks(framesToGrab.get(i), outputDirPath, true);
            if (isCancelled) return true;

        }

        //download and parse css files
        for (String urlToDownload : cssToGrab) {
            if (isCancelled) return true;
            downloadCssAndParse(urlToDownload, outputDirPath);
        }

        //download and parse extra css files
        //todo : make this recursive
        for (String urlToDownload : extraCssToGrab) {
            if (isCancelled) return true;
            downloadCssAndParse(urlToDownload, outputDirPath);
        }

        //download extra files, such as images / scripts
        ExecutorService pool = Executors.newFixedThreadPool(5);

        for (String urlToDownload : filesToGrab) {
            if (isCancelled) break;
            eventCallback.onCurrentFileChanged(urlToDownload.substring(urlToDownload.lastIndexOf("/") + 1));
            eventCallback.onProgressChanged(filesToGrab.indexOf(urlToDownload), filesToGrab.size(), false);

            pool.submit(new DownloadTask(urlToDownload, outputDir));
        }

        pool.shutdown();

        try {
            pool.awaitTermination(20, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return success;
    }

    private boolean downloadHtmlAndParseLinks(final String url, final String outputDir, final boolean isExtra) {
        //isExtra should be true when saving a html frame file.

        String filename;

        if (isExtra) {
            filename = getFileName(url);
        } else {
            filename = indexFileName;
        }

        String baseUrl = url;
        if (url.endsWith("/")) {
            baseUrl = url + filename;
        }

        try {
            String htmlContent = getStringFromUrl(url);
            htmlContent = parseHtmlForLinks(htmlContent, baseUrl);

            File outputFile = new File(outputDir, filename);
            saveStringToFile(htmlContent, outputFile);
            return true;

        } catch (IOException e) {
			eventCallback.onError(e.getMessage());
            return false;
        }
    }

    private void downloadCssAndParse(final String url, final String outputDir) {

        String filename = getFileName(url);
        File outputFile = new File(outputDir, filename);

        try {
            String cssContent = getStringFromUrl(url);
            cssContent = parseCssForLinks(cssContent, url);
            saveStringToFile(cssContent, outputFile);
        } catch (IOException e) {
			eventCallback.onError(e.getMessage());
            e.printStackTrace();
        }
    }

    private class DownloadTask implements Runnable {

        private String url;
        private File outputDir;

        public DownloadTask(String url, File toPath) {
            this.url = url;
            this.outputDir = toPath;
        }

        @Override
        public void run() {
            String filename = getFileName(url);
            File outputFile = new File(outputDir, filename);
            if (outputFile.exists()) {
                eventCallback.onLogMessage("File already exists, skipping: " + filename);
                return;
            }

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", getOptions().getUserAgent())
                    .tag(HTTP_REQUEST_TAG)
                    .build();

            try {
                Response response = client.newCall(request).execute();
                InputStream is = response.body().byteStream();

                FileOutputStream fos = new FileOutputStream(outputFile);
                final byte[] buffer = new byte[1024 * 16]; // read in batches of 16K
                int length;
                while ((length = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, length);
                }

                response.body().close();
                fos.flush();
                fos.close();
                is.close();

            } catch (MalformedURLException e) {
				eventCallback.onError(e.getMessage());
                e.printStackTrace();
            } catch (IOException e) {
				eventCallback.onError(e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private String getStringFromUrl(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", getOptions().getUserAgent())
                .build();

        Response response = client.newCall(request).execute();
        String out = response.body().string();
        response.body().close();
        return out;
    }

    private void saveStringToFile(String ToSave, File outputFile) throws IOException {

        if (outputFile.exists()) {
            return;
        }

        outputFile.createNewFile();

        FileOutputStream fos = new FileOutputStream(outputFile);
        fos.write(ToSave.getBytes());

        fos.flush();
        fos.close();


    }

    private String parseHtmlForLinks(String htmlToParse, String baseUrl) {
        //get all links from this webpage and add them to LinksToVisit ArrayList
        Document document;

        document = Jsoup.parse(htmlToParse, baseUrl);
        document.outputSettings().escapeMode(Entities.EscapeMode.extended);
		
		if (title.equals("")) {
			title = document.title();
		}

        String urlToGrab;

        Elements links;

        if (getOptions().saveFrames()) {
            links = document.select("frame[src]");
            eventCallback.onLogMessage("Got " + links.size() + " frames");
            for (Element link : links) {
                urlToGrab = link.attr("abs:src");
                addLinkToList(urlToGrab, framesToGrab);
                String replacedURL = getFileName(urlToGrab);
                link.attr("src", replacedURL);
            }

            links = document.select("iframe[src]");
            eventCallback.onLogMessage("Got " + links.size() + " iframes");
            for (Element link : links) {
                urlToGrab = link.attr("abs:src");

                addLinkToList(urlToGrab, framesToGrab);

                String replacedURL = getFileName(urlToGrab);
                link.attr("src", replacedURL);


            }
        }

        if (getOptions().saveOther()) {
            // Get all the links
            links = document.select("link[href]");
            eventCallback.onLogMessage("Got " + links.size() + " link elements with a href attribute");
            for (Element link : links) {
                urlToGrab = link.attr("abs:href");

                //if it is css, parse it later to extract urls (images referenced from "background" attributes for example)
                if (link.attr("rel").equals("stylesheet")) {
                    cssToGrab.add(link.attr("abs:href"));
                } else {
                    addLinkToList(urlToGrab, filesToGrab);
                }

                String replacedURL = getFileName(urlToGrab);
                link.attr("href", replacedURL);

            }

            //get links in embedded css also, and modify the links to point to local files
            links = document.select("style[type=text/css]");
            eventCallback.onLogMessage("Got " + links.size() + " embedded stylesheets, parsing CSS");
            for (Element link : links) {
                String cssToParse = link.data();
                String parsedCss = parseCssForLinks(cssToParse, baseUrl);
                if (link.dataNodes().size() != 0) {
                    link.dataNodes().get(0).setWholeData(parsedCss);
                }
            }

            links = document.select("[style]");
            eventCallback.onLogMessage("Got " + links.size() + " elements with a style attribute, parsing CSS");
            for (Element link : links) {
                String cssToParse = link.attr("style");
                String parsedCss = parseCssForLinks(cssToParse, baseUrl);
                link.attr("style", parsedCss);
            }

        }

        if (getOptions().saveScripts()) {
            links = document.select("script[src]");
            eventCallback.onLogMessage("Got " + links.size() + " script elements");
            for (Element link : links) {
                urlToGrab = link.attr("abs:src");
                addLinkToList(urlToGrab, filesToGrab);
                String replacedURL = getFileName(urlToGrab);
                link.attr("src", replacedURL);
            }
        }

        if (getOptions().saveImages()) {
            links = document.select("img[src]");
            eventCallback.onLogMessage("Got " + links.size() + " image elements");
            for (Element link : links) {
                urlToGrab = link.attr("abs:src");
                addLinkToList(urlToGrab, filesToGrab);

                String replacedURL = getFileName(urlToGrab);
                link.attr("src", replacedURL);
            }
        }

        if (getOptions().saveVideo()) {
            //video src is sometimes in a child element
            links = document.select("video:not([src])");
            eventCallback.onLogMessage("Got " + links.size() + " video elements without src attribute");
            for (Element link : links.select("[src]")) {
                urlToGrab = link.attr("abs:src");
                addLinkToList(urlToGrab, filesToGrab);

                String replacedURL = getFileName(urlToGrab);
                link.attr("src", replacedURL);
            }

            links = document.select("video[src]");
            eventCallback.onLogMessage("Got " + links.size() + " video elements");
            for (Element link : links) {
                urlToGrab = link.attr("abs:src");
                addLinkToList(urlToGrab, filesToGrab);

                String replacedURL = getFileName(urlToGrab);
                link.attr("src", replacedURL);
            }
        }

        if (getOptions().makeLinksAbsolute()) {
            //make links absolute, so they are not broken
            links = document.select("a[href]");
            eventCallback.onLogMessage("Making " + links.size() + " links absolute");
            for (Element link : links) {
                String absUrl = link.attr("abs:href");
                link.attr("href", absUrl);
            }
        }
        return document.outerHtml();
    }

    private String parseCssForLinks(String cssToParse, String baseUrl) {

        String patternString = "url(\\s*\\(\\s*['\"]*\\s*)(.*?)\\s*['\"]*\\s*\\)"; //I hate regexes...

        Pattern pattern = Pattern.compile(patternString);
        Matcher matcher = pattern.matcher(cssToParse);

        eventCallback.onLogMessage("Parsing CSS");

        int count = 0;
        //find everything inside url(" ... ")
        while (matcher.find()) {
            count++;

            if (matcher.group().replaceAll(patternString, "$2").contains("/")) {
                cssToParse = cssToParse.replace(matcher.group().replaceAll(patternString, "$2"), getFileName(matcher.group().replaceAll(patternString, "$2")));

            }

            addLinkToList(makeLinkAbsolute(matcher.group().replaceAll(patternString, "$2").trim(), baseUrl), filesToGrab);
        }

        // find css linked with @import  -  needs testing
        //todo: test this
        String importString = "@(import\\s*['\"])()([^ '\"]*)";
        pattern = Pattern.compile(importString);
        matcher = pattern.matcher(cssToParse);
        matcher.reset();
        count = 0;
        while (matcher.find()) {
            count++;
            if (matcher.group().replaceAll(patternString, "$2").contains("/")) {
                cssToParse = cssToParse.replace(matcher.group().replaceAll(patternString, "$2"), getFileName(matcher.group().replaceAll(patternString, "$2")));

            }
            addLinkToList(makeLinkAbsolute(matcher.group().replaceAll(patternString, "$2").trim(), baseUrl), extraCssToGrab);
        }

        return cssToParse;
    }

    private void addLinkToList(String link, List<String> list) {
        if (link == null) {
            return;
        }

        if (!list.contains(link)) {
            list.add(link);
        }
    }

    private String makeLinkAbsolute(String link, String baseurl) {

        try {
            URL u = new URL(new URL(baseurl), link);
            return u.toString();
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return link;
        }

    }

    private String getFileName(String url) {

        String filename = url.substring(url.lastIndexOf('/') + 1);

        if (filename.contains("?")) {
            filename = filename.substring(0, filename.indexOf("?"));
        }

        filename = fileNameReplacementPattern.matcher(filename).replaceAll("_");

        return filename;
    }

    class Options {
        private boolean makeLinksAbsolute = true;

        private boolean saveImages = true;
        private boolean saveFrames = true;
        private boolean saveOther = true;
        private boolean saveScripts = true;
        private boolean saveVideo = false;

        private String userAgent = "mozilla chrome webkit";

        public String getUserAgent() {
            return userAgent;
        }

        public void setUserAgent(final String userAgent) {
            this.userAgent = userAgent;
        }

        public boolean makeLinksAbsolute() {
            return makeLinksAbsolute;
        }

        public void makeLinksAbsolute(final boolean makeLinksAbsolute) {
            this.makeLinksAbsolute = makeLinksAbsolute;
        }

        public boolean saveImages() {
            return saveImages;
        }

        public void saveImages(final boolean saveImages) {
            this.saveImages = saveImages;
        }

        public boolean saveFrames() {
            return saveFrames;
        }

        public void saveFrames(final boolean saveFrames) {
            this.saveFrames = saveFrames;
        }

        public boolean saveScripts() {
            return saveScripts;
        }

        public void saveScripts(final boolean saveScripts) {
            this.saveScripts = saveScripts;
        }

        public boolean saveOther() {
            return saveOther;
        }

        public void saveOther(final boolean saveOther) {
            this.saveOther = saveOther;
        }

        public boolean saveVideo() {
            return saveVideo;
        }

        public void saveVideo(final boolean saveVideo) {
            this.saveVideo = saveVideo;
        }
    }
}

interface EventCallback {
    public void onProgressChanged(int progress, int maxProgress, boolean indeterminate);

    public void onCurrentFileChanged(String fileName);

    public void onLogMessage (String message);

    public void onError(String errorMessage);
}

