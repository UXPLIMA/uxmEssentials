package com.uxplima.uxmessentials.teleport.domain;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.teleport.application.TeleportMessageKey;

/**
 * The modelled, expected failures of a teleport flow, the {@code E} arm of a {@code Result} a use case
 * returns instead of throwing. Each value carries the {@link TeleportMessageKey} the adapter renders to
 * the player, so the failure reason and its localized text never drift apart.
 *
 * <p>These are first-class outcomes, not exceptions: an active cooldown, a self-request, a target who
 * has teleports toggled off, an expired or already-resolved request. A genuinely exceptional fault (a
 * corrupt queue row, a null world handle) still throws.
 */
public enum TeleportError {

    /** The requester aimed a teleport request at themselves. */
    SELF_REQUEST(TeleportMessageKey.TPA_SELF),

    /** The target is offline or could not be resolved. */
    TARGET_OFFLINE(TeleportMessageKey.TPA_TARGET_OFFLINE),

    /** The target has {@code /tptoggle} on and refuses incoming requests. */
    TARGET_TOGGLED_OFF(TeleportMessageKey.TPA_TOGGLED_OFF),

    /** The target has blocked the requester via {@code /tpblock}. */
    REQUESTER_BLOCKED(TeleportMessageKey.TPA_BLOCKED),

    /** No pending request matched the accept/deny/cancel. */
    NO_PENDING_REQUEST(TeleportMessageKey.TPA_NONE_PENDING),

    /** The request's time-to-live elapsed before it was resolved. */
    REQUEST_EXPIRED(TeleportMessageKey.TPA_EXPIRED),

    /** A shared cooldown gate is still counting down. */
    ON_COOLDOWN(TeleportMessageKey.COOLDOWN_ACTIVE),

    /** No captured {@code /back} location exists for the player. */
    NO_BACK_LOCATION(TeleportMessageKey.BACK_NONE),

    /** The captured {@code /back} location is a death point and death-back is not permitted. */
    BACK_ON_DEATH_DENIED(TeleportMessageKey.BACK_DEATH_DENIED),

    /** The captured death point is too recent: the post-death {@code /back} delay has not elapsed. */
    BACK_ON_DEATH_DELAY(TeleportMessageKey.BACK_DEATH_DELAY),

    /** The world has no random-teleport queue and no fallback world is configured. */
    RTP_WORLD_DISALLOWED(TeleportMessageKey.RTP_DISALLOWED),

    /** The safe-location queue is momentarily empty and no urgent fallback produced a location. */
    RTP_NO_SAFE_LOCATION(TeleportMessageKey.RTP_NO_LOCATION),

    /** The player cannot afford the configured random-teleport cost, rejected before any search runs. */
    RTP_CANT_AFFORD(TeleportMessageKey.RTP_CANT_AFFORD),

    /** {@code /rtp biome <biome>} named a biome the server's registry does not know. */
    RTP_BIOME_UNKNOWN(TeleportMessageKey.RTP_BIOME_UNKNOWN),

    /** {@code /rtp biome <biome>} found no landing in the target biome within the search budget. */
    RTP_BIOME_NOT_FOUND(TeleportMessageKey.RTP_BIOME_NOT_FOUND),

    /** No spawn (named, mirrored, or default) resolves for the requested world. */
    SPAWN_UNRESOLVED(TeleportMessageKey.SPAWN_UNRESOLVED),

    /** The mover is jailed; the moderation context's jail gate blocks every self-initiated teleport. */
    JAILED(TeleportMessageKey.JAILED),

    /** An installed combat plugin has the mover tagged, so they may not teleport out of the fight. */
    COMBAT_TAGGED(TeleportMessageKey.COMBAT_TAGGED),

    /** Another plugin refused the teleport through the developer API. Nothing moved. */
    VETOED(SharedMessageKey.COMMON_ACTION_VETOED);

    // Typed as the MessageKey interface rather than this context's own enum: a veto is refused for the same reason
    // in every context, so it renders one shared key rather than twenty near-identical ones.
    private final MessageKey messageKey;

    TeleportError(MessageKey messageKey) {
        this.messageKey = messageKey;
    }

    /** The catalog key the adapter renders for this failure. */
    public MessageKey messageKey() {
        return messageKey;
    }
}
