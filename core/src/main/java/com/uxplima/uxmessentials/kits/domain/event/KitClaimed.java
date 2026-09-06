package com.uxplima.uxmessentials.kits.domain.event;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A kit was claimed, a player ran {@code /kit <name>} (or a staff member gave it via {@code /kit <name>
 * <player>}) and the items were granted. The {@code recipient} is the player who received the kit; the
 * {@code actor} is whoever triggered the claim, which equals the recipient for a self-claim and differs for
 * a staff give. This is the event the adapter bridges to Bukkit and the audit log keys its {@code kit_give}
 * line off.
 *
 * @param kit the id of the kit that was claimed
 * @param recipient the player who received the kit's items
 * @param actor the player who triggered the claim (equals {@code recipient} for a self-claim)
 * @param at when the claim happened
 */
public record KitClaimed(KitId kit, PlayerRef recipient, PlayerRef actor, Instant at) implements KitEvent {

    public KitClaimed {
        Objects.requireNonNull(kit, "kit");
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(at, "at");
    }
}
