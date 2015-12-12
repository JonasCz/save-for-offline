package jonas.tool.saveForOffline;
import android.app.*;
import android.content.*;
import android.util.*;

public class NotificationTools {

		private Notification.Builder builder;
		private NotificationManager notificationManager;
		
		private Service context;

		private final int NOTIFICATION_ID = 1;
		
		public NotificationTools (Service context) {
			this.context = context;
			notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
			builder = new Notification.Builder(context);
		}
		
		public void notifySaveStarted () {
			builder = new Notification.Builder(context);
			builder.setTicker("Saving page...")
				.setContentTitle("Saving page...")
				.setContentText("Save in progress")
				.setSmallIcon(android.R.drawable.stat_sys_download)
				.setProgress(0, 1, true)
				.setOnlyAlertOnce(true)
				.setOngoing(true);
			addCancelAction();
			context.startForeground(NOTIFICATION_ID, builder.build());	
		}
		
		public void updateProgress (int progress, int maxProgress, boolean indeterminate, int saveQueueSize) {
			builder.setNumber(saveQueueSize);
			builder.setProgress(maxProgress, progress, indeterminate);
			notificationManager.notify(NOTIFICATION_ID, builder.build());
		}
		
		public void updateSmallText (String newText) {
			builder.setContentText(newText);
			notificationManager.notify(NOTIFICATION_ID, builder.build());
		}
		
		public void notifyFinished (String pageTitle) {
			builder = new Notification.Builder(context);
			
			builder.setTicker("Save completed: " + pageTitle)
				.setContentTitle("Save completed")
				.setContentText(pageTitle)
				.setSmallIcon(R.drawable.ic_notify_save)
				.setProgress(0,0,false)
				.setOnlyAlertOnce(false)
				.setOngoing(false);
				
			context.stopForeground(true);
			notificationManager.notify(NOTIFICATION_ID, builder.build());
		}
		
		public void notifyFailure (String message, String pageUrl) {
			Log.w("NotificationTools", "notifyFailure called");
			builder = new Notification.Builder(context);
			
			builder.setTicker("Error, page not saved: " + message)
				.setContentTitle("Error, page not saved")
				.setContentText(message)
				.setProgress(0,0,false)
				.setOngoing(false)
				.setOnlyAlertOnce(true)
				.setSmallIcon(android.R.drawable.stat_sys_warning);
				
			addRetryAction(pageUrl);
			
			context.stopForeground(false);
			notificationManager.notify(NOTIFICATION_ID, builder.build());
		}
		
		public void cancelAll() {
			context.stopForeground(true);
		}
		
		private void addCancelAction () {
			Intent cancelIntent = new Intent(context, SaveService.class);
			cancelIntent.putExtra("USER_CANCELLED", true);
			PendingIntent pendingIntent = PendingIntent.getService(context, 0, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT);
			builder.addAction(R.drawable.ic_notify_discard, "Cancel", pendingIntent);
		}

		private void addRetryAction (String url) {
			Intent intent = new Intent(context, SaveService.class);
			intent.putExtra(Intent.EXTRA_TEXT, url);
			PendingIntent pendingIntent = PendingIntent.getService(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);
			builder.addAction(R.drawable.ic_notify_retry, "Retry", pendingIntent);
		}
}
