package com.uxplima.uxmessentials.kits.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitCategoryManagerMenu;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitCreatePrompt;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitEditorView;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitManagerMenu;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitSettingsView;
import com.uxplima.uxmessentials.kits.application.CreateKit;
import com.uxplima.uxmessentials.kits.application.DelKit;
import com.uxplima.uxmessentials.kits.application.KitEditor;
import com.uxplima.uxmessentials.kits.application.port.KitCategoryRepository;
import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitCategory;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputInstaller;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.gui.anvil.AnvilInput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage that the engine-rendered kit manager's "create new kit" button starts kit creation directly
 * the kit name prompt, then the new kit's settings window: instead of opening a redundant chooser. Clicking the
 * create-button slot of the open {@link KitManagerMenu} through the engine's own {@link MenuListener} closes the
 * manager (the prompt path closes it) and arms a chat prompt for the kit name; the next chat line names the kit, which
 * is created in the repository and its settings window opens. The {@code kit.create-name} input point is configured as
 * chat here so the typed line round-trips through the shared {@link TextInput} chat backend.
 */
class KitManagerCreateButtonTest {

    @TempDir
    Path dataFolder;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private RecordingRepository repository;
    private GuiText guiText;
    private Scheduler scheduler;
    private KitManagerMenu manager;

    @BeforeEach
    void setUp() throws IOException {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        player.setOp(true);

        // Route the create-name input point through chat so firing an AsyncChatEvent completes the prompt.
        Path inputDir = plugin.getDataFolder().toPath();
        Files.createDirectories(inputDir);
        Files.writeString(inputDir.resolve("text-input.conf"), """
                default-mode = anvil
                modes {
                  "kit.create-name" = chat
                }
                """);

        Messages messages = new KeyMessages();
        MessageSink sink = (viewer, text) -> {};
        Notifier notifier = new Notifier(messages, sink);
        repository = new RecordingRepository();
        scheduler = new SyncScheduler();
        guiText = new GuiText(messages);
        AnvilInput anvil = new AnvilInput(plugin);
        anvil.install();
        // The installer registers the shared chat backend as a listener, so the AsyncChatEvent below routes.
        TextInput textInput = TextInputInstaller.install(
                        plugin, plugin.getDataFolder().toPath(), anvil, guiText, scheduler, new SilentLogger())
                .textInput();

        MenuBindings bindings = new MenuBindings();
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        bindings.action("close", ctx -> ctx.player().closeInventory());
        server.getPluginManager().registerEvents(listener, plugin);
        Menus menus = new Menus(renderer, scheduler, bindings.lists());

        // The per-kit settings panel renders through the engine now: build it over the same engine and register its
        // spec, so the create flow's open lands on a menu-backed window.
        KitEditor kitEditor = new KitEditor(repository, notifier);
        KitSettingsView settingsView = new KitSettingsView(
                menus,
                guiText,
                messages,
                textInput,
                kitEditor,
                new DelKit(repository, notifier),
                new KitEditorView(messages, kitEditor, scheduler),
                (p, v) -> {});
        settingsView.register(bindings, dataFolder, new SilentLogger());

        KitCategoryManagerMenu categoryManager =
                new KitCategoryManagerMenu(menus, messages, scheduler, new StubCategoryRepository(), textInput);
        KitCreatePrompt createPrompt =
                new KitCreatePrompt(messages, textInput, new CreateKit(repository, notifier), repository, settingsView);

        KitManagerMenu[] holder = new KitManagerMenu[1];
        manager = new KitManagerMenu(
                menus,
                scheduler,
                repository,
                messages,
                settingsView,
                categoryManager,
                createPrompt.boundTo((p, v) -> holder[0].open(p, v)));
        holder[0] = manager;
        manager.register(bindings, dataFolder, new SilentLogger());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void createButtonClosesTheManagerForTheNamePromptInsteadOfOpeningAChooser() {
        manager.open(player, ref(player));
        Inventory open = player.getOpenInventory().getTopInventory();
        assertThat(open.getHolder().getClass().getSimpleName()).isEqualTo("MenuHolder");

        fireClick(49); // the create-new-kit button

        // Going straight to kit creation means the create button closes the manager to ask for a kit name
        // it must NOT open any further inventory (a chooser would have).
        assertThat(player.getOpenInventory().getType()).isEqualTo(InventoryType.CRAFTING);
    }

    @Test
    void namingTheKitCreatesItAndOpensItsSettings() {
        manager.open(player, ref(player));
        fireClick(49);

        // The create button armed a chat prompt; the next chat line names the kit, which is created and its
        // settings window opens: the same name-to-kit flow the shared chat backend drives in production.
        fireChat("freshkit");

        assertThat(repository.exists(KitId.of("freshkit"))).isTrue();
        assertThat(player.getOpenInventory()
                        .getTopInventory()
                        .getHolder()
                        .getClass()
                        .getSimpleName())
                .isEqualTo("MenuHolder");
    }

    private void fireClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    private void fireChat(String line) {
        AsyncChatEvent event = mock(AsyncChatEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.message()).thenReturn(Component.text(line));
        // The mock would otherwise report a null handler list; point it at the real one the backend registered on.
        when(event.getHandlers()).thenReturn(AsyncChatEvent.getHandlerList());
        server.getPluginManager().callEvent(event);
    }

    private static PlayerRef ref(PlayerMock player) {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }

    /** A repository that records created kits so the test can prove the create button created one. */
    private static final class RecordingRepository implements KitRepository {
        private final List<KitDefinition> kits = new CopyOnWriteArrayList<>();

        @Override
        public Optional<KitDefinition> find(KitId id) {
            return kits.stream().filter(kit -> kit.id().equals(id)).findFirst();
        }

        @Override
        public List<KitDefinition> all() {
            return List.copyOf(kits);
        }

        @Override
        public boolean exists(KitId id) {
            return find(id).isPresent();
        }

        @Override
        public void save(KitDefinition definition) {
            kits.removeIf(kit -> kit.id().equals(definition.id()));
            kits.add(definition);
        }

        @Override
        public void delete(KitId id) {
            kits.removeIf(kit -> kit.id().equals(id));
        }
    }

    private static final class StubCategoryRepository implements KitCategoryRepository {
        @Override
        public Optional<KitCategory> find(String id) {
            return Optional.empty();
        }

        @Override
        public List<KitCategory> all() {
            return List.of();
        }

        @Override
        public void save(KitCategory category) {}

        @Override
        public void delete(String id) {}
    }

    private static final class SilentLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
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
