package com.createcolonylogistics.network;

import com.createcolonylogistics.clipboard.SmartClipboardReport;

import java.lang.reflect.Method;

public final class SmartClipboardClientBridge {
    private SmartClipboardClientBridge() {
    }

    public static void open(SmartClipboardReport report) {
        try {
            Class<?> client = Class.forName("com.createcolonylogistics.client.SmartClipboardClient");
            Method open = client.getMethod("open", SmartClipboardReport.class);
            open.invoke(null, report);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Client-only bridge; no-op if invoked in an unexpected environment.
        }
    }

    public static void applyCancelResult(SmartClipboardReport report, String requestToken, boolean accepted) {
        try {
            Class<?> client = Class.forName("com.createcolonylogistics.client.SmartClipboardClient");
            Method method = client.getMethod("applyCancelResult", SmartClipboardReport.class, String.class, boolean.class);
            method.invoke(null, report, requestToken, accepted);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Client-only bridge; no-op if invoked in an unexpected environment.
        }
    }
}
