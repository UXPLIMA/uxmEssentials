package com.uxplima.uxmessentials.vanish.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.mojang.brigadier.CommandDispatcher;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.vanish.adapter.outbound.BukkitVanishLevelResolver;
import com.uxplima.uxmessentials.vanish.adapter.outbound.BukkitVanishView;
import com.uxplima.uxmessentials.vanish.adapter.outbound.InMemoryVanishStore;
import com.uxplima.uxmessentials.vanish.adapter.outbound.PdcVanishPickup;
import com.uxplima.uxmessentials.vanish.adapter.outbound.VanishActionBar;
import com.uxplima.uxmessentials.vanish.adapter.outbound.VanishConnectionMessenger;
import com.uxplima.uxmessentials.vanish.application.ListVanished;
import com.uxplima.uxmessentials.vanish.application.ToggleVanish;
import com.uxplima.uxmessentials.vanish.application.VanishConfig;
import com.uxplima.uxmessentials.vanish.application.port.NetworkVanishStore;
import com.uxplima.uxmessentials.vanish.application.port.VanishBuffs;
import com.uxplima.uxmessentials.vanish.application.port.VanishBus;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of {@code /vanish <player>} and {@code /vanish list}: dispatching {@code /vanish <player>} flips
 * another player's vanish and confirms to the actor; {@code /vanish list} is gated by {@code uxmessentials.vanish.list}
 * and {@code /vanish <player>} by {@code uxmessentials.vanish.others}; the list shows the hidden players the caller may
 * see. A synchronous inline scheduler runs the view's entity hop deterministically.
 */
class VanishCommandTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    // enabled, silentChests, pickupItems, nightVision, allowFlight, noHunger, noDamage, mobTarget, fakeJoinQuit,
    // actionBar, joinVanished, fake-quit, fake-join, fake-quit-staff, fake-join-staff, cross-server,
    // read-foreign-vanish, foreign-vanish-level.
    private static final VanishConfig CONFIG = new VanishConfig(
            true,
            true,
            false,
            true,
            true,
            true,
            true,
            true,
            true,
            true,
            false,
            "{player} left",
            "{player} joined",
            "",
            "",
            false,
            true,
            1);

    private ServerMock server;
    private InMemoryVanishStore store;
    private RecordingSink sink;
    private VanishCommand command;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        InlineScheduler scheduler = new InlineScheduler();
        store = new InMemoryVanishStore();
        sink = new RecordingSink();
        BukkitVanishLevelResolver levels = new BukkitVanishLevelResolver();
        BukkitVanishView view = new BukkitVanishView(MockBukkit.createMockPlugin(), scheduler, levels);
        ToggleVanish toggleVanish = new ToggleVanish(
                store,
                view,
                levels,
                new Notifier(new KeyMessages(), new DiscardingSink()),
                new NoopBuffs(),
                VanishBus.disabled(),
                event -> {});
        ListVanished listVanished = new ListVanished(store, levels, NetworkVanishStore.empty());
        PdcVanishPickup pickup = new PdcVanishPickup(server, false);
        VanishConnectionMessenger messenger = new VanishConnectionMessenger(scheduler, sink, levels, CONFIG);
        VanishActionBar actionBar = new VanishActionBar(server, scheduler, new KeyMessages(), store, CONFIG);
        command = new VanishCommand(
                toggleVanish,
                listVanished,
                pickup,
                messenger,
                actionBar,
                server,
                new KeyMessages(),
                id -> Optional.empty());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void vanishOtherTogglesAnotherPlayerAndConfirmsToTheActor() {
        PlayerMock bob = server.addPlayer("Bob");
        PlayerMock actor = server.addPlayer("Staff");
        actor.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.vanish.use", true);
        actor.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.vanish.others", true);

        execute(CommandSourceStackMock.from(actor), "vanish Bob");

        assertThat(store.isVanished(bob.getUniqueId())).isTrue();
        assertThat(lastMessage(actor)).contains("vanish.other-on").contains("player=Bob");
    }

    @Test
    void theListNodeIsGatedByTheListPermission() {
        PlayerMock permitted = server.addPlayer("Permitted");
        permitted.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.vanish.list", true);
        PlayerMock denied = server.addPlayer("Denied");

        var listNode = command.build().getChild("list");
        assertThat(listNode.getRequirement().test(CommandSourceStackMock.from(permitted)))
                .isTrue();
        assertThat(listNode.getRequirement().test(CommandSourceStackMock.from(denied)))
                .isFalse();
    }

    @Test
    void theOthersNodeIsGatedByTheOthersPermission() {
        PlayerMock permitted = server.addPlayer("Permitted");
        permitted.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.vanish.others", true);
        PlayerMock denied = server.addPlayer("Denied");

        var othersNode = command.build().getChild("target");
        assertThat(othersNode.getRequirement().test(CommandSourceStackMock.from(permitted)))
                .isTrue();
        assertThat(othersNode.getRequirement().test(CommandSourceStackMock.from(denied)))
                .isFalse();
    }

    @Test
    void listShowsTheVanishedPlayersTheCallerCanSee() {
        PlayerMock bob = server.addPlayer("Bob");
        store.vanish(bob.getUniqueId(), VanishLevel.DEFAULT);
        PlayerMock caller = server.addPlayer("Caller");
        caller.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.vanish.use", true);
        caller.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.vanish.list", true);
        caller.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.vanish.see", true); // see level 1

        execute(CommandSourceStackMock.from(caller), "vanish list");

        assertThat(lastMessage(caller)).contains("vanish.list").contains("Bob").contains("count=1");
    }

    @Test
    void pickupToggleFlipsTheCallersPreferenceAndConfirms() {
        PlayerMock caller = server.addPlayer("Caller");
        caller.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.vanish.use", true);

        execute(CommandSourceStackMock.from(caller), "vanish pickup"); // default off → toggle on
        assertThat(lastMessage(caller)).contains("vanish.pickup-on");

        execute(CommandSourceStackMock.from(caller), "vanish pickup"); // on → toggle off
        assertThat(lastMessage(caller)).contains("vanish.pickup-off");

        execute(CommandSourceStackMock.from(caller), "vanish pickup off"); // absolute off is idempotent
        assertThat(lastMessage(caller)).contains("vanish.pickup-off");
    }

    @Test
    void aPlainVanishBroadcastsAFakeQuitToOtherPlayers() {
        PlayerMock caller = server.addPlayer("Caller");
        caller.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.vanish.use", true);
        PlayerMock observer = server.addPlayer("Observer");

        execute(CommandSourceStackMock.from(caller), "vanish");

        assertThat(sink.received(observer.getUniqueId())).anyMatch(line -> line.contains("Caller left"));
        assertThat(sink.received(caller.getUniqueId())).noneMatch(line -> line.contains("Caller left"));
    }

    @Test
    void theSilentFlagSkipsTheFakeBroadcast() {
        PlayerMock caller = server.addPlayer("Caller");
        caller.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.vanish.use", true);
        caller.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.vanish.silent", true);
        PlayerMock observer = server.addPlayer("Observer");

        execute(CommandSourceStackMock.from(caller), "vanish -s");

        assertThat(store.isVanished(caller.getUniqueId())).isTrue(); // still vanished
        assertThat(sink.received(observer.getUniqueId())).isEmpty(); // but nobody was told
    }

    @Test
    void theSilentFlagNodeIsGatedBySilentPermission() {
        PlayerMock permitted = server.addPlayer("Permitted");
        permitted.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.vanish.silent", true);
        PlayerMock denied = server.addPlayer("Denied");

        var silentNode = command.build().getChild("-s");
        assertThat(silentNode.getRequirement().test(CommandSourceStackMock.from(permitted)))
                .isTrue();
        assertThat(silentNode.getRequirement().test(CommandSourceStackMock.from(denied)))
                .isFalse();
    }

    @Test
    void listReportsNobodyWhenTheCallerSeesNoVanishedPlayer() {
        PlayerMock caller = server.addPlayer("Caller");
        caller.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.vanish.use", true);
        caller.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.vanish.list", true);

        execute(CommandSourceStackMock.from(caller), "vanish list");

        assertThat(lastMessage(caller)).contains("vanish.list-empty");
    }

    private void execute(CommandSourceStack source, String input) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command.build());
        try {
            dispatcher.execute(input, source);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    private static String lastMessage(PlayerMock player) {
        String last = null;
        String next;
        while ((next = player.nextMessage()) != null) {
            last = next;
        }
        return last == null ? "" : PLAIN.serialize(MiniMessage.miniMessage().deserialize(last));
    }

    /** A scheduler that runs every task inline so the entity-thread hop fires at once. */
    private static final class InlineScheduler implements Scheduler {
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

    /** Echoes the resolved key and placeholders so the rendered reply is assertable. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            StringBuilder out = new StringBuilder(key.key());
            placeholders.forEach(
                    (name, value) -> out.append(' ').append(name).append('=').append(value));
            return out.toString();
        }
    }

    private static final class DiscardingSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            // discarded: the target's own confirmation is not under test here
        }
    }

    /** Records every delivery per viewer so the fake-broadcast fan-out is assertable. */
    private static final class RecordingSink implements MessageSink {
        private final ConcurrentHashMap<UUID, List<String>> delivered = new ConcurrentHashMap<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            delivered.computeIfAbsent(viewer.uuid(), id -> new ArrayList<>()).add(renderedText);
        }

        List<String> received(UUID viewer) {
            return delivered.getOrDefault(viewer, List.of());
        }
    }

    /** A buffs port that does nothing: the command routing under test does not assert on buffs. */
    private static final class NoopBuffs implements VanishBuffs {
        @Override
        public void apply(PlayerRef who) {}

        @Override
        public void clear(PlayerRef who) {}
    }
}
