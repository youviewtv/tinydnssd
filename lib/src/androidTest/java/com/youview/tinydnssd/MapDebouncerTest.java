package com.youview.tinydnssd;

import android.app.Application;
import android.os.SystemClock;
import android.test.ApplicationTestCase;

import static com.youview.tinydnssd.RunHelper.runOnMainThread;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class MapDebouncerTest extends ApplicationTestCase<Application> {

    private static final int DEBOUNCE_PERIOD = 1000;

    private MapDebouncer<String, String> mMapDebouncer;
    private MapDebouncer.Listener<String, String> mMockListener;

    public MapDebouncerTest() {
        super(Application.class);
    }

    public void setUp() {
        createMockListener();
        mMapDebouncer = new MapDebouncer<>(DEBOUNCE_PERIOD, mMockListener);
    }

    @SuppressWarnings("unchecked")
    private void createMockListener() {
        mMockListener = mock(MapDebouncer.Listener.class);
    }

    public void testInsertIsImmediate() {
        mMapDebouncer.put("foo", "bar");
        verify(mMockListener).put(eq("foo"), eq("bar"));
        verifyNoMoreInteractions(mMockListener);
    }

    public void testUpdateIsImmediate() {
        mMapDebouncer.put("foo", "bar");
        verify(mMockListener).put(eq("foo"), eq("bar"));
        mMapDebouncer.put("foo", "baz");
        verify(mMockListener).put(eq("foo"), eq("baz"));
        verifyNoMoreInteractions(mMockListener);
    }

    public void testDelayedDelete() {
        putFromMainThread("foo", "bar");
        verify(mMockListener).put(eq("foo"), eq("bar"));
        putFromMainThread("foo", null);
        verify(mMockListener, times(1)).put(eq("foo"), anyString());
        SystemClock.sleep(DEBOUNCE_PERIOD + 10);
        verify(mMockListener).put(eq("foo"), isNull(String.class));
        verifyNoMoreInteractions(mMockListener);
    }

    public void testDeleteThenReAdd() {
        putFromMainThread("foo", "bar");
        verify(mMockListener).put(eq("foo"), eq("bar"));
        putFromMainThread("foo", null);
        verify(mMockListener, times(1)).put(eq("foo"), anyString());
        SystemClock.sleep(DEBOUNCE_PERIOD / 2);
        verify(mMockListener, times(1)).put(eq("foo"), anyString());
        putFromMainThread("foo", "bar");
        SystemClock.sleep(DEBOUNCE_PERIOD + 10);
        verifyNoMoreInteractions(mMockListener);
    }

    public void testDeleteThenImmediateUpdate() {
        putFromMainThread("foo", "bar");
        verify(mMockListener).put(eq("foo"), eq("bar"));
        putFromMainThread("foo", null);
        verify(mMockListener, times(1)).put(eq("foo"), anyString());
        SystemClock.sleep(DEBOUNCE_PERIOD / 2);
        verify(mMockListener, times(1)).put(eq("foo"), anyString());
        putFromMainThread("foo", "baz");
        verify(mMockListener, times(1)).put(eq("foo"), eq("baz"));
        SystemClock.sleep(DEBOUNCE_PERIOD + 10);
        verifyNoMoreInteractions(mMockListener);
    }

    private void putFromMainThread(final String key, final String value) {
        runOnMainThread(new Runnable() {
            @Override
            public void run() {
                mMapDebouncer.put(key, value);
            }
        });
    }
}
