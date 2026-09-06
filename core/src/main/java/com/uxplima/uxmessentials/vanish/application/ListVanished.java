package com.uxplima.uxmessentials.vanish.application;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.application.port.NetworkVanishStore;
import com.uxplima.uxmessentials.vanish.application.port.VanishLevelResolver;
import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import com.uxplima.uxmessentials.vanish.domain.VanishState;

/**
 * {@code /vanish list}: the currently-vanished players a caller is permitted to see. The list is scoped to the
 * caller's see level and the pure {@code VanishState#canSee} rule. A vanished player hidden <em>above</em> the
 * caller's see level does not appear, exactly as they are invisible in the world. The chosen semantics: a staffer
 * only ever learns about the vanished players they could already see, so {@code /vanish list} never leaks the
 * presence of a higher-level admin to a lower-level moderator. A vanished caller sees themselves in the list
 * (they always clear their own visibility), which reads as "you are vanished".
 *
 * <p>Returns raw uuids sorted deterministically; the adapter resolves display names on the command thread. The
 * caller's see level is resolved from their permissions through the {@link VanishLevelResolver}, the same seam the
 * store and view read, so the list agrees with what the caller actually sees.
 *
 * <p>The roster is <em>network-wide</em>: the locally-hidden players (the {@link VanishStore}) are merged with the
 * cluster's network-vanish view (the {@link NetworkVanishStore}) so a caller sees hidden players on other backends
 * too, still scoped to their see level. A player present in both wins with their local level. With {@code cross-server}
 * off the network view is {@link NetworkVanishStore#empty() empty}, so the list is exactly the local roster as before.
 */
public final class ListVanished {

    private final VanishStore store;
    private final VanishLevelResolver levels;
    private final NetworkVanishStore network;

    public ListVanished(VanishStore store, VanishLevelResolver levels, NetworkVanishStore network) {
        this.store = Objects.requireNonNull(store, "store");
        this.levels = Objects.requireNonNull(levels, "levels");
        this.network = Objects.requireNonNull(network, "network");
    }

    /** The vanished players {@code caller} may see across the network, sorted by uuid for a stable order. */
    public List<UUID> visibleTo(PlayerRef caller) {
        Objects.requireNonNull(caller, "caller");
        int seeLevel = levels.seeLevel(caller);
        VanishState state = merged();
        return state.vanishedIds().stream()
                .filter(id -> state.canSee(caller.uuid(), id, seeLevel))
                .sorted(Comparator.comparing(UUID::toString))
                .toList();
    }

    /** The local roster with the network-wide view folded in; a local entry takes precedence over a network one. */
    private VanishState merged() {
        Map<UUID, VanishLevel> combined = new HashMap<>(network.levels());
        combined.putAll(store.snapshot().vanished());
        return new VanishState(combined);
    }
}
