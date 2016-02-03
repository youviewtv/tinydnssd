/* The MIT License (MIT)
 * Copyright (c) 2016 YouView Ltd
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
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.youview.tinydnssd.DiscoverResolver;
import com.youview.tinydnssd.MDNSDiscover;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class DiscoverResolverDemoActivity extends ActionBarActivity implements DiscoverResolver.Listener {

    private static final String SERVICE_TYPE = "_yv-bridge._tcp";
    private static final String TAG = DiscoverResolverDemoActivity.class.getSimpleName();
    private static final int DEBOUNCE_MILLIS = 5000;

    private DiscoverResolver mDiscoverResolver;

    private List<MDNSDiscover.Result> mServiceList = new ArrayList<>();

    private BaseAdapter mAdapter = new ResultAdapter() {
        @Override
        public int getCount() {
            return mServiceList.size();
        }

        @Override
        public MDNSDiscover.Result getItem(int position) {
            return mServiceList.get(position);
        }
    };

    private Comparator<? super MDNSDiscover.Result> mFqdnComparator = new Comparator<MDNSDiscover.Result>() {
        @Override
        public int compare(MDNSDiscover.Result lhs, MDNSDiscover.Result rhs) {
            if (lhs.a == null) {
                return rhs.a != null ? -1 : 0;
            }
            if (rhs.a == null) {
                return 1;
            }
            return lhs.a.fqdn.compareTo(rhs.a.fqdn);
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
        setContentView(R.layout.activity_discoverresolver_demo);
        ListView listView = (ListView) findViewById(R.id.list);
        listView.setAdapter(mAdapter);
        mDiscoverResolver = new DiscoverResolver(this, SERVICE_TYPE, this, DEBOUNCE_MILLIS);
        ((TextView) findViewById(R.id.text_searching)).setText(
                getString(R.string.searching_for, SERVICE_TYPE));
    }

    @Override
    public void onServicesChanged(Map<String, MDNSDiscover.Result> services) {
        Log.d(TAG, "onServicesChanged() size=" + services.size());
        mServiceList.clear();
        mServiceList.addAll(services.values());
        Collections.sort(mServiceList, mFqdnComparator);
        mAdapter.notifyDataSetChanged();
    }
}
