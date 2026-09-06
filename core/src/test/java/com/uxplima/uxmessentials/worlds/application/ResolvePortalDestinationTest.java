package com.uxplima.uxmessentials.worlds.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;

import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.PortalDestination;
import com.uxplima.uxmessentials.worlds.domain.PortalKind;
import com.uxplima.uxmessentials.worlds.domain.WorldEnvironment;
import com.uxplima.uxmessentials.worlds.domain.WorldGenType;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldProperties;
import com.uxplima.uxmessentials.worlds.domain.WorldProperty;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import org.junit.jupiter.api.Test;

/**
 * {@code ResolvePortalDestination} maps a portal entered in a source world to the exit world configured by that
 * world's per-kind link property, scaling the horizontal coordinates by the vanilla rule between the two
 * environments. It resolves to nothing whenever the source is unknown, the link is unset, the link names a world
 * that is not registered, or the link is syntactically invalid. A corrupt or missing link can never carry a
 * teleport to a phantom world. The repository is an in-memory fake so the resolution is asserted without Bukkit.
 */
class ResolvePortalDestinationTest {

    private static final WorldName OVERWORLD = WorldName.of("overworld");
    private static final WorldName NETHER = WorldName.of("thenether");

    private static WorldSpec netherSpec() {
        return new WorldSpec(
                WorldEnvironment.NETHER,
                WorldGenType.NORMAL,
                Optional.empty(),
                Optional.empty(),
                true,
                Optional.empty());
    }

    private static ManagedWorld world(WorldName name, WorldSpec spec) {
        return ManagedWorld.created(name, spec, true, Optional.empty(), Instant.EPOCH);
    }

    private static ManagedWorld linked(WorldName name, WorldSpec spec, WorldProperty<String> link, String target) {
        ManagedWorld base = world(name, spec);
        return base.withSettings(base.settings().with(link, target));
    }

    @Test
    void resolvesToNothingWhenTheSourceWorldIsUnknown() {
        ResolvePortalDestination resolve = new ResolvePortalDestination(new FakeWorldRepository());

        assertThat(resolve.resolve(OVERWORLD, PortalKind.NETHER, 0, 64, 0)).isEmpty();
    }

    @Test
    void resolvesToNothingWhenNoLinkIsSet() {
        FakeWorldRepository repository = new FakeWorldRepository();
        repository.save(world(OVERWORLD, WorldSpec.normal()));
        ResolvePortalDestination resolve = new ResolvePortalDestination(repository);

        assertThat(resolve.resolve(OVERWORLD, PortalKind.NETHER, 0, 64, 0)).isEmpty();
    }

    @Test
    void resolvesToNothingWhenTheLinkedTargetIsNotRegistered() {
        FakeWorldRepository repository = new FakeWorldRepository();
        repository.save(linked(OVERWORLD, WorldSpec.normal(), WorldProperties.PORTAL_NETHER_LINK, "thenether"));
        ResolvePortalDestination resolve = new ResolvePortalDestination(repository);

        assertThat(resolve.resolve(OVERWORLD, PortalKind.NETHER, 0, 64, 0)).isEmpty();
    }

    @Test
    void scalesHorizontalCoordinatesDownFromANormalSourceToANetherTarget() {
        FakeWorldRepository repository = new FakeWorldRepository();
        repository.save(linked(OVERWORLD, WorldSpec.normal(), WorldProperties.PORTAL_NETHER_LINK, "thenether"));
        repository.save(world(NETHER, netherSpec()));
        ResolvePortalDestination resolve = new ResolvePortalDestination(repository);

        PortalDestination destination =
                resolve.resolve(OVERWORLD, PortalKind.NETHER, 80, 64, -16).orElseThrow();

        assertThat(destination.world()).isEqualTo(NETHER);
        assertThat(destination.x()).isEqualTo(10.0);
        assertThat(destination.y()).isEqualTo(64.0);
        assertThat(destination.z()).isEqualTo(-2.0);
    }

    @Test
    void scalesHorizontalCoordinatesUpFromANetherSourceToANormalTarget() {
        FakeWorldRepository repository = new FakeWorldRepository();
        repository.save(linked(NETHER, netherSpec(), WorldProperties.PORTAL_NETHER_LINK, "overworld"));
        repository.save(world(OVERWORLD, WorldSpec.normal()));
        ResolvePortalDestination resolve = new ResolvePortalDestination(repository);

        PortalDestination destination =
                resolve.resolve(NETHER, PortalKind.NETHER, 10, 64, -2).orElseThrow();

        assertThat(destination.world()).isEqualTo(OVERWORLD);
        assertThat(destination.x()).isEqualTo(80.0);
        assertThat(destination.y()).isEqualTo(64.0);
        assertThat(destination.z()).isEqualTo(-16.0);
    }

    @Test
    void leavesCoordinatesUnscaledForAnEndLinkBetweenNormalWorlds() {
        WorldName end = WorldName.of("theend");
        FakeWorldRepository repository = new FakeWorldRepository();
        repository.save(linked(OVERWORLD, WorldSpec.normal(), WorldProperties.PORTAL_END_LINK, "theend"));
        repository.save(world(end, WorldSpec.normal()));
        ResolvePortalDestination resolve = new ResolvePortalDestination(repository);

        PortalDestination destination =
                resolve.resolve(OVERWORLD, PortalKind.END, 80, 64, -16).orElseThrow();

        assertThat(destination.world()).isEqualTo(end);
        assertThat(destination.x()).isEqualTo(80.0);
        assertThat(destination.y()).isEqualTo(64.0);
        assertThat(destination.z()).isEqualTo(-16.0);
    }
}
