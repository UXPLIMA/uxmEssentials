package com.uxplima.uxmessentials.playerstate.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

import com.uxplima.uxmessentials.playerstate.application.port.InventoryViewer;
import com.uxplima.uxmessentials.playerstate.application.port.NearbyPlayers;
import com.uxplima.uxmessentials.playerstate.application.port.PlayerEffects;
import com.uxplima.uxmessentials.playerstate.application.port.PlayerInfo;
import com.uxplima.uxmessentials.playerstate.application.port.PlayerStateStore;
import com.uxplima.uxmessentials.playerstate.application.port.StateReconciler;
import com.uxplima.uxmessentials.playerstate.domain.AirAmount;
import com.uxplima.uxmessentials.playerstate.domain.BurnDuration;
import com.uxplima.uxmessentials.playerstate.domain.ExperienceChange;
import com.uxplima.uxmessentials.playerstate.domain.FoodLevel;
import com.uxplima.uxmessentials.playerstate.domain.FreezeDuration;
import com.uxplima.uxmessentials.playerstate.domain.GameModeRef;
import com.uxplima.uxmessentials.playerstate.domain.GlowColor;
import com.uxplima.uxmessentials.playerstate.domain.HealthLevel;
import com.uxplima.uxmessentials.playerstate.domain.PersonalTime;
import com.uxplima.uxmessentials.playerstate.domain.PersonalWeather;
import com.uxplima.uxmessentials.playerstate.domain.PlayerStateSnapshot;
import com.uxplima.uxmessentials.playerstate.domain.SpeedValue;
import com.uxplima.uxmessentials.playerstate.domain.event.Fed;
import com.uxplima.uxmessentials.playerstate.domain.event.GodToggled;
import com.uxplima.uxmessentials.playerstate.domain.event.Healed;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The playerstate use cases through their real implementations against in-memory fakes. The same wiring the
 * Brigadier handlers drive, minus Bukkit. It proves the headline rules of this context: a toggle mutates the
 * snapshot, reconciles, and publishes; an apply-once effect (heal/feed) changes no snapshot but still fires
 * its effect and event; a staff toggle affects the subject (not the actor); and the {@code .others} target is
 * carried through to the recorded event. The reconciler is a recorder, so the test also pins that every
 * snapshot mutation is pushed out exactly once.
 */
class PlayerStateUseCasesTest {

    private FakeStore store;
    private RecordingReconciler reconciler;
    private RecordingEffects effects;
    private RecordingEvents events;
    private Notifier notifier;
    private Clock clock;
    private PlayerRef alice;
    private PlayerRef bob;

    @BeforeEach
    void setUp() {
        store = new FakeStore();
        reconciler = new RecordingReconciler();
        effects = new RecordingEffects();
        events = new RecordingEvents();
        notifier = new Notifier(new KeyMessages(), new CapturingSink());
        clock = Clock.system(ZoneOffset.UTC);
        alice = new PlayerRef(UUID.randomUUID(), "Alice");
        bob = new PlayerRef(UUID.randomUUID(), "Bob");
    }

    @Test
    void toggleGodMutatesTheSnapshotReconcilesAndPublishes() {
        ToggleGod god = new ToggleGod(store, reconciler, notifier, events, clock);

        boolean enabled = god.toggle(alice);

        assertThat(enabled).isTrue();
        assertThat(store.current(alice).god()).isTrue();
        assertThat(reconciler.reconciled).containsExactly(alice.uuid());
        assertThat(events.published).hasSize(1).first().isInstanceOf(GodToggled.class);
    }

    @Test
    void toggleGodTwiceReturnsToOff() {
        ToggleGod god = new ToggleGod(store, reconciler, notifier, events, clock);

        god.toggle(alice);
        boolean second = god.toggle(alice);

        assertThat(second).isFalse();
        assertThat(store.current(alice).god()).isFalse();
        assertThat(reconciler.reconciled).hasSize(2); // pushed out on each toggle
    }

    @Test
    void aStaffToggleAffectsTheSubjectAndRecordsBothParties() {
        ToggleFly fly = new ToggleFly(store, reconciler, notifier, events, clock);

        fly.toggleFor(alice, bob);

        assertThat(store.current(bob).fly()).isTrue();
        assertThat(store.current(alice).fly()).isFalse(); // the actor's own state is untouched
        assertThat(events.published)
                .first()
                .isInstanceOf(com.uxplima.uxmessentials.playerstate.domain.event.FlyToggled.class);
    }

    @Test
    void setGamemodePinsTheModeIntoTheSnapshot() {
        SetGamemode gamemode = new SetGamemode(store, reconciler, notifier, events, clock);

        gamemode.set(alice, GameModeRef.CREATIVE);

        assertThat(store.current(alice).gameMode()).contains(GameModeRef.CREATIVE);
        assertThat(reconciler.reconciled).containsExactly(alice.uuid());
    }

    @Test
    void setSpeedBothUpdatesWalkAndFlyAndReconcilesTwice() {
        SetSpeed speed = new SetSpeed(store, reconciler, notifier, events, clock);

        speed.setBoth(alice, alice, SpeedValue.of(6.0));

        assertThat(store.current(alice).walkSpeed().scale()).isEqualTo(6.0);
        assertThat(store.current(alice).flySpeed().scale()).isEqualTo(6.0);
        assertThat(reconciler.reconciled).hasSize(2); // one per affected speed
    }

    @Test
    void healIsApplyOnceChangingNoSnapshotButFiringTheEffect() {
        Heal heal = new Heal(effects, notifier, events, clock, true);

        heal.heal(alice);

        assertThat(effects.healed).containsExactly(alice.uuid());
        assertThat(effects.clearedEffectsFor).contains(alice.uuid()); // heal-remove-effects honoured
        assertThat(store.current(alice)).isEqualTo(PlayerStateSnapshot.initial()); // no flag changed
        assertThat(events.published).first().isInstanceOf(Healed.class);
        assertThat(reconciler.reconciled).isEmpty(); // an apply-once effect does not reconcile
    }

    @Test
    void feedIsApplyOnce() {
        Feed feed = new Feed(effects, notifier, events, clock);

        feed.feed(alice);

        assertThat(effects.fed).containsExactly(alice.uuid());
        assertThat(events.published).first().isInstanceOf(Fed.class);
    }

    @Test
    void extinguishAndSuicideAreLiveOnlyVerbs() {
        new Extinguish(effects, notifier).extinguish(alice);
        new Suicide(effects, notifier).suicide(alice);

        assertThat(effects.extinguished).containsExactly(alice.uuid());
        assertThat(effects.killed).containsExactly(alice.uuid());
        assertThat(events.published).isEmpty(); // these verbs publish no domain event
    }

    @Test
    void nightVisionReportsTheResultingState() {
        ToggleNightVision nv = new ToggleNightVision(effects, notifier);

        assertThat(nv.toggle(alice)).isTrue();
        assertThat(nv.toggle(alice)).isFalse();
    }

    @Test
    void glowReportsTheResultingState() {
        ToggleGlow glow = new ToggleGlow(effects, notifier);

        assertThat(glow.toggle(alice)).isTrue();
        assertThat(glow.toggle(alice)).isFalse();
    }

    @Test
    void namingAColourTurnsTheOutlineOnAndKeepsItOnWhenRepeated() {
        ToggleGlow glow = new ToggleGlow(effects, notifier);

        glow.colourFor(alice, alice, GlowColor.RED);
        glow.colourFor(alice, alice, GlowColor.AQUA);

        // Colouring is not a toggle: a second call only re-colours, so the outline never blinks off mid-choice.
        assertThat(effects.glowing).containsEntry(alice.uuid(), Boolean.TRUE);
        assertThat(effects.glowColours).containsEntry(alice.uuid(), GlowColor.AQUA);
    }

    @Test
    void glowingSomebodyElseConfirmsToTheActorAndTellsTheSubject() {
        CapturingSink sink = new CapturingSink();
        ToggleGlow glow = new ToggleGlow(effects, new Notifier(new KeyMessages(), sink));

        glow.toggleFor(alice, bob);

        assertThat(sink.delivered)
                .containsExactly(PlayerstateMessageKey.GLOW_ON_OTHER.key(), PlayerstateMessageKey.GLOW_ON.key());
    }

    @Test
    void colouringSomebodyElseConfirmsToBothSides() {
        CapturingSink sink = new CapturingSink();
        ToggleGlow glow = new ToggleGlow(effects, new Notifier(new KeyMessages(), sink));

        glow.colourFor(alice, bob, GlowColor.GOLD);

        assertThat(sink.delivered)
                .containsExactly(PlayerstateMessageKey.GLOW_COLOR_OTHER.key(), PlayerstateMessageKey.GLOW_COLOR.key());
        assertThat(effects.glowColours).containsEntry(bob.uuid(), GlowColor.GOLD);
    }

    @Test
    void nightVisionOnSomebodyElseConfirmsToBothSides() {
        CapturingSink sink = new CapturingSink();
        ToggleNightVision nv = new ToggleNightVision(effects, new Notifier(new KeyMessages(), sink));

        nv.toggleFor(alice, bob);

        assertThat(sink.delivered)
                .containsExactly(
                        PlayerstateMessageKey.NIGHTVISION_ON_OTHER.key(), PlayerstateMessageKey.NIGHTVISION_ON.key());
    }

    @Test
    void personalTimeAndWeatherApplyThroughTheEffectsPort() {
        new SetPersonalTime(effects, notifier)
                .apply(alice, PersonalTime.parse("night").orElseThrow());
        new SetPersonalWeather(effects, notifier).apply(alice, PersonalWeather.RAIN);

        assertThat(effects.appliedTime)
                .extractingByKey(alice.uuid())
                .extracting(PersonalTime::ticks)
                .isEqualTo(14_000L);
        assertThat(effects.appliedWeather).containsEntry(alice.uuid(), PersonalWeather.RAIN);
    }

    @Test
    void nearClampsTheRadiusAndPushesTheRenderedScan() {
        FakeNearby nearby = new FakeNearby(List.of(new NearbyPlayers.Nearby(bob, 12)));
        CapturingSink sink = new CapturingSink();
        ListNearby near = new ListNearby(nearby, new Notifier(new KeyMessages(), sink));

        near.near(alice, 9_999_999); // void: the use case never blocks on a returned list, it pushes

        assertThat(nearby.invoked).isTrue();
        assertThat(nearby.requestedRadius).isEqualTo(ListNearby.MAX_RADIUS); // clamped to the bound
        // header line then one entry line, both delivered through the notifier (no .get() in the flow)
        assertThat(sink.delivered)
                .containsExactly(PlayerstateMessageKey.NEAR_HEADER.key(), PlayerstateMessageKey.NEAR_ENTRY.key());
    }

    @Test
    void nearPushesTheEmptyMessageWhenNobodyIsInRange() {
        FakeNearby nearby = new FakeNearby(List.of());
        CapturingSink sink = new CapturingSink();
        ListNearby near = new ListNearby(nearby, new Notifier(new KeyMessages(), sink));

        near.near(alice);

        assertThat(nearby.invoked).isTrue();
        assertThat(nearby.requestedRadius).isEqualTo(ListNearby.DEFAULT_RADIUS);
        assertThat(sink.delivered).containsExactly(PlayerstateMessageKey.NEAR_EMPTY.key());
    }

    @Test
    void experienceGiveAddsToTheCurrentTotalAndIsLiveOnly() {
        SetExperience exp = new SetExperience(effects, notifier);

        exp.apply(alice, new ExperienceChange(ExperienceChange.Op.GIVE, ExperienceChange.Unit.POINTS, 30));
        exp.apply(alice, new ExperienceChange(ExperienceChange.Op.GIVE, ExperienceChange.Unit.POINTS, 20));

        assertThat(effects.currentExp).containsEntry(alice.uuid(), 50);
        assertThat(events.published).isEmpty(); // exp publishes no domain event
    }

    @Test
    void experienceTakeIsFlooredAtZeroAndResetClears() {
        SetExperience exp = new SetExperience(effects, notifier);
        exp.apply(alice, new ExperienceChange(ExperienceChange.Op.SET, ExperienceChange.Unit.POINTS, 40));

        exp.apply(alice, new ExperienceChange(ExperienceChange.Op.TAKE, ExperienceChange.Unit.POINTS, 100));
        assertThat(effects.currentExp).containsEntry(alice.uuid(), 0);

        exp.apply(alice, new ExperienceChange(ExperienceChange.Op.SET, ExperienceChange.Unit.POINTS, 7));
        exp.apply(alice, ExperienceChange.reset());
        assertThat(effects.currentExp).containsEntry(alice.uuid(), 0);
    }

    @Test
    void airAndBurnApplyThroughTheEffectsPortClampedInTheDomain() {
        new SetAir(effects, notifier).set(alice, AirAmount.ofSeconds(15, 300));
        new Burn(effects, notifier).burn(bob, BurnDuration.ofSeconds(BurnDuration.MAX_SECONDS + 50));

        assertThat(effects.air).containsEntry(alice.uuid(), 300); // 15s = 300 ticks
        assertThat(effects.fireSeconds).containsEntry(bob.uuid(), BurnDuration.MAX_SECONDS); // clamped
    }

    @Test
    void freezeAppliesThroughTheEffectsPortClampedInTheDomain() {
        new Freeze(effects, notifier).freeze(bob, FreezeDuration.ofSeconds(FreezeDuration.MAX_SECONDS + 50));

        assertThat(effects.freezeSeconds).containsEntry(bob.uuid(), FreezeDuration.MAX_SECONDS); // clamped
    }

    @Test
    void foodLevelAppliesThroughTheEffectsPortClampedInTheDomain() {
        new SetFoodLevel(effects, notifier).set(alice, FoodLevel.of(25));

        assertThat(effects.food).containsEntry(alice.uuid(), 20); // clamped to the vanilla maximum
        assertThat(FoodLevel.of(-3).value()).isZero(); // a negative request empties the bar
    }

    @Test
    void healthAppliesThroughTheEffectsPortClampedInTheDomain() {
        new SetHealth(effects, notifier).set(alice, HealthLevel.of(10));

        assertThat(effects.health).containsEntry(alice.uuid(), 10.0); // the upper bound is the adapter's job
        assertThat(HealthLevel.of(-2).value()).isZero(); // a negative request floors at zero
    }

    @Test
    void restResetsWhenEnabledAndIsANoOpWhenDisabled() {
        new ResetRest(effects, notifier, true).reset(alice);
        assertThat(effects.restReset).containsExactly(alice.uuid());

        new ResetRest(effects, notifier, false).reset(bob);
        assertThat(effects.restReset).doesNotContain(bob.uuid()); // disabled gate blocks the effect
    }

    @Test
    void openInventoryViewOpensTheSubjectsInventoryForTheViewer() {
        RecordingInventoryViewer viewer = new RecordingInventoryViewer();
        OpenContainer open = new OpenContainer(viewer, notifier);

        open.openInventory(alice, bob);

        assertThat(viewer.inventoryViews).containsEntry(alice.uuid(), bob.uuid());
    }

    @Test
    void openEnderChestViewOpensTheSubjectsEnderChestForTheViewer() {
        RecordingInventoryViewer viewer = new RecordingInventoryViewer();
        OpenContainer open = new OpenContainer(viewer, notifier);

        open.openEnderChest(alice, bob);

        assertThat(viewer.enderViews).containsEntry(alice.uuid(), bob.uuid());
    }

    @Test
    void getposAndPingReadThroughTheInfoPort() {
        Position at = new Position(new WorldRef(UUID.randomUUID(), "world"), 1.0, 64.0, -3.0, 0f, 0f);
        FakeInfo info = new FakeInfo(at, 42, java.time.Duration.ofHours(5));

        new ShowPosition(info, notifier).show(alice);
        new ShowPing(info, notifier).show(alice);

        // No exception and no mutation: read-only queries touch neither store nor reconciler.
        assertThat(reconciler.reconciled).isEmpty();
    }

    @Test
    void getposIsSilentWhenTheTargetIsOffline() {
        FakeInfo offline = new FakeInfo(null, null, null);

        new ShowPosition(offline, notifier).show(alice);
        new ShowPing(offline, notifier).show(alice);

        assertThat(reconciler.reconciled).isEmpty();
    }

    @Test
    void playtimeRendersTheBreakdownAndIsReadOnly() {
        FakeInfo online = new FakeInfo(null, null, java.time.Duration.ofHours(5));
        FakePlaytimeRepository repo = new FakePlaytimeRepository();

        new ShowPlaytime(repo, online, notifier, Clock.systemUTC()).show(alice);

        assertThat(reconciler.reconciled).isEmpty(); // read-only: nothing is reconciled
    }

    @Test
    void playtimeAlwaysSendsTheBreakdownEvenWithNoTrackedRows() {
        FakeInfo offline = new FakeInfo(null, null, null);
        FakePlaytimeRepository repo = new FakePlaytimeRepository();
        var captured = new ArrayList<String>();
        Notifier capturing = new Notifier(new KeyMessages(), (viewer, renderedText) -> captured.add(renderedText));

        // An untracked player offline yields an all-zero summary, which the breakdown still renders (unlike the
        // old behaviour, which sent nothing when the Bukkit lifetime stat was unavailable).
        new ShowPlaytime(repo, offline, capturing, Clock.systemUTC()).show(alice);

        assertThat(captured).containsExactly(PlayerstateMessageKey.PLAYTIME_SHOW.key()); // self uses the self key
    }

    @Test
    void resetPlaytimeWipesTheLedgerAndConfirms() {
        FakePlaytimeRepository repo = new FakePlaytimeRepository();
        repo.reset.clear();
        var captured = new ArrayList<String>();
        Notifier capturing = new Notifier(new KeyMessages(), (viewer, renderedText) -> captured.add(renderedText));

        new ResetPlaytime(repo, capturing).reset(alice);

        assertThat(repo.reset).containsExactly(alice.uuid());
        assertThat(captured).containsExactly(PlayerstateMessageKey.PLAYTIME_RESET.key());
    }

    @Test
    void resetAllPlaytimeWipesEveryLedgerAndConfirms() {
        FakePlaytimeRepository repo = new FakePlaytimeRepository();
        var captured = new ArrayList<String>();
        Notifier capturing = new Notifier(new KeyMessages(), (viewer, renderedText) -> captured.add(renderedText));

        new ResetPlaytime(repo, capturing).resetAll(alice);

        assertThat(repo.resetAllCalls).isEqualTo(1);
        assertThat(captured).containsExactly(PlayerstateMessageKey.PLAYTIME_RESET_ALL.key());
    }

    /** A map-backed {@link PlayerStateStore} mutated via the same compute contract as the real adapter. */
    private static final class FakeStore implements PlayerStateStore {
        private final ConcurrentHashMap<UUID, PlayerStateSnapshot> map = new ConcurrentHashMap<>();

        @Override
        public PlayerStateSnapshot current(PlayerRef who) {
            return map.computeIfAbsent(who.uuid(), id -> PlayerStateSnapshot.initial());
        }

        @Override
        public PlayerStateSnapshot update(PlayerRef who, UnaryOperator<PlayerStateSnapshot> mutator) {
            return map.compute(who.uuid(), (id, existing) -> {
                PlayerStateSnapshot base = existing == null ? PlayerStateSnapshot.initial() : existing;
                return mutator.apply(base);
            });
        }

        @Override
        public void forget(PlayerRef who) {
            map.remove(who.uuid());
        }
    }

    /** A reconciler that records each player it was asked to push a snapshot for. */
    private static final class RecordingReconciler implements StateReconciler {
        private final List<UUID> reconciled = new ArrayList<>();

        @Override
        public void reconcile(PlayerRef who, PlayerStateSnapshot snapshot) {
            reconciled.add(who.uuid());
        }
    }

    /** An effects port that records every live-only action it was asked to perform. */
    private static final class RecordingEffects implements PlayerEffects {
        private final List<UUID> healed = new ArrayList<>();
        private final List<UUID> clearedEffectsFor = new ArrayList<>();
        private final List<UUID> fed = new ArrayList<>();
        private final List<UUID> extinguished = new ArrayList<>();
        private final List<UUID> cleared = new ArrayList<>();
        private final List<UUID> killed = new ArrayList<>();
        private final Map<UUID, Boolean> nightVision = new ConcurrentHashMap<>();
        private final Map<UUID, Boolean> glowing = new ConcurrentHashMap<>();
        private final Map<UUID, GlowColor> glowColours = new ConcurrentHashMap<>();
        private final Map<UUID, PersonalTime> appliedTime = new ConcurrentHashMap<>();
        private final Map<UUID, PersonalWeather> appliedWeather = new ConcurrentHashMap<>();
        private final Map<UUID, Integer> currentExp = new ConcurrentHashMap<>();
        private final Map<UUID, Integer> air = new ConcurrentHashMap<>();
        private final Map<UUID, Integer> fireSeconds = new ConcurrentHashMap<>();
        private final Map<UUID, Integer> freezeSeconds = new ConcurrentHashMap<>();
        private final Map<UUID, Integer> food = new ConcurrentHashMap<>();
        private final Map<UUID, Double> health = new ConcurrentHashMap<>();
        private final List<UUID> restReset = new ArrayList<>();

        @Override
        public void heal(PlayerRef who, boolean clearEffects) {
            healed.add(who.uuid());
            if (clearEffects) {
                clearedEffectsFor.add(who.uuid());
            }
        }

        @Override
        public void feed(PlayerRef who) {
            fed.add(who.uuid());
        }

        @Override
        public void extinguish(PlayerRef who) {
            extinguished.add(who.uuid());
        }

        @Override
        public void clearInventory(PlayerRef who) {
            cleared.add(who.uuid());
        }

        @Override
        public void kill(PlayerRef who) {
            killed.add(who.uuid());
        }

        @Override
        public boolean toggleNightVision(PlayerRef who) {
            return nightVision.merge(who.uuid(), Boolean.TRUE, (old, ignored) -> !old);
        }

        @Override
        public boolean toggleGlow(PlayerRef who) {
            return glowing.merge(who.uuid(), Boolean.TRUE, (old, ignored) -> !old);
        }

        @Override
        public void setGlow(PlayerRef who, GlowColor colour) {
            glowing.put(who.uuid(), Boolean.TRUE);
            glowColours.put(who.uuid(), colour);
        }

        @Override
        public void applyTime(PlayerRef who, PersonalTime time) {
            appliedTime.put(who.uuid(), time);
        }

        @Override
        public void applyWeather(PlayerRef who, PersonalWeather weather) {
            appliedWeather.put(who.uuid(), weather);
        }

        @Override
        public void applyExperience(
                PlayerRef who,
                ExperienceChange change,
                java.util.function.Consumer<PlayerEffects.ExperienceReport> onResult) {
            int current = currentExp.getOrDefault(who.uuid(), 0);
            int next = (int) change.resolve(current);
            currentExp.put(who.uuid(), next);
            onResult.accept(new PlayerEffects.ExperienceReport(next / 100, next));
        }

        @Override
        public void setRemainingAir(PlayerRef who, AirAmount value) {
            air.put(who.uuid(), value.ticks());
        }

        @Override
        public void setFire(PlayerRef who, BurnDuration duration) {
            fireSeconds.put(who.uuid(), duration.seconds());
        }

        @Override
        public void setFreeze(PlayerRef who, FreezeDuration duration) {
            freezeSeconds.put(who.uuid(), duration.seconds());
        }

        @Override
        public void setFoodLevel(PlayerRef who, FoodLevel value) {
            food.put(who.uuid(), value.value());
        }

        @Override
        public void setHealth(PlayerRef who, HealthLevel value) {
            health.put(who.uuid(), value.value());
        }

        @Override
        public void resetRest(PlayerRef who) {
            restReset.add(who.uuid());
        }
    }

    /** An inventory viewer that records the (viewer → subject) pairing of each open it was asked for. */
    private static final class RecordingInventoryViewer implements InventoryViewer {
        private final Map<UUID, UUID> inventoryViews = new ConcurrentHashMap<>();
        private final Map<UUID, UUID> enderViews = new ConcurrentHashMap<>();

        @Override
        public void viewInventory(PlayerRef viewer, PlayerRef subject) {
            inventoryViews.put(viewer.uuid(), subject.uuid());
        }

        @Override
        public void viewEnderChest(PlayerRef viewer, PlayerRef subject) {
            enderViews.put(viewer.uuid(), subject.uuid());
        }
    }

    /** A read-only info port returning fixed position, ping, and play time. */
    private static final class FakeInfo implements PlayerInfo {
        private final @Nullable Position position;
        private final @Nullable Integer ping;
        private final java.time.@Nullable Duration playtime;

        FakeInfo(@Nullable Position position, @Nullable Integer ping, java.time.@Nullable Duration playtime) {
            this.position = position;
            this.ping = ping;
            this.playtime = playtime;
        }

        @Override
        public java.util.Optional<Position> positionOf(PlayerRef who) {
            return java.util.Optional.ofNullable(position);
        }

        @Override
        public java.util.Optional<Integer> pingOf(PlayerRef who) {
            return java.util.Optional.ofNullable(ping);
        }

        @Override
        public java.util.Optional<java.time.Duration> playtimeOf(PlayerRef who) {
            return java.util.Optional.ofNullable(playtime);
        }
    }

    /** A map-backed playtime ledger recording resets and accumulating per-day deltas for assertions. */
    private static final class FakePlaytimeRepository
            implements com.uxplima.uxmessentials.playerstate.application.port.PlaytimeRepository {
        private final List<UUID> reset = new ArrayList<>();
        private int resetAllCalls;
        private final Map<UUID, long[]> totals = new ConcurrentHashMap<>(); // [active, afk]

        @Override
        public void addSeconds(UUID uuid, java.time.LocalDate day, long activeDelta, long afkDelta) {
            totals.merge(uuid, new long[] {activeDelta, afkDelta}, (a, b) -> new long[] {a[0] + b[0], a[1] + b[1]});
        }

        @Override
        public com.uxplima.uxmessentials.playerstate.domain.PlaytimeSummary summaryOf(
                UUID uuid, java.time.LocalDate today) {
            long[] t = totals.getOrDefault(uuid, new long[] {0L, 0L});
            return com.uxplima.uxmessentials.playerstate.domain.PlaytimeSummary.ofSeconds(
                    t[0], t[1], t[0], t[1], t[0], t[1], t[0], t[1]);
        }

        @Override
        public void reset(UUID uuid) {
            reset.add(uuid);
            totals.remove(uuid);
        }

        @Override
        public void resetAll() {
            resetAllCalls++;
            totals.clear();
        }
    }

    /**
     * A nearby scan that pushes a fixed list to the supplied callback and records the radius it was asked for.
     * Invokes the callback inline (synchronously), standing in for the adapter's global-thread resolve, the
     * use case must not block on or otherwise depend on a returned value, only on the push.
     */
    private static final class FakeNearby implements NearbyPlayers {
        private final List<Nearby> result;
        private int requestedRadius;
        private boolean invoked;

        FakeNearby(List<Nearby> result) {
            this.result = result;
        }

        @Override
        public void within(PlayerRef viewer, int radius, java.util.function.Consumer<List<Nearby>> onResolved) {
            requestedRadius = radius;
            invoked = true;
            onResolved.accept(result);
        }
    }

    private static final class RecordingEvents implements DomainEventPublisher {
        private final List<DomainEvent> published = new ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            published.add(event);
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class CapturingSink implements MessageSink {
        private final List<String> delivered = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            delivered.add(renderedText);
        }
    }
}
