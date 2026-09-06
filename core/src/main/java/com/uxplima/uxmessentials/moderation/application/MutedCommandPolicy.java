package com.uxplima.uxmessentials.moderation.application;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * The pure rule that closes the mute-bypass via chat-adjacent commands: a muted player can still type
 * {@code /me}, {@code /msg}, {@code /mail} and friends to broadcast text the mute was meant to silence, so an
 * operator lists those command names in {@code moderation.muted-blocked-commands} and this policy answers
 * "may a muted player run this command label?" against that list. The messaging mute gate already blocks the
 * use cases this plugin owns ({@code /msg}, {@code /mail}, {@code /helpop}); this policy is the wider net that
 * also catches third-party and vanilla commands an operator names.
 *
 * <p>The match is on the bare command label only. The leading slash and any arguments are stripped by the
 * caller, and is case-insensitive, since command dispatch on Paper is. The domain never reads the live mute
 * state or the wall clock; the adapter consults {@link com.uxplima.uxmessentials.moderation.domain.MuteState}
 * first and only asks this policy whether the label is one of the blocked ones, so the rule stays a pure
 * function of its configured set.
 */
public final class MutedCommandPolicy {

    private final Set<String> blocked;

    /**
     * @param blockedCommands the configured command labels a muted player may not run, in any case; a leading
     *     slash on an entry is tolerated and stripped so {@code "/me"} and {@code "me"} are equivalent
     */
    public MutedCommandPolicy(Set<String> blockedCommands) {
        Objects.requireNonNull(blockedCommands, "blockedCommands");
        this.blocked = blockedCommands.stream()
                .filter(Objects::nonNull)
                .map(MutedCommandPolicy::normalize)
                .filter(label -> !label.isEmpty())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** True when {@code label} is a command a muted player is forbidden to run. */
    public boolean isBlocked(String label) {
        Objects.requireNonNull(label, "label");
        return blocked.contains(normalize(label));
    }

    /** True when no command is blocked, so the adapter can skip the mute lookup entirely. */
    public boolean isEmpty() {
        return blocked.isEmpty();
    }

    private static String normalize(String label) {
        String trimmed = label.trim();
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }
}
