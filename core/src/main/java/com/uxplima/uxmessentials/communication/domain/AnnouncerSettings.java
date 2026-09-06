package com.uxplima.uxmessentials.communication.domain;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The operator's in-game override of the two global announcer settings the file {@code announcer.conf} sets: the
 * default interval between announcements and the minimum-online-players gate. Each is optional, an absent override
 * means "defer to the file default", so a fresh install (nothing set in the settings screen) behaves exactly as
 * before, reading both globals from the file. When a value is present it wins over the file default.
 *
 * <p>This is the editable shape the {@code /announce} editor's settings screen reads and writes, persisted durably
 * by the {@code AnnouncerSettingsStore} so it survives a restart and a world rollback. It is pure value data: the
 * announcer's merge step calls {@link #applyTo(AnnouncerConfig)} to fold the present overrides into the file config
 * before the rotation reads it, so the effective interval and gate reflect the screen without a reload.
 *
 * @param interval the override default interval, or empty to use the file's {@code default-interval-seconds}
 * @param minOnlinePlayers the override min-players gate, or empty to use the file's {@code min-players}
 */
public record AnnouncerSettings(Optional<Duration> interval, OptionalInt minOnlinePlayers) {

    public AnnouncerSettings {
        Objects.requireNonNull(interval, "interval");
        Objects.requireNonNull(minOnlinePlayers, "minOnlinePlayers");
        interval.ifPresent(value -> {
            if (value.isZero() || value.isNegative()) {
                throw new IllegalArgumentException("announcer interval override must be positive: " + value);
            }
        });
        minOnlinePlayers.ifPresent(value -> {
            if (value < 0) {
                throw new IllegalArgumentException("announcer min-players override must not be negative: " + value);
            }
        });
    }

    /** No override set: both globals defer to the file. The shape a fresh, never-edited settings row reads as. */
    public static AnnouncerSettings unset() {
        return new AnnouncerSettings(Optional.empty(), OptionalInt.empty());
    }

    /** This settings record with the interval override replaced by {@code seconds} ({@code <= 0} clears it). */
    public AnnouncerSettings withIntervalSeconds(long seconds) {
        Optional<Duration> next = seconds > 0 ? Optional.of(Duration.ofSeconds(seconds)) : Optional.empty();
        return new AnnouncerSettings(next, minOnlinePlayers);
    }

    /** This settings record with the min-players gate override replaced by {@code players} ({@code < 0} clears it). */
    public AnnouncerSettings withMinOnlinePlayers(int players) {
        OptionalInt next = players >= 0 ? OptionalInt.of(players) : OptionalInt.empty();
        return new AnnouncerSettings(interval, next);
    }

    /**
     * The same {@code config} with this record's present overrides folded in: the override interval replaces the
     * file default when set, the override gate replaces the file gate when set, and the ordering and announcement
     * list are carried through unchanged. With nothing set this returns an equivalent config, so the file remains
     * authoritative until the operator sets an override in the settings screen.
     */
    public AnnouncerConfig applyTo(AnnouncerConfig config) {
        Objects.requireNonNull(config, "config");
        Duration effectiveInterval = interval.orElse(config.defaultInterval());
        int effectiveGate = minOnlinePlayers.orElse(config.minOnlinePlayers());
        return new AnnouncerConfig(effectiveInterval, effectiveGate, config.ordering(), config.announcements());
    }
}
