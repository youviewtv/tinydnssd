package com.example.slilly.tinydnssd;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.youview.tinydnssd.DiscoverResolver;
import com.youview.tinydnssd.MDNSDiscover;

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

        Context context = this;

        DiscoverResolver resolver = new DiscoverResolver(context, "_googlecast._tcp",
                new DiscoverResolver.Listener() {
                    @Override
                    public void onServicesChanged(Map<String, MDNSDiscover.Result> services) {
                        for (MDNSDiscover.Result result : services.values()) {
                            // access the model description from the TXT record
                            String model = result.txt.dict.get("md");
                            String location = result.a.ipaddr + ":" + result.srv.port;
                            Log.d(TAG, model + " -> " + location);
                        }
                    }
                });
        resolver.start();
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
    public void onServicesChanged(Map<String, MDNSDiscover.Result> services) {
        mServiceList = new ArrayList<>();
        for (MDNSDiscover.Result service : services.values()) {
            mServiceList.add(service.srv.fqdn + " " + service.txt.dict);
        }
        Collections.sort(mServiceList);
        mAdapter.notifyDataSetChanged();
    }
}
