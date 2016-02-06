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
