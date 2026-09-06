package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime;

import java.time.Duration;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.RefreshSpec;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;

/**
 * Starts the single repeating task that re-renders an open menu. A menu whose spec disables refresh starts no
 * task at all, so a static menu costs nothing beyond the open. When refresh is enabled the task runs on the
 * global region thread at the spec's cadence, and its cancel handle is parked on the holder, the holder owns
 * teardown, so closing or quitting the menu cancels exactly this task and never leaks a timer.
 */
public final class MenuRefresh {

    private MenuRefresh() {}

    /** Schedule the holder's refresh, if its spec enables one; {@code reRender} is the caller's redraw step. */
    public static void start(MenuHolder holder, Scheduler scheduler, Runnable reRender) {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(reRender, "reRender");
        RefreshSpec r = holder.spec().refresh();
        if (!r.enabled()) {
            return;
        }
        Duration period = Duration.ofMillis(Math.max(1, r.intervalTicks()) * 50L);
        AutoCloseable handle = scheduler.repeatGlobal(reRender, period, period);
        holder.setRefreshHandle(() -> {
            try {
                handle.close();
            } catch (Exception ignored) {
                // cancelling a finished task is harmless
            }
        });
    }
}
