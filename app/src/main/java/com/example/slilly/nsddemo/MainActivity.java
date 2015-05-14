package com.example.slilly.nsddemo;

import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


public class MainActivity extends ActionBarActivity {

    private static final String SERVICE_TYPE = "_yv-bridge._tcp";
    private static final String TAG = MainActivity.class.getSimpleName();

    private NsdManager mNsdManager;

    private boolean mDiscovering;

    private Map<String, NsdServiceInfo> mDiscoveredServices = new HashMap<>();
    private Map<String, NsdServiceInfo> mResolvedServices = new HashMap<>();

    private Handler mResolveHandler = new Handler();

    private NsdManager.ResolveListener mResolveListener = new NsdManager.ResolveListener() {
        @Override
        public void onResolveFailed(final NsdServiceInfo serviceInfo, int errorCode) {
            Log.d(TAG, "onResolveFailed() serviceInfo = [" + serviceInfo + "], errorCode = [" + errorCode + "]");
            mResolveHandler.post(new Runnable() {
                @Override
                public void run() {
                    mResolving = false;
                    if (!mResumed) return;
                    String name = unescape(serviceInfo.getServiceName());
                    mDiscoveredServices.remove(name);
                    startServiceResolution();
                }
            });
        }

        @Override
        public void onServiceResolved(final NsdServiceInfo serviceInfo) {
            Log.d(TAG, "onServiceResolved() serviceInfo = [" + serviceInfo + "]");
            mResolveHandler.post(new Runnable() {
                @Override
                public void run() {
                    mResolving = false;
                    if (!mResumed) return;
                    String name = unescape(serviceInfo.getServiceName());
                    Log.d(TAG, "serviceName = " + name);
                    mDiscoveredServices.remove(name);
                    mResolvedServices.put(name, serviceInfo);
                    onResolvedServicesChanged();
                    startServiceResolution();
                }
            });
        }

        private String unescape(String name) {
            return name.replace("\\032", " ");
        }
    };

    private NsdManager.DiscoveryListener mDiscoveryListener = new NsdManager.DiscoveryListener() {
        @Override
        public void onStartDiscoveryFailed(String serviceType, int errorCode) {
            Log.d(TAG, "onStartDiscoveryFailed() serviceType = [" + serviceType + "], errorCode = [" + errorCode + "]");
        }

        @Override
        public void onStopDiscoveryFailed(String serviceType, int errorCode) {
            Log.d(TAG, "onStopDiscoveryFailed() serviceType = [" + serviceType + "], errorCode = [" + errorCode + "]");
        }

        @Override
        public void onDiscoveryStarted(String serviceType) {
            Log.d(TAG, "onDiscoveryStarted() serviceType = [" + serviceType + "]");
            mDiscovering = true;
        }

        @Override
        public void onDiscoveryStopped(String serviceType) {
            Log.d(TAG, "onDiscoveryStopped() serviceType = [" + serviceType + "]");
            mDiscovering = false;
        }

        @Override
        public void onServiceFound(final NsdServiceInfo serviceInfo) {
            Log.d(TAG, "onServiceFound() serviceInfo = [" + serviceInfo + "]");
            String name = serviceInfo.getServiceName();
            if (!mResolvedServices.containsKey(name)) {
                mDiscoveredServices.put(name, serviceInfo);
                startServiceResolution();
            }
        }

        @Override
        public void onServiceLost(NsdServiceInfo serviceInfo) {
            Log.d(TAG, "onServiceLost() serviceInfo = [" + serviceInfo + "]");
            if (!mResumed) return;
            String name = serviceInfo.getServiceName();
            mDiscoveredServices.remove(name);
            mResolvedServices.remove(name);
            onResolvedServicesChanged();
        }
    };

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

    private void onResolvedServicesChanged() {
        mServiceList = new ArrayList<>(mResolvedServices.keySet());
        Collections.sort(mServiceList);
        mAdapter.notifyDataSetChanged();
    }

    private boolean mResolving;

    private void startServiceResolution() {
        if (!mResolving && !mDiscoveredServices.isEmpty()) {
            NsdServiceInfo serviceInfo = mDiscoveredServices.values().iterator().next();
            Log.d(TAG, "startServiceResolution() resolving " + serviceInfo.getServiceName());
            mResolving = true;
            mNsdManager.resolveService(serviceInfo, mResolveListener);
        }
    }

    private boolean mResumed;

    @Override
    protected void onResume() {
        super.onResume();
        mResumed = true;
        mResolving = false;
        mNsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mResumed = false;
        if (mDiscovering) {
            mNsdManager.stopServiceDiscovery(mDiscoveryListener);
        }
        mDiscoveredServices.clear();
        mResolvedServices.clear();
        onResolvedServicesChanged();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ListView listView = (ListView) findViewById(R.id.list);
        listView.setAdapter(mAdapter);
        mNsdManager = (NsdManager) getSystemService(NSD_SERVICE);
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
}
