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

import android.os.Handler;
import android.os.Looper;

public class RunHelper {
    /**
     * Runs the {@code action} on the main thread, and blocks this thread until the action has
     * completed. If the action throws any exception, it is caught and thrown again in this thread
     * as an unchecked exception.
     * @param action code to execute on the main thread
     * @throws RuntimeException wrapping any {@link Throwable} if thrown by the {@code action}
     */
    static void runOnMainThread(final Runnable action) {
        final Object condVar = new Object();
        final Throwable[] throwable = { null };
        synchronized (condVar) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    try {
                        action.run();
                    } catch (Throwable t) {
                        throwable[0] = t;
                    }
                    synchronized (condVar) {
                        condVar.notify();
                    }
                }
            });
            try {
                condVar.wait();
                if (throwable[0] != null) {
                    throw new RuntimeException(throwable[0]);
                }
            } catch (InterruptedException e) {
                throw new Error(e);
            }
        }
    }
}
