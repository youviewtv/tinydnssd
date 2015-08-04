package com.example.slilly.nsddemo;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.util.Log;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by slilly on 04/08/2015.
 */
class DiscoverResolver {

    private static final String TAG = DiscoverResolver.class.getSimpleName();

    public interface Listener {
        void onServicesChanged(Map<String, NsdServiceInfo> services);
    }

    private final NsdManager mNsdManager;
    private final String mServiceType;
    private HashMap<String, NsdServiceInfo> mServices = new HashMap<>();
    private Handler mHandler = new Handler();
    private final Listener mListener;
    private boolean mStarted;
    private boolean mDiscovering;

    DiscoverResolver(Context context, String serviceType, Listener listener) {
        if (serviceType == null || listener == null) {
            throw new NullPointerException();
        }
        mNsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        mServiceType = serviceType;
        mListener = listener;
    }

    synchronized void start() {
        if (mStarted) {
            throw new IllegalStateException();
        }
        mNsdManager.discoverServices(mServiceType, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
        mStarted = true;
    }

    synchronized void stop() {
        if (!mStarted) {
            throw new IllegalStateException();
        }
        if (mDiscovering) {
            mNsdManager.stopServiceDiscovery(mDiscoveryListener);
        }
        mStarted = false;
    }

    private LinkedHashMap<String, NsdServiceInfo> mResolveQueue = new LinkedHashMap<>();

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
                if (mStarted) {
                    mDiscovering = true;
                } else {
                    mNsdManager.stopServiceDiscovery(this);
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
            synchronized (DiscoverResolver.this) {
                if (mStarted) {
                    mResolveQueue.put(serviceInfo.getServiceName(), serviceInfo);
                    iterateResolver();
                }
            }
        }

        @Override
        public void onServiceLost(NsdServiceInfo serviceInfo) {
            Log.d(TAG, "onServiceLost() serviceInfo = [" + serviceInfo + "]");
            synchronized (DiscoverResolver.this) {
                if (mStarted) {
                    mResolveQueue.remove(serviceInfo.getServiceName());
                }
            }
        }
    };

    private void iterateResolver() {
        if (!(mResolving || mResolveQueue.isEmpty())) {
            NsdServiceInfo info = mResolveQueue.values().iterator().next();
            mNsdManager.resolveService(info, mResolveListener);
            mResolving = true;
        }
    }

    private boolean mResolving;
    private NsdManager.ResolveListener mResolveListener = new NsdManager.ResolveListener() {

        @Override
        public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
            Log.d(TAG, "onResolveFailed() serviceInfo = [" + serviceInfo + "], errorCode = " + errorCode);
            synchronized (DiscoverResolver.this) {
                mResolving = false;
                mResolveQueue.remove(serviceInfo.getServiceName());
                if (mStarted) {
                    removeService(unescape(serviceInfo.getServiceName()));
                    iterateResolver();
                }
            }
        }

        @Override
        public void onServiceResolved(NsdServiceInfo serviceInfo) {
            Log.d(TAG, "onServiceResolved() serviceInfo = [" + serviceInfo + "]");
            synchronized (DiscoverResolver.this) {
                mResolving = false;
                mResolveQueue.remove(serviceInfo.getServiceName());
                if (mStarted) {
                    addService(serviceInfo);
                    iterateResolver();
                }
            }
        }
    };

    private String unescape(String name) {
        return name.replace("\\032", " ");
    }

    private void addService(NsdServiceInfo serviceInfo) {
        mServices.put(serviceInfo.getServiceName(), serviceInfo);
        dispatchServicesChanged();
    }

    private void removeService(String name) {
        if (mServices.remove(name) != null) {
            dispatchServicesChanged();
        }
    }

    private void dispatchServicesChanged() {
        final HashMap<String, NsdServiceInfo> services = (HashMap) mServices.clone();
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mListener.onServicesChanged(services);
            }
        });
    }
}
