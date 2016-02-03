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
    private static final int DEBOUNCE_PERIOD_BEFORE = DEBOUNCE_PERIOD / 2;
    private static final int DEBOUNCE_PERIOD_AFTER = DEBOUNCE_PERIOD + 10;

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
        SystemClock.sleep(DEBOUNCE_PERIOD_AFTER);
        verify(mMockListener).put(eq("foo"), isNull(String.class));
        verifyNoMoreInteractions(mMockListener);
    }

    public void testDeleteThenReAdd() {
        putFromMainThread("foo", "bar");
        verify(mMockListener).put(eq("foo"), eq("bar"));
        putFromMainThread("foo", null);
        verify(mMockListener, times(1)).put(eq("foo"), anyString());
        SystemClock.sleep(DEBOUNCE_PERIOD_BEFORE);
        verify(mMockListener, times(1)).put(eq("foo"), anyString());
        putFromMainThread("foo", "bar");
        SystemClock.sleep(DEBOUNCE_PERIOD_AFTER);
        verifyNoMoreInteractions(mMockListener);
    }

    public void testDeleteThenImmediateUpdate() {
        putFromMainThread("foo", "bar");
        verify(mMockListener).put(eq("foo"), eq("bar"));
        putFromMainThread("foo", null);
        verify(mMockListener, times(1)).put(eq("foo"), anyString());
        SystemClock.sleep(DEBOUNCE_PERIOD_BEFORE);
        verify(mMockListener, times(1)).put(eq("foo"), anyString());
        putFromMainThread("foo", "baz");
        verify(mMockListener, times(1)).put(eq("foo"), eq("baz"));
        SystemClock.sleep(DEBOUNCE_PERIOD_AFTER);
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
