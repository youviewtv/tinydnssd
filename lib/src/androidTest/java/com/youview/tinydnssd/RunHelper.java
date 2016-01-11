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
