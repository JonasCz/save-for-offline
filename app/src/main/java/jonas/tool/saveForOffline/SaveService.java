package jonas.tool.saveForOffline;

import android.app.*;
import android.os.*;
import android.content.*;
import java.util.concurrent.*;
import android.database.sqlite.*;
import java.io.*;
import android.preference.*;
import android.util.*;

public class SaveService extends Service {
	
private final String TAG = "SaveService";

private ThreadPoolExecutor executor;
private SharedPreferences sharedPreferences;
private PageSaver pageSaver;
private NotificationTools notificationTools;

	@Override
	public void onCreate() {
		executor = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
		sharedPreferences = PreferenceManager.getDefaultSharedPreferences(SaveService.this);
		pageSaver = new PageSaver(new PageSaveEventCallback());
		notificationTools = new NotificationTools(this);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent.getBooleanExtra("USER_CANCELLED", false)) {
			//cancelling okhttp seems to cause networkOnMainThreadException, hence this.
			Log.w(TAG, "Cancelled");
			new Thread(new Runnable() {
				@Override
				public void run() {
					pageSaver.cancel();
				}			
			}).start();
			
			return START_NOT_STICKY;
		}
		
		String pageUrl = intent.getStringExtra(Intent.EXTRA_TEXT);
		executor.submit(new PageSaveTask(pageUrl));
		
		return START_NOT_STICKY;
	}
	
	private class PageSaveTask implements Runnable {
		private final String pageUrl;
		private String destinationDirectory;

		public PageSaveTask (String pageUrl) {
			this.pageUrl = pageUrl;
			this.destinationDirectory = DirectoryHelper.getDestinationDirectory(sharedPreferences);
		}

		@Override
		public void run() {
			pageSaver.resetState();
			
			notificationTools.notifySaveStarted();
				
			pageSaver.getOptions().setUserAgent(sharedPreferences.getString("user_agent", getResources().getStringArray(R.array.entries_list_preference)[1]));
			pageSaver.getOptions().setCache(getApplicationContext().getExternalCacheDir(),1024 * 1024 * Integer.valueOf(sharedPreferences.getString("cache_size", "30")));
            boolean success = pageSaver.getPage(pageUrl, destinationDirectory, "index.html");
			
			if (pageSaver.isCancelled() || !success) {
				DirectoryHelper.deleteDirectory(new File(destinationDirectory));
				if (pageSaver.isCancelled()) { //user cancelled, remove the notification, and delete files.
					Log.e("SaveService", "Stopping Service, (Cancelled). Deleting files in: " + destinationDirectory + ", from: " + pageUrl);
					notificationTools.cancelAll();
					stopService();
				} else if (!success) { //something went wrong, leave the notification, and delete files.
					Log.e("SaveService", "Failed. Deleting files in: " + destinationDirectory + ", from: " + pageUrl);
				}
				return;
			}
			
			notificationTools.updateSmallText("Finishing...");

            File oldSavedPageDirectory = new File(destinationDirectory);
			File newSavedPageDirectory = new File(getNewDirectoryPath(pageSaver.getPageTitle(), oldSavedPageDirectory.getPath()));
            oldSavedPageDirectory.renameTo(newSavedPageDirectory);

            new Database(SaveService.this).addToDatabase(newSavedPageDirectory.getPath() + File.separator, pageSaver.getPageTitle(), pageUrl);

            Intent i = new Intent(SaveService.this, ScreenshotService.class);
            i.putExtra(Database.FILE_LOCATION, "file://" + newSavedPageDirectory.getPath() + File.separator + "index.html");
			i.putExtra(Database.ORIGINAL_URL, pageUrl);
            i.putExtra(Database.THUMBNAIL, newSavedPageDirectory + File.separator + "saveForOffline_thumbnail.png");
            startService(i);
			
			stopService();
			
			notificationTools.notifyFinished(pageSaver.getPageTitle());	
		}
		
		private String getNewDirectoryPath (String title, String oldDirectoryPath) {
			String returnString = title.replaceAll("[^a-zA-Z0-9-_\\.]", "_") + DirectoryHelper.createUniqueFilename();

			File f = new File(oldDirectoryPath);
			return f.getParentFile().getAbsolutePath() + File.separator  + returnString + File.separator;
		}
	}
	
	private class PageSaveEventCallback implements EventCallback {

		@Override
		public void onFatalError(final Throwable e, String pageUrl) {
			Log.e("PageSaverService", e.getMessage(), e);
			stopService();
			
			notificationTools.notifyFailure(e.getMessage(), pageUrl);
		}
		
		@Override
		public void onProgressChanged(final int progress, final int maxProgress, final boolean indeterminate) {
			notificationTools.updateProgress(progress, maxProgress, indeterminate, executor.getQueue().size());
		}

		@Override
		public void onProgressMessage(final String message) {
			notificationTools.updateSmallText(message);
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
	
	private void stopService (){
		if (executor.getQueue().isEmpty()) {
			stopSelf();
		}
	}

	@Override
	public void onDestroy() {
		Log.d(TAG, "Service destroyed");
	}

	@Override
	public IBinder onBind(Intent i) {
		return null;
	}	
}
