package com.uxplima.uxmessentials.nametags.adapter.outbound;

import java.util.Objects;

import com.uxplima.uxmessentials.nametags.application.port.NametagVanish;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.application.port.VanishLevelResolver;
import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link NametagVanish} implementation, reading the vanish context's single {@link VanishStore} authority directly.
 * A {@code viewer} may see a {@code wearer}'s nametag when the wearer is not vanished, or the viewer's see level clears
 * the wearer's use level. The pure {@code VanishState#canSee} rule the messaging and staff surfaces read too (with the
 * viewer's see level resolved through the shared {@link VanishLevelResolver}), so the nametag cull tracks exactly the
 * same layered visibility every other consumer sees. The renderer builds an eligible-viewer set, so the polarity here
 * is the natural "can this viewer see the wearer?", the inverse of messaging's {@code isHiddenFrom}.
 *
 * <p>Bound only when the vanish module is enabled; with vanish off the wiring binds {@link NametagVanish#ALWAYS_VISIBLE}
 * instead, so the nametag renderer degrades to "everyone can see everyone" rather than failing.
 */
@NullMarked
public final class AuthorityNametagVanish implements NametagVanish {

    private final VanishStore store;
    private final VanishLevelResolver levels;

    public AuthorityNametagVanish(VanishStore store, VanishLevelResolver levels) {
        this.store = Objects.requireNonNull(store, "store");
        this.levels = Objects.requireNonNull(levels, "levels");
    }

    @Override
    public boolean canSee(PlayerRef viewer, PlayerRef wearer) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(wearer, "wearer");
        return store.snapshot().canSee(viewer.uuid(), wearer.uuid(), levels.seeLevel(viewer));
    }
}
