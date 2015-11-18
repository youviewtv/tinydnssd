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

package com.youview.tinydnssd;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class DiscoverResolver {

    private static final String TAG = DiscoverResolver.class.getSimpleName();
    private static final int RESOLVE_TIMEOUT = 1000;

    public interface Listener {
        void onServicesChanged(Map<String, MDNSDiscover.Result> services);
    }

    private final Context mContext;
    private final String mServiceType;
    private HashMap<String, MDNSDiscover.Result> mServices = new HashMap<>();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Listener mListener;
    private boolean mStarted;
    private boolean mTransitioning;
    private ResolveTask mResolveTask;
    private final Map<String, NsdServiceInfo> mResolveQueue = new LinkedHashMap<>();

    public DiscoverResolver(Context context, String serviceType, Listener listener) {
        if     (context == null) throw new NullPointerException("context was null");
        if (serviceType == null) throw new NullPointerException("serviceType was null");
        if    (listener == null) throw new NullPointerException("listener was null");

        mContext = context;
        mServiceType = serviceType;
        mListener = listener;
    }

    public synchronized void start() {
        if (mStarted) {
            throw new IllegalStateException();
        }
        if (!mTransitioning) {
            discoverServices(mServiceType, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
            mTransitioning = true;
        }
        mStarted = true;
    }

    public synchronized void stop() {
        if (!mStarted) {
            throw new IllegalStateException();
        }
        if (!mTransitioning) {
            stopServiceDiscovery(mDiscoveryListener);
            mTransitioning = true;
        }
        synchronized (mResolveQueue) {
            mResolveQueue.clear();
        }
        mStarted = false;
    }

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
            synchronized (DiscoverResolver.this) {
                if (!mStarted) {
                    stopServiceDiscovery(this);
                } else {
                    mTransitioning = false;
                }
            }
        }

        @Override
        public void onDiscoveryStopped(String serviceType) {
            Log.d(TAG, "onDiscoveryStopped() serviceType = [" + serviceType + "]");
            if (mStarted) {
                discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, this);
            } else {
                mTransitioning = false;
            }
        }

        @Override
        public void onServiceFound(final NsdServiceInfo serviceInfo) {
            Log.d(TAG, "onServiceFound() serviceInfo = [" + serviceInfo + "]");
            if (mStarted) {
                String name = serviceInfo.getServiceName() + "." + serviceInfo.getServiceType() + "local";
                synchronized (mResolveQueue) {
                    mResolveQueue.put(name, null);
                }
                startResolveTaskIfNeeded();
            }
        }

        @Override
        public void onServiceLost(final NsdServiceInfo serviceInfo) {
            Log.d(TAG, "onServiceLost() serviceInfo = [" + serviceInfo + "]");
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mStarted) {
                        String name = serviceInfo.getServiceName() + "." + serviceInfo.getServiceType() + "local";
                        synchronized (mResolveQueue) {
                            mResolveQueue.remove(name);
                        }
                        removeService(name);
                    }
                }
            });
        }
    };

    private void addService(String serviceName, MDNSDiscover.Result result) {
        mServices.put(serviceName, result);
        dispatchServicesChanged();
    }

    private void removeService(String name) {
        if (mServices.remove(name) != null) {
            dispatchServicesChanged();
        }
    }

    private void dispatchServicesChanged() {
        final HashMap<String, MDNSDiscover.Result> services = (HashMap) mServices.clone();
        mListener.onServicesChanged(services);
    }

    private class ResolveTask extends AsyncTask<Void, Object, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            while (!isCancelled()) {
                String serviceName;
                synchronized (mResolveQueue) {
                    Iterator<String> it = mResolveQueue.keySet().iterator();
                    if (!it.hasNext()) {
                        break;
                    }
                    serviceName = it.next();
                    it.remove();
                }
                try {
                    MDNSDiscover.Result result = resolve(serviceName, RESOLVE_TIMEOUT);
                    publishProgress(serviceName, result);
                } catch(IOException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            mResolveTask = null;
            startResolveTaskIfNeeded();
        }

        @Override
        protected void onProgressUpdate(Object... values) {
            addService((String) values[0], (MDNSDiscover.Result) values[1]);
        }
    }

    private void startResolveTaskIfNeeded() {
        if (mResolveTask == null) {
            synchronized (mResolveQueue) {
                if (!mResolveQueue.isEmpty()) {
                    mResolveTask = new ResolveTask();
                    mResolveTask.execute();
                }
            }
        }
    }

    // default implementation is to delegate to NsdManager
    // tests can stub this to mock the NsdManager
    protected void discoverServices(String serviceType, int protocol, NsdManager.DiscoveryListener listener) {
        ((NsdManager) mContext.getSystemService(Context.NSD_SERVICE)).discoverServices(serviceType, protocol, listener);
    }

    // default implementation is to delegate to NsdManager
    // tests can stub this to mock the NsdManager
    protected void stopServiceDiscovery(NsdManager.DiscoveryListener listener) {
        ((NsdManager) mContext.getSystemService(Context.NSD_SERVICE)).stopServiceDiscovery(listener);
    }

    // default implementation is to delegate to MDNSDiscover
    // tests can stub this to mock it
    protected MDNSDiscover.Result resolve(String serviceName, int resolveTimeout) throws IOException {
        return MDNSDiscover.resolve(serviceName, resolveTimeout);
    }
}
