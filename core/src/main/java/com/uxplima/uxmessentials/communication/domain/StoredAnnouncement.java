package com.uxplima.uxmessentials.communication.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import com.uxplima.uxmessentials.shared.display.BroadcastChannel;
import com.uxplima.uxmessentials.shared.display.ConditionParser;
import com.uxplima.uxmessentials.shared.display.DisplayCondition;

/**
 * The editable shape of a GUI-managed announcement as it lives in the store, distinct from the rendered
 * {@link Announcement} the announcer rotates. Where {@link Announcement} carries a <em>parsed</em>
 * {@link DisplayCondition} (ready to evaluate per viewer) and is unconditionally broadcast once selected, a stored
 * announcement carries the editable surface the editor reads and writes back: the raw operator-written
 * {@link #condition} string (so the editor can pull the {@code world:} / {@code permission:} parts out, change one,
 * and recompose them), an {@link #enabled} flag the announcer honours when merging the store set with the config
 * set, and the same lines/channels/sound/interval fields.
 *
 * <p>The id, lines, channels, condition string, sound, and interval are exactly what a row of the
 * {@code communication_announcement} table holds; {@link #toAnnouncement()} bridges to the domain
 * {@link Announcement} the announcer needs by parsing the condition fail-safe through {@link ConditionParser}. A
 * disabled stored announcement still round-trips through the store. It is the announcer's merge step, not the
 * record, that excludes a disabled one from the rotation, so toggling enabled never loses the rest of the edit.
 *
 * @param id a non-blank stable identifier, the table's primary key
 * @param lines the non-empty operator-authored broadcast templates, delivered together
 * @param channels the non-empty set of surfaces the lines are pushed to; defaults to {@code {CHAT}}
 * @param enabled whether the announcer includes this announcement in the rotation
 * @param condition the raw operator-written display-condition string ({@code ""} is unconditional)
 * @param sound an optional sound key played with the broadcast
 * @param intervalSeconds an optional per-announcement cadence override in seconds; absent rotates on the default
 */
public record StoredAnnouncement(
        String id,
        List<String> lines,
        Set<BroadcastChannel> channels,
        boolean enabled,
        String condition,
        Optional<String> sound,
        OptionalLong intervalSeconds) {

    public StoredAnnouncement {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("announcement id must not be blank");
        }
        lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("stored announcement '" + id + "' must declare at least one line");
        }
        Set<BroadcastChannel> requested = Set.copyOf(Objects.requireNonNull(channels, "channels"));
        channels = requested.isEmpty() ? Set.of(BroadcastChannel.CHAT) : requested;
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(sound, "sound");
        Objects.requireNonNull(intervalSeconds, "intervalSeconds");
    }

    /**
     * A fresh announcement with one placeholder line, enabled, on the CHAT channel and unconditional, the shape
     * the editor's create button stores for a new id before the operator edits any field.
     */
    public static StoredAnnouncement fresh(String id, String firstLine) {
        Objects.requireNonNull(firstLine, "firstLine");
        String line = firstLine.isBlank() ? " " : firstLine;
        return new StoredAnnouncement(
                id, List.of(line), Set.of(BroadcastChannel.CHAT), true, "", Optional.empty(), OptionalLong.empty());
    }

    /** This announcement with {@code enabled} replaced; every other field is carried unchanged. */
    public StoredAnnouncement withEnabled(boolean value) {
        return new StoredAnnouncement(id, lines, channels, value, condition, sound, intervalSeconds);
    }

    /** This announcement with its lines replaced; the new list must be non-empty (the constructor enforces it). */
    public StoredAnnouncement withLines(List<String> value) {
        return new StoredAnnouncement(id, value, channels, enabled, condition, sound, intervalSeconds);
    }

    /** This announcement with its channel set replaced (an empty set falls back to {@code {CHAT}}). */
    public StoredAnnouncement withChannels(Set<BroadcastChannel> value) {
        return new StoredAnnouncement(id, lines, value, enabled, condition, sound, intervalSeconds);
    }

    /** This announcement with its raw display-condition string replaced ({@code ""} clears the gate). */
    public StoredAnnouncement withCondition(String value) {
        return new StoredAnnouncement(id, lines, channels, enabled, value, sound, intervalSeconds);
    }

    /**
     * The rendered {@link Announcement} the announcer rotates, built by parsing the raw {@link #condition} through
     * {@link ConditionParser} (blank is unconditional, an unparseable one fails safe to never-show per the parser
     * contract). The interval override and the enabled flag are not part of {@link Announcement}: the interval is
     * threaded separately and the enabled flag gates the merge, so this carries only what the broadcast needs.
     */
    public Announcement toAnnouncement() {
        Optional<java.time.Duration> override = intervalSeconds.stream()
                .filter(seconds -> seconds > 0)
                .mapToObj(java.time.Duration::ofSeconds)
                .findFirst();
        return new Announcement(id, lines, ConditionParser.parse(condition), override, channels, sound, false);
    }
}
