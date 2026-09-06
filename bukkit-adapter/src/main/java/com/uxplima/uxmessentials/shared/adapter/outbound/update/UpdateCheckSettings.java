package com.uxplima.uxmessentials.shared.adapter.outbound.update;

import java.time.Duration;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.jspecify.annotations.NullMarked;

/**
 * The update checker's typed settings, read once from the root {@code config.conf} {@code update-check} block at
 * wiring time. Off by default: the check makes an outbound network call, so an operator opts in explicitly.
 *
 * @param enabled whether the checker runs at all; {@code false} wires nothing and makes no network call
 * @param sourceUrl the release JSON endpoint (a {@code tag_name}/{@code version} field is read from the body)
 * @param notifyOpsOnJoin whether operators are shown the update notice when they join
 * @param interval the re-check cadence; {@link Duration#ZERO} checks once on enable only
 */
@NullMarked
public record UpdateCheckSettings(boolean enabled, String sourceUrl, boolean notifyOpsOnJoin, Duration interval) {

    private static final String ENABLED_PATH = "update-check.enabled";
    private static final String SOURCE_URL_PATH = "update-check.source-url";
    private static final String NOTIFY_PATH = "update-check.notify-ops-on-join";
    private static final String INTERVAL_HOURS_PATH = "update-check.interval-hours";

    private static final String DEFAULT_SOURCE_URL =
            "https://api.github.com/repos/UXPLIMA/uxmEssentials/releases/latest";
    private static final int DEFAULT_INTERVAL_HOURS = 12;

    public UpdateCheckSettings {
        Objects.requireNonNull(sourceUrl, "sourceUrl");
        Objects.requireNonNull(interval, "interval");
        if (interval.isNegative()) {
            throw new IllegalArgumentException("interval must not be negative");
        }
    }

    /** Read the {@code update-check} block from {@code config}, falling back to the shipped defaults per key. */
    public static UpdateCheckSettings from(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        boolean enabled = config.getBoolean(ENABLED_PATH, false);
        String sourceUrl = config.getString(SOURCE_URL_PATH, DEFAULT_SOURCE_URL);
        boolean notify = config.getBoolean(NOTIFY_PATH, true);
        int hours = Math.max(0, config.getInt(INTERVAL_HOURS_PATH, DEFAULT_INTERVAL_HOURS));
        return new UpdateCheckSettings(enabled, sourceUrl, notify, Duration.ofHours(hours));
    }

    /** Whether the checker re-checks on a recurring interval (vs once on enable when the interval is zero). */
    public boolean repeats() {
        return !interval.isZero();
    }
}
