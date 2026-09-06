package com.uxplima.uxmessentials.worlds.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputTestKit;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.menu.TileText;
import com.uxplima.uxmessentials.worlds.application.CreateWorld;
import com.uxplima.uxmessentials.worlds.application.WorldEditorMessageKey;
import com.uxplima.uxmessentials.worlds.application.WorldsMessageKey;
import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldError;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Unit coverage of the create screen's package-private name/seed apply seams, the path a live anvil/chat submission
 * runs, which MockBukkit cannot drive, so the validation and the re-open with the captured value are exercised
 * without a live prompt, mirroring the economy amount-seam test. A valid name/seed re-opens the screen carrying it
 * (surfaced through the create-name / create-seed token), and an invalid one sends the existing rejection through the
 * notifier and re-opens unchanged. The test drives the seam directly because it lives in the menu's own package.
 */
class WorldCreateMenuInputSeamTest {

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private Scheduler scheduler;
    private RecordingMessages messages;
    private WorldCreateMenu menu;

    @TempDir
    Path dataFolder;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Admin");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        scheduler = new SyncScheduler();
        messages = new RecordingMessages();
        menu = buildMenu();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aValidNameReOpensCarryingIt() {
        menu.applyName(player, viewer, WorldCreateDraft.empty(), "fresh");

        Inventory reopened = player.getOpenInventory().getTopInventory();
        assertThat(nameAt(reopened, 4)).isEqualTo("fresh");
    }

    @Test
    void anInvalidNameRejectsAndReOpensUnchanged() {
        menu.applyName(player, viewer, WorldCreateDraft.empty(), "has spaces");

        assertThat(messages.seen).contains(WorldsMessageKey.WORLD_NAME_INVALID.key());
        // The draft re-opens unchanged: the name slot shows the unset placeholder, not the rejected input.
        assertThat(nameAt(player.getOpenInventory().getTopInventory(), 4)).isEqualTo("(not set)");
    }

    @Test
    void anEmptySeedClearsItAndReOpens() {
        menu.applySeed(player, viewer, WorldCreateDraft.empty().withSeed(Optional.of(7L)), "");

        // The seed slot reverts to the random placeholder once cleared.
        assertThat(nameAt(player.getOpenInventory().getTopInventory(), 16)).isEqualTo("(random)");
    }

    @Test
    void aNonNumericSeedRejectsAndReOpensUnchanged() {
        menu.applySeed(player, viewer, WorldCreateDraft.empty(), "abc");

        assertThat(messages.seen).contains(WorldEditorMessageKey.CREATE_SEED_INVALID.key());
    }

    /** Build the create menu over a real synchronous engine, surfacing the create-name/seed token in slot labels. */
    private WorldCreateMenu buildMenu() {
        MenuBindings bindings = new MenuBindings();
        GuiText guiText = new GuiText(messages);
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);
        Menus menus = new Menus(renderer, scheduler, bindings.lists());
        Notifier notifier = new Notifier(messages, new SilentSink());
        TextInput textInput = TextInputTestKit.create(plugin, guiText, scheduler, Path.of("nonexistent"), NOOP);
        CreateWorld createWorld = new CreateWorld(
                new FakeRepository(), new FakeEngine(), notifier, new SilentEvents(), scheduler, Clock.systemUTC());
        WorldCreateMenu created = new WorldCreateMenu(menus, scheduler, createWorld, notifier, textInput, (p, v) -> {});
        created.register(bindings, dataFolder, NOOP);
        return created;
    }

    /** The name button's label resolves to the create-name/seed token, so its display name is the carried value. */
    private static String nameAt(Inventory inv, int slot) {
        ItemStack item = inv.getItem(slot);
        if (item == null || item.getItemMeta() == null) {
            return "";
        }
        return TileText.title(item);
    }

    /** Surfaces the create-name and create-seed tokens (the slot labels) so a carried value is observable. */
    private static final class RecordingMessages implements Messages {
        private final Set<String> seen = new java.util.HashSet<>();

        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            seen.add(key.key());
            if (key.key().equals(WorldEditorMessageKey.CREATE_NAME.key())) {
                return placeholders.getOrDefault("world_create_name", "");
            }
            if (key.key().equals(WorldEditorMessageKey.CREATE_SEED.key())) {
                return placeholders.getOrDefault("world_create_seed", "");
            }
            return key.key();
        }
    }

    private static final class SilentSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    private static final class SilentEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {}
    }

    private static final class FakeRepository implements WorldRepository {
        private final Map<String, ManagedWorld> byName = new LinkedHashMap<>();

        @Override
        public Optional<ManagedWorld> find(WorldName name) {
            return Optional.ofNullable(byName.get(name.value()));
        }

        @Override
        public List<ManagedWorld> all() {
            return new ArrayList<>(byName.values());
        }

        @Override
        public boolean exists(WorldName name) {
            return byName.containsKey(name.value());
        }

        @Override
        public void save(ManagedWorld world) {
            byName.put(world.name().value(), world);
        }

        @Override
        public void delete(WorldName name) {
            byName.remove(name.value());
        }
    }

    private static final class FakeEngine implements WorldEngine {
        @Override
        public Result<Unit, WorldError> create(ManagedWorld world) {
            return Result.ok();
        }

        @Override
        public Result<Unit, WorldError> load(ManagedWorld world) {
            return Result.ok();
        }

        @Override
        public Result<Unit, WorldError> unload(WorldName name, boolean save) {
            return Result.ok();
        }

        @Override
        public Result<Unit, WorldError> deleteFiles(WorldName name) {
            return Result.ok();
        }

        @Override
        public Optional<DetectedWorld> scanFolder(WorldName name) {
            return Optional.empty();
        }

        @Override
        public boolean exists(WorldName name) {
            return false;
        }

        @Override
        public boolean isLoaded(WorldName name) {
            return false;
        }

        @Override
        public Set<WorldName> loadedWorldNames() {
            return Set.of();
        }

        @Override
        public Optional<WorldName> defaultWorldName() {
            return Optional.empty();
        }

        @Override
        public Optional<UUID> uidOf(WorldName name) {
            return Optional.empty();
        }

        @Override
        public int playerCount(WorldName name) {
            return 0;
        }

        @Override
        public Optional<Position> spawnPoint(WorldName name) {
            return Optional.empty();
        }
    }

    private static final Logger NOOP = new Logger() {
        @Override
        public void info(String m, Object... a) {}

        @Override
        public void warn(String m, Object... a) {}

        @Override
        public void error(String m, Throwable t) {}

        @Override
        public void debug(String m, Object... a) {}
    };

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
