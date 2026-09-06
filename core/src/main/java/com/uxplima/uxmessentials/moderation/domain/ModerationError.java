package com.uxplima.uxmessentials.moderation.domain;

import com.uxplima.uxmessentials.moderation.application.ModerationMessageKey;

/**
 * The modelled, expected failures of a moderation action, the {@code E} arm of a {@code Result} a use case
 * returns instead of throwing. Each value carries the {@link ModerationMessageKey} the adapter renders to the
 * actor, so the failure reason and its localized text never drift apart (the same pattern the teleport
 * context's {@code TeleportError} uses).
 *
 * <p>These are first-class outcomes, not exceptions: a target with the exempt node, a malformed duration, an
 * unknown jail, an {@code /unmute} of a player who is not muted. A genuinely exceptional fault (a corrupt
 * row, a null world handle) still throws.
 */
public enum ModerationError {

    /** The target holds the exempt node and cannot be sanctioned by lower staff. */
    TARGET_EXEMPT(ModerationMessageKey.TARGET_EXEMPT),

    /** The supplied duration string did not parse to a positive span. */
    BAD_DURATION(ModerationMessageKey.BAD_DURATION),

    /** The target name/UUID did not resolve to a known player. */
    UNKNOWN_TARGET(ModerationMessageKey.UNKNOWN_TARGET),

    /** The named jail is not configured in {@code moderation.conf}. */
    UNKNOWN_JAIL(ModerationMessageKey.JAIL_UNKNOWN),

    /** The target is already muted (a re-mute without a changed duration is a no-op). */
    ALREADY_MUTED(ModerationMessageKey.MUTE_ALREADY),

    /** The {@code /unmute} target was not muted. */
    NOT_MUTED(ModerationMessageKey.UNMUTE_NOT_MUTED),

    /** The {@code /unjail} target was not jailed. */
    NOT_JAILED(ModerationMessageKey.UNJAIL_NOT_JAILED),

    /** The {@code /unbanip} address was not banned. */
    NOT_IP_BANNED(ModerationMessageKey.UNBANIP_NOT_BANNED),

    /** The {@code /unban} target was not banned. */
    NOT_BANNED(ModerationMessageKey.BAN_NOT_BANNED),

    /** The {@code /punish} template name is not configured under {@code moderation.conf} {@code templates}. */
    UNKNOWN_TEMPLATE(ModerationMessageKey.TEMPLATE_UNKNOWN);

    private final ModerationMessageKey messageKey;

    ModerationError(ModerationMessageKey messageKey) {
        this.messageKey = messageKey;
    }

    /** The catalog key the adapter renders for this failure. */
    public ModerationMessageKey messageKey() {
        return messageKey;
    }
}
