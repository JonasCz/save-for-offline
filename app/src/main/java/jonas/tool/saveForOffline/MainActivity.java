package jonas.tool.saveForOffline;

import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListView;
import android.widget.Toast;
import android.view.*;
import android.widget.*;
import org.apache.http.impl.conn.*;
import android.app.*;
import java.util.*;
import android.content.*;
import android.util.*;
import java.io.*;
import android.media.*;
import android.preference.*;
import android.graphics.*;
import android.content.res.*;

public class MainActivity extends Activity implements SearchView.OnQueryTextListener
{


	private DisplayAdapter gridAdapter;
	private DbHelper mHelper;
	private SQLiteDatabase dataBase;
	
	private TextView noSavedPages;
	private TextView helpText;

	private int sortOrder = 0;


	private GridView mainGrid;
	private SearchView mSearchView;
	private String searchQuery = "";
	private ProgressDialog pageLoadDialog;
	private AlertDialog dialogSortItemsBy;
	private ActionBar actionbar;

	private int scrollPosition;


	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		//initialize the preferences
		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
		mainGrid = (GridView) findViewById(R.id.List);

		mainGrid.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
		mainGrid.setMultiChoiceModeListener(new ModeCallback());


		int list_layout_type = Integer.parseInt(sharedPref.getString("layout" , "1"));
		switch (list_layout_type) {
				case 1: break;
				case 2: break;
				case 3: mainGrid.setNumColumns(1); break;
				case 4: mainGrid.setNumColumns(1); break;
				case 5: mainGrid.setNumColumns(1); break;
				default:
			}


		pageLoadDialog = new ProgressDialog(MainActivity.this);
		actionbar = getActionBar();

		setUpGridClickListener();
		
		noSavedPages = (TextView) findViewById(R.id.textNoSavedPages);
		helpText = (TextView) findViewById(R.id.how_to_text);
		
		gridAdapter = new DisplayAdapter(MainActivity.this);

		mainGrid.setAdapter(gridAdapter);


	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == 1 && resultCode == RESULT_FIRST_USER) {
			recreate();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		// Inflate the menu items for use in the action bar
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_activity_actions, menu);
		MenuItem searchItem = menu.findItem(R.id.action_search);
        mSearchView = (SearchView) searchItem.getActionView();
		mSearchView.setIconifiedByDefault(true);
		mSearchView.setOnQueryTextListener(this);
		return super.onCreateOptionsMenu(menu);
	}


	public boolean onQueryTextChange(String newText)
	{

		//dataBase = mHelper.getWritableDatabase();
		//searchAdapter = new SimpleCursorAdapter(this, android.R.layout.simple_list_item_1, searchCursor, new String[] { DbHelper.KEY_TITLE, },new int[] {android.R.id.text1});
		//searchCursor = dataBase.rawQuery(sqlStatement, null);
		//mSearchView.setSuggestionsAdapter(searchAdapter);
		searchQuery = newText;
		displayData(newText);
		if (newText.length() == 0)
		{
			actionbar.setSubtitle(R.string.action_bar_subtitle_showing_all);
		} else { 
			if (gridAdapter.getCount() == 1) {actionbar.setSubtitle(R.string.one_search_result);}
			else if (gridAdapter.getCount() == 0) {actionbar.setSubtitle(R.string.no_search_results);}
			else {actionbar.setSubtitle(gridAdapter.getCount() + " " + getResources().getString(R.string.num_search_results));}
		}
        return false;
    }

    public boolean onQueryTextSubmit(String query)
	{

		displayData(query);
        return false;
    }

    public boolean onClose()
	{
        displayData("");
        return false;
    }

	@Override
	protected void onPause()
	{
		super.onPause();
		scrollPosition = mainGrid.getFirstVisiblePosition();
	}



	@Override
	protected void onResume()
	{
		super.onResume();

		pageLoadDialog.cancel();
		displayData(searchQuery);
		mainGrid.setSelection(scrollPosition);
		if (searchQuery.length() == 0)
		{
			actionbar.setSubtitle(R.string.action_bar_subtitle_showing_all);
		}

	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		// Handle presses on the action bar items
		switch (item.getItemId())
		{
			case R.id.action_add:
				Intent i = new Intent(getApplicationContext(), AddActivity.class);
				startActivity(i);

				return true;
				
			case R.id.action_sort_by:
				
				AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
				builder.setTitle("Sort by");
				builder.setSingleChoiceItems(R.array.sort_by, sortOrder, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							sortOrder = which;
							displayData(searchQuery);
							dialogSortItemsBy.cancel();
						}
					});
				dialogSortItemsBy = builder.create();
				dialogSortItemsBy.show();

				return true;

			case R.id.ic_action_settings:
				Intent settings = new Intent(getApplicationContext(), Preferences.class);
				startActivityForResult(settings, 1);

				return true;
				
			case R.id.ic_action_about:
				Intent intent = new Intent(this, FirstRunDialog.class);
				startActivity(intent);
				return true;
				
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	private void setUpGridClickListener()
	{
		//click to show saved page
		mainGrid.setOnItemClickListener(new OnItemClickListener() {

				public void onItemClick(AdapterView<?> arg0, View clickedView, int position,
										long id)
				{
					
					clickedView.setBackgroundColor(Color.parseColor("#FFC107"));
					
					//Toast.makeText(MainActivity.this, "Clicked " + id, Toast.LENGTH_SHORT).show();



					pageLoadDialog.setMessage("Please wait while loading...");
					pageLoadDialog.setIndeterminate(true);
					pageLoadDialog.setCancelable(false);

					pageLoadDialog.show();

					Intent i = new Intent(getApplicationContext(),
										  ViewActivity.class);
					i.putExtra("orig_url", gridAdapter.getPropertiesByPosition(position, "orig_url"));
					i.putExtra("title", gridAdapter.getPropertiesByPosition(position, "title"));
					i.putExtra("id", gridAdapter.getPropertiesByPosition(position, "id"));
					i.putExtra("fileLocation", gridAdapter.getPropertiesByPosition(position,"file_location"));
					i.putExtra("thumbnailLocation", gridAdapter.getPropertiesByPosition(position,"thumbnail_location"));

					startActivity(i);


				}
			});
		}
			
	private void displayData(String searchQuery)
	{
		
		gridAdapter.refreshData(searchQuery, sortOrder, true);

		if (gridAdapter.getCount() == 0 && !searchQuery.equals("")) {
			noSavedPages.setText("No search results");
			noSavedPages.setVisibility(View.VISIBLE);
			noSavedPages.setGravity(Gravity.CENTER_HORIZONTAL);
			mainGrid.setVisibility(View.GONE);
		} 
		else if (gridAdapter.getCount() == 0) {
			noSavedPages.setText("No saved pages");
			noSavedPages.setVisibility(View.VISIBLE);
			helpText.setVisibility(View.VISIBLE);
			mainGrid.setVisibility(View.GONE);
		} else {
			helpText.setVisibility(View.GONE); 
			noSavedPages.setVisibility(View.GONE); 
			mainGrid.setVisibility(View.VISIBLE); 
			}



	}



	class ModeCallback implements ListView.MultiChoiceModeListener
	{




        public boolean onCreateActionMode(ActionMode mode, Menu menu)
		{
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.main_activity_multi_choice, menu);
            mode.setTitle("Select Items");


            return true;
        }

        public boolean onPrepareActionMode(ActionMode mode, Menu menu)
		{
            return true;
        }

        public boolean onActionItemClicked(final ActionMode mode, MenuItem item)
		{

			switch (item.getItemId())
			{
				case R.id.action_delete:

					

					AlertDialog.Builder build;
					build = new AlertDialog.Builder(MainActivity.this);
					if (gridAdapter.selectedViewsPositions.size() == 1)
					{
						build.setTitle("Do you want to delete ?");

						build.setMessage(gridAdapter.getPropertiesByPosition(gridAdapter.selectedViewsPositions.get(0), "title"));
					}
					else
					{
						build.setTitle("Delete ?");
						build.setMessage("Do you want to delete these " + gridAdapter.selectedViewsPositions.size() + " saved pages ?");
					}
					build.setPositiveButton("Delete",
						new DialogInterface.OnClickListener() {

							public void onClick(DialogInterface dialog,
												int which)
							{
								mHelper = new DbHelper(MainActivity.this);
								dataBase = mHelper.getWritableDatabase();
								//Context mContext = getApplicationContext();

								

								for (Integer position: gridAdapter.selectedViewsPositions)
								{
								
									String fileLocation = gridAdapter.getPropertiesByPosition(position, "thumbnail_location");
									File file = new File(fileLocation);
									file.delete();

									//for compatibility with old versions
									if (gridAdapter.getPropertiesByPosition(position, "file_location").endsWith("mht")) {
										fileLocation = gridAdapter.getPropertiesByPosition(position, "file_location");
										file = new File(fileLocation);
										file.delete();
									} else {
										fileLocation = gridAdapter.getPropertiesByPosition(position, "file_location");
										file = new File(fileLocation);
										Log.w("Deleting", file.getAbsolutePath());
										file = file.getParentFile();
										Log.w("Deleting parent file", file.getAbsolutePath());
										DirectoryHelper.deleteDirectory(file);
									}
									
									dataBase.delete(DbHelper.TABLE_NAME, DbHelper.KEY_ID + "=" + gridAdapter.getPropertiesByPosition(position, "id"), null);
									
									
									
								}

						
								Toast.makeText(MainActivity.this, "Deleted " + mainGrid.getCheckedItemCount() + " saved pages", Toast.LENGTH_LONG).show();
								dataBase.close();
								displayData("");
								mode.finish();	
							}
						});

					build.setNegativeButton("Cancel",
						new DialogInterface.OnClickListener() {

							public void onClick(DialogInterface dialog,
												int which)
							{
								dialog.cancel();
								mode.finish();
							}
						});
					AlertDialog alert = build.create();
					alert.show();


					break;
				default:
					Toast.makeText(MainActivity.this, "Clicked " + item.getTitle(),
								   Toast.LENGTH_SHORT).show();
					break;
            }
            return true;
        }

        public void onDestroyActionMode(ActionMode mode)
		{
			gridAdapter.selectedViewsPositions.clear();
        }

        public void onItemCheckedStateChanged(ActionMode mode,
											  int position, long itemId, boolean checked)
		{
			Integer pos = position;
			View gridCellLayout = mainGrid.getChildAt(position - mainGrid.getFirstVisiblePosition()).findViewById(R.id.gridCellLayout);
			if (checked)
			{
				gridAdapter.selectedViewsPositions.add(pos);
				
				//getViewByPosition(position, mainGrid).setBackgroundColor(Color.parseColor("#FEA597"));

				gridCellLayout.setBackgroundColor(Color.parseColor("#FFC107"));

				//Toast.makeText(MainActivity.this, "Checked " + position, Toast.LENGTH_SHORT).show();

			}
			else
			{
				
				gridAdapter.selectedViewsPositions.remove(pos);
				//getViewByPosition(position, mainGrid).setBackgroundColor(Color.parseColor("#E2E2E2"));
				gridCellLayout.setBackgroundColor(Color.parseColor("#E2E2E2"));
				//Toast.makeText(MainActivity.this, "unChecked " + position, Toast.LENGTH_SHORT).show();

			}


			final int checkedCount = gridAdapter.selectedViewsPositions.size();

            switch (checkedCount)
			{
                case 0:
                    mode.setSubtitle("Select items");
					Log.w("MainActivity / Gridview", "Should never happen: nothing selected");
                    break;
                case 1:
                    mode.setSubtitle("One item selected");
                    break;
                default:
                    mode.setSubtitle("" + checkedCount + " items selected");
                    break;
            }
        }

	}



}
