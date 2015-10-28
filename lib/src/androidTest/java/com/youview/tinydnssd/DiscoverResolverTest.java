package com.youview.tinydnssd;

import android.app.Application;
import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.test.ApplicationTestCase;

import org.junit.runner.RunWith;
import org.junit.runner.Runner;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static org.mockito.Mockito.*;

@RunWith(Runner.class)
public class DiscoverResolverTest extends ApplicationTestCase<Application> {

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
        mDiscoverResolver.start();
        verify(mDiscoverResolver).discoverServices(eq(SERVICE_TYPE), eq(NsdManager.PROTOCOL_DNS_SD), any(NsdManager.DiscoveryListener.class));
        mDiscoveryListener.onDiscoveryStarted(SERVICE_TYPE);    // FIXME real NsdManager would call this in a worker thread
        verify(mDiscoverResolver, never()).stopServiceDiscovery(any(NsdManager.DiscoveryListener.class));
        mDiscoverResolver.stop();
        verify(mDiscoverResolver).stopServiceDiscovery(any(NsdManager.DiscoveryListener.class));
    }

    public void testDebounceStartStop() {
        mDiscoverResolver.start();
        verify(mDiscoverResolver).discoverServices(eq(SERVICE_TYPE), eq(NsdManager.PROTOCOL_DNS_SD), any(NsdManager.DiscoveryListener.class));
        mDiscoverResolver.stop();
        verify(mDiscoverResolver, never()).stopServiceDiscovery(any(NsdManager.DiscoveryListener.class));
        mDiscoveryListener.onDiscoveryStarted(SERVICE_TYPE);    // FIXME real NsdManager would call this in a worker thread
        verify(mDiscoverResolver).stopServiceDiscovery(any(NsdManager.DiscoveryListener.class));
    }

    public void testDebounceStartStopStart() {
        mDiscoverResolver.start();
        verify(mDiscoverResolver).discoverServices(eq(SERVICE_TYPE), eq(NsdManager.PROTOCOL_DNS_SD), any(NsdManager.DiscoveryListener.class));
        mDiscoverResolver.stop();
        mDiscoverResolver.start();
        mDiscoveryListener.onDiscoveryStarted(SERVICE_TYPE);    // FIXME real NsdManager would call this in a worker thread
        verify(mDiscoverResolver, times(1)).discoverServices(anyString(), anyInt(), any(NsdManager.DiscoveryListener.class));
        verify(mDiscoverResolver, never()).stopServiceDiscovery(any(NsdManager.DiscoveryListener.class));
    }

    public void testDebounceStartStopStartStop() {
        mDiscoverResolver.start();
        verify(mDiscoverResolver).discoverServices(eq(SERVICE_TYPE), eq(NsdManager.PROTOCOL_DNS_SD), any(NsdManager.DiscoveryListener.class));
        mDiscoverResolver.stop();
        mDiscoverResolver.start();
        mDiscoverResolver.stop();
        verify(mDiscoverResolver, never()).stopServiceDiscovery(any(NsdManager.DiscoveryListener.class));
        mDiscoveryListener.onDiscoveryStarted(SERVICE_TYPE);    // FIXME real NsdManager would call this in a worker thread
        verify(mDiscoverResolver).stopServiceDiscovery(any(NsdManager.DiscoveryListener.class));
    }

    public void testStartAgainWhileNsdIsStopping() {
        mDiscoverResolver.start();
        verify(mDiscoverResolver).discoverServices(eq(SERVICE_TYPE), eq(NsdManager.PROTOCOL_DNS_SD), any(NsdManager.DiscoveryListener.class));
        mDiscoveryListener.onDiscoveryStarted(SERVICE_TYPE);
        mDiscoverResolver.stop();
        verify(mDiscoverResolver).stopServiceDiscovery(eq(mDiscoveryListener));
        // we call start() again before onDiscoveryStopped() => must restart discovery automatically
        mDiscoverResolver.start();
        verify(mDiscoverResolver, times(1)).discoverServices(eq(SERVICE_TYPE), eq(NsdManager.PROTOCOL_DNS_SD), any(NsdManager.DiscoveryListener.class));
        mDiscoveryListener.onDiscoveryStopped(SERVICE_TYPE);
        verify(mDiscoverResolver, times(2)).discoverServices(eq(SERVICE_TYPE), eq(NsdManager.PROTOCOL_DNS_SD), any(NsdManager.DiscoveryListener.class));
        mDiscoveryListener.onDiscoveryStarted(SERVICE_TYPE);
        mDiscoverResolver.stop();
        verify(mDiscoverResolver, times(2)).stopServiceDiscovery(eq(mDiscoveryListener));
    }

    public void testResolve() throws IOException, InterruptedException {
        mDiscoverResolver.start();
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
        mDiscoverResolver.stop();
    }

    public void testServiceLost() throws IOException, InterruptedException {
        mDiscoverResolver.start();
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
        mDiscoverResolver.stop();
    }

    public void testServiceFoundFailedResolve() throws IOException, InterruptedException {
        mDiscoverResolver.start();
        mDiscoveryListener.onDiscoveryStarted(SERVICE_TYPE);
        NsdServiceInfo serviceInfo = newNsdServiceInfo("device-1234", "_example._tcp.");
        when(mMockResolver.resolve(eq("device-1234._example._tcp.local"), anyInt())).thenThrow(new IOException());
        mDiscoveryListener.onServiceFound(serviceInfo);
        Thread.sleep(100);
        verify(mMockResolver).resolve(eq("device-1234._example._tcp.local"), anyInt());
        verify(mMockListener, never()).onServicesChanged(anyMap());
        mDiscoverResolver.stop();
    }

    public void testNoCallbackAfterStop() throws IOException, InterruptedException {
        mDiscoverResolver.start();
        mDiscoveryListener.onDiscoveryStarted(SERVICE_TYPE);
        NsdServiceInfo serviceInfo = newNsdServiceInfo("device-1234", "_example._tcp.");
        mDiscoverResolver.stop();
        mDiscoveryListener.onServiceFound(serviceInfo);
        mDiscoveryListener.onDiscoveryStopped(SERVICE_TYPE);
        Thread.sleep(100);
        verify(mMockListener, never()).onServicesChanged(anyMap());
    }

    private static NsdServiceInfo newNsdServiceInfo(String name, String type) {
        NsdServiceInfo result = new NsdServiceInfo();
        result.setServiceName(name);
        result.setServiceType(type);
        return result;
    }
}
