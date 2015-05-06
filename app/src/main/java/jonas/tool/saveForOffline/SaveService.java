/**

 This file is part of saveForOffline, an app which saves / downloads complete webpages.
 Copyright (C) 2015  Jonas Czech

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
 This was originally based on getMeThatPage (https://github.com/PramodKhare/GetMeThatPage/),
 with lots of improvements.
 The code for actually saving pages is further down in this file.
 **/

package jonas.tool.saveForOffline;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.File;

//this is a mess...

public class SaveService extends Service {

    private Looper mServiceLooper;
    private ServiceHandler mServiceHandler;
    private Notification.Builder mBuilder;
    private NotificationManager mNotificationManager;

    private SharedPreferences sharedPref;

    private final int NOTIFICATION_ID = 1;

    private int waitingIntentCount = 0;


    private void addToDb(String fileLocation, String thumbnailLocation, String title, String originalUrl) {

        DbHelper mHelper = new DbHelper(SaveService.this);
        SQLiteDatabase dataBase = mHelper.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(DbHelper.KEY_FILE_LOCATION, fileLocation);
        values.put(DbHelper.KEY_TITLE, title);
        values.put(DbHelper.KEY_THUMBNAIL, thumbnailLocation);
        values.put(DbHelper.KEY_ORIG_URL, originalUrl);

        dataBase.insert(DbHelper.TABLE_NAME, null, values);

        dataBase.close();
    }

    private void notifyUser(String contentTitle, String extraMessage, int progress, int maxProgress, boolean progressIndeterminate, boolean showProgress, boolean isFinished) {
        Intent cancelIntent = new Intent(SaveService.this, SaveService.class);
        cancelIntent.putExtra("USER_CANCELLED", true);
        PendingIntent pendingIntent = PendingIntent.getService(SaveService.this, 0, cancelIntent, 0);

        if (mBuilder == null) {
            mBuilder = new Notification.Builder(SaveService.this);
        }

        if (contentTitle != null) {
            mBuilder.setContentTitle(contentTitle);
            mBuilder.setTicker(contentTitle);
        }

        if (extraMessage != null) {
            mBuilder.setContentText(extraMessage);
        }

        mBuilder.setProgress(maxProgress, progress, progressIndeterminate);
        mBuilder.setSmallIcon(isFinished ? R.drawable.ic_notify_save : android.R.drawable.stat_sys_download);
        mBuilder.setProgress(showProgress ? maxProgress : 0, showProgress ? progress : 0, progressIndeterminate);
        mBuilder.setOngoing(!isFinished);
        mBuilder.setOnlyAlertOnce(true);
        mBuilder.setPriority(Notification.PRIORITY_LOW);

        mBuilder.addAction(R.drawable.ic_action_discard, "Cancel current", pendingIntent);

        mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
    }

    private String getNewDirectoryPath (String title, String oldDirectoryPath) {
        if (!sharedPref.getBoolean("is_custom_storage_dir", false)) {
            System.out.println(sharedPref.getBoolean("is_custom_storage_dir", false));
            return oldDirectoryPath;
        }

        String returnString = title.replaceAll("[^a-zA-Z0-9-_\\.]", "_") + DirectoryHelper.createUniqueFilename();

        File f = new File(oldDirectoryPath);
        return f.getParentFile().getAbsolutePath() + File.separator  + returnString + File.separator;
    }

    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(final Message msg) {

            Intent intent = (Intent) msg.obj;
            if (intent.getBooleanExtra("userHasCancelled", false)) {
                return;
            }
            waitingIntentCount--;

            String originalUrl = intent.getStringExtra("origurl");
            String destinationDirectory = DirectoryHelper.getDestinationDirectory(sharedPref);

            notifyUser("Saving page...", "Save in progress", 0, 0, true, true, false);

            PageSaver pageSaver = new PageSaver(new PageSaveEventCallback());
            boolean success = pageSaver.getPage(originalUrl, destinationDirectory, "index.html");

            boolean userHasCancelled = false;

            if (userHasCancelled) { //user cancelled, remove the notification, and delete files.
                mNotificationManager.cancelAll();
                File file = new File(destinationDirectory);
                DirectoryHelper.deleteDirectory(file);
                return;
            } else if (!success) { //something went wrong, leave the notification, and delete files.
                File file = new File(destinationDirectory);
                DirectoryHelper.deleteDirectory(file);
                return;
            }


            File oldFile = new File(destinationDirectory);
            oldFile.renameTo(new File(getNewDirectoryPath(pageSaver.getPageTitle(), destinationDirectory)));
            System.out.println("original: "  + destinationDirectory);
            System.out.println("rename to: "  + getNewDirectoryPath(pageSaver.getPageTitle(), destinationDirectory));

            destinationDirectory = getNewDirectoryPath(pageSaver.getPageTitle(), destinationDirectory);
            String thumbnailLocation = destinationDirectory + "saveForOffline_thumbnail.png";


            notifyUser(null, "Finishing..", 0, 0, true, true, false);

            addToDb(destinationDirectory + "index.html", thumbnailLocation, pageSaver.getPageTitle(), originalUrl);

            Intent i = new Intent(SaveService.this, ScreenshotService.class);
            i.putExtra("origurl", "file://" + destinationDirectory + "index.html");
            i.putExtra("thumbnail", thumbnailLocation);
            startService(i);

            notifyUser("Save completed", pageSaver.getPageTitle(), 0, 0, false, false, true);
        }

        private class PageSaveEventCallback implements EventCallback {

            @Override
            public void onProgressChanged(final int progress, final int maxProgress, final boolean indeterminate) {
                notifyUser(null, null, progress, maxProgress, indeterminate, true, false);
            }

            @Override
            public void onCurrentFileChanged(final String fileName) {
                notifyUser(null, "Saving file: " + fileName, 0, 0, false, true, false);
            }

            @Override
            public void onLogMessage(final String message) {
                Log.d("PageSaverService", message);
            }

            @Override
            public void onError(final String errorMessage) {
                Log.e("PageSaverService", errorMessage);
            }
        }

    }

    @Override
    public void onCreate() {

        HandlerThread thread = new HandlerThread("SaveService", Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        // Get the HandlerThread's Looper and use it for our Handler
        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        sharedPref = PreferenceManager.getDefaultSharedPreferences(SaveService.this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        waitingIntentCount++;

        // For each start request, send a message to start a job and deliver the
        // start ID so we know which request we're stopping when we finish the job
        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        msg.obj = intent;
        mServiceHandler.sendMessage(msg);

        return START_NOT_STICKY;

    }

    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding, so return null
        return null;
    }
}