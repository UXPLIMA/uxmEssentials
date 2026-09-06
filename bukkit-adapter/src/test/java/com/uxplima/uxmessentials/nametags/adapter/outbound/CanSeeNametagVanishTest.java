package com.uxplima.uxmessentials.nametags.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.nametags.application.port.NametagVanish;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Covers {@link CanSeeNametagVanish}: it answers through Bukkit's {@code Player#canSee} graph, so with the presence
 * module absent (no one is hidden) it degrades to "everyone can see everyone". The renderer then culls nobody for
 * vanish. A miss on the live-player lookup answers {@code false} (not visible) so a since-departed player is never
 * re-added to a viewer set.
 */
class CanSeeNametagVanishTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void degradesToEveryoneVisibleWhenNothingIsHidden() {
        PlayerMock viewer = server.addPlayer();
        PlayerMock wearer = server.addPlayer();
        NametagVanish vanish = new CanSeeNametagVanish();

        // No vanish applied: canSee is true both ways, so the nametag is visible to everyone.
        assertThat(vanish.canSee(BukkitRefs.toRef(viewer), BukkitRefs.toRef(wearer)))
                .isTrue();
    }

    @Test
    void hidesAWearerHiddenFromTheViewer() {
        PlayerMock viewer = server.addPlayer();
        PlayerMock wearer = server.addPlayer();
        // Simulate presence vanishing the wearer from this viewer (the canSee graph the presence applier drives).
        viewer.hideEntity(MockBukkit.createMockPlugin(), wearer);
        NametagVanish vanish = new CanSeeNametagVanish();

        assertThat(vanish.canSee(BukkitRefs.toRef(viewer), BukkitRefs.toRef(wearer)))
                .isFalse();
    }

    @Test
    void anOfflinePlayerIsTreatedAsNotVisible() {
        PlayerMock viewer = server.addPlayer();
        PlayerMock wearer = server.addPlayer();
        com.uxplima.uxmessentials.shared.domain.PlayerRef wearerRef = BukkitRefs.toRef(wearer);
        wearer.disconnect();
        NametagVanish vanish = new CanSeeNametagVanish();

        // The wearer is offline, so they cannot be resolved to a live Player; the gate answers not-visible.
        assertThat(vanish.canSee(BukkitRefs.toRef(viewer), wearerRef)).isFalse();
    }
}
