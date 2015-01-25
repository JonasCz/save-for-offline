package jonas.tool.saveForOffline;


import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;


public class DbHelper extends SQLiteOpenHelper {
	
	static String DATABASE_NAME="SavedPagesMeta.db";
	public static final String TABLE_NAME="main";
	public static final String KEY_TITLE="title";
	public static final String KEY_FILE_LOCATION="file_location";
	public static final String KEY_THUMBNAIL="thumbnail";
	public static final String KEY_ORIG_URL="origurl";
	public static final String KEY_ID="_id";
	public static final String KEY_TIMESTAMP="timestamp";
	public static final String KEY_TAGS="tags";
	
	public DbHelper(Context context) {
		super(context, DATABASE_NAME, null, 4);

	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		String CREATE_TABLE="CREATE TABLE "+TABLE_NAME+" ("
		+KEY_ID+" INTEGER PRIMARY KEY, "
		+KEY_TITLE+" TEXT, "
		+KEY_FILE_LOCATION+" TEXT, "
		+KEY_THUMBNAIL+" TEXT, "
		+KEY_ORIG_URL+" TEXT, "
		+KEY_TAGS+" TEXT, "
		+KEY_TIMESTAMP+" TEXT DEFAULT CURRENT_TIMESTAMP)";
		
		db.execSQL(CREATE_TABLE);

	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		db.execSQL("DROP TABLE IF EXISTS "+TABLE_NAME);
		onCreate(db);

	}

}
