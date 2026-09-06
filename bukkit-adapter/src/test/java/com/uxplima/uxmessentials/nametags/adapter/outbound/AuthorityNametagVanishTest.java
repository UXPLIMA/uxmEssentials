package com.uxplima.uxmessentials.nametags.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.nametags.application.port.NametagVanish;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.adapter.outbound.InMemoryVanishStore;
import com.uxplima.uxmessentials.vanish.application.port.VanishLevelResolver;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import org.junit.jupiter.api.Test;

/**
 * The migrated nametags vanish gate reads the vanish {@code VanishStore} authority directly: a viewer sees a wearer's
 * nametag when the wearer is not vanished, or the viewer's see level clears the wearer's use level, and cannot when the
 * wearer is vanished above the viewer's see level: the inverse polarity of the messaging gate over the same one state.
 */
class AuthorityNametagVanishTest {

    private final InMemoryVanishStore store = new InMemoryVanishStore();
    private final FakeLevels levels = new FakeLevels();
    private final PlayerRef viewer = new PlayerRef(UUID.randomUUID(), "Viewer");
    private final PlayerRef wearer = new PlayerRef(UUID.randomUUID(), "Wearer");

    @Test
    void everyoneSeesANonVanishedWearersNametag() {
        NametagVanish vanish = new AuthorityNametagVanish(store, levels);

        assertThat(vanish.canSee(viewer, wearer)).isTrue();
    }

    @Test
    void aVanishedWearersNametagIsHiddenFromAViewerBelowItsUseLevel() {
        store.vanish(wearer.uuid(), VanishLevel.DEFAULT);
        NametagVanish vanish = new AuthorityNametagVanish(store, levels);

        assertThat(vanish.canSee(viewer, wearer)).isFalse();
    }

    @Test
    void aVanishedWearersNametagIsVisibleToAViewerWhoseSeeLevelClearsIt() {
        store.vanish(wearer.uuid(), VanishLevel.DEFAULT);
        levels.seeLevels.put(viewer.uuid(), 1);
        NametagVanish vanish = new AuthorityNametagVanish(store, levels);

        assertThat(vanish.canSee(viewer, wearer)).isTrue();
    }

    private static final class FakeLevels implements VanishLevelResolver {
        private final Map<UUID, Integer> seeLevels = new HashMap<>();

        @Override
        public VanishLevel useLevel(PlayerRef who) {
            return VanishLevel.DEFAULT;
        }

        @Override
        public int seeLevel(PlayerRef who) {
            return seeLevels.getOrDefault(who.uuid(), 0);
        }
    }
}
