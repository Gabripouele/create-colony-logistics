package com.createcolonylogistics.item;

public final class SmartClipboardColonyLinkGuard {
    private static final ThreadLocal<Boolean> EXPLICIT_LINK = ThreadLocal.withInitial(() -> false);

    private SmartClipboardColonyLinkGuard() {
    }

    public static boolean explicitLinkAllowed() {
        return EXPLICIT_LINK.get();
    }

    public static void runWithExplicitLink(Runnable action) {
        boolean previous = EXPLICIT_LINK.get();
        EXPLICIT_LINK.set(true);
        try {
            action.run();
        } finally {
            EXPLICIT_LINK.set(previous);
        }
    }
}
