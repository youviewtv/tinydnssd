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
import android.support.v7.app.ActionBarActivity;
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
