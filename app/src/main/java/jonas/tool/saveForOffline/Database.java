package jonas.tool.saveForOffline;


import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.*;


public class Database extends SQLiteOpenHelper {
	
	public static final String DATABASE_NAME="SavedPagesMeta.db";
	public static final String TABLE_NAME="main";
	public static final String TITLE="title";
	public static final String FILE_LOCATION="file_location";
	public static final String THUMBNAIL="thumbnail";
	public static final String ORIGINAL_URL="origurl";
	public static final String ID="_id";
	public static final String TIMESTAMP="timestamp";
	public static final String SAVED_PAGE_BASE_DIRECTORY="tags";
	
	public Database(Context context) {
		super(context, DATABASE_NAME, null, 4);

	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		String CREATE_TABLE="CREATE TABLE "+TABLE_NAME+" ("
		+ID+" INTEGER PRIMARY KEY, "
		+TITLE+" TEXT, "
		+FILE_LOCATION+" TEXT, "
		+THUMBNAIL+" TEXT, "
		+ORIGINAL_URL+" TEXT, "
		+SAVED_PAGE_BASE_DIRECTORY+" TEXT, "
		+TIMESTAMP+" TEXT DEFAULT CURRENT_TIMESTAMP)";
		
		db.execSQL(CREATE_TABLE);

	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		db.execSQL("DROP TABLE IF EXISTS "+TABLE_NAME);
		onCreate(db);

	}
	
	public void addToDatabase(String destinationDirectory, String pageTitle, String originalUrl) {

		SQLiteDatabase dataBase = getWritableDatabase();
		ContentValues values = new ContentValues();

		values.put(Database.FILE_LOCATION, destinationDirectory + "index.html");
		values.put(Database.SAVED_PAGE_BASE_DIRECTORY, destinationDirectory);
		values.put(Database.TITLE, pageTitle);
		values.put(Database.THUMBNAIL, destinationDirectory + "saveForOffline_thumbnail.png");
		values.put(Database.ORIGINAL_URL, originalUrl);

		dataBase.insert(Database.TABLE_NAME, null, values);

		dataBase.close();
	}

}
