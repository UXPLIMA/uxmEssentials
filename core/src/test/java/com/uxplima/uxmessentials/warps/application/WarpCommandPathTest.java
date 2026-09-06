package com.uxplima.uxmessentials.warps.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.warps.application.port.WarpEconomy;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.application.port.WarpTeleporter;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpCost;
import com.uxplima.uxmessentials.warps.domain.WarpError;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The warps command paths through the real use cases against an in-memory repository and a recording
 * teleporter: the same wiring the Brigadier handlers drive, minus Bukkit. It proves that {@code /setwarp}
 * persists a warp and refuses no one (re-anchor in place), that {@code /warp} delegates execution to the
 * teleport context rather than moving the player itself, that the per-warp permission gate is enforced, and
 *, the headline of this context, that the per-warp cost <em>soft-couples</em> to economy: with a provider
 * present the charge gates the hop, and with no provider a priced warp is usable for free.
 */
class WarpCommandPathTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    private FakeWarpRepository repository;
    private RecordingTeleporter teleporter;
    private StubPermissions permissions;
    private Notifier notifier;
    private DomainEventPublisher events;
    private SetWarp setWarp;
    private PlayerRef alice;
    private PlayerRef operator;

    @BeforeEach
    void setUp() {
        repository = new FakeWarpRepository();
        teleporter = new RecordingTeleporter();
        permissions = new StubPermissions();
        notifier = new Notifier(new KeyMessages(), new CapturingSink());
        events = new CapturingEvents();
        setWarp = new SetWarp(repository, notifier, events, Clock.system(ZoneOffset.UTC), List.of());
        alice = new PlayerRef(UUID.randomUUID(), "Alice");
        operator = new PlayerRef(UUID.randomUUID(), "Operator");
    }

    @Test
    void setWarpPersistsTheWarp() {
        Result<Unit, WarpError> result = setWarp.set(operator, WarpName.of("shop"), at(10, 64, 20));

        assertThat(result.isOk()).isTrue();
        assertThat(repository.exists(WarpName.of("shop"))).isTrue();
    }

    @Test
    void setWarpOnAnExistingNameReanchorsInPlace() {
        setWarp.set(operator, WarpName.of("shop"), at(0, 0, 0));

        setWarp.set(operator, WarpName.of("shop"), at(100, 70, 100));

        assertThat(repository.all()).hasSize(1);
        assertThat(repository.find(WarpName.of("shop")).orElseThrow().location().blockX())
                .isEqualTo(100);
    }

    @Test
    void warpDelegatesExecutionToTheTeleportContext() {
        setWarp.set(operator, WarpName.of("shop"), at(7, 64, 7));
        permissions.grant(alice, WarpName.of("shop").useNode());

        Result<Unit, WarpError> result = useWarp(Optional.empty()).use(alice, WarpName.of("shop"));

        assertThat(result.isOk()).isTrue();
        assertThat(teleporter.hops).isEqualTo(1); // the hop was delegated, not performed here
        assertThat(teleporter.lastWarp.name()).isEqualTo(WarpName.of("shop"));
    }

    @Test
    void warpWithoutThePerWarpNodeIsRefused() {
        setWarp.set(operator, WarpName.of("shop"), at(0, 0, 0));
        // alice is granted no uxmessentials.warp.use.shop node

        Result<Unit, WarpError> result = useWarp(Optional.empty()).use(alice, WarpName.of("shop"));

        assertThat(result.errorOrThrow()).isEqualTo(WarpError.NO_PERMISSION);
        assertThat(teleporter.hops).isZero();
    }

    @Test
    void aMissingWarpIsRejected() {
        Result<Unit, WarpError> result = useWarp(Optional.empty()).use(alice, WarpName.of("ghost"));

        assertThat(result.errorOrThrow()).isEqualTo(WarpError.NOT_FOUND);
        assertThat(teleporter.hops).isZero();
    }

    @Test
    void aPricedWarpIsFreeWhenNoEconomyProviderIsPresent() {
        repository.save(priced("vip", new BigDecimal("500")));
        permissions.grant(alice, WarpName.of("vip").useNode());

        // No economy provider wired (P3 not landed): the cost is recorded on the warp but ignored at use.
        Result<Unit, WarpError> result = useWarp(Optional.empty()).use(alice, WarpName.of("vip"));

        assertThat(result.isOk()).isTrue();
        assertThat(teleporter.hops).isEqualTo(1);
    }

    @Test
    void aPricedWarpChargesThroughTheEconomyProviderWhenPresent() {
        repository.save(priced("vip", new BigDecimal("500")));
        permissions.grant(alice, WarpName.of("vip").useNode());
        RecordingEconomy economy = new RecordingEconomy(true);

        Result<Unit, WarpError> result = useWarp(Optional.of(economy)).use(alice, WarpName.of("vip"));

        assertThat(result.isOk()).isTrue();
        assertThat(economy.charged).isEqualByComparingTo("500");
        assertThat(teleporter.hops).isEqualTo(1);
    }

    @Test
    void aPricedWarpThePlayerCannotAffordDoesNotTeleport() {
        repository.save(priced("vip", new BigDecimal("500")));
        permissions.grant(alice, WarpName.of("vip").useNode());
        RecordingEconomy economy = new RecordingEconomy(false); // withdrawal fails

        Result<Unit, WarpError> result = useWarp(Optional.of(economy)).use(alice, WarpName.of("vip"));

        assertThat(result.errorOrThrow()).isEqualTo(WarpError.CANNOT_AFFORD);
        assertThat(teleporter.hops).isZero();
    }

    @Test
    void aVetoedSetwarpWritesNothing() {
        SetWarp refusing =
                new SetWarp(repository, notifier, events, proposal -> false, Clock.system(ZoneOffset.UTC), List.of());

        Result<Unit, WarpError> result = refusing.set(operator, WarpName.of("shop"), at(10, 64, 20));

        assertThat(result.errorOrThrow()).isEqualTo(WarpError.VETOED);
        assertThat(repository.exists(WarpName.of("shop"))).isFalse();
    }

    @Test
    void aVetoedDelwarpLeavesTheWarpInPlace() {
        setWarp.set(operator, WarpName.of("shop"), at(0, 64, 0));
        DelWarp refusing = new DelWarp(repository, notifier, events, proposal -> false);

        Result<Unit, WarpError> result = refusing.delete(operator, WarpName.of("shop"));

        assertThat(result.errorOrThrow()).isEqualTo(WarpError.VETOED);
        assertThat(repository.exists(WarpName.of("shop"))).isTrue();
    }

    @Test
    void aMissingWarpIsRejectedBeforeAnybodyIsAskedAboutDeletingIt() {
        // Order matters for the published event: a listener should never be handed a delete for a warp that was
        // not there, and should never be able to turn "no such warp" into "blocked".
        DelWarp refusing = new DelWarp(repository, notifier, events, proposal -> false);

        Result<Unit, WarpError> result = refusing.delete(operator, WarpName.of("ghost"));

        assertThat(result.errorOrThrow()).isEqualTo(WarpError.NOT_FOUND);
    }

    @Test
    void listFiltersToWarpsThePlayerMayUse() {
        setWarp.set(operator, WarpName.of("shop"), at(0, 0, 0));
        setWarp.set(operator, WarpName.of("pvp"), at(1, 1, 1));
        permissions.grant(alice, WarpName.of("shop").useNode());

        List<Warp> visible = new ListWarps(repository, permissions, notifier).list(alice);

        assertThat(visible.stream().map(w -> w.name().value())).containsExactly("shop");
    }

    private UseWarp useWarp(Optional<WarpEconomy> economy) {
        WarpAccess access = new WarpAccess(permissions, economy);
        return new UseWarp(repository, access, teleporter, notifier, pos -> true, permissions, new InlineScheduler());
    }

    private Warp priced(String name, BigDecimal amount) {
        return Warp.create(
                WarpName.of(name),
                at(0, 64, 0),
                operator,
                java.time.Instant.EPOCH,
                WarpCost.of(amount),
                Optional.empty());
    }

    private static Position at(double x, double y, double z) {
        return Position.of(WORLD, x, y, z);
    }

    /** A map-backed {@link WarpRepository} keeping warps in insertion order. */
    private static final class FakeWarpRepository implements WarpRepository {
        private final Map<String, Warp> byName = new LinkedHashMap<>();

        @Override
        public Optional<Warp> find(WarpName name) {
            return Optional.ofNullable(byName.get(name.value()));
        }

        @Override
        public List<Warp> all() {
            return List.copyOf(byName.values());
        }

        @Override
        public boolean exists(WarpName name) {
            return byName.containsKey(name.value());
        }

        @Override
        public void save(Warp warp) {
            byName.put(warp.name().value(), warp);
        }

        @Override
        public void delete(WarpName name) {
            byName.remove(name.value());
        }

        @Override
        public void rate(WarpName name, java.util.UUID player, double rating) {}

        @Override
        public double averageRating(WarpName name) {
            return 0.0;
        }
    }

    private static final class RecordingTeleporter implements WarpTeleporter {
        int hops;
        private Warp lastWarp = Warp.create(
                WarpName.of("none"),
                Position.of(WORLD, 0, 0, 0),
                new PlayerRef(new UUID(0L, 0L), "none"),
                java.time.Instant.EPOCH);

        @Override
        public void teleportTo(PlayerRef who, Warp warp) {
            hops++;
            lastWarp = warp;
        }
    }

    /** A recording economy whose withdrawal succeeds or fails per the constructor flag. */
    private static final class RecordingEconomy implements WarpEconomy {
        private final boolean solvent;
        private BigDecimal charged = BigDecimal.ZERO;

        RecordingEconomy(boolean solvent) {
            this.solvent = solvent;
        }

        @Override
        public boolean canAfford(PlayerRef who, BigDecimal amount, String currencyId) {
            return solvent;
        }

        @Override
        public boolean withdraw(PlayerRef who, BigDecimal amount, String currencyId) {
            if (!solvent) {
                return false;
            }
            charged = charged.add(amount);
            return true;
        }
    }

    /** A stub permissions port granting an explicit set of nodes per player. */
    private static final class StubPermissions implements Permissions {
        private final Map<UUID, Set<String>> granted = new HashMap<>();

        void grant(PlayerRef who, String node) {
            granted.computeIfAbsent(who.uuid(), u -> new java.util.HashSet<>()).add(node);
        }

        @Override
        public boolean has(PlayerRef who, String node) {
            return granted.getOrDefault(who.uuid(), Set.of()).contains(node);
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @org.jspecify.annotations.Nullable WorldRef world, long fallback) {
            return QuotaResult.limited(fallback);
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class CapturingSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            // discarded: feedback delivery is not under test here
        }
    }

    private static final class CapturingEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {
            // discarded: event publication is asserted elsewhere
        }
    }
}
