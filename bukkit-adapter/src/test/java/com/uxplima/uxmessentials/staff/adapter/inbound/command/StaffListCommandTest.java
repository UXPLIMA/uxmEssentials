package com.uxplima.uxmessentials.staff.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.staff.adapter.StaffServices;
import com.uxplima.uxmessentials.staff.adapter.inbound.gui.StaffPlayerMenu;
import com.uxplima.uxmessentials.staff.application.SendStaffChat;
import com.uxplima.uxmessentials.staff.application.StaffMessageKey;
import com.uxplima.uxmessentials.staff.application.port.StaffChannel;
import com.uxplima.uxmessentials.staff.application.port.StaffInspector;
import com.uxplima.uxmessentials.staff.application.port.StaffTeleport;
import com.uxplima.uxmlib.gui.Guis;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * {@code /stafflist} routing: the command reads the vanish-aware staff roster on the global region thread and either
 * opens the engine picker over it or, when no staff are online, sends {@link StaffMessageKey#STAFF_LIST_EMPTY}
 * instead of opening an empty window. The empty-roster behaviour the old {@code StaffListView} owned, now in the
 * command. The picker rendering and the head-click teleport are proven slot-for-slot by the engine golden test.
 */
class StaffListCommandTest {

    private static final String STAFF_MEMBER_NODE = "uxmessentials.staff.member";
    private static final String STAFF_LIST_NODE = "uxmessentials.staff.list";

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock looker;
    private RecordingSink sink;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        looker = server.addPlayer("Looker");
        // Grant only the command's own node, not op. An op would also hold the staff-member node and so count
        // itself as staff, defeating the empty-roster case.
        looker.addAttachment(plugin, STAFF_LIST_NODE, true);
        sink = new RecordingSink();
        Guis.install(plugin);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void withStaffOnlineTheCommandOpensThePickerAndSendsNoEmptyLine() {
        PlayerMock staffer = server.addPlayer("Staffer");
        staffer.addAttachment(plugin, STAFF_MEMBER_NODE, true);

        dispatch();

        Inventory top = looker.getOpenInventory().getTopInventory();
        assertThat(top.getSize()).isEqualTo(54);
        assertThat(top.getItem(0)).isNotNull();
        assertThat(top.getItem(0).getType()).isEqualTo(Material.PLAYER_HEAD);
        assertThat(sink.keys).doesNotContain(StaffMessageKey.STAFF_LIST_EMPTY);
    }

    @Test
    void withNoStaffOnlineTheCommandSendsTheEmptyLineAndOpensNothing() {
        // Only the looker is online and holds no staff-member node, so the roster is empty.
        dispatch();

        Inventory top = looker.getOpenInventory().getTopInventory();
        // No picker opened, so the top view is whatever default the player already had, never the 54-slot picker.
        assertThat(top == null ? 0 : top.getSize()).isNotEqualTo(54);
        assertThat(sink.keys).contains(StaffMessageKey.STAFF_LIST_EMPTY);
    }

    private void dispatch() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command().build());
        try {
            dispatcher.execute("stafflist", CommandSourceStackMock.from(looker));
        } catch (CommandSyntaxException failure) {
            throw new AssertionError("command did not parse: stafflist", failure);
        }
    }

    private StaffListCommand command() {
        Scheduler scheduler = new SyncScheduler();
        return new StaffListCommand(services(), new KeyMessages(), scheduler, server, sink, playerMenu(scheduler));
    }

    /** A live menu engine with the navigator/list specs registered, so a non-empty open renders the picker. */
    private StaffPlayerMenu playerMenu(Scheduler scheduler) {
        GuiText guiText = new GuiText(new KeyMessages());
        MenuBindings bindings = new MenuBindings();
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);
        Menus menus = new Menus(renderer, scheduler, bindings.lists());
        StaffPlayerMenu menu = new StaffPlayerMenu(menus, server, new KeyMessages(), sink, new RecordingTeleport());
        menu.register(bindings, specDir(), new NoopLogger());
        return menu;
    }

    /**
     * The command only reads the sender through {@link StaffServices} (it invokes no use case), but the record
     * requires every use case non-null, so this builds them over in-memory fakes. The enter/exit/recover trio shares
     * one store, repository, capture, vanish, and notifier; staff chat and the inspector stay on their no-op seams.
     */
    private static StaffServices services() {
        var store = new com.uxplima.uxmessentials.staff.adapter.outbound.StaffModeStoreImpl();
        var repository = new FakeRepository();
        var settings = new com.uxplima.uxmessentials.staff.adapter.StaffSettings(new DefaultConfig(), new NoopLogger());
        var gadgetItems = new com.uxplima.uxmessentials.staff.adapter.StaffGadgetItems(MockBukkit.createMockPlugin());
        var vanish = new FakeVanish();
        var capture = new com.uxplima.uxmessentials.staff.adapter.outbound.BukkitStaffLoadoutCapture(
                settings, gadgetItems, vanish);
        var notifier = new com.uxplima.uxmessentials.shared.application.message.Notifier(
                new KeyMessages(), new RecordingSink());
        var recover = new com.uxplima.uxmessentials.staff.application.RecoverStaffLoadout(
                store, repository, capture, vanish, notifier);
        var enter = new com.uxplima.uxmessentials.staff.application.EnterStaffMode(
                store, repository, capture, vanish, notifier, new RecordingEvents(), recover, "default", true);
        var exit = new com.uxplima.uxmessentials.staff.application.ExitStaffMode(
                store, repository, capture, vanish, notifier, new RecordingEvents());
        SendStaffChat chat = new SendStaffChat(StaffChannel.NONE, new RecordingEvents());
        return new StaffServices(enter, exit, recover, chat, StaffInspector.NONE, store);
    }

    private static Path specDir() {
        Path repoRoot = Path.of("").toAbsolutePath();
        while (repoRoot != null && !Files.exists(repoRoot.resolve("settings.gradle.kts"))) {
            repoRoot = repoRoot.getParent();
        }
        Objects.requireNonNull(repoRoot, "repo root");
        return repoRoot.resolve("bukkit-adapter/src/main/resources");
    }

    private static final class FakeRepository
            implements com.uxplima.uxmessentials.staff.application.port.StaffLoadoutRepository {
        @Override
        public void save(java.util.UUID owner, com.uxplima.uxmessentials.staff.domain.SavedLoadout loadout) {}

        @Override
        public java.util.Optional<com.uxplima.uxmessentials.staff.domain.SavedLoadout> load(java.util.UUID owner) {
            return java.util.Optional.empty();
        }

        @Override
        public void delete(java.util.UUID owner) {}
    }

    private static final class FakeVanish implements com.uxplima.uxmessentials.staff.application.port.StaffVanish {
        @Override
        public void setVanished(PlayerRef who, boolean vanished) {}

        @Override
        public boolean isVanished(PlayerRef who) {
            return false;
        }
    }

    private static final class DefaultConfig implements com.uxplima.uxmessentials.shared.application.port.ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }
    }

    private static final class RecordingTeleport implements StaffTeleport {
        @Override
        public boolean teleportTo(PlayerRef staff, PlayerRef target) {
            return true;
        }
    }

    private static final class RecordingEvents
            implements com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher {
        @Override
        public void publish(com.uxplima.uxmessentials.shared.domain.DomainEvent event) {}
    }

    /** Records which staff keys were delivered, by matching the bare key the test's {@code KeyMessages} returns. */
    private static final class RecordingSink implements MessageSink {
        private final List<MessageKey> keys = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            for (StaffMessageKey key : StaffMessageKey.values()) {
                if (renderedText.startsWith(key.key())) {
                    keys.add(key);
                }
            }
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

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
