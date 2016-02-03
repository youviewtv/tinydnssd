/* The MIT License (MIT)
 * Copyright (c) 2015 YouView Ltd
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

package com.youview.tinydnssd.demo;

import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import com.youview.tinydnssd.MDNSDiscover;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class MDNSDiscoverDemoActivity extends ActionBarActivity {

    private static final String SERVICE_TYPE = "_yv-bridge._tcp.local";
    private static final int DISCOVER_TIMEOUT = 5000;

    private static final String TAG = MDNSDiscoverDemoActivity.class.getSimpleName();

    private Handler mHandler = new Handler();
    private MDNSDiscover.Callback mDiscoverCallback = new MDNSDiscover.Callback() {
        @Override
        public void onResult(final MDNSDiscover.Result result) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    onServiceDiscovered(result);
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
        setContentView(R.layout.activity_mdnsdiscover_demo);
        findViewById(R.id.button_discover).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onDiscoverClicked();
            }
        });
        ((ListView) findViewById(R.id.list)).setAdapter(mAdapter);
        mTextNumFound = (TextView) findViewById(R.id.num_services_found);
        mTextNumFound.setText(getString(R.string.search_for, SERVICE_TYPE));
    }

    private void onDiscoverClicked() {
        new Thread() {
            @Override
            public void run() {
                try {
                    MDNSDiscover.discover(SERVICE_TYPE, mDiscoverCallback, DISCOVER_TIMEOUT);
                } catch (IOException e) {
                    Log.e(TAG, "error calling discover()", e);
                }
            }
        }.start();
    }

    private void onServiceDiscovered(MDNSDiscover.Result result) {
        if (result.txt != null && result.a != null && result.srv != null) {
            if (mSeenMap.put(result.txt.fqdn, result) == null) {
                mSeenList.add(result.txt.fqdn);
            }
            mAdapter.notifyDataSetChanged();
            mTextNumFound.setText(getString(R.string.num_services_found, mSeenList.size()));
        }
    }

    private class MyAdapter extends ResultAdapter {

        @Override
        public int getCount() {
            return mSeenList.size();
        }

        @Override
        public MDNSDiscover.Result getItem(int position) {
            return mSeenMap.get(mSeenList.get(position));
        }
    }
}
