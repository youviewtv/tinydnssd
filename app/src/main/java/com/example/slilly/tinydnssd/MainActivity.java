package com.example.slilly.tinydnssd;

import android.net.nsd.NsdServiceInfo;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;


public class MainActivity extends ActionBarActivity implements DiscoverResolver.Listener {

    private static final String SERVICE_TYPE = "_yv-bridge._tcp";
    private static final String TAG = MainActivity.class.getSimpleName();

    private DiscoverResolver mDiscoverResolver;

    private List<String> mServiceList = new ArrayList<>();

    private BaseAdapter mAdapter = new BaseAdapter() {
        @Override
        public int getCount() {
            return mServiceList.size();
        }

        @Override
        public Object getItem(int position) {
            return mServiceList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView view;
            if (convertView == null) {
                view = (TextView) getLayoutInflater().inflate(R.layout.list_item, parent, false);
            } else {
                view = (TextView) convertView;
            }
            view.setText(mServiceList.get(position));
            return view;
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        mDiscoverResolver.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mDiscoverResolver.stop();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ListView listView = (ListView) findViewById(R.id.list);
        listView.setAdapter(mAdapter);
        mDiscoverResolver = new DiscoverResolver(this, SERVICE_TYPE, this);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onServicesChanged(Map<String, NsdServiceInfo> services) {
        mServiceList = new ArrayList<>();
        for (NsdServiceInfo service : services.values()) {
            mServiceList.add(service.getServiceName() + " " + service.getAttributes());
        }
        Collections.sort(mServiceList);
        mAdapter.notifyDataSetChanged();
    }
}
