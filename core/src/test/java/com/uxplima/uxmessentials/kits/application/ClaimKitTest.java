package com.uxplima.uxmessentials.kits.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.kits.application.port.KitClaimStore;
import com.uxplima.uxmessentials.kits.application.port.KitEconomy;
import com.uxplima.uxmessentials.kits.application.port.KitGranter;
import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.application.port.KitStockStore;
import com.uxplima.uxmessentials.kits.application.port.KitUnlockStore;
import com.uxplima.uxmessentials.kits.domain.KitCost;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitError;
import com.uxplima.uxmessentials.kits.domain.KitFullPolicy;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.kits.domain.KitItem;
import com.uxplima.uxmessentials.kits.domain.KitSchedule;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.DomainGate;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The {@code /kit} claim path through the real use cases against in-memory ports. The same wiring the
 * Brigadier handler drives, minus Bukkit. It exercises the claim rules in one place: the cooldown gate (a
 * kit on cooldown is refused, a ready kit grants), the one-time rule (a consumed one-time kit is refused and
 * dropped from the list), the per-kit permission gate, and, the headline of this context, that the per-kit
 * cost <em>soft-couples</em> to economy: with a provider present the charge gates the grant, and with no
 * provider a priced kit is claimable for free.
 */
class ClaimKitTest {

    private FakeKitRepository repository;
    private StubPermissions permissions;
    private FakeCooldowns cooldowns;
    private FakeClaimStore claims;
    private RecordingGranter granter;
    private Notifier notifier;
    private DomainEventPublisher events;
    private FakeStockStore stock;
    private FakeUnlockStore unlocks;
    private PlayerRef alice;
    private PlayerRef bob;

    @BeforeEach
    void setUp() {
        repository = new FakeKitRepository();
        permissions = new StubPermissions();
        cooldowns = new FakeCooldowns();
        claims = new FakeClaimStore();
        granter = new RecordingGranter();
        notifier = new Notifier(new KeyMessages(), new CapturingSink());
        events = new CapturingEvents();
        stock = new FakeStockStore();
        unlocks = new FakeUnlockStore();
        alice = new PlayerRef(UUID.randomUUID(), "Alice");
        bob = new PlayerRef(UUID.randomUUID(), "Bob");
    }

    @Test
    void claimGrantsTheItemsAndStampsTheCooldown() {
        repository.save(repeatable("starter", Duration.ofSeconds(90)));

        Result<Unit, KitError> result = claimKit(Optional.empty()).claim(alice, KitId.of("starter"));

        assertThat(result.isOk()).isTrue();
        assertThat(granter.grants).isEqualTo(1);
        assertThat(cooldowns.stamped).containsExactly("kit.starter");
    }

    @Test
    void aMissingKitIsRejected() {
        Result<Unit, KitError> result = claimKit(Optional.empty()).claim(alice, KitId.of("ghost"));

        assertThat(result.errorOrThrow()).isEqualTo(KitError.NOT_FOUND);
        assertThat(granter.grants).isZero();
    }

    @Test
    void aKitOnCooldownIsRefusedAndGrantsNothing() {
        repository.save(repeatable("daily", Duration.ofSeconds(90)));
        cooldowns.onCooldown.add("kit.daily");

        Result<Unit, KitError> result = claimKit(Optional.empty()).claim(alice, KitId.of("daily"));

        assertThat(result.errorOrThrow()).isEqualTo(KitError.ON_COOLDOWN);
        assertThat(granter.grants).isZero();
        assertThat(cooldowns.stamped).isEmpty();
    }

    @Test
    void aOneTimeKitIsConsumedForeverAfterTheFirstClaim() {
        repository.save(oneTime("vote"));

        Result<Unit, KitError> first = claimKit(Optional.empty()).claim(alice, KitId.of("vote"));
        Result<Unit, KitError> second = claimKit(Optional.empty()).claim(alice, KitId.of("vote"));

        assertThat(first.isOk()).isTrue();
        assertThat(second.errorOrThrow()).isEqualTo(KitError.ALREADY_CLAIMED);
        assertThat(granter.grants).isEqualTo(1); // the second claim granted nothing
    }

    @Test
    void aKitRequiringAPermissionIsRefusedWithoutIt() {
        repository.save(gated("vip"));

        Result<Unit, KitError> result = claimKit(Optional.empty()).claim(alice, KitId.of("vip"));

        assertThat(result.errorOrThrow()).isEqualTo(KitError.NO_PERMISSION);
        assertThat(granter.grants).isZero();
    }

    @Test
    void aKitRequiringAPermissionIsClaimableWithIt() {
        repository.save(gated("vip"));
        permissions.grant(alice, KitId.of("vip").permissionNode());

        Result<Unit, KitError> result = claimKit(Optional.empty()).claim(alice, KitId.of("vip"));

        assertThat(result.isOk()).isTrue();
        assertThat(granter.grants).isEqualTo(1);
    }

    @Test
    void aPricedKitIsFreeWhenNoEconomyProviderIsPresent() {
        repository.save(priced("crate", new BigDecimal("250")));

        Result<Unit, KitError> result = claimKit(Optional.empty()).claim(alice, KitId.of("crate"));

        assertThat(result.isOk()).isTrue();
        assertThat(granter.grants).isEqualTo(1);
    }

    @Test
    void aPricedKitChargesThroughTheEconomyProviderWhenPresent() {
        repository.save(priced("crate", new BigDecimal("250")));
        RecordingEconomy economy = new RecordingEconomy(true);

        Result<Unit, KitError> result = claimKit(Optional.of(economy)).claim(alice, KitId.of("crate"));

        assertThat(result.isOk()).isTrue();
        assertThat(economy.charged).isEqualByComparingTo("250");
        assertThat(granter.grants).isEqualTo(1);
    }

    @Test
    void aPricedKitThePlayerCannotAffordGrantsNothing() {
        repository.save(priced("crate", new BigDecimal("250")));
        RecordingEconomy economy = new RecordingEconomy(false);

        Result<Unit, KitError> result = claimKit(Optional.of(economy)).claim(alice, KitId.of("crate"));

        assertThat(result.errorOrThrow()).isEqualTo(KitError.CANNOT_AFFORD);
        assertThat(granter.grants).isZero();
    }

    @Test
    void aVetoedClaimGrantsNothingChargesNothingAndLeavesTheKitClaimable() {
        repository.save(priced("crate", new BigDecimal("250")));
        RecordingEconomy economy = new RecordingEconomy(true);

        Result<Unit, KitError> refused =
                claimKit(Optional.of(economy), proposal -> false).claim(alice, KitId.of("crate"));
        claimKit(Optional.of(economy)).claim(alice, KitId.of("crate")); // the same claim, nobody refusing

        assertThat(refused.errorOrThrow()).isEqualTo(KitError.VETOED);
        assertThat(granter.grants).isEqualTo(1); // only the retry
        assertThat(economy.charged).isEqualByComparingTo("250"); // the refused claim cost nothing
        assertThat(cooldowns.stamped).containsExactly("kit.crate");
    }

    @Test
    void anUnlockOnceKitChargesOnTheFirstClaimAndIsFreeThereafter() {
        repository.save(priced("forge", new BigDecimal("400")).withUnlockOnce(true));
        RecordingEconomy economy = new RecordingEconomy(true);

        Result<Unit, KitError> first = claimKit(Optional.of(economy)).claim(alice, KitId.of("forge"));
        Result<Unit, KitError> second = claimKit(Optional.of(economy)).claim(alice, KitId.of("forge"));

        assertThat(first.isOk()).isTrue();
        assertThat(second.isOk()).isTrue();
        assertThat(granter.grants).isEqualTo(2); // both claims granted
        assertThat(economy.charged)
                .isEqualByComparingTo("400"); // charged once, the unlock survived to the second claim
        assertThat(unlocks.hasUnlocked(alice, KitId.of("forge"))).isTrue();
    }

    @Test
    void aKitOutsideItsScheduleIsRefusedAndGrantsNothing() {
        KitSchedule notYetOpen = new KitSchedule(
                Set.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(LocalDateTime.of(2999, 1, 1, 0, 0)),
                Optional.empty());
        repository.save(repeatable("event", Duration.ZERO).withSchedule(notYetOpen));

        Result<Unit, KitError> result = claimKit(Optional.empty()).claim(alice, KitId.of("event"));

        assertThat(result.errorOrThrow()).isEqualTo(KitError.UNAVAILABLE);
        assertThat(granter.grants).isZero();
        assertThat(cooldowns.stamped).isEmpty();
    }

    @Test
    void aKitInsideItsScheduleClaimsNormally() {
        KitSchedule open = new KitSchedule(
                Set.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(LocalDateTime.of(2000, 1, 1, 0, 0)),
                Optional.of(LocalDateTime.of(2999, 1, 1, 0, 0)));
        repository.save(repeatable("event", Duration.ZERO).withSchedule(open));

        Result<Unit, KitError> result = claimKit(Optional.empty()).claim(alice, KitId.of("event"));

        assertThat(result.isOk()).isTrue();
        assertThat(granter.grants).isEqualTo(1);
    }

    @Test
    void aStockLimitedKitIsRefusedOnceItsGlobalLimitIsReached() {
        repository.save(repeatable("limited", Duration.ZERO).withStockLimit(1));

        Result<Unit, KitError> first = claimKit(Optional.empty()).claim(alice, KitId.of("limited"));
        Result<Unit, KitError> second = claimKit(Optional.empty()).claim(bob, KitId.of("limited"));

        assertThat(first.isOk()).isTrue();
        assertThat(second.errorOrThrow()).isEqualTo(KitError.OUT_OF_STOCK);
        assertThat(granter.grants).isEqualTo(1); // the sold-out claim granted nothing
    }

    @Test
    void aDenyOnFullKitWithNoRoomIsRefusedAndLeavesNoSideEffects() {
        repository.save(priced("loadout", new BigDecimal("100"))
                .withOnFull(KitFullPolicy.DENY)
                .withStockLimit(1));
        RecordingEconomy economy = new RecordingEconomy(true);
        granter.hasRoom = false;

        Result<Unit, KitError> result = claimKit(Optional.of(economy)).claim(alice, KitId.of("loadout"));

        assertThat(result.errorOrThrow()).isEqualTo(KitError.INVENTORY_FULL);
        assertThat(granter.grants).isZero();
        assertThat(cooldowns.stamped).isEmpty();
        assertThat(economy.charged).isEqualByComparingTo("0"); // the charge never ran
        assertThat(stock.claimed(KitId.of("loadout"))).isZero(); // no stock unit was reserved
    }

    @Test
    void aDenyOnFullKitWithRoomIsGrantedNormally() {
        repository.save(repeatable("loadout", Duration.ZERO).withOnFull(KitFullPolicy.DENY));
        granter.hasRoom = true;

        Result<Unit, KitError> result = claimKit(Optional.empty()).claim(alice, KitId.of("loadout"));

        assertThat(result.isOk()).isTrue();
        assertThat(granter.grants).isEqualTo(1);
    }

    @Test
    void aFailedChargeReleasesTheReservedStockUnit() {
        repository.save(priced("limited", new BigDecimal("250")).withStockLimit(1));

        Result<Unit, KitError> broke =
                claimKit(Optional.of(new RecordingEconomy(false))).claim(alice, KitId.of("limited"));
        // the reservation was given back, so the last copy is still claimable by a solvent player
        Result<Unit, KitError> solvent =
                claimKit(Optional.of(new RecordingEconomy(true))).claim(bob, KitId.of("limited"));

        assertThat(broke.errorOrThrow()).isEqualTo(KitError.CANNOT_AFFORD);
        assertThat(solvent.isOk()).isTrue();
        assertThat(granter.grants).isEqualTo(1);
    }

    @Test
    void listOmitsAConsumedOneTimeKitButKeepsACooldownedOne() {
        repository.save(oneTime("vote"));
        repository.save(repeatable("daily", Duration.ofSeconds(90)));
        claimKit(Optional.empty()).claim(alice, KitId.of("vote")); // consume the one-time kit
        cooldowns.onCooldown.add("kit.daily"); // the repeatable kit is now on cooldown

        List<KitDefinition> visible = new ListKits(repository, permissions, claims, notifier).list(alice);

        assertThat(visible.stream().map(k -> k.id().value())).containsExactly("daily");
    }

    private ClaimKit claimKit(Optional<KitEconomy> economy) {
        return claimKit(economy, DomainGate.allowAll());
    }

    private ClaimKit claimKit(Optional<KitEconomy> economy, DomainGate gate) {
        KitAccess access = new KitAccess(
                permissions, cooldowns, claims, economy, Optional.empty(), Optional.of(stock), Optional.of(unlocks));
        return new ClaimKit(
                repository,
                access,
                granter,
                notifier,
                events,
                Clock.system(ZoneOffset.UTC),
                economy,
                Optional.empty(),
                gate);
    }

    private static KitDefinition repeatable(String id, Duration cooldown) {
        return KitDefinition.repeatable(KitId.of(id), items(), cooldown);
    }

    private static KitDefinition oneTime(String id) {
        return KitDefinition.builder()
                .id(KitId.of(id))
                .items(items())
                .oneTime(true)
                .build();
    }

    private static KitDefinition gated(String id) {
        return KitDefinition.builder()
                .id(KitId.of(id))
                .items(items())
                .permission(true)
                .build();
    }

    private static KitDefinition priced(String id, BigDecimal amount) {
        return KitDefinition.builder()
                .id(KitId.of(id))
                .items(items())
                .cost(KitCost.of(amount))
                .build();
    }

    private static List<KitItem> items() {
        return List.of(KitItem.of("payload", 1));
    }

    /** A map-backed {@link KitRepository} keeping kits in insertion order. */
    private static final class FakeKitRepository implements KitRepository {
        private final Map<String, KitDefinition> byId = new LinkedHashMap<>();

        @Override
        public Optional<KitDefinition> find(KitId id) {
            return Optional.ofNullable(byId.get(id.value()));
        }

        @Override
        public List<KitDefinition> all() {
            return List.copyOf(byId.values());
        }

        @Override
        public boolean exists(KitId id) {
            return byId.containsKey(id.value());
        }

        @Override
        public void save(KitDefinition definition) {
            byId.put(definition.id().value(), definition);
        }

        @Override
        public void delete(KitId id) {
            byId.remove(id.value());
        }
    }

    /** A cooldown port whose check fails for a stamp-scope listed in {@code onCooldown}. */
    private static final class FakeCooldowns implements Cooldowns {
        private final Set<String> onCooldown = new HashSet<>();
        private final List<String> stamped = new java.util.ArrayList<>();

        @Override
        public Result<Unit, java.time.Duration> check(PlayerRef who, CooldownKind kind) {
            return onCooldown.contains(kind.stampScope()) ? Result.err(java.time.Duration.ofSeconds(30)) : Result.ok();
        }

        @Override
        public void stamp(PlayerRef who, CooldownKind kind) {
            stamped.add(kind.stampScope());
        }

        @Override
        public Result<Unit, java.time.Duration> checkLabel(PlayerRef who, String label) {
            return Result.ok();
        }

        @Override
        public void stampLabel(PlayerRef who, String label) {
            // unused
        }
    }

    /** A map-backed one-time claim store. */
    private static final class FakeClaimStore implements KitClaimStore {
        private final Map<UUID, Set<String>> claimed = new HashMap<>();

        @Override
        public boolean hasClaimed(PlayerRef who, KitId kit) {
            return claimed.getOrDefault(who.uuid(), Set.of()).contains(kit.value());
        }

        @Override
        public void markClaimed(PlayerRef who, KitId kit) {
            claimed.computeIfAbsent(who.uuid(), u -> new HashSet<>()).add(kit.value());
        }

        @Override
        public void reset(PlayerRef who, KitId kit) {
            claimed.getOrDefault(who.uuid(), new HashSet<>()).remove(kit.value());
        }

        @Override
        public void resetAll(PlayerRef who) {
            claimed.remove(who.uuid());
        }
    }

    /** A map-backed buy-to-unlock store mirroring the PDC-backed production store. */
    private static final class FakeUnlockStore implements KitUnlockStore {
        private final Map<UUID, Set<String>> unlocked = new HashMap<>();

        @Override
        public boolean hasUnlocked(PlayerRef who, KitId kit) {
            return unlocked.getOrDefault(who.uuid(), Set.of()).contains(kit.value());
        }

        @Override
        public void markUnlocked(PlayerRef who, KitId kit) {
            unlocked.computeIfAbsent(who.uuid(), u -> new HashSet<>()).add(kit.value());
        }
    }

    /** A map-backed global stock counter with atomic reserve/release, mirroring the file-backed production store. */
    private static final class FakeStockStore implements KitStockStore {
        private final Map<String, Integer> counts = new HashMap<>();

        @Override
        public boolean tryConsume(KitId kit, int limit) {
            int now = counts.getOrDefault(kit.value(), 0);
            if (now < limit) {
                counts.put(kit.value(), now + 1);
                return true;
            }
            return false;
        }

        @Override
        public void release(KitId kit) {
            counts.computeIfPresent(kit.value(), (id, current) -> current > 0 ? current - 1 : 0);
        }

        @Override
        public long claimed(KitId kit) {
            return counts.getOrDefault(kit.value(), 0);
        }
    }

    private static final class RecordingGranter implements KitGranter {
        private int grants;
        private boolean hasRoom = true;

        @Override
        public boolean hasRoomFor(PlayerRef recipient, KitDefinition kit) {
            return hasRoom;
        }

        @Override
        public Grant grant(PlayerRef recipient, KitDefinition kit) {
            grants++;
            return Grant.complete();
        }
    }

    /** A recording economy whose withdrawal succeeds or fails per the constructor flag. */
    private static final class RecordingEconomy implements KitEconomy {
        private final boolean solvent;
        private BigDecimal charged = BigDecimal.ZERO;
        private BigDecimal credited = BigDecimal.ZERO;

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

        @Override
        public boolean deposit(PlayerRef who, BigDecimal amount, String currencyId) {
            credited = credited.add(amount);
            return true;
        }
    }

    /** A stub permissions port granting an explicit set of nodes per player. */
    private static final class StubPermissions implements Permissions {
        private final Map<UUID, Set<String>> granted = new HashMap<>();

        void grant(PlayerRef who, String node) {
            granted.computeIfAbsent(who.uuid(), u -> new HashSet<>()).add(node);
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
            // discarded: event publication asserted elsewhere
        }
    }
}
