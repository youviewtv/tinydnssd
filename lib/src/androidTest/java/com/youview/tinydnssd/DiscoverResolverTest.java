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

package com.youview.tinydnssd;

import android.app.Application;
import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.test.ApplicationTestCase;
import android.util.Log;

import org.junit.runner.RunWith;
import org.junit.runner.Runner;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static com.youview.tinydnssd.RunHelper.runOnMainThread;
import static org.mockito.Mockito.*;

@RunWith(Runner.class)
public class DiscoverResolverTest extends ApplicationTestCase<Application> {

    private static final String TAG = DiscoverResolverTest.class.getSimpleName();

    private static final String SERVICE_TYPE = "_example._tcp._local";

    private DiscoverResolver mDiscoverResolver;
    private DiscoverResolver.Listener mMockListener;
    private NsdManager.DiscoveryListener mDiscoveryListener;

    public DiscoverResolverTest() {
        super(Application.class);
    }

    // Stub that allows mocking of the call to MDNSDiscover.resolve()
    interface Resolver {
        MDNSDiscover.Result resolve(String serviceName, int timeout) throws IOException;
    }

    private Resolver mMockResolver;

    // Spy this so tests can use verify() on onServicesChanged(), but have a common behaviour that
    // decrements the latch since we want the test to wait until the callback is dispatched from
    // another thread
    public class ListenerStub implements DiscoverResolver.Listener {
        @Override
        public void onServicesChanged(Map<String, MDNSDiscover.Result> services) {
            if (mLatch != null) {
                mLatch.countDown();
            }
        }
    }

    private CountDownLatch mLatch;

    public void setUp() throws IOException {
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().getPath());

        // Context is used only to access the NsdManager, which we don't want to happen since we
        // will be stubbing those methods later. Ideally we would mock NsdManager, but it is a final
        // class which cannot be mocked with Mockito.
        Context mockContext = mock(Context.class);
        when(mockContext.getSystemService(Context.NSD_SERVICE)).thenThrow(new AssertionError("did not call mock"));

        mMockListener = spy(new ListenerStub());

        mDiscoverResolver = new DiscoverResolver(mockContext, SERVICE_TYPE, mMockListener);
        mDiscoverResolver = Util.powerSpy(mDiscoverResolver);

        // Stub DiscoverResolver.discoverServices() and stopServiceDiscovery() to prevent it from
        // invoking the actual NsdManager. Also store the reference to the DiscoveryListener so we
        // can imitate NsdManager's callbacks.
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocationOnMock) {
                mDiscoveryListener = (NsdManager.DiscoveryListener) invocationOnMock.getArguments()[2];
                return null;
            }
        }).when(mDiscoverResolver).discoverServices(anyString(), anyInt(), any(NsdManager.DiscoveryListener.class));
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocationOnMock) {
                assertEquals(mDiscoveryListener, invocationOnMock.getArguments()[0]);
                return null;
            }
        }).when(mDiscoverResolver).stopServiceDiscovery(any(NsdManager.DiscoveryListener.class));

        // Stub DiscoverResolver.resolve() to delegate to mMockResolver
        mMockResolver = mock(Resolver.class);
        doAnswer(new Answer<MDNSDiscover.Result>() {
            @Override
            public MDNSDiscover.Result answer(InvocationOnMock invocation) throws Throwable {
                String serviceName = (String) invocation.getArguments()[0];
                int timeout = (Integer) invocation.getArguments()[1];
                return mMockResolver.resolve(serviceName, timeout);
            }
        }).when(mDiscoverResolver).resolve(anyString(), anyInt());
    }

    public void testDoubleStartThrowsException() {
        mDiscoverResolver.start();
        try {
            mDiscoverResolver.start();
            fail("expected IllegalStateException");
        } catch (IllegalStateException e) {
            // success
        }
    }

    public void testStopWhenNotStartedThrowsException() {
        try {
            mDiscoverResolver.stop();
            fail("expected IllegalStateException");
        } catch (IllegalStateException e) {
            // success
        }
    }

     public void testStartStop() {
        startDiscoveryOnMainThread();
        verify(mDiscoverResolver).discoverServices(eq(SERVICE_TYPE), eq(NsdManager.PROTOCOL_DNS_SD), any(NsdManager.DiscoveryListener.class));
        mDiscoveryListener.onDiscoveryStarted(SERVICE_TYPE);
        verify(mDiscoverResolver, never()).stopServiceDiscovery(any(NsdManager.DiscoveryListener.class));
        stopDiscoveryOnMainThread();
        verify(mDiscoverResolver).stopServiceDiscovery(any(NsdManager.DiscoveryListener.class));
    }

    // 'Debounce' tests check that start()/stop() can be safely called on the DiscoverResolver
    // without regard to the finer grained state in NsdManager, which will throw an exception if you
    // call stopServiceDiscovery() in the time between discoverServices() and onDiscoveryStarted().
    // Since this is asynchronous internally, NsdManager may even need to be restarted as soon as
    // it stops to respect the simplified state.

    public void testDebounceStartStop() {
        startDiscoveryOnMainThread();
        verify(mDiscoverResolver).discoverServices(eq(SERVICE_TYPE), eq(NsdManager.PROTOCOL_DNS_SD), any(NsdManager.DiscoveryListener.class));
        stopDiscoveryOnMainThread();
        verify(mDiscoverResolver, never()).stopServiceDiscovery(any(NsdManager.DiscoveryListener.class));
        mDiscoveryListener.onDiscoveryStarted(SERVICE_TYPE);
        verify(mDiscoverResolver).stopServiceDiscovery(any(NsdManager.DiscoveryListener.class));
    }

    public void testDebounceStartStopStart() {
        startDiscoveryOnMainThread();
        verify(mDiscoverResolver).discoverServices(eq(SERVICE_TYPE), eq(NsdManager.PROTOCOL_DNS_SD), any(NsdManager.DiscoveryListener.class));
        stopDiscoveryOnMainThread();
        startDiscoveryOnMainThread();
        mDiscoveryListener.onDiscoveryStarted(SERVICE_TYPE);
        verify(mDiscoverResolver, times(1)).discoverServices(anyString(), anyInt(), any(NsdManager.DiscoveryListener.class));
        verify(mDiscoverResolver, never()).stopServiceDiscovery(any(NsdManager.DiscoveryListener.class));
    }

    public void testDebounceStartStopStartStop() {
        startDiscoveryOnMainThread();
        verify(mDiscoverResolver).discoverServices(eq(SERVICE_TYPE), eq(NsdManager.PROTOCOL_DNS_SD), any(NsdManager.DiscoveryListener.class));
        stopDiscoveryOnMainThread();
        startDiscoveryOnMainThread();
        stopDiscoveryOnMainThread();
        verify(mDiscoverResolver, never()).stopServiceDiscovery(any(NsdManager.DiscoveryListener.class));
        mDiscoveryListener.onDiscoveryStarted(SERVICE_TYPE);
        verify(mDiscoverResolver).stopServiceDiscovery(any(NsdManager.DiscoveryListener.class));
    }

    public void testStartAgainWhileNsdIsStopping() {
        startDiscoveryOnMainThread();
        verify(mDiscoverResolver).discoverServices(eq(SERVICE_TYPE), eq(NsdManager.PROTOCOL_DNS_SD), any(NsdManager.DiscoveryListener.class));
        mDiscoveryListener.onDiscoveryStarted(SERVICE_TYPE);
        stopDiscoveryOnMainThread();
        verify(mDiscoverResolver).stopServiceDiscovery(eq(mDiscoveryListener));
        // we call start() again before onDiscoveryStopped() => must restart discovery automatically
        startDiscoveryOnMainThread();
        verify(mDiscoverResolver, times(1)).discoverServices(eq(SERVICE_TYPE), eq(NsdManager.PROTOCOL_DNS_SD), any(NsdManager.DiscoveryListener.class));
        mDiscoveryListener.onDiscoveryStopped(SERVICE_TYPE);
        verify(mDiscoverResolver, times(2)).discoverServices(eq(SERVICE_TYPE), eq(NsdManager.PROTOCOL_DNS_SD), any(NsdManager.DiscoveryListener.class));
        mDiscoveryListener.onDiscoveryStarted(SERVICE_TYPE);
        stopDiscoveryOnMainThread();
        verify(mDiscoverResolver, times(2)).stopServiceDiscovery(eq(mDiscoveryListener));
    }

    public void testResolve() throws IOException, InterruptedException {
        startDiscoveryOnMainThread();
        mDiscoveryListener.onDiscoveryStarted(SERVICE_TYPE);
        NsdServiceInfo serviceInfo = newNsdServiceInfo("device-1234", "_example._tcp.");
        MDNSDiscover.Result result = new MDNSDiscover.Result();
        when(mMockResolver.resolve(eq("device-1234._example._tcp.local"), anyInt())).thenReturn(result);
        mLatch = new CountDownLatch(1);
        mDiscoveryListener.onServiceFound(serviceInfo);
        mLatch.await();
        verify(mMockResolver).resolve(eq("device-1234._example._tcp.local"), anyInt());
        Map<String, MDNSDiscover.Result> expectedMap = new HashMap<>();
        expectedMap.put("device-1234._example._tcp.local", result);
        verify(mMockListener).onServicesChanged(eq(expectedMap));
        stopDiscoveryOnMainThread();
    }

    public void testServiceLost() throws IOException, InterruptedException {
        startDiscoveryOnMainThread();
        mDiscoveryListener.onDiscoveryStarted(SERVICE_TYPE);
        NsdServiceInfo serviceInfo = newNsdServiceInfo("device-1234", "_example._tcp.");
        MDNSDiscover.Result result = new MDNSDiscover.Result();
        when(mMockResolver.resolve(eq("device-1234._example._tcp.local"), anyInt())).thenReturn(result);
        mLatch = new CountDownLatch(1);
        mDiscoveryListener.onServiceFound(serviceInfo);
        mLatch.await();
        Map<String, MDNSDiscover.Result> expectedMap = new HashMap<>();
        expectedMap.put("device-1234._example._tcp.local", result);
        verify(mMockListener).onServicesChanged(eq(expectedMap));
        mLatch = new CountDownLatch(1);
        mDiscoveryListener.onServiceLost(serviceInfo);
        mLatch.await();
        expectedMap.clear();
        verify(mMockListener).onServicesChanged(eq(expectedMap));
        stopDiscoveryOnMainThread();
    }

    public void testServiceFoundFailedResolve() throws IOException, InterruptedException {
        startDiscoveryOnMainThread();
        mDiscoveryListener.onDiscoveryStarted(SERVICE_TYPE);
        NsdServiceInfo serviceInfo = newNsdServiceInfo("device-1234", "_example._tcp.");
        when(mMockResolver.resolve(eq("device-1234._example._tcp.local"), anyInt())).thenThrow(new IOException());
        mDiscoveryListener.onServiceFound(serviceInfo);
        Thread.sleep(100);
        verify(mMockResolver).resolve(eq("device-1234._example._tcp.local"), anyInt());
        verify(mMockListener, never()).onServicesChanged(anyMap());
        stopDiscoveryOnMainThread();
    }

    public void testNoCallbackAfterStop() throws IOException, InterruptedException {
        startDiscoveryOnMainThread();
        mDiscoveryListener.onDiscoveryStarted(SERVICE_TYPE);
        NsdServiceInfo serviceInfo = newNsdServiceInfo("device-1234", "_example._tcp.");
        stopDiscoveryOnMainThread();
        mDiscoveryListener.onServiceFound(serviceInfo);
        mDiscoveryListener.onDiscoveryStopped(SERVICE_TYPE);
        Thread.sleep(100);
        verify(mMockListener, never()).onServicesChanged(anyMap());
    }

    @Override
    protected void tearDown() throws Exception {
        Log.d(TAG, "tearDown()");
        // call unblockMainThread() before super.tearDown(), since super.tearDown() scrubs the class
        // members which will wipe the signalling mechanism for unblockMainThread().
        unblockMainThread();
        super.tearDown();
    }

    /**
     * Input two services while the main thread is busy. When the main thread becomes free, it
     * should receive only one onServicesChanged() with the two services.
     */
    public void testCoalesceServiceFound() throws IOException, InterruptedException {
        MDNSDiscover.Result result1 = new MDNSDiscover.Result();
        when(mMockResolver.resolve(eq("device-1234._example._tcp.local"), anyInt())).thenReturn(result1);
        MDNSDiscover.Result result2 = new MDNSDiscover.Result();
        when(mMockResolver.resolve(eq("device-5678._example._tcp.local"), anyInt())).thenReturn(result2);

        startDiscoveryOnMainThread();
        mDiscoveryListener.onDiscoveryStarted(SERVICE_TYPE);
        NsdServiceInfo serviceInfo1 = newNsdServiceInfo("device-1234", "_example._tcp.");
        NsdServiceInfo serviceInfo2 = newNsdServiceInfo("device-5678", "_example._tcp.");
        blockMainThread();
        mDiscoveryListener.onServiceFound(serviceInfo1);
        mDiscoveryListener.onServiceFound(serviceInfo2);
        Thread.sleep(100);
        verify(mMockResolver).resolve(eq("device-1234._example._tcp.local"), anyInt());
        verify(mMockResolver).resolve(eq("device-5678._example._tcp.local"), anyInt());
        verify(mMockListener, never()).onServicesChanged(anyMap());
        mLatch = new CountDownLatch(1);
        unblockMainThread();
        mLatch.await();

        Map<String, MDNSDiscover.Result> expectedServices = new HashMap<>();
        expectedServices.put("device-1234._example._tcp.local", result1);
        expectedServices.put("device-5678._example._tcp.local", result2);
        verify(mMockListener, times(1)).onServicesChanged(anyMap());
        verify(mMockListener, times(1)).onServicesChanged(eq(expectedServices));
        Thread.sleep(100);
        verify(mMockListener, times(1)).onServicesChanged(anyMap());

        stopDiscoveryOnMainThread();
    }

    /**
     * Discover two services, then with the main thread blocked, lose both services. When the main
     * thread is unblocked, there should be one update indicating both services are lost.
     * @throws IOException
     * @throws InterruptedException
     */
    public void testCoalesceServiceLost() throws IOException, InterruptedException {
        MDNSDiscover.Result result1 = new MDNSDiscover.Result();
        when(mMockResolver.resolve(eq("device-1234._example._tcp.local"), anyInt())).thenReturn(result1);
        MDNSDiscover.Result result2 = new MDNSDiscover.Result();
        when(mMockResolver.resolve(eq("device-5678._example._tcp.local"), anyInt())).thenReturn(result2);

        Map<String, MDNSDiscover.Result> populatedMap = new HashMap<>();
        populatedMap.put("device-1234._example._tcp.local", result1);
        populatedMap.put("device-5678._example._tcp.local", result2);

        startDiscoveryOnMainThread();
        mDiscoveryListener.onDiscoveryStarted(SERVICE_TYPE);
        NsdServiceInfo serviceInfo1 = newNsdServiceInfo("device-1234", "_example._tcp.");
        NsdServiceInfo serviceInfo2 = newNsdServiceInfo("device-5678", "_example._tcp.");
        blockMainThread();
        mDiscoveryListener.onServiceFound(serviceInfo1);
        mDiscoveryListener.onServiceFound(serviceInfo2);
        Thread.sleep(100);  // give the resolver some time
        verify(mMockResolver).resolve(eq("device-1234._example._tcp.local"), anyInt());
        verify(mMockResolver).resolve(eq("device-5678._example._tcp.local"), anyInt());
        verify(mMockListener, never()).onServicesChanged(anyMap());
        mLatch = new CountDownLatch(1);
        unblockMainThread();
        mLatch.await();
        verify(mMockListener).onServicesChanged(anyMap());
        verify(mMockListener).onServicesChanged(eq(populatedMap));

        blockMainThread();
        mDiscoveryListener.onServiceLost(serviceInfo1);
        mDiscoveryListener.onServiceLost(serviceInfo2);
        Thread.sleep(100);
        verify(mMockListener).onServicesChanged(anyMap());
        mLatch = new CountDownLatch(1);
        unblockMainThread();
        mLatch.await();
        Map<String, MDNSDiscover.Result> emptyMap = Collections.emptyMap();
        verify(mMockListener, times(2)).onServicesChanged(anyMap());
        verify(mMockListener, times(1)).onServicesChanged(eq(emptyMap));

        Thread.sleep(100);
        verify(mMockListener, times(2)).onServicesChanged(anyMap());

        stopDiscoveryOnMainThread();
    }

    /**
     * Resolve a service but inhibit the callback by having the main thread blocked. Stop the
     * DiscoverResolver as soon as the main thread is unblocked, then check the callback does not
     * occur after being stopped.
     * @throws IOException
     * @throws InterruptedException
     */
    public void testNoChangesAfterStop() throws IOException, InterruptedException {
        MDNSDiscover.Result result = new MDNSDiscover.Result();
        when(mMockResolver.resolve(eq("device-1234._example._tcp.local"), anyInt())).thenReturn(result);

        startDiscoveryOnMainThread();
        mDiscoveryListener.onDiscoveryStarted(SERVICE_TYPE);
        NsdServiceInfo serviceInfo = newNsdServiceInfo("device-1234", "_example._tcp.");
        blockMainThread();
        new Handler(Looper.getMainLooper()).post(DISCOVER_STOP); // queue this to happen straight after unblockMainThread()
        mDiscoveryListener.onServiceFound(serviceInfo);
        Thread.sleep(100);  // give the resolver some time
        verify(mMockResolver).resolve(eq("device-1234._example._tcp.local"), anyInt());
        verify(mMockListener, never()).onServicesChanged(anyMap());
        unblockMainThread();
        Thread.sleep(100);
        verify(mMockListener, never()).onServicesChanged(anyMap());
    }

    private boolean mBlockMainThread;
    private final Object mMainThreadBlockCondVar = new Object();

    /**
     * Once this method returns, the main thread is guaranteed to be blocked waiting for
     * {@link #unblockMainThread()} to be called.
     */
    private void blockMainThread() {
        Log.d(TAG, "blockMainThread()");

        final CountDownLatch runLatch = new CountDownLatch(1);

        // post a Runnable to the main thread that will block on the value mBlockMainThread
        mBlockMainThread = true;
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                // notify blockMainThread() that this Runnable is now running
                runLatch.countDown();

                // wait for a call to unblockMainThread()
                Log.d(TAG, "blocking main thread");
                synchronized (mMainThreadBlockCondVar) {
                    while (mBlockMainThread) {
                        try {
                            mMainThreadBlockCondVar.wait();
                        } catch (InterruptedException e) {
                            throw new Error(e);
                        }
                    }
                }
                Log.d(TAG, "unblocking main thread");
            }
        });

        // wait for the main thread to enter the Runnable we just posted
        try {
            runLatch.await();
        } catch (InterruptedException e) {
            throw new Error(e);
        }
    }

    /**
     * Resume the main thread having previously blocked it with {@link #blockMainThread()}.
     */
    private void unblockMainThread() {
        Log.d(TAG, "unblockMainThread()");

        synchronized (mMainThreadBlockCondVar) {
            mBlockMainThread = false;
            mMainThreadBlockCondVar.notifyAll();
        }
    }

    private final Runnable DISCOVER_START = new Runnable() {
        @Override
        public void run() {
            mDiscoverResolver.start();
        }
    };

    private final Runnable DISCOVER_STOP = new Runnable() {
        @Override
        public void run() {
            mDiscoverResolver.stop();
        }
    };

    private void startDiscoveryOnMainThread() {
        runOnMainThread(DISCOVER_START);
    }

    private void stopDiscoveryOnMainThread() {
        runOnMainThread(DISCOVER_STOP);
    }

    private static NsdServiceInfo newNsdServiceInfo(String name, String type) {
        NsdServiceInfo result = new NsdServiceInfo();
        result.setServiceName(name);
        result.setServiceType(type);
        return result;
    }
}
