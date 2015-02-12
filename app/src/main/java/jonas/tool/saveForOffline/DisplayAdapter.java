package jonas.tool.saveForOffline;

import java.util.ArrayList;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.*;
import android.net.*;
import android.graphics.*;
import android.database.*;
import android.database.sqlite.*;
import android.content.*;
import android.preference.*;
import android.graphics.drawable.*;
import java.lang.ref.*;
import android.os.*;
import android.content.res.*;
import android.util.*;
import java.util.*;
import java.text.*;
import java.text.ParseException;


public class DisplayAdapter extends BaseAdapter
{

	

	private Context mContext;
	private DbHelper mHelper;
	private SQLiteDatabase dataBase;
	private FuzzyDateFormatter fuzzyFormatter;
	
	
	private String searchQuery = "";
	private String sqlStatement;
	
	public int list_layout_type = 1;
	
	public ArrayList<Integer> selectedViewsPositions = new ArrayList<Integer>();
	
	
	private Bitmap placeHolder;
	
	private LruCache<String, Bitmap> mMemoryCache;
	
	
	
	
	public Cursor dbCursor;
	
	public void refreshData(String searchQuery, int sortOrder, boolean dataSetChanged) {
		//sortOrder 0 = date newest first, 1 = oldest first, 2 = alphabetical
		if (sortOrder == 0) sqlStatement = "SELECT * FROM " + DbHelper.TABLE_NAME + " WHERE " + DbHelper.KEY_TITLE + " LIKE'%"+searchQuery+"%' ORDER BY " + DbHelper.KEY_ID + " DESC";
		if (sortOrder == 1) sqlStatement = "SELECT * FROM " + DbHelper.TABLE_NAME + " WHERE " + DbHelper.KEY_TITLE + " LIKE'%"+searchQuery+"%' ORDER BY " + DbHelper.KEY_ID + " ASC";
		else if (sortOrder == 2) sqlStatement = "SELECT * FROM " + DbHelper.TABLE_NAME + " WHERE " + DbHelper.KEY_TITLE + " LIKE'%"+searchQuery+"%' ORDER BY " + DbHelper.KEY_TITLE + " ASC";
		else sqlStatement = "SELECT * FROM " + DbHelper.TABLE_NAME + " WHERE " + DbHelper.KEY_TITLE + " LIKE'%"+searchQuery+"%' ORDER BY " + DbHelper.KEY_ID + " DESC";
	
		 
		dbCursor = dataBase.rawQuery(sqlStatement,null);
		dbCursor.moveToFirst();
		if (dataSetChanged) notifyDataSetChanged();
	}

	public DisplayAdapter(Context c) {
		this.mContext = c;
		
		mHelper = new DbHelper(c);
		dataBase = mHelper.getReadableDatabase();
		FuzzyDateMessages fdm = new FuzzyDateMessages();
		fuzzyFormatter = new FuzzyDateFormatter(Calendar.getInstance(), fdm);
		
		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
		
		list_layout_type = Integer.parseInt(sharedPref.getString("layout" , "1"));
	
		
		refreshData(null, 1, false);
		
		placeHolder = BitmapFactory.decodeResource(mContext.getResources(), R.drawable.placeholder);
		
		final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
		// Use 1/8th of the available memory for this memory cache.
		final int cacheSize = maxMemory / 4;

		mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
			@Override
			protected int sizeOf(String key, Bitmap bitmap) {
				// The cache size will be measured in kilobytes rather than
				// number of items.
				return bitmap.getByteCount() / 1024;
			}
		};
		
	}

	public String getSearchQuery()
	{
		return searchQuery;
	}
	
	

	@Override
	public boolean isEmpty()
	{
		if (dbCursor.getCount() == 0) return true;
		else return false;
	}

	public int getCount() {
		// TODO Auto-generated method stub
		return dbCursor.getCount();
	}

	public Object getItem(int position) {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public long getItemId(int position)
	{
		if (dbCursor.getCount() != 0) {
		return Long.valueOf(dbCursor.getString(dbCursor.getColumnIndex(DbHelper.KEY_ID)));
		} else return 0;
	}

	public String getPropertiesByPosition(int position, String type) {
		
		dbCursor.moveToPosition(position);
		
		if (type.equals("id")) {
			return dbCursor.getString(dbCursor.getColumnIndex(DbHelper.KEY_ID));
		}
		
		else if (type.equals("thumbnail_location")) {
			return dbCursor.getString(dbCursor.getColumnIndex(DbHelper.KEY_THUMBNAIL));
		}
		
		else if (type.equals("file_location")) {
			return dbCursor.getString(dbCursor.getColumnIndex(DbHelper.KEY_FILE_LOCATION));
		}
		
		else if (type.equals("title")) {
			return dbCursor.getString(dbCursor.getColumnIndex(DbHelper.KEY_TITLE));
		}
		
		else if (type.equals("orig_url")) {
			return dbCursor.getString(dbCursor.getColumnIndex(DbHelper.KEY_ORIG_URL));
		}
		
		else if (type.equals("date")) {
			return dbCursor.getString(dbCursor.getColumnIndex(DbHelper.KEY_TIMESTAMP));
		}
		
		else { return null; }
	}
	
	

	public View getView(int pos, View convertView, ViewGroup parent) {
		
		
		dbCursor.moveToPosition(pos);
		
		Holder mHolder;
		LayoutInflater layoutInflater;
		
		if (selectedViewsPositions.contains(pos)) {
			convertView.setBackgroundColor(Color.parseColor("#FFC107"));
		} else if (convertView != null) {convertView.setBackgroundColor(Color.parseColor("#E2E2E2"));}
		
		if (convertView == null) {
			layoutInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			switch (list_layout_type) {
				case 2: convertView = layoutInflater.inflate(R.layout.listcell_grid, null); break;
				case 3: convertView = layoutInflater.inflate(R.layout.listcell_list, null); break;
				case 4: convertView = layoutInflater.inflate(R.layout.listcell_list_details, null); break;
				case 5: convertView = layoutInflater.inflate(R.layout.listcell_list_details_small, null); break;
				default: convertView = layoutInflater.inflate(R.layout.listcell_default, null);
			}
			
			mHolder = new Holder();
			if (list_layout_type == 4 || list_layout_type == 5) {
				mHolder.txt_date = (TextView) convertView.findViewById(R.id.txt_date);
			}
			mHolder.txt_id = (TextView) convertView.findViewById(R.id.txt_id);
			mHolder.txt_filelocation = (TextView) convertView.findViewById(R.id.txt_fileLocation);
			mHolder.txt_orig_url = (TextView) convertView.findViewById(R.id.txt_orig_url);
			mHolder.txt_title = (TextView) convertView.findViewById(R.id.txt_title);
			if (list_layout_type != 5) {
				mHolder.listimage = (ImageView) convertView.findViewById(R.id.listimage);
			}
			convertView.setTag(mHolder);
		} else {
			mHolder = (Holder) convertView.getTag();
		}
		if (list_layout_type == 4 || list_layout_type == 5) {
			try {
				mHolder.txt_date.setText("Saved " + fuzzyFormatter.getFuzzy(dbCursor.getString(dbCursor.getColumnIndex(DbHelper.KEY_TIMESTAMP))));
			} catch (ParseException e) {
				Log.e("displayAdapter", "attempted to parse date '" + dbCursor.getString(dbCursor.getColumnIndex(DbHelper.KEY_TIMESTAMP)) + "' for display, with format yyyy-MM-dd HH:mm:ss, which resulted in a ParseException");
				mHolder.txt_date.setText("Date unavailable");
			}
		}
		mHolder.txt_id.setText(dbCursor.getString(dbCursor.getColumnIndex(DbHelper.KEY_ID)));
		mHolder.txt_filelocation.setText(dbCursor.getString(dbCursor.getColumnIndex(DbHelper.KEY_FILE_LOCATION)));
		mHolder.txt_orig_url.setText(dbCursor.getString(dbCursor.getColumnIndex(DbHelper.KEY_ORIG_URL)));
		mHolder.txt_title.setText(dbCursor.getString(dbCursor.getColumnIndex(DbHelper.KEY_TITLE)));
		
		//if (bitmapCache.size() > pos) {
		//	mHolder.mBitmap= bitmapCache.get(pos);
		//} else { 
		//	mHolder.mBitmap = BitmapFactory.decodeFile(dbCursor.getString(dbCursor.getColumnIndex(DbHelper.KEY_THUMBNAIL)));
		//	bitmapCache.add(pos, mHolder.mBitmap);
		//}
	
		//mHolder.listimage.setImageBitmap(mHolder.mBitmap);
		
		if (list_layout_type != 5) {
			loadBitmap(dbCursor.getString(dbCursor.getColumnIndex(DbHelper.KEY_THUMBNAIL)), mHolder.listimage);
		}
		
		return convertView;
	}

	static class Holder {
		TextView txt_id;
		TextView txt_filelocation;
		TextView txt_orig_url;
		TextView txt_title;
		TextView txt_date;
		ImageView listimage;
		Bitmap mBitmap;
	}
	
	public void addBitmapToMemoryCache(String key, Bitmap bitmap) {
		if (bitmap != null && getBitmapFromMemCache(key) == null) {
			mMemoryCache.put(key, bitmap);
		}
	}

	public Bitmap getBitmapFromMemCache(String key) {
		 return mMemoryCache.get(key);
	}
	
	
	
	public void loadBitmap(String path, ImageView imageView) {
		if (cancelPotentialWork(0, imageView)) {
			
			
			final String imageKey = path;
			final Bitmap bitmap = mMemoryCache.get(imageKey);
			if (bitmap != null) {
				imageView.setImageBitmap(bitmap);
			} else {
				BitmapWorkerTask task = new BitmapWorkerTask(imageView);
				final AsyncDrawable asyncDrawable =new AsyncDrawable(placeHolder, task);
				imageView.setImageDrawable(asyncDrawable);
				task.execute(path);
			}
			//task.execute(path);
		}
	}

	static class AsyncDrawable extends BitmapDrawable {
		private final WeakReference<BitmapWorkerTask> bitmapWorkerTaskReference;

		public AsyncDrawable(Bitmap bitmap, BitmapWorkerTask bitmapWorkerTask) {
			super(bitmap);
			bitmapWorkerTaskReference =
				new WeakReference<BitmapWorkerTask>(bitmapWorkerTask);
		}

		public BitmapWorkerTask getBitmapWorkerTask() {
			return bitmapWorkerTaskReference.get();
		}
	}

	public static boolean cancelPotentialWork(int data, ImageView imageView) {
		final BitmapWorkerTask bitmapWorkerTask = getBitmapWorkerTask(imageView);

		if (bitmapWorkerTask != null) {
			final int bitmapData = bitmapWorkerTask.data;
			if (bitmapData != data) {
				// Cancel previous task
				bitmapWorkerTask.cancel(true);
			} else {
				// The same work is already in progress
				return false;
			}
		}
		// No task associated with the ImageView, or an existing task was cancelled
		return true;
	}

	private static BitmapWorkerTask getBitmapWorkerTask(ImageView imageView) {
		if (imageView != null) {
			final Drawable drawable = imageView.getDrawable();
			if (drawable instanceof AsyncDrawable) {
				final AsyncDrawable asyncDrawable = (AsyncDrawable) drawable;
				return asyncDrawable.getBitmapWorkerTask();
			}
		}
		return null;
	}
	
	class BitmapWorkerTask extends AsyncTask<String, Void, Bitmap> {
		private final WeakReference<ImageView> imageViewReference;
		private int data = 0;

		public BitmapWorkerTask(ImageView imageView) {
			// Use a WeakReference to ensure the ImageView can be garbage collected
			imageViewReference = new WeakReference<ImageView>(imageView);
		}

		// Decode image in background.
		@Override
		protected Bitmap doInBackground(String... params) {
			final Bitmap bitmap = BitmapFactory.decodeFile(params[0]);
			addBitmapToMemoryCache(params[0], bitmap);
			return bitmap;
		}

		// Once complete, see if ImageView is still around and set bitmap.
		@Override
		protected void onPostExecute(Bitmap bitmap) {
			if (imageViewReference != null && bitmap != null) {
				final ImageView imageView = imageViewReference.get();
				if (imageView != null) {
					
					imageView.setImageBitmap(bitmap);
					
					// Transition drawable with a transparent drwabale and the final bitmap
//					TransitionDrawable td = new TransitionDrawable(new Drawable[] {
//						new ColorDrawable(Color.parseColor("#E2E2E2")),
//						new BitmapDrawable(mContext.getResources(), bitmap)
//					 });
//					 
//					// Set background to loading bitmap
//					imageView.setBackgroundDrawable(new BitmapDrawable(mContext.getResources(), bitmap));
//
//					imageView.setImageDrawable(td);
//					td.startTransition(5000);
				}
			}
		}
	}
}
