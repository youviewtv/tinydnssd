package com.example.slilly.nsddemo;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import java.io.IOException;

/**
 * Created by slilly on 14/05/2015.
 */
public class DiscoverActivity extends Activity {

    private static final String TAG = DiscoverActivity.class.getSimpleName();

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
    }

    private void onDiscoverClicked() {
        new Thread() {
            @Override
            public void run() {
                try {
                    MDNSDiscover.discover("_yv-bridge._tcp.local");
                } catch (IOException e) {
                    Log.e(TAG, "error calling discover()", e);
                }
            }
        }.start();
    }
}
