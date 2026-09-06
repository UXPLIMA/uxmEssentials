package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.menu.TestMenuEngine;
import com.uxplima.uxmessentials.shared.menu.TileText;
import com.uxplima.uxmessentials.warps.application.UseWarp;
import com.uxplima.uxmessentials.warps.application.WarpsMessageKey;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import com.uxplima.uxmessentials.warps.domain.WelcomeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The warp welcome-messages golden test: the engine-rendered list editor must draw the exact list the old bespoke
 * {@code WarpWelcomeMessagesView} drew. One icon per stored message across the top two rows (slots 0..17, the icon
 * keyed off the message's delivery type), the WRITABLE_BOOK add button (slot 18), the ARROW back button (slot 22),
 * and the LAVA_BUCKET clear button (slot 26), and each gesture must run the same edit the old click did. The window
 * is snapshotted as {@code (slot -> material, plain name)} and asserted equal, slot for slot. Then, through the
 * engine's own menu listener, the remove / cycle / clear clicks and the add / edit input seams prove the migrated
 * path saves the warp, and the back button proves it reopens the engine warp editor.
 */
class WarpWelcomeMessagesGoldenTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "Owner");
    private static final int ADD_SLOT = 18;
    private static final int BACK_SLOT = 22;
    private static final int CLEAR_SLOT = 26;

    private static final Logger NOOP = new Logger() {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    };

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private Scheduler scheduler;
    private TestMenuEngine engine;
    private RecordingRepository repository;
    private WarpEditorView editor;
    private WarpWelcomeMessagesView welcome;

    @TempDir
    Path dataFolder;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        scheduler = new SyncScheduler();
        engine = TestMenuEngine.create(new KeyMessages(), scheduler);
        engine.installListener(plugin);
        repository = new RecordingRepository();
        TextInput textInput = org.mockito.Mockito.mock(TextInput.class);
        editor = new WarpEditorView(
                engine.menus(),
                new KeyMessages(),
                scheduler,
                repository,
                textInput,
                org.mockito.Mockito.mock(UseWarp.class),
                new PlayerWarpRepositoryHandle(),
                new PlayerWarpGoToHandle());
        editor.register(engine.bindings(), dataFolder, NOOP);
        welcome = WarpWelcomeMessagesView.create(engine.menus(), scheduler, textInput, repository, editor);
        welcome.register(engine.bindings(), dataFolder, NOOP);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSameListAsTheOldView() {
        repository.save(warpWith(new WelcomeMessage("hello", "CHAT"), new WelcomeMessage("hi", "TITLE")));
        welcome.open(player, viewer, "spawn", null);

        Map<Integer, Snapshot> rendered = snapshot(top());

        assertThat(rendered).isEqualTo(baseline(Material.PAPER, Material.GOLDEN_HELMET));
    }

    @Test
    void theEngineWindowIsMenuBacked() {
        repository.save(warpWith(new WelcomeMessage("hello", "CHAT")));
        welcome.open(player, viewer, "spawn", null);
        assertThat(top().getHolder()).isInstanceOf(MenuHolder.class);
    }

    @Test
    void rightClickingAnEntryRemovesItAndSaves() {
        repository.save(warpWith(new WelcomeMessage("a", "CHAT"), new WelcomeMessage("b", "CHAT")));
        welcome.open(player, viewer, "spawn", null);
        fireClick(0, ClickType.RIGHT);
        assertThat(messagesOf("spawn")).extracting(WelcomeMessage::message).containsExactly("b");
    }

    @Test
    void shiftClickingAnEntryCyclesItsType() {
        repository.save(warpWith(new WelcomeMessage("a", "CHAT")));
        welcome.open(player, viewer, "spawn", null);
        fireClick(0, ClickType.SHIFT_LEFT);
        assertThat(messagesOf("spawn")).extracting(WelcomeMessage::type).containsExactly("ACTION_BAR");
    }

    @Test
    void clickingClearDropsEveryMessage() {
        repository.save(warpWith(new WelcomeMessage("a", "CHAT"), new WelcomeMessage("b", "CHAT")));
        welcome.open(player, viewer, "spawn", null);
        fireClick(CLEAR_SLOT, ClickType.LEFT);
        assertThat(messagesOf("spawn")).isEmpty();
    }

    @Test
    void applyingAnAddAppendsACHATMessageAndSaves() {
        repository.save(warpWith(new WelcomeMessage("a", "CHAT")));
        welcome.open(player, viewer, "spawn", null);
        welcome.applyAdd(player, viewer, listOf("spawn", null), "new line");
        assertThat(messagesOf("spawn")).extracting(WelcomeMessage::message).containsExactly("a", "new line");
        assertThat(messagesOf("spawn").get(1).type()).isEqualTo("CHAT");
    }

    @Test
    void applyingAnEditReplacesTheEntryTextKeepingItsType() {
        repository.save(warpWith(new WelcomeMessage("old", "TITLE")));
        welcome.open(player, viewer, "spawn", null);
        welcome.applyEdit(player, viewer, listOf("spawn", null), 0, "fresh");
        assertThat(messagesOf("spawn")).hasSize(1);
        assertThat(messagesOf("spawn").get(0).message()).isEqualTo("fresh");
        assertThat(messagesOf("spawn").get(0).type()).isEqualTo("TITLE");
    }

    @Test
    void clickingBackReopensTheEngineWarpEditor() {
        repository.save(warpWith(new WelcomeMessage("a", "CHAT")));
        welcome.open(player, viewer, "spawn", null);
        fireClick(BACK_SLOT, ClickType.LEFT);
        assertThat(holderSpecId(top())).isEqualTo(WarpEditorView.SPEC_ID);
    }

    // --- helpers ---

    private Inventory top() {
        return player.getOpenInventory().getTopInventory();
    }

    private void fireClick(int slot, ClickType click) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, click, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    private List<WelcomeMessage> messagesOf(String name) {
        return repository.find(WarpName.of(name)).orElseThrow().welcomeMessages();
    }

    private WarpWelcomeMessagesView.WelcomeList listOf(
            String name, @org.jspecify.annotations.Nullable PlayerRef owner) {
        return new WarpWelcomeMessagesView.WelcomeList(name, owner, List.of());
    }

    private Map<Integer, Snapshot> baseline(Material... entryMaterials) {
        Map<Integer, Snapshot> out = new LinkedHashMap<>();
        for (int i = 0; i < entryMaterials.length; i++) {
            out.put(i, new Snapshot(entryMaterials[i], WarpsMessageKey.WARP_EDITOR_WELCOME_MANAGER_ENTRY_NAME.key()));
        }
        out.put(
                ADD_SLOT,
                new Snapshot(Material.WRITABLE_BOOK, WarpsMessageKey.WARP_EDITOR_WELCOME_MANAGER_ADD_NAME.key()));
        out.put(BACK_SLOT, new Snapshot(Material.ARROW, WarpsMessageKey.WARP_EDITOR_SELECTOR_BACK.key()));
        out.put(
                CLEAR_SLOT,
                new Snapshot(Material.LAVA_BUCKET, WarpsMessageKey.WARP_EDITOR_WELCOME_MANAGER_CLEAR_NAME.key()));
        return out;
    }

    private static Map<Integer, Snapshot> snapshot(Inventory inv) {
        Map<Integer, Snapshot> out = new LinkedHashMap<>();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType() == Material.GRAY_STAINED_GLASS_PANE) {
                continue;
            }
            out.put(slot, new Snapshot(item.getType(), plainName(item)));
        }
        return out;
    }

    private static String plainName(ItemStack item) {
        // The title reads off the tile wherever the canon puts it: the display name of a bare button, or the
        // first lore line of a titled tile, whose display name is deliberately blank.
        return TileText.title(item);
    }

    private static String holderSpecId(Inventory inv) {
        return ((MenuHolder) Objects.requireNonNull(inv.getHolder())).specId();
    }

    private static Warp warpWith(WelcomeMessage... messages) {
        return Warp.create(WarpName.of("spawn"), Position.of(WORLD, 0, 64, 0), OWNER, Instant.EPOCH)
                .withWelcomeMessages(List.of(messages));
    }

    private record Snapshot(Material material, String name) {}

    /** A repository that records stored warps in insertion order. */
    private static final class RecordingRepository implements WarpRepository {
        private final List<Warp> warps = new CopyOnWriteArrayList<>();

        @Override
        public java.util.Optional<Warp> find(WarpName name) {
            return warps.stream().filter(w -> w.name().equals(name)).findFirst();
        }

        @Override
        public List<Warp> all() {
            return List.copyOf(warps);
        }

        @Override
        public boolean exists(WarpName name) {
            return find(name).isPresent();
        }

        @Override
        public void save(Warp warp) {
            warps.removeIf(existing -> existing.name().equals(warp.name()));
            warps.add(warp);
        }

        @Override
        public void delete(WarpName name) {
            warps.removeIf(existing -> existing.name().equals(name));
        }

        @Override
        public void rate(WarpName name, UUID player, double rating) {}

        @Override
        public double averageRating(WarpName name) {
            return 0.0;
        }
    }

    /** A messages port whose resolve returns the catalog key verbatim, so a rendered name equals its key. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** A scheduler that runs every hop inline, so the menu open and clicks complete synchronously. */
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
