package com.uxplima.uxmessentials.communication.domain;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.uxplima.uxmessentials.shared.display.BroadcastChannel;
import com.uxplima.uxmessentials.shared.display.DisplayCondition;

/**
 * A single rotating announcement: a named bundle of operator-authored {@link #lines} (MiniMessage templates)
 * delivered together each time the announcer selects it. Beyond the raw text it carries the gating and delivery
 * choices the announcer applies per viewer and per broadcast:
 *
 * <ul>
 *   <li>{@link #condition}. A {@link DisplayCondition} evaluated per recipient so an announcement can be scoped
 *       to a world, a permission, a gamemode, or any combination; the unconditional default shows to everyone.
 *   <li>{@link #intervalOverride}, an optional per-announcement cadence; when present the adapter schedules this
 *       announcement on its own timer rather than the config-wide default (the cadence is an adapter scheduling
 *       concern: this record only carries the value).
 *   <li>{@link #channels}. The surfaces the lines are pushed to (chat, action bar, title, …); never empty, it
 *       defaults to {@link BroadcastChannel#CHAT} when an operator leaves it unset.
 *   <li>{@link #sound}: an optional sound key played alongside the broadcast.
 *   <li>{@link #centered}: whether the adapter should centre each line in chat.
 * </ul>
 *
 * <p>The lines are operator content rendered through MiniMessage later, never {@code MessageKey}s and never
 * parity-checked. The record is pure value data with no Bukkit dependency: the cursor selects which announcement
 * fires and the adapter renders, gates, and delivers it.
 *
 * @param id a non-blank stable identifier (used by {@code /announce preview <id>} and per-announcement scheduling)
 * @param lines the non-empty operator-authored broadcast templates, delivered together
 * @param condition the per-viewer show/hide gate; {@link DisplayCondition#always()} for an unconditional broadcast
 * @param intervalOverride an optional cadence overriding the config-wide default for this announcement
 * @param channels the non-empty set of surfaces the lines are pushed to; defaults to {@code {CHAT}}
 * @param sound an optional sound key played with the broadcast
 * @param centered whether each line is centred in chat
 */
public record Announcement(
        String id,
        List<String> lines,
        DisplayCondition condition,
        Optional<Duration> intervalOverride,
        Set<BroadcastChannel> channels,
        Optional<String> sound,
        boolean centered) {

    public Announcement {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("announcement id must not be blank");
        }
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("announcement '" + id + "' must declare at least one line");
        }
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(intervalOverride, "intervalOverride");
        Objects.requireNonNull(sound, "sound");
        Set<BroadcastChannel> requested = Set.copyOf(Objects.requireNonNull(channels, "channels"));
        channels = requested.isEmpty() ? Set.of(BroadcastChannel.CHAT) : requested;
    }
}
