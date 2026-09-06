package com.uxplima.uxmessentials.playerstate.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.playerstate.application.port.ClearInventoryPreferences;
import com.uxplima.uxmessentials.playerstate.application.port.PlayerEffects;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /clearinventory} (aliases {@code /ci}, {@code /clear}) {@code [player]}: empty a player's inventory. A
 * live-only effect through the {@link PlayerEffects} port (no persisted flag, no domain event), then a
 * confirmation to the actor and, for a staff target, to the subject.
 *
 * <p>A player who has turned on the {@code /clearinventoryconfirmtoggle} preference gets a two-step self clear:
 * the first {@code /clearinventory} stages a short-lived pending confirmation and asks them to repeat the
 * command, and a second one inside {@link #CONFIRM_WINDOW} performs the clear. The pending stamp is in-memory
 * and times out on its own, so a forgotten prompt never clears anything. A staff clear of another player
 * ({@code .others}) is never gated: only the clearer's own inventory is protected.
 */
public final class ClearInventory {

    /** How long a staged self-clear confirmation stays valid before it must be re-issued. */
    static final Duration CONFIRM_WINDOW = Duration.ofSeconds(15);

    private final PlayerEffects effects;
    private final Notifier notifier;
    private final ClearInventoryPreferences preferences;
    private final Clock clock;
    private final ConcurrentHashMap<UUID, Instant> pendingUntil = new ConcurrentHashMap<>();

    public ClearInventory(
            PlayerEffects effects, Notifier notifier, ClearInventoryPreferences preferences, Clock clock) {
        this.effects = Objects.requireNonNull(effects, "effects");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.preferences = Objects.requireNonNull(preferences, "preferences");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Clear {@code who}'s own inventory, honouring their confirmation preference. */
    public void clear(PlayerRef who) {
        clearFor(who, who);
    }

    /** Clear {@code subject}'s inventory on behalf of {@code actor}. */
    public void clearFor(PlayerRef actor, PlayerRef subject) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(subject, "subject");
        if (actor.equals(subject) && requiresConfirmation(actor)) {
            return;
        }
        effects.clearInventory(subject);
        if (actor.equals(subject)) {
            notifier.send(actor, PlayerstateMessageKey.INVENTORY_CLEARED);
            return;
        }
        notifier.send(actor, PlayerstateMessageKey.INVENTORY_CLEARED_OTHER, Map.of("player", subject.name()));
        notifier.send(subject, PlayerstateMessageKey.INVENTORY_CLEARED);
    }

    /**
     * For a self clear, stage and prompt when the preference is on and nothing is pending; consume a live
     * pending stamp on the repeat. Returns {@code true} when the caller must stop (a prompt was just sent),
     * {@code false} when the clear should proceed.
     */
    private boolean requiresConfirmation(PlayerRef who) {
        if (!preferences.confirmEnabled(who)) {
            return false;
        }
        Instant now = clock.instant();
        Instant deadline = pendingUntil.remove(who.uuid());
        if (deadline != null && now.isBefore(deadline)) {
            return false; // a live confirmation, let the clear run
        }
        pendingUntil.put(who.uuid(), now.plus(CONFIRM_WINDOW));
        notifier.send(who, PlayerstateMessageKey.INVENTORY_CLEAR_CONFIRM);
        return true;
    }
}
