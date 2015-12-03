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
	private final String TAG = "SaveService";
	
    private ServiceHandler mServiceHandler;
    private SharedPreferences sharedPref;

    private int waitingIntentCount = 0;
	private int currentStartId = 0;

    private PageSaver pageSaver;
	private NotificationTools notificationTools;
	
    private void addToDb(String destinationDirectory, String title, String originalUrl) {

        Database mHelper = new Database(SaveService.this);
        SQLiteDatabase dataBase = mHelper.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(Database.FILE_LOCATION, destinationDirectory + "index.html");
		values.put(Database.SAVED_PAGE_BASE_DIRECTORY, destinationDirectory);
        values.put(Database.TITLE, title);
        values.put(Database.THUMBNAIL, destinationDirectory + "saveForOffline_thumbnail.png");
        values.put(Database.ORIGINAL_URL, originalUrl);

        dataBase.insert(Database.TABLE_NAME, null, values);

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
			
			currentStartId = msg.arg1;
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
				.setOngoing(true)
				.createNotification();

            pageSaver = new PageSaver(new PageSaveEventCallback());

            pageSaver.getOptions().setUserAgent(getUserAgent());
			pageSaver.getOptions().setCache(getApplicationContext().getExternalCacheDir(),1024 * 1024 * Integer.valueOf(sharedPref.getString("cache_size", "30")));
            boolean success = pageSaver.getPage(originalUrl, destinationDirectory, "index.html");

            if (pageSaver.isCancelled()) { //user cancelled, remove the notification, and delete files.
                notificationTools.cancelAll();
                File file = new File(destinationDirectory);
                DirectoryHelper.deleteDirectory(file);
				Log.e("SaveService", "Stopping service. Saving cancelled (by user), with startId " + msg.arg1);
				stopSelf(msg.arg1);
                return;
            } else if (!success) { //something went wrong, leave the notification, and delete files.
                File file = new File(destinationDirectory);
                DirectoryHelper.deleteDirectory(file);
				Log.e("SaveService", "Stopping service. Deleting files in: " + destinationDirectory + ", from: " + originalUrl + ", because of failure, with startId " + msg.arg1);
                return;
            }
			
			notificationTools.setContentText("Finishing..")
				.createNotification();

            File oldSavedPageDirectory = new File(destinationDirectory);
			Log.i(TAG, "Original saved page directory: "  + oldSavedPageDirectory.getPath());
			
			File newSavedPageDirectory = new File(getNewDirectoryPath(pageSaver.getPageTitle(), oldSavedPageDirectory.getPath()));
			Log.i(TAG, "Rename to: "  + newSavedPageDirectory.getPath());
			
            oldSavedPageDirectory.renameTo(newSavedPageDirectory);

            addToDb(newSavedPageDirectory.getPath() + File.separator, pageSaver.getPageTitle(), originalUrl);

            Intent i = new Intent(SaveService.this, ScreenshotService.class);
            i.putExtra(Database.FILE_LOCATION, "file://" + newSavedPageDirectory.getPath() + File.separator + "index.html");
            i.putExtra(Database.THUMBNAIL, newSavedPageDirectory + File.separator + "saveForOffline_thumbnail.png");
            startService(i);
			
			stopSelf(msg.arg1);
			Log.i("SaveService", "Stopping service, with startId " + msg.arg1);
			
			notificationTools.setTicker("Save completed: " + pageSaver.getPageTitle())
				.setContentTitle("Save completed")
				.setContentText(pageSaver.getPageTitle())
				.setIcon(R.drawable.ic_notify_save)
				.setShowProgress(false)
				.setOngoing(false)
				.createNotificationWithAlert();	
        }
		

        private class PageSaveEventCallback implements EventCallback {

			@Override
			public void onFatalError(final Throwable e) {
				Log.e("PageSaverService", e.getMessage(), e);
				
				Log.i("SaveService", "Stopping service because of failure, with startId " + currentStartId);
				stopSelf(currentStartId);
				
				notificationTools.setTicker("Error, page not saved: " + e.getMessage())
					.setContentTitle("Error, page not saved")
					.setContentText(e.getMessage())
					.setShowProgress(false)
					.setOngoing(false)
					.setIcon(android.R.drawable.stat_sys_warning)
					.createNotificationWithAlert();
			}
			

            @Override
            public void onProgressChanged(final int progress, final int maxProgress, final boolean indeterminate) {
				notificationTools.setProgress(progress, maxProgress, indeterminate);
            }

            @Override
            public void onProgressMessage(final String message) {
				notificationTools.setContentText(message)
					.createNotification();
            }

            @Override
            public void onLogMessage(final String message) {
                Log.d("PageSaverService", message);
            }

            @Override
            public void onError(final Throwable e) {
                Log.e("PageSaverService", e.getMessage(), e);
            }
			
			@Override
			public void onError(String errorMessage) {
				Log.e(TAG, errorMessage);
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
				startForeground(NOTIFICATION_ID, builder.build());
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

        mServiceHandler = new ServiceHandler(thread.getLooper());
		
        sharedPref = PreferenceManager.getDefaultSharedPreferences(SaveService.this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent.getBooleanExtra("USER_CANCELLED", false)) {
            pageSaver.cancel();
            return 0;
        }

        waitingIntentCount++;
		
        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        msg.obj = intent;
        mServiceHandler.sendMessage(msg);

        return START_NOT_STICKY;
    }

	@Override
	public void onDestroy() {
		Log.i("SaveService", "Service destroyed");
	}

    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding, so return null
        return null;
    }
}
