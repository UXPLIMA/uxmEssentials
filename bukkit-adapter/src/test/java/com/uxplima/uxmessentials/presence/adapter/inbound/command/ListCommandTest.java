package com.uxplima.uxmessentials.presence.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.Map;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.mojang.brigadier.CommandDispatcher;
import com.uxplima.uxmessentials.presence.adapter.PresenceServices;
import com.uxplima.uxmessentials.presence.adapter.inbound.gui.OnlinePlayerListView;
import com.uxplima.uxmessentials.presence.application.ClearAfkOnActivity;
import com.uxplima.uxmessentials.presence.application.ClearNick;
import com.uxplima.uxmessentials.presence.application.MarkAfk;
import com.uxplima.uxmessentials.presence.application.PresenceMessageKey;
import com.uxplima.uxmessentials.presence.application.SetNick;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of {@code /list}: the read-only online-player listing, vanish-aware through the same
 * {@code canSee} graph the presence {@code VisibilityApplier} drives. A viewing player sees only the players they
 * can see, with the count matching; a vanished player hidden from the viewer drops out of both the line and the
 * count; the console (no {@code canSee} graph) sees everyone. The {@link Messages} fake echoes the resolved key
 * with its placeholders substituted so the rendered line is observable through the player's message queue.
 */
class ListCommandTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final String PERMISSION = "uxmessentials.list.use";

    private ServerMock server;
    private ListCommand command;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        PresenceServices services = new PresenceServices(
                mock(MarkAfk.class), mock(ClearAfkOnActivity.class), mock(SetNick.class), mock(ClearNick.class));
        command = new ListCommand(services, new EchoMessages(), new InlineScheduler());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theLiteralIsList() {
        assertThat(command.build().getLiteral()).isEqualTo("list");
    }

    @Test
    void withoutAGuiViewTheGuiRootIsAbsentSoTheBareRootStaysChat() {
        // No view wired: the GuiRootBinding installs nothing, so the bare /list keeps its chat executor.
        assertThat(command.guiRoot()).isEmpty();
    }

    @Test
    void withAGuiViewBareListOpensTheHeadGridWhileTheChatFormStillWorks() {
        org.bukkit.plugin.Plugin plugin = MockBukkit.createMockPlugin();
        // The head grid is an engine list, so the engine's listener is installed; opening it produces a MenuHolder.
        com.uxplima.uxmessentials.shared.menu.TestMenuEngine engine =
                com.uxplima.uxmessentials.shared.menu.TestMenuEngine.create(
                        new KeyEchoMessages(), new InlineScheduler());
        engine.installListener(plugin);
        OnlinePlayerListView view = new OnlinePlayerListView(
                engine.menus(),
                new com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText(new KeyEchoMessages()),
                new InlineScheduler(),
                com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout.paginatedDefault(
                        org.bukkit.Material.PLAYER_HEAD));
        ListCommand guiCommand = new ListCommand(services(), new KeyEchoMessages(), new InlineScheduler(), view);
        // The gui opener is exposed for the bare root; firing it opens the head grid.
        assertThat(guiCommand.guiRoot()).isPresent();

        PlayerMock alice = server.addPlayer("Alice");
        server.addPlayer("Bob");
        alice.addAttachment(plugin, PERMISSION, true);

        com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher =
                new com.mojang.brigadier.CommandDispatcher<>();
        com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> node = guiCommand.build();
        // Install the gui opener on the bare root the way the GuiRootBinding would when gui is on.
        dispatcher.getRoot().addChild(rebindRoot(node, guiCommand.guiRoot().orElseThrow()));
        execute(dispatcher, CommandSourceStackMock.from(alice), "list");

        assertThat(alice.getOpenInventory().getTopInventory().getHolder())
                .isInstanceOf(com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder.class);
    }

    private static com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> rebindRoot(
            com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> node,
            com.mojang.brigadier.Command<CommandSourceStack> opener) {
        com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> rebuilt =
                com.mojang.brigadier.builder.LiteralArgumentBuilder.<CommandSourceStack>literal(node.getLiteral())
                        .requires(node.getRequirement())
                        .executes(opener);
        return rebuilt.build();
    }

    private void execute(
            com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher,
            CommandSourceStack source,
            String input) {
        try {
            dispatcher.execute(input, source);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    private PresenceServices services() {
        return new PresenceServices(
                mock(MarkAfk.class), mock(ClearAfkOnActivity.class), mock(SetNick.class), mock(ClearNick.class));
    }

    @Test
    void aViewerSeesEveryOnlinePlayerTheyCanSee() {
        PlayerMock alice = server.addPlayer("Alice");
        server.addPlayer("Bob");
        alice.addAttachment(MockBukkit.createMockPlugin(), PERMISSION, true);

        execute(CommandSourceStackMock.from(alice), "list");

        String line = PLAIN.serialize(alice.nextComponentMessage());
        assertThat(line).contains("count=2").contains("Alice").contains("Bob");
    }

    @Test
    void aVanishedPlayerTheViewerCannotSeeIsExcluded() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock ghost = server.addPlayer("Ghost");
        alice.addAttachment(MockBukkit.createMockPlugin(), PERMISSION, true);
        alice.hidePlayer(MockBukkit.createMockPlugin(), ghost); // alice's canSee graph drops the vanished ghost

        execute(CommandSourceStackMock.from(alice), "list");

        String line = PLAIN.serialize(alice.nextComponentMessage());
        assertThat(line).contains("count=1").contains("Alice").doesNotContain("Ghost");
    }

    @Test
    void theConsoleSeesEveryOnlinePlayer() {
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock ghost = server.addPlayer("Ghost");
        alice.hidePlayer(MockBukkit.createMockPlugin(), ghost); // hidden from alice, but the console has no graph
        ConsoleCommandSenderMock console = server.getConsoleSender();

        execute(CommandSourceStackMock.from(console), "list");

        String line = PLAIN.serialize(console.nextComponentMessage());
        assertThat(line).contains("count=2").contains("Alice").contains("Ghost");
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

    /** Resolves any key to its own string: used by the GUI path, which raises several different keys. */
    private static final class KeyEchoMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** Echoes the key's placeholders as a single MiniMessage-safe line so the rendered output is assertable. */
    private static final class EchoMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            if (key.key().equals("prefix")) {
                return "";
            }
            assertThat(key).isEqualTo(PresenceMessageKey.LIST_PLAYERS);
            return "count=" + placeholders.get("count") + " players=" + placeholders.get("players");
        }
    }

    /** Runs the global-thread roster scan and the sender-thread reply inline so the rendered line is observable. */
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
}
