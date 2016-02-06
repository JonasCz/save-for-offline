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
 
 //todo: refactor and fix this, it's badly broken and messy..

package jonas.tool.saveForOffline;
import android.app.*;
import android.content.*;
import android.util.*;
import android.graphics.*;
import java.io.*;
import android.content.res.*;

public class NotificationTools {

	private Notification.Builder builder;
	private NotificationManager notificationManager;

	private Service context;

	private final int NOTIFICATION_ID = 1;

	private boolean hasCancelAllAction = false;

	public NotificationTools(Service context) {
		this.context = context;
		notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		builder = new Notification.Builder(context);
	}

	public void notifySaveStarted(int saveQueueSize) {
		builder = new Notification.Builder(context);
		builder.setTicker("Saving page...")
			.setContentTitle("Saving page...")
			.setContentText("Save in progress")
			.setSmallIcon(android.R.drawable.stat_sys_download)
			.setProgress(0, 1, true)
			.setOnlyAlertOnce(true)
			.setOngoing(true);
		addCancelAction();

		if (saveQueueSize > 0) {
			addCancelAllAction();
		}
		context.startForeground(NOTIFICATION_ID, builder.build());	
	}

	public void updateProgress(int progress, int maxProgress, boolean indeterminate, int saveQueueSize) {
		builder.setProgress(maxProgress, progress, indeterminate);
		notificationManager.notify(NOTIFICATION_ID, builder.build());
	}

	public void updateText(String newTitle, String newContentText, int saveQueueSize) {
		if (newTitle != null) {
			builder.setContentTitle(newTitle);
		}

		if (newContentText != null) {
			builder.setContentText(newContentText);
		}

		if (saveQueueSize > 0 && !hasCancelAllAction) {
			hasCancelAllAction = true;
			addCancelAllAction();
		}

		builder.setNumber(saveQueueSize);
		notificationManager.notify(NOTIFICATION_ID, builder.build());
	}

	public void notifyFinished(String pageTitle, String savedPageDirectoryLocation) {
		builder = new Notification.Builder(context);

		builder.setTicker("Save completed: " + pageTitle)
			.setContentTitle("Save completed")
			.setContentText(pageTitle)
			.setSmallIcon(R.drawable.ic_notify_save)
			.setProgress(0, 0, false)
			.setOnlyAlertOnce(false)
			.setOngoing(false);

		int maxLargeIconWidth = context.getResources().getDimensionPixelSize(android.R.dimen.notification_large_icon_width);
		Bitmap largeIconBitmap = BitmapFactory.decodeFile(savedPageDirectoryLocation + File.separator + "saveForOffline_icon.png");

		builder.setLargeIcon(Bitmap.createScaledBitmap(largeIconBitmap, maxLargeIconWidth / 2, maxLargeIconWidth / 2, false));

		context.stopForeground(false);
		notificationManager.notify(NOTIFICATION_ID, builder.build());
	}

	public void notifyFailure(String message, String pageUrl) {
		Log.w("NotificationTools", "notifyFailure called");
		builder = new Notification.Builder(context);

		builder.setTicker("Error, page not saved: " + message)
			.setContentTitle("Error, page not saved")
			.setContentText(message)
			.setProgress(0, 0, false)
			.setOngoing(false)
			.setOnlyAlertOnce(true)
			.setSmallIcon(android.R.drawable.stat_sys_warning);

		if (pageUrl != null) {
			addRetryAction(pageUrl);
		}

		context.stopForeground(false);
		notificationManager.notify(NOTIFICATION_ID, builder.build());
	}

	public void cancelAll() {
		context.stopForeground(true);
	}

	private void addCancelAction() {
		Intent cancelIntent = new Intent(context, SaveService.class);
		cancelIntent.putExtra("USER_CANCELLED", true);
		PendingIntent pendingIntent = PendingIntent.getService(context, 0, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		builder.addAction(R.drawable.ic_notify_discard, "Cancel", pendingIntent);
	}

	private void addCancelAllAction() {
		Intent cancelIntent = new Intent(context, SaveService.class);
		cancelIntent.putExtra("USER_CANCELLED_ALL", true);
		PendingIntent pendingIntent = PendingIntent.getService(context, 0, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT);
		builder.addAction(R.drawable.ic_notify_discard, "Cancel all", pendingIntent);
	}

	private void addRetryAction(String url) {
		Intent intent = new Intent(context, SaveService.class);
		intent.putExtra(Intent.EXTRA_TEXT, url);
		PendingIntent pendingIntent = PendingIntent.getService(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		builder.addAction(R.drawable.ic_notify_retry, "Retry", pendingIntent);
	}
}
