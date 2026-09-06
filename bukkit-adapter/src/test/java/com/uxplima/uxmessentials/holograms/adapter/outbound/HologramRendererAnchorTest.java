package com.uxplima.uxmessentials.holograms.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.holograms.application.port.LinkedNpcLocator;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage of the renderer's anchor resolution, the pure {@link HologramRenderer#anchorFor} seam that
 * decides where a hologram renders: at its linked NPC's head, or (fail-soft) at its own stored location.
 */
class HologramRendererAnchorTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position OWN = Position.of(WORLD, 1, 64, 1);

    @Test
    void anUnlinkedHologramAnchorsAtItsOwnLocation() {
        Position anchor = HologramRenderer.anchorFor(hologram(null), new FakeLocator(Map.of()));

        assertThat(anchor).isEqualTo(OWN);
    }

    @Test
    void aLinkedHologramAnchorsAboveTheNpc() {
        Position npc = Position.of(WORLD, 10, 70, -5);

        Position anchor = HologramRenderer.anchorFor(hologram("guide"), new FakeLocator(Map.of("guide", npc)));

        assertThat(anchor.world()).isEqualTo(WORLD);
        assertThat(anchor.x()).isEqualTo(10.0);
        assertThat(anchor.z()).isEqualTo(-5.0);
        assertThat(anchor.y()).isEqualTo(70.0 + HologramRenderer.LINKED_NPC_Y_OFFSET, offset(1.0e-9));
    }

    @Test
    void aLinkedHologramWhoseNpcIsMissingFallsBackToItsOwnLocation() {
        Position anchor = HologramRenderer.anchorFor(hologram("ghost"), new FakeLocator(Map.of()));

        assertThat(anchor).isEqualTo(OWN);
    }

    private static Hologram hologram(@Nullable String linkedNpc) {
        Hologram base = Hologram.create(
                HologramName.of("spawn"), OWN, List.of(new HologramLine("line")), Instant.ofEpochMilli(1_000));
        return linkedNpc == null ? base : base.linkedTo(linkedNpc);
    }

    private record FakeLocator(Map<String, Position> positions) implements LinkedNpcLocator {
        @Override
        public Optional<Position> locate(String npcName) {
            return Optional.ofNullable(positions.get(npcName));
        }
    }
}
