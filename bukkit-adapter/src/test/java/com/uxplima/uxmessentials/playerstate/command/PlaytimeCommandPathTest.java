package com.uxplima.uxmessentials.playerstate.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.mojang.brigadier.CommandDispatcher;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerStateServices;
import com.uxplima.uxmessentials.playerstate.adapter.inbound.command.PlaytimeCommand;
import com.uxplima.uxmessentials.playerstate.application.Burn;
import com.uxplima.uxmessentials.playerstate.application.ClearInventory;
import com.uxplima.uxmessentials.playerstate.application.Extinguish;
import com.uxplima.uxmessentials.playerstate.application.Feed;
import com.uxplima.uxmessentials.playerstate.application.Freeze;
import com.uxplima.uxmessentials.playerstate.application.Heal;
import com.uxplima.uxmessentials.playerstate.application.ListNearby;
import com.uxplima.uxmessentials.playerstate.application.OpenContainer;
import com.uxplima.uxmessentials.playerstate.application.ResetPlaytime;
import com.uxplima.uxmessentials.playerstate.application.ResetRest;
import com.uxplima.uxmessentials.playerstate.application.SetAir;
import com.uxplima.uxmessentials.playerstate.application.SetExperience;
import com.uxplima.uxmessentials.playerstate.application.SetFoodLevel;
import com.uxplima.uxmessentials.playerstate.application.SetGamemode;
import com.uxplima.uxmessentials.playerstate.application.SetHealth;
import com.uxplima.uxmessentials.playerstate.application.SetPersonalTime;
import com.uxplima.uxmessentials.playerstate.application.SetPersonalWeather;
import com.uxplima.uxmessentials.playerstate.application.SetSpeed;
import com.uxplima.uxmessentials.playerstate.application.ShowPing;
import com.uxplima.uxmessentials.playerstate.application.ShowPlaytime;
import com.uxplima.uxmessentials.playerstate.application.ShowPosition;
import com.uxplima.uxmessentials.playerstate.application.Suicide;
import com.uxplima.uxmessentials.playerstate.application.ToggleClearInventoryConfirm;
import com.uxplima.uxmessentials.playerstate.application.ToggleFly;
import com.uxplima.uxmessentials.playerstate.application.ToggleGlow;
import com.uxplima.uxmessentials.playerstate.application.ToggleGod;
import com.uxplima.uxmessentials.playerstate.application.ToggleNightVision;
import com.uxplima.uxmessentials.playerstate.application.port.PlayerInfo;
import com.uxplima.uxmessentials.playerstate.application.port.PlaytimeRepository;
import com.uxplima.uxmessentials.playerstate.domain.PlaytimeSummary;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of {@code /playtime}, {@code /playtime reset} and {@code /playtime resetall}, driving the
 * real {@code ShowPlaytime} and {@code ResetPlaytime} use cases over a fake {@link PlaytimeRepository}. It proves
 * the breakdown is rendered with the today/week/month/all-time placeholders; that the {@code player} target is a
 * plain online-name word (never a selector, so {@code @a} never reaches a single-target read) and resolves a named
 * target under the {@code .others} node; that {@code reset} and {@code resetall} are gated by
 * {@code uxmessentials.playtime.reset} (a sender without it cannot run either subcommand); that with the node a
 * self-reset wipes one ledger and resetall wipes every ledger, each confirming; and that the catalog {@code gui}
 * flag swaps the bare-root chat executor for the GUI panel opener while leaving it for the chat fallback when off.
 */
class PlaytimeCommandPathTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final String USE = "uxmessentials.playtime.use";
    private static final String RESET = "uxmessentials.playtime.reset";
    private static final String OTHERS = "uxmessentials.playerstate.others";

    private ServerMock server;
    private PlaytimeCommand command;
    private FakePlaytimeRepository repo;
    private ShowPlaytime show;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        repo = new FakePlaytimeRepository();
        Notifier notifier = new Notifier(new EchoMessages(), new BukkitMessageSink());
        show = new ShowPlaytime(repo, new EmptyInfo(), notifier, Clock.systemUTC());
        ResetPlaytime reset = new ResetPlaytime(repo, notifier);
        PlayerStateServices services = new PlayerStateServices(
                mock(ToggleGod.class),
                mock(ToggleFly.class),
                mock(Heal.class),
                mock(Feed.class),
                mock(SetFoodLevel.class),
                mock(SetHealth.class),
                mock(SetGamemode.class),
                mock(SetSpeed.class),
                mock(Extinguish.class),
                mock(ClearInventory.class),
                mock(ToggleClearInventoryConfirm.class),
                mock(OpenContainer.class),
                mock(Suicide.class),
                mock(ListNearby.class),
                mock(ToggleNightVision.class),
                mock(ToggleGlow.class),
                mock(SetPersonalTime.class),
                mock(SetPersonalWeather.class),
                mock(SetExperience.class),
                mock(SetAir.class),
                mock(Burn.class),
                mock(Freeze.class),
                mock(ShowPosition.class),
                mock(ShowPing.class),
                show,
                reset,
                mock(ResetRest.class),
                mock(PlayerLookup.class));
        command = new PlaytimeCommand(services, new EchoMessages());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theLiteralIsPlaytimeWithResetAndResetAllSubcommands() {
        assertThat(command.build().getLiteral()).isEqualTo("playtime");
        assertThat(command.build().getChild("reset")).isNotNull();
        assertThat(command.build().getChild("resetall")).isNotNull();
    }

    @Test
    void theNameArgumentIsAPlainWordNotASelector() {
        // The /playtime player argument completes against the online roster as a plain word; it is not a Paper
        // entity-selector node, so @a/@p/@s never parse here: showing one player's stats is a single-target read.
        var playerArg = command.build().getChild("player");
        assertThat(playerArg).isNotNull();
        assertThat(playerArg.getName()).isEqualTo("player");
        assertThat(playerArg).isInstanceOf(com.mojang.brigadier.tree.ArgumentCommandNode.class);
        var argNode = (com.mojang.brigadier.tree.ArgumentCommandNode<?, ?>) playerArg;
        assertThat(argNode.getType()).isInstanceOf(com.mojang.brigadier.arguments.StringArgumentType.class);
    }

    @Test
    void aPlainNameResolvesTheNamedTargetWithOthers() {
        PlayerMock viewer = server.addPlayer("Viewer");
        viewer.addAttachment(MockBukkit.createMockPlugin(), USE, true);
        viewer.addAttachment(MockBukkit.createMockPlugin(), OTHERS, true);
        PlayerMock other = server.addPlayer("Other");
        repo.addSeconds(other.getUniqueId(), LocalDate.now(java.time.ZoneOffset.UTC), 60L, 0L);

        execute(CommandSourceStackMock.from(viewer), "playtime Other");

        String line = PLAIN.serialize(viewer.nextComponentMessage());
        assertThat(line).contains("playerstate.playtime.show-other").contains("player=Other");
    }

    @Test
    void resetAllWithoutTheResetNodeDoesNothing() {
        PlayerMock player = server.addPlayer("Player");
        player.addAttachment(MockBukkit.createMockPlugin(), USE, true); // has /playtime, not the reset node

        boolean parsed = tryExecute(CommandSourceStackMock.from(player), "playtime resetall");

        assertThat(parsed)
                .as("the resetall subcommand is unreachable without the reset node")
                .isFalse();
        assertThat(repo.resetAllCalls).isZero();
    }

    @Test
    void resetAllWithTheNodeWipesEveryLedgerAndConfirms() {
        PlayerMock player = server.addPlayer("Player");
        player.addAttachment(MockBukkit.createMockPlugin(), USE, true);
        player.addAttachment(MockBukkit.createMockPlugin(), RESET, true);
        repo.addSeconds(player.getUniqueId(), LocalDate.now(java.time.ZoneOffset.UTC), 100L, 0L);

        execute(CommandSourceStackMock.from(player), "playtime resetall");

        assertThat(repo.resetAllCalls).isEqualTo(1);
        String line = PLAIN.serialize(player.nextComponentMessage());
        assertThat(line).contains("playerstate.playtime.reset-all");
    }

    @Test
    void showRendersTheBreakdown() {
        PlayerMock player = server.addPlayer("Player");
        player.addAttachment(MockBukkit.createMockPlugin(), USE, true);

        execute(CommandSourceStackMock.from(player), "playtime");

        String line = PLAIN.serialize(player.nextComponentMessage());
        assertThat(line)
                .contains("playerstate.playtime.show")
                .contains("today_active=")
                .contains("week_active=")
                .contains("month_active=")
                .contains("total_afk=");
    }

    @Test
    void resetWithoutTheResetNodeDoesNothing() {
        PlayerMock player = server.addPlayer("Player");
        player.addAttachment(MockBukkit.createMockPlugin(), USE, true); // has /playtime, not the reset node
        repo.addSeconds(player.getUniqueId(), LocalDate.now(java.time.ZoneOffset.UTC), 100L, 0L);

        boolean parsed = tryExecute(CommandSourceStackMock.from(player), "playtime reset");

        assertThat(parsed)
                .as("the reset subcommand is unreachable without the reset node")
                .isFalse();
        assertThat(repo.reset).isEmpty();
    }

    @Test
    void resetWithTheNodeWipesTheLedgerAndConfirms() {
        PlayerMock player = server.addPlayer("Player");
        player.addAttachment(MockBukkit.createMockPlugin(), USE, true);
        player.addAttachment(MockBukkit.createMockPlugin(), RESET, true);
        repo.addSeconds(player.getUniqueId(), LocalDate.now(java.time.ZoneOffset.UTC), 100L, 0L);

        execute(CommandSourceStackMock.from(player), "playtime reset");

        assertThat(repo.reset).containsExactly(player.getUniqueId());
        String line = PLAIN.serialize(player.nextComponentMessage());
        assertThat(line).contains("playerstate.playtime.reset");
    }

    @Test
    void bareRootGainsTheGuiOpenerWhenGuiOn(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dataFolder) {
        PlaytimeCommand guiCommand = withView(dataFolder);
        var node = binding("playtime", true).wrap(guiCommand).build();

        assertThat(guiCommand.guiRoot()).isPresent();
        // The bare root gains the opener and the reset/resetall/name children carry across.
        assertThat(node.getCommand()).isNotNull();
        assertThat(node.getChild("reset")).isNotNull();
        assertThat(node.getChild("resetall")).isNotNull();
        assertThat(node.getChild("player")).isNotNull();
    }

    @Test
    void bareRootKeepsTheChatExecutorWhenGuiOff(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dataFolder) {
        PlaytimeCommand guiCommand = withView(dataFolder);
        var node = binding("playtime", false).wrap(guiCommand).build();

        // gui off leaves the command's own bare-root chat show executor untouched.
        assertThat(node.getCommand()).isNotNull();
        assertThat(node.getChild("player")).isNotNull();
    }

    @Test
    void aPlaytimeWithNoGuiExposesNoOpener() {
        assertThat(command.guiRoot()).isEmpty();
    }

    /** A {@link PlaytimeCommand} carrying a real {@link PlaytimeView} over the test fakes and a temp gui folder. */
    private PlaytimeCommand withView(java.nio.file.Path dataFolder) {
        com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText guiText =
                new com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText(new EchoMessages());
        com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts layouts =
                new com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts(dataFolder, new NoopLogger());
        var view = new com.uxplima.uxmessentials.playerstate.adapter.inbound.gui.PlaytimeView(
                guiText, new SyncScheduler(), layouts, new EchoMessages(), show, engine(guiText));
        return new PlaytimeCommand(servicesWith(), new EchoMessages(), view);
    }

    /** A minimal editor-capable engine; this test only wires the view into a command and never opens it. */
    private com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus engine(
            com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText guiText) {
        var editorRenderer =
                new com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.EditorRenderer(guiText);
        var itemRenderer = new com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer(
                guiText, new com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry());
        var renderer = new com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer(
                itemRenderer,
                new com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ConditionRegistry());
        return new com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus(
                renderer,
                new SyncScheduler(),
                new com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ListSourceRegistry(),
                editorRenderer);
    }

    /** The same mock service bundle as {@link #setUp}, with the real show/reset use cases wired. */
    private PlayerStateServices servicesWith() {
        return new PlayerStateServices(
                mock(ToggleGod.class),
                mock(ToggleFly.class),
                mock(Heal.class),
                mock(Feed.class),
                mock(SetFoodLevel.class),
                mock(SetHealth.class),
                mock(SetGamemode.class),
                mock(SetSpeed.class),
                mock(Extinguish.class),
                mock(ClearInventory.class),
                mock(ToggleClearInventoryConfirm.class),
                mock(OpenContainer.class),
                mock(Suicide.class),
                mock(ListNearby.class),
                mock(ToggleNightVision.class),
                mock(ToggleGlow.class),
                mock(SetPersonalTime.class),
                mock(SetPersonalWeather.class),
                mock(SetExperience.class),
                mock(SetAir.class),
                mock(Burn.class),
                mock(Freeze.class),
                mock(ShowPosition.class),
                mock(ShowPing.class),
                show,
                new ResetPlaytime(repo, new Notifier(new EchoMessages(), new BukkitMessageSink())),
                mock(ResetRest.class),
                mock(PlayerLookup.class));
    }

    private static com.uxplima.uxmessentials.shared.adapter.inbound.command.GuiRootBinding binding(
            String id, boolean gui) {
        return new com.uxplima.uxmessentials.shared.adapter.inbound.command.GuiRootBinding(Map.of(
                id,
                new com.uxplima.uxmessentials.shared.application.command.EffectiveCommand(
                        new com.uxplima.uxmessentials.shared.application.command.CommandId(id),
                        id,
                        java.util.List.of(),
                        true,
                        gui)));
    }

    private void execute(CommandSourceStack source, String input) {
        if (!tryExecute(source, input)) {
            throw new AssertionError("command did not parse: " + input);
        }
    }

    private boolean tryExecute(CommandSourceStack source, String input) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command.build());
        try {
            return dispatcher.execute(input, source) > 0;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException unreachable) {
            // An unmet requirement leaves the node unknown, surfacing as a parse failure, the gate held.
            return false;
        }
    }

    /** Echoes the full catalog key and its placeholders as one line so the rendered reply is assertable. */
    private static final class EchoMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            StringBuilder out = new StringBuilder(key.key());
            placeholders.forEach(
                    (name, value) -> out.append(' ').append(name).append('=').append(value));
            return out.toString();
        }
    }

    /** A message sink that delivers the rendered string to the live MockBukkit player as a plain component. */
    private static final class BukkitMessageSink
            implements com.uxplima.uxmessentials.shared.application.port.MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(viewer.uuid());
            if (player != null) {
                player.sendMessage(net.kyori.adventure.text.Component.text(renderedText));
            }
        }
    }

    /** Runs every scheduled hop inline, so the GUI build path is exercised synchronously in the opener tests. */
    private static final class SyncScheduler implements com.uxplima.uxmessentials.shared.application.port.Scheduler {
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
        public void asyncAfter(java.time.Duration delay, Runnable task) {
            task.run();
        }
    }

    /** A logger that swallows output, for the temp-folder GuiLayouts in the opener tests. */
    private static final class NoopLogger implements com.uxplima.uxmessentials.shared.application.port.Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }

    /** A {@link PlayerInfo} with no live data: the lifetime line falls back to the tracked all-time total. */
    private static final class EmptyInfo implements PlayerInfo {
        @Override
        public java.util.Optional<Position> positionOf(PlayerRef who) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<Integer> pingOf(PlayerRef who) {
            return java.util.Optional.empty();
        }

        @Override
        public java.util.Optional<java.time.Duration> playtimeOf(PlayerRef who) {
            return java.util.Optional.empty();
        }
    }

    /** A map-backed ledger recording resets, for the command-path assertions. */
    private static final class FakePlaytimeRepository implements PlaytimeRepository {
        private final java.util.List<UUID> reset = new java.util.ArrayList<>();
        private int resetAllCalls;
        private final Map<UUID, long[]> totals = new ConcurrentHashMap<>();

        @Override
        public void addSeconds(UUID uuid, LocalDate day, long activeDelta, long afkDelta) {
            totals.merge(uuid, new long[] {activeDelta, afkDelta}, (a, b) -> new long[] {a[0] + b[0], a[1] + b[1]});
        }

        @Override
        public PlaytimeSummary summaryOf(UUID uuid, LocalDate today) {
            long[] t = totals.getOrDefault(uuid, new long[] {0L, 0L});
            return PlaytimeSummary.ofSeconds(t[0], t[1], t[0], t[1], t[0], t[1], t[0], t[1]);
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
}
