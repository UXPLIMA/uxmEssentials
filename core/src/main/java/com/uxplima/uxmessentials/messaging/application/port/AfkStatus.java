package com.uxplima.uxmessentials.messaging.application.port;

import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Soft-coupled query to the presence context: whether a {@code who} is currently AFK, and if so the reason
 * they set. Messaging uses this to add a courtesy notice, a sender who {@code /msg}s an AFK player is told
 * the target is away (and why), so they know not to expect an immediate reply. AFK is a notice, never a
 * block: the message still delivers (docs/02-concurrency.md §6.9: presence integrates with messaging).
 *
 * <p>The coupling is soft: when the presence module is disabled (or has not yet landed) the wiring binds
 * {@link #NEVER}, so messaging degrades to "no one is AFK" rather than failing, the same
 * degrade-when-the-other-module-is-off pattern {@link MutePolicy} uses against moderation and
 * {@link VanishVisibility} uses against vanish.
 */
public interface AfkStatus {

    /** A status under which no one is ever AFK: the binding when presence is disabled. */
    AfkStatus NEVER = who -> Optional.empty();

    /**
     * The AFK reason for {@code who}, or empty when they are not AFK (or presence is disabled). An AFK player
     * with no reason set still returns a present (possibly blank) value, so the caller can tell "AFK, no
     * reason" apart from "not AFK".
     */
    Optional<String> afkReasonOf(PlayerRef who);
}
