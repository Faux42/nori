/*
 * This file is part of nori.
 * Copyright (c) 2014-2016 Tomasz Jan Góralczyk <tomg@fastmail.uk>
 * License: GNU GPLv2
 */

package io.github.tjg1.nori;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import io.github.tjg1.library.norilib.clients.SearchClient;
import io.github.tjg1.library.norilib.service.ServiceTypeDetectionService;
import io.github.tjg1.nori.database.APISettingsDatabase;
import io.github.tjg1.nori.fragment.EditAPISettingDialogFragment;

/** Adds, edits or removes API settings from {@link io.github.tjg1.nori.database.APISettingsDatabase}. */
public class APISettingsActivity extends AppCompatActivity implements EditAPISettingDialogFragment.Listener {
  /** Intent action used to indicate that the add service dialog should be displayed, when the Activity is created. */
  public static final String ACTION_CREATE_SERVICE = "CREATE_SERVICE";
  /** A new row will be inserted into the database when this row ID value is passed to {@link #editService(long, String, String, String, String)}. */
  private static final long ROW_ID_INSERT = -1L;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // Inflate layout XML.
    setContentView(R.layout.activity_service_settings);

    // Set up Toolbar.
    Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    // Hide the app icon and use the activity title as the home button.
    ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      actionBar.setDisplayShowHomeEnabled(false);
      actionBar.setDisplayShowTitleEnabled(true);
      actionBar.setDisplayHomeAsUpEnabled(true);
    }

    // Set up the ListView adapter and OnItemClickListener.
    ListView listView = (ListView) findViewById(android.R.id.list);
    ListAdapter listAdapter = new ListAdapter();
    listView.setOnItemClickListener(listAdapter);
    listView.setAdapter(listAdapter);

    // Show the service creation dialog, if started with the create service intent action.
    Intent intent = getIntent();
    if (savedInstanceState == null && ACTION_CREATE_SERVICE.equals(intent.getAction())) {
      showCreateServiceDialog();
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    // Inflate menu XML.
    getMenuInflater().inflate(R.menu.api_settings, menu);
    return true;
  }

  /**
   * Remove setting from the {@link io.github.tjg1.nori.database.APISettingsDatabase}.
   *
   * @param id Database row ID.
   */
  private void removeSetting(final long id) {
    // Remove setting from database on a background thread.
    // This is so database I/O doesn't block the UI thread.
    new Thread(new Runnable() {
      @Override
      public void run() {
        new APISettingsDatabase(APISettingsActivity.this).delete(id);
      }
    }).start();
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    // Handle action bar button clicks.
    switch (item.getItemId()) {
      case android.R.id.home:
        // Make pressing the action bar "up" button perform same action as pressing the physical "back" button.
        onBackPressed();
        return true;
      case R.id.action_add:
        // Show dialog to let user add a new service.
        showCreateServiceDialog();
        return true;
      default:
        // Perform default action.
        return super.onOptionsItemSelected(item);
    }
  }

  @Override
  public void addService(String name, String url, String username, String passphrase) {
    editService(ROW_ID_INSERT, name, url, username, passphrase);
  }

  @Override
  public void editService(final long rowId, final String name, final String url, final String username, final String passphrase) {
    // Show progress dialog during the service type detection process.
    final ProgressDialog dialog = new ProgressDialog(this);
    dialog.setIndeterminate(true);
    dialog.setCancelable(false);
    dialog.setMessage(getString(R.string.dialog_message_detectingApiType));
    dialog.show();

    // Register broadcast receiver to get results from the background service type detection service.
    registerReceiver(new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        // Get result code from received intent.
        int resultCode = intent.getIntExtra(ServiceTypeDetectionService.RESULT_CODE, -1);
        if (resultCode == ServiceTypeDetectionService.RESULT_OK) {
          // Add a new service to the database on a background thread.
          // This is so database I/O doesn't block the UI thread.
          SearchClient.Settings.APIType apiType =
              SearchClient.Settings.APIType.values()[intent.getIntExtra(ServiceTypeDetectionService.API_TYPE, 0)];
          String endpointUrl = intent.getStringExtra(ServiceTypeDetectionService.ENDPOINT_URL);
          final SearchClient.Settings settings = new SearchClient.Settings(apiType, name, endpointUrl, username, passphrase);
          new Thread(new Runnable() {
            @Override
            public void run() {
              APISettingsDatabase database = new APISettingsDatabase(APISettingsActivity.this);
              if (rowId == ROW_ID_INSERT) {
                database.insert(settings);
              } else {
                database.update(rowId, settings);
              }
              database.close();
            }
          }).start();
        } else if (resultCode == ServiceTypeDetectionService.RESULT_FAIL_INVALID_URL) {
          Toast.makeText(APISettingsActivity.this, R.string.toast_error_serviceUriInvalid, Toast.LENGTH_LONG).show();
        } else if (resultCode == ServiceTypeDetectionService.RESULT_FAIL_NETWORK) {
          Toast.makeText(APISettingsActivity.this, R.string.toast_error_noNetwork, Toast.LENGTH_LONG).show();
        } else if (resultCode == ServiceTypeDetectionService.RESULT_FAIL_NO_API) {
          Toast.makeText(APISettingsActivity.this, R.string.toast_error_noServiceAtGivenUri, Toast.LENGTH_LONG).show();
        }

        // Unregister the broadcast receiver.
        unregisterReceiver(this);
        // Dismiss progress dialog.
        dialog.dismiss();
      }
    }, new IntentFilter(ServiceTypeDetectionService.ACTION_DONE));

    // Start the background service type detection service.
    Intent serviceIntent = new Intent(this, ServiceTypeDetectionService.class);
    serviceIntent.putExtra(ServiceTypeDetectionService.ENDPOINT_URL, url);
    startService(serviceIntent);
  }

  /** Displays the service creation dialog. */
  private void showCreateServiceDialog() {
    new EditAPISettingDialogFragment().show(getSupportFragmentManager(), "EditAPISettingDialogFragment");
  }

  /** Populates the {@link android.widget.ListView} with data from {@link io.github.tjg1.nori.database.APISettingsDatabase}. */
  private class ListAdapter extends BaseAdapter implements LoaderManager.LoaderCallbacks<List<Pair<Integer, SearchClient.Settings>>>, AdapterView.OnItemClickListener {
    /** {@link io.github.tjg1.nori.database.APISettingsDatabase} loader ID. */
    private static final int LOADER_ID_DATABASE_LOADER = 0x00;
    /** List of {@link android.util.Pair}s mapping database row IDs to {@link io.github.tjg1.library.norilib.clients.SearchClient.Settings} objects. */
    private List<Pair<Integer, SearchClient.Settings>> settingsList;

    public ListAdapter() {
      // Initialize the asynchronous database loader.
      getSupportLoaderManager().initLoader(LOADER_ID_DATABASE_LOADER, null, this);
    }

    @Override
    public int getCount() {
      if (settingsList != null) {
        return settingsList.size();
      }
      return 0;
    }

    @Override
    public SearchClient.Settings getItem(int position) {
      return settingsList.get(position).second;
    }

    @Override
    public long getItemId(int position) {
      return settingsList.get(position).first;
    }

    @Override
    public View getView(int position, View recycledView, ViewGroup container) {
      // Recycle the view, if possible.
      View view = recycledView;

      if (view == null) {
        // Create a new instance of the view.
        view = LayoutInflater.from(APISettingsActivity.this)
            .inflate(R.layout.listitem_service_setting, container, false);
      }

      // Get data from the List for current position.
      SearchClient.Settings settings = getItem(position);
      final long id = getItemId(position);
      // Populate views with content.
      TextView title = (TextView) view.findViewById(R.id.title);
      title.setText(settings.getName());
      TextView summary = (TextView) view.findViewById(R.id.summary);
      summary.setText(settings.getEndpoint());
      // Attach onClickListener to the remove button and hook it up to the #removeSetting method.
      ImageButton actionRemove = (ImageButton) view.findViewById(R.id.action_remove);
      actionRemove.setFocusable(false);
      actionRemove.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          removeSetting(id);
        }
      });

      return view;
    }

    @Override
    public Loader<List<Pair<Integer, SearchClient.Settings>>> onCreateLoader(int id, Bundle args) {
      if (id == LOADER_ID_DATABASE_LOADER) {
        // Initialize the database loader.
        return new APISettingsDatabase.Loader(APISettingsActivity.this);
      }
      return null;
    }

    @Override
    public void onLoadFinished(Loader<List<Pair<Integer, SearchClient.Settings>>> loader, List<Pair<Integer, SearchClient.Settings>> data) {
      settingsList = data;
      notifyDataSetChanged();
    }

    @Override
    public void onLoaderReset(Loader<List<Pair<Integer, SearchClient.Settings>>> loader) {
      settingsList = null;
      notifyDataSetInvalidated();
    }

    @Override
    public void onItemClick(AdapterView<?> listView, View view, int position, long itemId) {
      // Show dialog to edit the service settings object.
      EditAPISettingDialogFragment.newInstance(itemId, getItem(position))
          .show(getSupportFragmentManager(), "EditAPISettingDialogFragment");
    }
  }
}
