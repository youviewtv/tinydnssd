package com.example.slilly.nsddemo;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class DiscoverActivity extends Activity {

    private static final String TAG = DiscoverActivity.class.getSimpleName();

    private Handler mHandler = new Handler();
    private MDNSDiscover.Callback mDiscoverCallback = new MDNSDiscover.Callback() {
        @Override
        public void onResult(final MDNSDiscover.Result result) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    onBoxDiscovered(result);
                }
            });
        }
    };

    private TextView mTextNumFound;
    private HashMap<String, MDNSDiscover.Result> mSeenMap = new HashMap<>();
    private ArrayList<String> mSeenList = new ArrayList<>();
    private MyAdapter mAdapter = new MyAdapter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.discover_activity);
        findViewById(R.id.button_discover).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onDiscoverClicked();
            }
        });
        ((ListView) findViewById(R.id.list)).setAdapter(mAdapter);
        mTextNumFound = (TextView) findViewById(R.id.num_boxes_found);
    }

    private void onDiscoverClicked() {
        new Thread() {
            @Override
            public void run() {
                try {
                    MDNSDiscover.discover("_yv-bridge._tcp.local", mDiscoverCallback, 5000);
                } catch (IOException e) {
                    Log.e(TAG, "error calling discover()", e);
                }
            }
        }.start();
    }

    private void onBoxDiscovered(MDNSDiscover.Result result) {
        if (result.txt != null && result.a != null && result.srv != null) {
            if (mSeenMap.put(result.txt.fqdn, result) == null) {
                mSeenList.add(result.txt.fqdn);
            }
            mAdapter.notifyDataSetChanged();
            mTextNumFound.setText(getString(R.string.num_boxes_found, mSeenList.size()));
        }
    }

    private class MyAdapter extends BaseAdapter {

        class Holder {
            TextView mTextServiceName, mTextDNSName, mTextIPPort, mTextVendorSerial;
        }

        @Override
        public int getCount() {
            return mSeenList.size();
        }

        @Override
        public MDNSDiscover.Result getItem(int position) {
            return mSeenMap.get(mSeenList.get(position));
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view;
            Holder holder;
            if (convertView == null) {
                view = getLayoutInflater().inflate(R.layout.stb_list_item, parent, false);
                holder = new Holder();
                view.setTag(holder);
                holder.mTextServiceName = (TextView) view.findViewById(R.id.text_service_name);
                holder.mTextDNSName = (TextView) view.findViewById(R.id.text_dns_name);
                holder.mTextIPPort = (TextView) view.findViewById(R.id.text_ip_port);
                holder.mTextVendorSerial = (TextView) view.findViewById(R.id.text_vendor_serial);
            } else {
                view = convertView;
                holder = (Holder) view.getTag();
            }
            MDNSDiscover.Result item = getItem(position);
            String ipAndPort = item.a.ipaddr + ":" + item.srv.port;
            String vendorAndSerial = item.txt.dict.get("vendor") + " " + item.txt.dict.get("model") + " (" + item.txt.dict.get("serial4") + ")";
            holder.mTextServiceName.setText(item.txt.fqdn);
            holder.mTextDNSName.setText(item.a.fqdn);
            holder.mTextIPPort.setText(ipAndPort);
            holder.mTextVendorSerial.setText(vendorAndSerial);
            return view;
        }
    }
}
