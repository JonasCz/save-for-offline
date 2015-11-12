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
    
    private SharedPreferences sharedPref;

    private int waitingIntentCount = 0;

    private PageSaver pageSaver;
	
	private NotificationTools notificationTools;


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

    private String getUserAgent () {
        return sharedPref.getString("user_agent", getResources().getStringArray(R.array.entries_list_preference)[1]);
    }


    private String getNewDirectoryPath (String title, String oldDirectoryPath) {
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
            waitingIntentCount--;

            String originalUrl = intent.getStringExtra("origurl");
            String destinationDirectory = DirectoryHelper.getDestinationDirectory(sharedPref);
			
			notificationTools = new NotificationTools();
			notificationTools.setTicker("Saving page...")
				.setContentTitle("Saving page...")
				.setContentText("Save in progress")
				.setIcon(android.R.drawable.stat_sys_download)
				.setProgress(0, 1, true)
				.setShowProgress(true)
				.createNotification();

            pageSaver = new PageSaver(new PageSaveEventCallback());

            pageSaver.getOptions().setUserAgent(getUserAgent());
            boolean success = pageSaver.getPage(originalUrl, destinationDirectory, "index.html");


            if (pageSaver.isCancelled()) { //user cancelled, remove the notification, and delete files.
                notificationTools.cancelAll();
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

			notificationTools.setContentText("Finishing..")
				.createNotification();

            addToDb(destinationDirectory + "index.html", thumbnailLocation, pageSaver.getPageTitle(), originalUrl);

            Intent i = new Intent(SaveService.this, ScreenshotService.class);
            i.putExtra("origurl", "file://" + destinationDirectory + "index.html");
            i.putExtra("thumbnail", thumbnailLocation);
            startService(i);
			
			notificationTools.setTicker("Save completed: " + pageSaver.getPageTitle())
				.setContentTitle("Save completed")
				.setContentText(pageSaver.getPageTitle())
				.setIcon(R.drawable.ic_notify_save)
				.setShowProgress(false)
				.createNotificationWithAlert();
        }
		

        private class PageSaveEventCallback implements EventCallback {

            @Override
            public void onProgressChanged(final int progress, final int maxProgress, final boolean indeterminate) {
				notificationTools.setProgress(progress, maxProgress, indeterminate);
            }

            @Override
            public void onCurrentFileChanged(final String fileName) {
				notificationTools.setContentText("Saving file: " + fileName)
					.createNotification();
            }

            @Override
            public void onLogMessage(final String message) {
                Log.d("PageSaverService", message);
            }

            @Override
            public void onError(final String errorMessage) {
                Log.e("PageSaverService", errorMessage);
				
				notificationTools.setTicker("Error, page not saved: " + errorMessage)
					.setContentTitle("Error, page not saved")
					.setContentText(errorMessage)
					.setShowProgress(false)
					.setIcon(android.R.drawable.stat_sys_warning)
					.createNotificationWithAlert();
            }
        }

    }
	
	private class NotificationTools {
			
			private Notification.Builder builder;
            private NotificationManager notificationManager;
			
			private final int NOTIFICATION_ID = 1;
			
			public NotificationTools () {
				notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
				builder = new Notification.Builder(SaveService.this);
				builder.setOnlyAlertOnce(true);
				builder.setPriority(Notification.PRIORITY_HIGH);
			}
			
			public NotificationTools setTicker (String text) {
				builder.setTicker(text);
				return this;
			}
			
			public NotificationTools setContentTitle (String text) {
				builder.setContentTitle(text);
				return this;
			}
			
			public NotificationTools setContentText (String text) {
				builder.setContentText(text);
				return this;
			}
			
			public NotificationTools setIcon (int icon) {
				builder.setSmallIcon(icon);
				return this;
			}
			
			public NotificationTools setProgress (int progress, int maxProgress, boolean indeterminate) {
				builder.setProgress(maxProgress, progress, indeterminate);
				return this;
			}
			
			public NotificationTools setShowProgress (boolean showProgress) {
				if (showProgress) {
					builder.setProgress(0, 1, true);
				} else {
					builder.setProgress(0, 0, false);
				}
				return this;
			}
			
			public NotificationTools setOngoing (boolean ongoing) {
				builder.setOngoing(ongoing);
				return this;
			}
			
			public void createNotification () {
				notificationManager.notify(NOTIFICATION_ID, builder.build());
			}
			
		    public void createNotificationWithAlert () {
				builder.setOnlyAlertOnce(false);
			    notificationManager.notify(NOTIFICATION_ID, builder.build());
				builder.setOnlyAlertOnce(true);
		    }
			
			public void cancelAll () {
				notificationManager.cancelAll();
			}
			
			
		}

    @Override
    public void onCreate() {

        HandlerThread thread = new HandlerThread("SaveService", Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);
		
        sharedPref = PreferenceManager.getDefaultSharedPreferences(SaveService.this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent.getBooleanExtra("USER_CANCELLED", false)) {
            pageSaver.cancel();
            return 0;
        }

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
