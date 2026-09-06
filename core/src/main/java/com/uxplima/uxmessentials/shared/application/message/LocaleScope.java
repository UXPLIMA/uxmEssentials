package com.uxplima.uxmessentials.shared.application.message;

import java.util.Locale;
import java.util.Objects;

/**
 * The request-scoped carrier for the requesting player's resolved locale (docs/13-i18n §5).
 *
 * <p>Most messages are sent synchronously on the command thread, where the viewer's locale is trivially
 * available. The hard case is the deferred message. A {@code /baltop} page rendered after an off-tick
 * DB read, an {@code /rtp} confirmation after an off-thread safe-location search, where the thread that
 * finally calls {@code messages.resolve(...)} is a worker that knows nothing about the requester. The
 * rule is: bind the resolved locale once at the command boundary so the resolver can read it on the
 * on-thread portion of the handler rather than threading a {@code Locale} through every signature.
 *
 * <p>A plain {@link ThreadLocal} backs the binding (not {@code ScopedValue}, which is a Java-21 preview
 * API and would version-lock the compiled classes to a single JDK). {@link #runWith} sets the value for
 * the duration of the call and restores the previous binding in a {@code finally} block, so nothing
 * leaks across a pooled worker thread. When a handler hops to a different executor the binding does not
 * follow, and that is fine: the locale resolver falls back to the recipient's own client locale, which
 * is the correct language for that viewer anyway. The bound value is an immutable {@link Locale};
 * capturing the client locale off the Bukkit API happens once on the region thread at the boundary.
 */
public final class LocaleScope {

    private static final ThreadLocal<Locale> CURRENT = new ThreadLocal<>();

    private LocaleScope() {}

    /**
     * Run {@code body} with {@code locale} bound as the current request locale for the duration of the
     * call. The previous binding (if any) is restored when {@code body} returns, so nested binds and
     * pooled threads never leak a stale locale.
     */
    public static void runWith(Locale locale, Runnable body) {
        Objects.requireNonNull(locale, "locale");
        Objects.requireNonNull(body, "body");
        Locale previous = CURRENT.get();
        CURRENT.set(locale);
        try {
            body.run();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    /** The bound locale if a boundary set one on this thread, otherwise {@code fallback}. Never null. */
    public static Locale orElse(Locale fallback) {
        Objects.requireNonNull(fallback, "fallback");
        Locale current = CURRENT.get();
        return current != null ? current : fallback;
    }
}
