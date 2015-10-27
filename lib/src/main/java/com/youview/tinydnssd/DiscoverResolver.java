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

/**
 * Created by slilly on 04/08/2015.
 */
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
    private boolean mStarting;
    private ResolveTask mResolveTask;
    private final Map<String, NsdServiceInfo> mResolveQueue = new LinkedHashMap<>();

    public DiscoverResolver(Context context, String serviceType, Listener listener) {
        if (context == null || serviceType == null || listener == null) {
            throw new NullPointerException();
        }
        mContext = context;
        mServiceType = serviceType;
        mListener = listener;
    }

    public synchronized void start() {
        if (mStarted) {
            throw new IllegalStateException();
        }
        if (!mStarting) {
            discoverServices(mServiceType, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
            mStarting = true;
        }
        mStarted = true;
    }

    public synchronized void stop() {
        if (!mStarted) {
            throw new IllegalStateException();
        }
        if (!mStarting) {
            stopServiceDiscovery(mDiscoveryListener);
            mStarting = false;
        }
        synchronized (mResolveQueue) {
            mResolveQueue.clear();
        }
        mStarted = false;
    }

    // default implementation is to delegate to NsdManager
    // tests can stub this to mock the NsdManager
    public void discoverServices(String serviceType, int protocol, NsdManager.DiscoveryListener listener) {
        ((NsdManager) mContext.getSystemService(Context.NSD_SERVICE)).discoverServices(serviceType, protocol, listener);
    }

    // default implementation is to delegate to NsdManager
    // tests can stub this to mock the NsdManager
    public void stopServiceDiscovery(NsdManager.DiscoveryListener listener) {
        ((NsdManager) mContext.getSystemService(Context.NSD_SERVICE)).stopServiceDiscovery(listener);
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
                mStarting = false;
                if (!mStarted) {
                    stopServiceDiscovery(this);
                }
            }
        }

        @Override
        public void onDiscoveryStopped(String serviceType) {
            Log.d(TAG, "onDiscoveryStopped() serviceType = [" + serviceType + "]");
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

    private void addService(MDNSDiscover.Result result) {
        mServices.put(result.srv.fqdn, result);
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

    private class ResolveTask extends AsyncTask<Void, MDNSDiscover.Result, Void> {
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
                    MDNSDiscover.Result result = MDNSDiscover.resolve(serviceName, RESOLVE_TIMEOUT);
                    publishProgress(result);
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
        protected void onProgressUpdate(MDNSDiscover.Result... values) {
            addService(values[0]);
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
}
