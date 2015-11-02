/* Copyright (c) 2015 YouView Ltd
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.example.slilly.tinydnssd;

import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.youview.tinydnssd.MDNSDiscover;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class DiscoverActivity extends ActionBarActivity {

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
