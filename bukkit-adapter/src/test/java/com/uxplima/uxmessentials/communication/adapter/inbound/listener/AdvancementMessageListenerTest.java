package com.uxplima.uxmessentials.communication.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

import io.papermc.paper.advancement.AdvancementDisplay;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.TranslationStore;

import com.uxplima.uxmessentials.communication.application.port.BroadcastOptOutStore;
import com.uxplima.uxmessentials.communication.domain.AdvancementNoticeConfig;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.ChannelBroadcaster;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.ChannelDisplay;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.display.BroadcastChannel;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit + Mockito coverage of {@link AdvancementMessageListener}. The {@code Advancement}/{@code
 * AdvancementDisplay} pair are Bukkit/Paper interfaces MockBukkit does not implement, so they are mocked; the earner
 * and the broadcast recipients are real {@link PlayerMock}s and the {@link PlayerAdvancementDoneEvent} is a real
 * event built from the public {@code (Player, Advancement, Component)} constructor, so the listener runs end to end
 * the vanilla-message edit ({@code event.message(null)}) and the actual fan-out through the {@link ChannelBroadcaster}
 * are both observable. The {@link AdvancementFilter} gate logic itself is unit-tested in core; here it is exercised
 * through the live event to confirm the listener wires the facts (key, recipe-ness, announceable) into it correctly.
 *
 * <p>The substitution and recipe-detection seams are also asserted directly, since a template referencing
 * {@code {title}}/{@code {description}} only renders correctly when those flatten the display components to text.
 */
class AdvancementMessageListenerTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final String VANILLA = "vanilla advancement line";
    private static final Key TRANSLATIONS = Key.key("uxmessentials", "advancement-test");

    private ServerMock server;
    private PlayerMock earner;
    private PlayerMock viewer;
    private FakeOptOut optOut;
    private @Nullable TranslationStore<MessageFormat> translations;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        server.addSimpleWorld("world");
        earner = server.addPlayer("Earner");
        viewer = server.addPlayer("Viewer");
        optOut = new FakeOptOut();
    }

    @AfterEach
    void tearDown() {
        if (translations != null) {
            GlobalTranslator.translator().removeSource(translations);
            translations = null;
        }
        MockBukkit.unmock();
    }

    /**
     * Register a single key → {@code value} translation with the {@link GlobalTranslator}, mirroring how a vanilla
     * advancement title resolves: a real title is a {@link Component#translatable} keyed by name, and the listener
     * resolves it through the translator before flattening. Removed in {@link #tearDown()} so the global state does
     * not leak between tests.
     */
    private void registerTranslation(String key, String value) {
        TranslationStore<MessageFormat> store = TranslationStore.messageFormat(TRANSLATIONS);
        store.defaultLocale(Locale.ENGLISH);
        store.register(key, Locale.ENGLISH, new MessageFormat(value, Locale.ENGLISH));
        GlobalTranslator.translator().addSource(store);
        translations = store;
    }

    @Test
    void announceableAdvancementBroadcastsTheCustomNoticeAndClearsTheVanillaLine() {
        AdvancementNoticeConfig cfg = enabled("<yellow>{player} earned [{title}]");
        PlayerAdvancementDoneEvent event = fire(
                cfg,
                AdvancementMessageListenerTest::never,
                advancement("minecraft:end/kill_dragon", true, "The End?", "Kill the dragon"));

        assertThat(event.message()).isNull();
        assertThat(PLAIN.serialize(received(viewer))).isEqualTo("Earner earned [The End?]");
        // The earner is also an online recipient and receives the broadcast.
        assertThat(PLAIN.serialize(received(earner))).isEqualTo("Earner earned [The End?]");
    }

    @Test
    void translatableTitleRendersToReadableTextNotTheRawKey() {
        // A real vanilla advancement title is a translatable component keyed by name; the listener must resolve it
        // through the GlobalTranslator before flattening, or the raw key leaks into the broadcast.
        registerTranslation("advancements.test.title", "The End?");
        AdvancementNoticeConfig cfg = enabled("{player} has made the advancement [{title}]");
        fire(
                cfg,
                AdvancementMessageListenerTest::never,
                translatableAdvancement("minecraft:end/kill_dragon", "advancements.test.title"));

        String broadcast = PLAIN.serialize(received(viewer));
        assertThat(broadcast).isEqualTo("Earner has made the advancement [The End?]");
        assertThat(broadcast).doesNotContain("advancements.test.title");
    }

    @Test
    void disabledFeatureLeavesTheVanillaLineAndBroadcastsNothing() {
        AdvancementNoticeConfig cfg = AdvancementNoticeConfig.disabled();
        PlayerAdvancementDoneEvent event = fire(
                cfg,
                AdvancementMessageListenerTest::never,
                advancement("minecraft:end/kill_dragon", true, "The End?", "desc"));

        assertThat(PLAIN.serialize(event.message())).isEqualTo(VANILLA);
        assertThat(viewer.nextComponentMessage()).isNull();
    }

    @Test
    void recipeAdvancementIsSkippedAndTheVanillaLineIsUntouched() {
        AdvancementNoticeConfig cfg = enabled("{player} {title}");
        PlayerAdvancementDoneEvent event = fire(
                cfg,
                AdvancementMessageListenerTest::never,
                advancement("minecraft:recipes/building_blocks/oak_planks", false, "Oak Planks", ""));

        assertThat(PLAIN.serialize(event.message())).isEqualTo(VANILLA);
        assertThat(viewer.nextComponentMessage()).isNull();
    }

    @Test
    void nonAnnounceableAdvancementIsSkippedWhenOnlyAnnounceable() {
        AdvancementNoticeConfig cfg = enabled("{player} {title}");
        PlayerAdvancementDoneEvent event = fire(
                cfg,
                AdvancementMessageListenerTest::never,
                advancement("minecraft:husbandry/root", false, "Husbandry", ""));

        assertThat(PLAIN.serialize(event.message())).isEqualTo(VANILLA);
        assertThat(viewer.nextComponentMessage()).isNull();
    }

    @Test
    void deniedAdvancementIsSkipped() {
        AdvancementNoticeConfig cfg = new AdvancementNoticeConfig(
                true,
                true,
                true,
                Set.of(),
                Set.of("minecraft:end/kill_dragon"),
                "{player} {title}",
                Map.of(),
                Set.of(BroadcastChannel.CHAT),
                Optional.empty());
        PlayerAdvancementDoneEvent event = fire(
                cfg,
                AdvancementMessageListenerTest::never,
                advancement("minecraft:end/kill_dragon", true, "The End?", ""));

        assertThat(PLAIN.serialize(event.message())).isEqualTo(VANILLA);
        assertThat(viewer.nextComponentMessage()).isNull();
    }

    @Test
    void advancementOutsideANonEmptyAllowListIsSkipped() {
        AdvancementNoticeConfig cfg = new AdvancementNoticeConfig(
                true,
                true,
                true,
                Set.of("minecraft:nether/all_potions"),
                Set.of(),
                "{player} {title}",
                Map.of(),
                Set.of(BroadcastChannel.CHAT),
                Optional.empty());
        PlayerAdvancementDoneEvent event = fire(
                cfg,
                AdvancementMessageListenerTest::never,
                advancement("minecraft:end/kill_dragon", true, "The End?", ""));

        assertThat(PLAIN.serialize(event.message())).isEqualTo(VANILLA);
        assertThat(viewer.nextComponentMessage()).isNull();
    }

    @Test
    void vanishedEarnerSuppressesTheVanillaLineAndBroadcastsNothing() {
        AdvancementNoticeConfig cfg = enabled("{player} {title}");
        PlayerAdvancementDoneEvent event =
                fire(cfg, p -> p.equals(earner), advancement("minecraft:end/kill_dragon", true, "The End?", ""));

        assertThat(event.message()).isNull();
        assertThat(viewer.nextComponentMessage()).isNull();
        assertThat(earner.nextComponentMessage()).isNull();
    }

    @Test
    void vanishCheckAndBroadcastResolveOnTheGlobalRegionThreadWithoutAMarshal() {
        // The event fires on the earner's region thread; the vanish read (a cross-region canSee scan) and the
        // broadcast decision must hop once onto the global thread, where the predicate then runs inline: no nested
        // CompletableFuture.get marshal of its own. The predicate records the thread-ownership it observed.
        RecordingScheduler scheduler = new RecordingScheduler();
        boolean[] testedOnGlobal = {false};
        Predicate<Player> vanished = p -> {
            testedOnGlobal[0] = scheduler.onGlobalThread();
            return false;
        };
        AdvancementNoticeConfig cfg = enabled("{player} {title}");

        PlayerAdvancementDoneEvent event =
                fire(cfg, vanished, advancement("minecraft:end/kill_dragon", true, "The End?", ""), scheduler);

        assertThat(scheduler.globalHops).isOne(); // exactly one hop carried both the vanish test and the broadcast
        assertThat(testedOnGlobal[0]).isTrue(); // the vanish read ran on the global thread, not the firing thread
        // The vanilla line is cleared on the firing thread, and the broadcast fired from the single global hop.
        assertThat(event.message()).isNull();
        assertThat(PLAIN.serialize(received(viewer))).isEqualTo("Earner The End?");
    }

    @Test
    void optedOutViewerDoesNotReceiveTheNotice() {
        optOut.optOut(viewer.getUniqueId());
        AdvancementNoticeConfig cfg = enabled("{player} {title}");
        fire(
                cfg,
                AdvancementMessageListenerTest::never,
                advancement("minecraft:end/kill_dragon", true, "The End?", ""));

        assertThat(viewer.nextComponentMessage()).isNull();
        // The opted-in earner still receives it, so the broadcast did fire: only the opted-out viewer was skipped.
        assertThat(PLAIN.serialize(received(earner))).isEqualTo("Earner The End?");
    }

    @Test
    void perAdvancementTemplateOverridesTheGlobalTemplate() {
        AdvancementNoticeConfig cfg = new AdvancementNoticeConfig(
                true,
                true,
                true,
                Set.of(),
                Set.of(),
                "global {title}",
                Map.of("minecraft:end/kill_dragon", "<gold>{player} slew the dragon!"),
                Set.of(BroadcastChannel.CHAT),
                Optional.empty());
        fire(
                cfg,
                AdvancementMessageListenerTest::never,
                advancement("minecraft:end/kill_dragon", true, "The End?", ""));

        assertThat(PLAIN.serialize(received(viewer))).isEqualTo("Earner slew the dragon!");
    }

    @Test
    void substituteFlattensTitleAndDescriptionToPlainText() {
        AdvancementDisplay display = mock(AdvancementDisplay.class);
        lenient().when(display.title()).thenReturn(Component.text("Adventuring Time"));
        lenient().when(display.description()).thenReturn(Component.text("Discover every biome"));

        String result = AdvancementMessageListener.substitute(
                "{player}: {title}, {description} ({advancement})",
                "Steve",
                "minecraft:adventure/adventuring_time",
                display,
                Locale.ENGLISH);

        assertThat(result)
                .isEqualTo("Steve: Adventuring Time, Discover every biome (minecraft:adventure/adventuring_time)");
    }

    @Test
    void substituteToleratesANullDisplayWithEmptyTitleAndDescription() {
        String result = AdvancementMessageListener.substitute(
                "{player} [{title}] {description}", "Steve", "minecraft:recipes/misc/map", null, Locale.ENGLISH);

        assertThat(result).isEqualTo("Steve [] ");
    }

    @Test
    void isRecipeMatchesNamespacedRecipeKeys() {
        assertThat(AdvancementMessageListener.isRecipe("minecraft:recipes/building_blocks/oak_planks"))
                .isTrue();
        assertThat(AdvancementMessageListener.isRecipe("custom:recipes/foo")).isTrue();
        assertThat(AdvancementMessageListener.isRecipe("minecraft:end/kill_dragon"))
                .isFalse();
    }

    private PlayerAdvancementDoneEvent fire(
            AdvancementNoticeConfig cfg, Predicate<Player> vanished, Advancement advancement) {
        return fire(cfg, vanished, advancement, new RecordingScheduler());
    }

    private PlayerAdvancementDoneEvent fire(
            AdvancementNoticeConfig cfg,
            Predicate<Player> vanished,
            Advancement advancement,
            RecordingScheduler scheduler) {
        ChannelBroadcaster channels = new ChannelBroadcaster(new SyncScheduler(), display());
        AdvancementMessageListener listener =
                new AdvancementMessageListener(() -> cfg, channels, optOut, vanished, scheduler, Locale.ENGLISH);
        PlayerAdvancementDoneEvent event = new PlayerAdvancementDoneEvent(earner, advancement, Component.text(VANILLA));
        listener.onAdvancement(event);
        return event;
    }

    private static Advancement advancement(String key, boolean announceable, String title, String description) {
        Advancement advancement = mock(Advancement.class);
        lenient().when(advancement.getKey()).thenReturn(NamespacedKey.fromString(key));
        AdvancementDisplay display = mock(AdvancementDisplay.class);
        lenient().when(display.doesAnnounceToChat()).thenReturn(announceable);
        lenient().when(display.title()).thenReturn(Component.text(title));
        lenient().when(display.description()).thenReturn(Component.text(description));
        lenient().when(advancement.getDisplay()).thenReturn(display);
        return advancement;
    }

    /**
     * An announceable advancement whose title is a {@link Component#translatable} keyed by {@code titleKey}, as a real
     * vanilla advancement title is. Unlike the literal-title {@link #advancement} helper, this exercises the
     * translator-render path.
     */
    private static Advancement translatableAdvancement(String key, String titleKey) {
        Advancement advancement = mock(Advancement.class);
        lenient().when(advancement.getKey()).thenReturn(NamespacedKey.fromString(key));
        AdvancementDisplay display = mock(AdvancementDisplay.class);
        lenient().when(display.doesAnnounceToChat()).thenReturn(true);
        lenient().when(display.title()).thenReturn(Component.translatable(titleKey));
        lenient().when(display.description()).thenReturn(Component.empty());
        lenient().when(advancement.getDisplay()).thenReturn(display);
        return advancement;
    }

    private static Component received(PlayerMock player) {
        Component message = player.nextComponentMessage();
        assertThat(message).as("expected a broadcast for %s", player.getName()).isNotNull();
        return message;
    }

    private static AdvancementNoticeConfig enabled(String template) {
        return new AdvancementNoticeConfig(
                true,
                true,
                true,
                Set.of(),
                Set.of(),
                template,
                Map.of(),
                Set.of(BroadcastChannel.CHAT),
                Optional.empty());
    }

    /** A vanish predicate under which no one is vanished, passed by method reference. */
    private static boolean never(Player player) {
        return false;
    }

    private static ChannelDisplay display() {
        return new ChannelDisplay(100, 500, 100, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS, 1);
    }

    /** Opt-out store backed by a UUID set; absent UUID means opted-in (receives broadcasts). */
    private static final class FakeOptOut implements BroadcastOptOutStore {
        private final Set<UUID> out = ConcurrentHashMap.newKeySet();

        void optOut(UUID uuid) {
            out.add(uuid);
        }

        @Override
        public boolean receivesBroadcasts(PlayerRef who) {
            return !out.contains(who.uuid());
        }

        @Override
        public boolean toggle(PlayerRef who) {
            return out.add(who.uuid()) ? true : !out.remove(who.uuid());
        }

        @Override
        public void forget(PlayerRef who) {
            out.remove(who.uuid());
        }
    }

    /**
     * Runs {@code onGlobal} inline while counting the hops and reporting that the task body is on the global thread,
     * so a test can assert the vanish read was marshalled there rather than run on the firing thread. The other hops
     * (none exercised on the listener's own scheduler) simply run inline.
     */
    private static final class RecordingScheduler implements Scheduler {
        private int globalHops;
        private boolean onGlobal;

        @Override
        public boolean onGlobalThread() {
            return onGlobal;
        }

        @Override
        public void onGlobal(Runnable task) {
            globalHops++;
            boolean previous = onGlobal;
            onGlobal = true;
            try {
                task.run();
            } finally {
                onGlobal = previous;
            }
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }

    /** Runs every hop inline so the entity-thread fan-out executes within the test. */
    private static final class SyncScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }
}
