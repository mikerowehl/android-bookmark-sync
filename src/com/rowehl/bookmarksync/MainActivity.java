package com.rowehl.bookmarksync;

import java.io.IOException;

import org.apache.http.client.methods.HttpGet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.Browser;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewStub;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

public class MainActivity extends Activity {
	public static final String LOG_TAG = "BSYNC";
	
	public static final String PREFS_NAME = "BookmarkSync";
	public static final String JSON_URL_PREF = "jsonUrl";
	
	private EditText mJsonUrl = null;
	private Button mSyncButton = null;
	
	private View mSyncPanel = null;
	private SyncTask mSyncTask = null;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String jsonUrlValue = prefs.getString(JSON_URL_PREF, "");
        
        mJsonUrl = (EditText)findViewById(R.id.jsonUrl);
        mJsonUrl.setText(jsonUrlValue);
        
        mSyncButton = (Button)findViewById(R.id.syncButton);
        mSyncButton.setOnClickListener(new OnClickListener() {
        	public void onClick(View v) {
        		if (mSyncTask == null || mSyncTask.getStatus() == UserTask.Status.FINISHED) {
        			mSyncTask = (SyncTask) new SyncTask().execute(mJsonUrl.getText().toString());
        		}
        	}
        });   
    }
    
    protected void onStop() {
    	super.onStop();
    	
    	SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    	SharedPreferences.Editor editor = prefs.edit();
    	editor.putString(JSON_URL_PREF, mJsonUrl.getText().toString());
    	editor.commit();
    }
    
    private void onCancelSync() {
    	if (mSyncTask != null && mSyncTask.getStatus() == UserTask.Status.RUNNING) {
    		mSyncTask.cancel(true);
    		mSyncTask = null;
    	}
    }
    private class SyncTask extends UserTask<String, Void, Integer> {
    	@Override
    	public void onPreExecute() {
    		if (mSyncPanel == null) {
                mSyncPanel = ((ViewStub)findViewById(R.id.stub_sync)).inflate();
                ((ProgressBar) mSyncPanel.findViewById(R.id.progress)).setIndeterminate(true);

                final View cancelButton = mSyncPanel.findViewById(R.id.buttonCancel);
                cancelButton.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        onCancelSync();
                    }
                });
            }
    		mSyncPanel.setVisibility(View.VISIBLE);
    	}
    	
    	public Integer doInBackground(String... params) {
            JSONObject json = null;
            int changes = 0;
			
			HttpGet get = new HttpGet(params[0]);
			try {
				String response = HTTPManager.execute(get);
				Log.d(LOG_TAG, "Raw: " + response);
				json = new JSONObject(response);
			} catch (IOException e) {
				e.printStackTrace();
			} catch (JSONException e) {
				e.printStackTrace();
			}
			
			ContentResolver cr = getContentResolver();
			JSONArray bookmarks = json.optJSONArray("bookmarks");
			for (int i = 0; i < bookmarks.length(); i++) {
				try {
					JSONObject bookmark = bookmarks.getJSONObject(i);
					String url = bookmark.getString("url");
					if (url != null) {
					     String title = bookmark.optString("title", url);
					     Log.d(LOG_TAG, "URL: " + url);
					     Log.d(LOG_TAG, "Title: " + title);
					     
					     Cursor c = cr.query(Browser.BOOKMARKS_URI, null, Browser.BookmarkColumns.BOOKMARK + " = ? AND " + Browser.BookmarkColumns.URL + " = ?", new String[] {"1", url}, null);
					     if (c.getCount() == 0) {
					    	 ContentValues bookmarkValues = new ContentValues();
					    	 bookmarkValues.put(Browser.BookmarkColumns.TITLE, title);
					    	 bookmarkValues.put(Browser.BookmarkColumns.URL, url);
					    	 bookmarkValues.put(Browser.BookmarkColumns.BOOKMARK, 1);

					    	 cr.insert(Browser.BOOKMARKS_URI, bookmarkValues);
					    	 changes++;
					     }
					}
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
    
    		return changes;
    	}
    	
    	@Override
        public void onCancelled() {
    		mSyncPanel.setVisibility(View.GONE);
    	}
    	
    	@Override
    	public void onPostExecute(Integer changes) {
    		mSyncPanel.setVisibility(View.GONE);
    		if (changes > 0) {
    			Toast.makeText(MainActivity.this, "Synced " + changes + " bookmark" + ((changes > 1) ? "s" : ""), Toast.LENGTH_LONG).show();
    		}
    	}
    }
}