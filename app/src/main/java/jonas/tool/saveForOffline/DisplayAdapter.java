package jonas.tool.saveForOffline;

import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.preference.*;
import android.util.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.ref.*;
import java.text.*;
import java.util.*;
import com.squareup.picasso.Picasso;


public class DisplayAdapter extends BaseAdapter {
	
	public static class Constants {
		private Constants() {}		
		public final static int LIST_LAYOUT_DEFAULT = 1;
		public final static int LIST_LAYOUT_GRID = 2;
		public final static int LIST_LAYOUT_DETAILS_THUMBNAILS = 4;
		public final static int LIST_LAYOUT_DETAILS_SMALL_TEXT_ONLY = 5;
		public final static int LIST_LAYOUT_SMALL_ICON = 6;
	}

	private Context mContext;
	private DbHelper mHelper;
	private SQLiteDatabase dataBase;
	private FuzzyDateFormatter fuzzyFormatter;

	private String searchQuery = "";
	private String sqlStatement;

	public int list_layout_type = 1;
	private boolean darkMode;

	public ArrayList<Integer> selectedViewsPositions = new ArrayList<Integer>();

	private Bitmap placeHolder;

	public Cursor dbCursor;

	public void refreshData(String searchQuery, int sortOrder, boolean dataSetChanged) {
		//sortOrder 0 = date newest first, 1 = oldest first, 2 = alphabetical
		if (sortOrder == 0) sqlStatement = "SELECT * FROM " + DbHelper.TABLE_NAME + " WHERE " + DbHelper.KEY_TITLE + " LIKE'%" + searchQuery + "%' ORDER BY " + DbHelper.KEY_ID + " DESC";
		if (sortOrder == 1) sqlStatement = "SELECT * FROM " + DbHelper.TABLE_NAME + " WHERE " + DbHelper.KEY_TITLE + " LIKE'%" + searchQuery + "%' ORDER BY " + DbHelper.KEY_ID + " ASC";
		else if (sortOrder == 2) sqlStatement = "SELECT * FROM " + DbHelper.TABLE_NAME + " WHERE " + DbHelper.KEY_TITLE + " LIKE'%" + searchQuery + "%' ORDER BY " + DbHelper.KEY_TITLE + " ASC";
		else sqlStatement = "SELECT * FROM " + DbHelper.TABLE_NAME + " WHERE " + DbHelper.KEY_TITLE + " LIKE'%" + searchQuery + "%' ORDER BY " + DbHelper.KEY_ID + " DESC";

		dbCursor = dataBase.rawQuery(sqlStatement, null);
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
		darkMode = sharedPref.getBoolean("dark_mode", false);

		refreshData(null, 1, false);

		placeHolder = BitmapFactory.decodeResource(mContext.getResources(), R.drawable.icon_website_large);

	}

	public String getSearchQuery() {
		return searchQuery;
	}

	@Override
	public boolean isEmpty() {
        return dbCursor.getCount() == 0;
	}

	public int getCount() {
		return dbCursor.getCount();
	}

	public Object getItem(int position) {
		return null;
	}

	@Override
	public long getItemId(int position) {
		if (dbCursor.getCount() != 0) {
			return Long.valueOf(dbCursor.getString(dbCursor.getColumnIndex(DbHelper.KEY_ID)));
		} else return 0;
	}

	public String getPropertiesByPosition(int position, String type) {

		dbCursor.moveToPosition(position);

		//todo use switch statement, this is horrible..
		if (type.equals("id")) {
			return dbCursor.getString(dbCursor.getColumnIndex(DbHelper.KEY_ID));
		} else if (type.equals("thumbnail_location")) {
			return dbCursor.getString(dbCursor.getColumnIndex(DbHelper.KEY_THUMBNAIL));
		} else if (type.equals("file_location")) {
			return dbCursor.getString(dbCursor.getColumnIndex(DbHelper.KEY_FILE_LOCATION));
		} else if (type.equals("title")) {
			return dbCursor.getString(dbCursor.getColumnIndex(DbHelper.KEY_TITLE));
		} else if (type.equals("orig_url")) {
			return dbCursor.getString(dbCursor.getColumnIndex(DbHelper.KEY_ORIG_URL));
		} else if (type.equals("date")) {
			return dbCursor.getString(dbCursor.getColumnIndex(DbHelper.KEY_TIMESTAMP));
		} else { return null; }
	}
	
	private View inflateView (View convertView, Holder mHolder) {
		LayoutInflater layoutInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		switch (list_layout_type) {
			case 2: convertView = layoutInflater.inflate(R.layout.listcell_grid, null); break;
			case 4: convertView = layoutInflater.inflate(R.layout.listcell_list_details, null); break;
			case 5: convertView = layoutInflater.inflate(R.layout.listcell_list_details_small, null); break;
			case 6: convertView = layoutInflater.inflate(R.layout.listcell_list_details_small_icon_only, null); break;
			default: convertView = layoutInflater.inflate(R.layout.listcell_default, null);
		}	
		if (darkMode) {
			convertView.setBackgroundColor(Color.BLACK);
			if (list_layout_type == 4 || list_layout_type == 5) {
				mHolder.txt_date = (TextView) convertView.findViewById(R.id.txt_date);
				mHolder.txt_date.setTextColor(Color.WHITE);
			}
			mHolder.txt_id = (TextView) convertView.findViewById(R.id.txt_id);
			mHolder.txt_filelocation = (TextView) convertView.findViewById(R.id.txt_fileLocation);
			mHolder.txt_orig_url = (TextView) convertView.findViewById(R.id.txt_orig_url);
			mHolder.txt_orig_url.setTextColor(Color.WHITE);
			mHolder.txt_title = (TextView) convertView.findViewById(R.id.txt_title);
			mHolder.txt_title.setTextColor(Color.WHITE);
		} else {
			if (list_layout_type == 4 || list_layout_type == 5) {
				mHolder.txt_date = (TextView) convertView.findViewById(R.id.txt_date);
			}
			mHolder.txt_id = (TextView) convertView.findViewById(R.id.txt_id);
			mHolder.txt_filelocation = (TextView) convertView.findViewById(R.id.txt_fileLocation);
			mHolder.txt_orig_url = (TextView) convertView.findViewById(R.id.txt_orig_url);
			mHolder.txt_title = (TextView) convertView.findViewById(R.id.txt_title);
		}

		if (list_layout_type != 5) {
			mHolder.listimage = (ImageView) convertView.findViewById(R.id.listimage);
		}
		convertView.setTag(mHolder);
		
		return convertView;
	}
	
	private void setListImage (ImageView imageView) {
		if (list_layout_type == Constants.LIST_LAYOUT_DETAILS_SMALL_TEXT_ONLY) return;
		switch ((String) imageView.getTag()) {
			case "show:icon":
				File icon = new File(new File(dbCursor.getString(dbCursor.getColumnIndex(DbHelper.KEY_THUMBNAIL))).getParent(), "saveForOffline_icon.png");
				Picasso.with(mContext).load(icon).error(R.drawable.icon_website_large).into(imageView);
			break;
			case "show:thumbnail":
				File image = new File(dbCursor.getString(dbCursor.getColumnIndex(DbHelper.KEY_THUMBNAIL)));
				Picasso.with(mContext).load(image).placeholder(R.drawable.placeholder).into(imageView);
			break;
			default:
				Log.e("displayAdapter", "Bug: image / icon not set due to imageView.getTag() returning bad value:" + imageView.getTag());
		}
	}



	public View getView(int position, View convertView, ViewGroup parent) {
		dbCursor.moveToPosition(position);

		Holder mHolder = null;
		
		if (convertView == null) {
			mHolder = new Holder();
			convertView = inflateView(convertView, mHolder);
		} else {
			mHolder = (Holder) convertView.getTag();
		}

		if (selectedViewsPositions.contains(position)) {
			convertView.setBackgroundColor(Color.parseColor("#FFC107"));
		} else {
			if (darkMode) {
				convertView.setBackgroundColor(Color.BLACK);
			} else {
				convertView.setBackgroundColor(Color.parseColor("#E2E2E2"));
			}
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
		
		setListImage(mHolder.listimage);

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
}
