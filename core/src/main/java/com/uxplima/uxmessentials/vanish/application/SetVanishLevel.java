package com.uxplima.uxmessentials.vanish.application;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.application.port.VanishBuffs;
import com.uxplima.uxmessentials.vanish.application.port.VanishBus;
import com.uxplima.uxmessentials.vanish.application.port.VanishLevelResolver;
import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import com.uxplima.uxmessentials.vanish.application.port.VanishView;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;

/**
 * Re-resolves a vanished player's use level from their permissions and re-applies their visibility across every viewer.
 * Because levels are derived from permissions rather than stored, a promotion or demotion only takes effect once this
 * runs. The natural trigger is a (re)join, mirroring PremiumVanish's "players need to rejoin for layered permission
 * changes to register". A player who is not currently vanished is left untouched.
 *
 * <p>Re-applying goes through the {@link VanishView#hide} reconciliation, which both hides the player from viewers who
 * no longer clear the new level and reveals them to viewers who now do, so a level change settles in one pass without a
 * blanket reveal flash. The stored level is updated first so every {@code canSee} reader agrees with the view. The
 * configured buffs are re-applied too, so a still-vanished player who rejoins is topped back up to night vision and
 * flight rather than reappearing plain (this becomes load-bearing once join-vanished / cross-server persistence lands).
 *
 * <p>Because the natural trigger is a (re)join, the re-applied level is also announced to the peer backends through the
 * {@link VanishBus}: a player who hops in and is re-hidden here re-broadcasts their now-local level, so every backend's
 * network-vanish view converges on where the player actually is. With the bus {@link VanishBus#disabled() disabled} the
 * publish is a no-op and this stays the single-server re-apply.
 */
public final class SetVanishLevel {

    private final VanishStore store;
    private final VanishView view;
    private final VanishLevelResolver levels;
    private final VanishBuffs buffs;
    private final VanishBus bus;

    public SetVanishLevel(
            VanishStore store, VanishView view, VanishLevelResolver levels, VanishBuffs buffs, VanishBus bus) {
        this.store = Objects.requireNonNull(store, "store");
        this.view = Objects.requireNonNull(view, "view");
        this.levels = Objects.requireNonNull(levels, "levels");
        this.buffs = Objects.requireNonNull(buffs, "buffs");
        this.bus = Objects.requireNonNull(bus, "bus");
    }

    /**
     * Re-resolve {@code who}'s use level and re-apply their visibility and buffs if they are vanished; returns the
     * applied level, or empty when {@code who} is not vanished (nothing to re-apply).
     */
    public Optional<VanishLevel> reapply(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        if (!store.isVanished(who.uuid())) {
            return Optional.empty();
        }
        VanishLevel level = levels.useLevel(who);
        store.vanish(who.uuid(), level);
        view.hide(who, level);
        buffs.apply(who);
        bus.publish(VanishSync.vanished(who, level));
        return Optional.of(level);
    }
}
