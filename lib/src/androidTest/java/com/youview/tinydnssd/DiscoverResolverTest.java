package com.youview.tinydnssd;

import android.app.Application;
import android.content.Context;
import android.net.nsd.NsdManager;
import android.test.ApplicationTestCase;

import org.junit.runner.RunWith;
import org.junit.runner.Runner;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

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

    public void setUp() {
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().getPath());

        mMockListener = mock(DiscoverResolver.Listener.class);

        Context mockContext = mock(Context.class);
        when(mockContext.getSystemService(Context.NSD_SERVICE)).thenThrow(new AssertionError("did not call mock"));
        mDiscoverResolver = new DiscoverResolver(mockContext, SERVICE_TYPE, mMockListener);

        mDiscoverResolver = Util.powerSpy(mDiscoverResolver);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocationOnMock) throws Throwable {
                mDiscoveryListener = (NsdManager.DiscoveryListener) invocationOnMock.getArguments()[2];
//  Direct alternative when using spy() instead of powerSpy(), assumes knowledge of DUT's object graph
//                Field field = mDiscoveryListener.getClass().getDeclaredField("this$0");
//                field.setAccessible(true);
//                field.set(mDiscoveryListener, mDiscoverResolver);
                return null;
            }
        }).when(mDiscoverResolver).discoverServices(anyString(), anyInt(), any(NsdManager.DiscoveryListener.class));
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocationOnMock) throws Throwable {
                assertEquals(mDiscoveryListener, invocationOnMock.getArguments()[0]);
                mDiscoveryListener = null;
                return null;
            }
        }).when(mDiscoverResolver).stopServiceDiscovery(any(NsdManager.DiscoveryListener.class));

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
}
