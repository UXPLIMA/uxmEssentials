package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
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
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The warp-editor golden test: the engine-rendered editor hub must draw the exact three-row property panel the old
 * bespoke {@code WarpEditorView} drew, and each button must run its old handler through the engine. A server warp
 * shows the category, lock, password, and welcome buttons; a player warp hides all four, because the surrogate-id
 * rebuild dropped the lock, password, and welcome facets from the player-warp aggregate and categories were always a
 * server-warp concept. The engine window is snapshotted as {@code (slot -> material, plain name)} and asserted equal,
 * slot for slot, to the baseline the old view produced. Then, through the engine's own menu listener, the toggle /
 * clear buttons prove they save the warp and the sub-screen buttons prove they open the right engine child window.
 */
class WarpEditorGoldenTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final int TELEPORT_SLOT = 0;
    private static final int ICON_SLOT = 4;
    private static final int CATEGORY_SLOT = 8;
    private static final int LOCK_SLOT = 10;
    private static final int PASSWORD_SLOT = 11;
    private static final int WELCOME_SLOT = 12;
    private static final int SOUNDS_SLOT = 13;
    private static final int PARTICLES_SLOT = 14;
    private static final int WARMUP_SLOT = 15;
    private static final int COOLDOWN_SLOT = 16;
    private static final int CLOSE_SLOT = 22;

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
    private RecordingPlayerWarps playerWarps;
    private PlayerWarpRepositoryHandle playerWarpHandle;
    private WarpEditorView editor;

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
        playerWarps = new RecordingPlayerWarps();
        playerWarpHandle = new PlayerWarpRepositoryHandle();
        playerWarpHandle.bind(playerWarps);
        TextInput textInput = org.mockito.Mockito.mock(TextInput.class);
        editor = new WarpEditorView(
                engine.menus(),
                new KeyMessages(),
                scheduler,
                repository,
                textInput,
                org.mockito.Mockito.mock(UseWarp.class),
                playerWarpHandle,
                new PlayerWarpGoToHandle());
        // Bind the real engine sub-screens so a button click opens its child window through the same façade.
        WarpCategorySelectorMenu categorySelector = new WarpCategorySelectorMenu(
                engine.menus(), new KeyMessages(), scheduler, new EmptyCategories(), repository, editor);
        categorySelector.register(engine.bindings(), dataFolder, NOOP);
        WarpWelcomeMessagesView welcome =
                WarpWelcomeMessagesView.create(engine.menus(), scheduler, textInput, repository, editor);
        welcome.register(engine.bindings(), dataFolder, NOOP);
        WarpSoundSelectorView soundSelector =
                WarpSoundSelectorView.create(new KeyMessages(), engine.menus(), repository, editor, textInput);
        WarpSoundMenu soundMenu = WarpSoundMenu.create(engine.menus(), soundSelector, repository, editor, textInput);
        soundMenu.register(engine.bindings(), dataFolder, NOOP);
        WarpParticleSelectorView particleSelector =
                WarpParticleSelectorView.create(new KeyMessages(), engine.menus(), repository, editor, textInput);
        editor.bind(categorySelector, welcome, soundMenu, soundSelector, particleSelector);
        editor.register(engine.bindings(), dataFolder, NOOP);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSameHubAsTheOldViewForAServerWarp() {
        repository.save(serverWarp("spawn"));
        editor.open(player, viewer, "spawn", null);

        Map<Integer, Snapshot> baseline = baseline(true);
        Map<Integer, Snapshot> rendered = snapshot(top());

        assertThat(rendered.keySet()).containsExactlyInAnyOrderElementsOf(baseline.keySet());
        assertThat(rendered).isEqualTo(baseline);
    }

    @Test
    void aPlayerWarpHidesTheServerOnlyControls() {
        playerWarps.save(playerWarp("home"));
        editor.open(player, viewer, "home", viewer);

        Map<Integer, Snapshot> rendered = snapshot(top());

        // Category, lock, password, and welcome are gated to server warps; a player warp draws none of them.
        assertThat(rendered).doesNotContainKeys(CATEGORY_SLOT, LOCK_SLOT, PASSWORD_SLOT, WELCOME_SLOT);
        assertThat(rendered).isEqualTo(baseline(false));
    }

    @Test
    void theEngineWindowIsMenuBacked() {
        repository.save(serverWarp("spawn"));
        editor.open(player, viewer, "spawn", null);
        assertThat(top().getHolder()).isInstanceOf(MenuHolder.class);
    }

    @Test
    void theIconButtonShowsTheWarpsLiveMaterialFallingBackToEnderPearl() {
        repository.save(serverWarp("spawn"));
        editor.open(player, viewer, "spawn", null);
        assertThat(top().getItem(ICON_SLOT)).isNotNull();
        assertThat(top().getItem(ICON_SLOT).getType()).isEqualTo(Material.ENDER_PEARL);

        repository.save(serverWarp("spawn").withIconMaterial(Optional.of("DIAMOND")));
        editor.open(player, viewer, "spawn", null);
        assertThat(top().getItem(ICON_SLOT).getType()).isEqualTo(Material.DIAMOND);
    }

    @Test
    void clickingLockTogglesTheWarpsLockedStateAndSaves() {
        repository.save(serverWarp("spawn"));
        editor.open(player, viewer, "spawn", null);
        fireClick(LOCK_SLOT, ClickType.LEFT);
        assertThat(repository.find(WarpName.of("spawn")))
                .hasValueSatisfying(w -> assertThat(w.isLocked()).isTrue());
    }

    @Test
    void leftClickingIconCopiesTheMainHandItemAndSaves() {
        repository.save(serverWarp("spawn"));
        editor.open(player, viewer, "spawn", null);
        player.getInventory().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
        fireClick(ICON_SLOT, ClickType.LEFT);
        assertThat(repository.find(WarpName.of("spawn")))
                .hasValueSatisfying(w -> assertThat(w.iconMaterial()).contains("NETHERITE_SWORD"));
    }

    @Test
    void rightClickingIconClearsItAndSaves() {
        repository.save(serverWarp("spawn").withIconMaterial(Optional.of("DIAMOND")));
        editor.open(player, viewer, "spawn", null);
        fireClick(ICON_SLOT, ClickType.RIGHT);
        assertThat(repository.find(WarpName.of("spawn")))
                .hasValueSatisfying(w -> assertThat(w.iconMaterial()).isEmpty());
    }

    @Test
    void rightClickingPasswordClearsItAndSaves() {
        repository.save(serverWarp("spawn").withPassword(Optional.of("secret")));
        editor.open(player, viewer, "spawn", null);
        fireClick(PASSWORD_SLOT, ClickType.RIGHT);
        assertThat(repository.find(WarpName.of("spawn")))
                .hasValueSatisfying(w -> assertThat(w.password()).isEmpty());
    }

    @Test
    void applyingAWarmupSavesItAndReopensTheEditor() {
        repository.save(serverWarp("spawn"));
        editor.open(player, viewer, "spawn", null);
        editor.applyDuration(actionContextSubject(), targetOf("spawn", null), true, "5.0");
        assertThat(repository.find(WarpName.of("spawn")))
                .hasValueSatisfying(w -> assertThat(w.warmupOverrideSeconds()).contains(5.0));
        assertThat(top().getHolder()).isInstanceOf(MenuHolder.class);
    }

    @Test
    void applyingANonNumberCooldownIsRejectedWithoutSaving() {
        repository.save(serverWarp("spawn"));
        editor.open(player, viewer, "spawn", null);
        editor.applyDuration(actionContextSubject(), targetOf("spawn", null), false, "abc");
        assertThat(repository.find(WarpName.of("spawn")))
                .hasValueSatisfying(w -> assertThat(w.cooldownOverrideSeconds()).isEmpty());
    }

    @Test
    void clickingShiftClearsBothSoundSides() {
        repository.save(serverWarp("spawn").withDepartureSound(Optional.of("a")).withArrivalSound(Optional.of("b")));
        editor.open(player, viewer, "spawn", null);
        fireClick(SOUNDS_SLOT, ClickType.SHIFT_LEFT);
        assertThat(repository.find(WarpName.of("spawn"))).hasValueSatisfying(w -> {
            assertThat(w.departureSound()).isEmpty();
            assertThat(w.arrivalSound()).isEmpty();
        });
    }

    @Test
    void clickingTheWelcomeButtonOpensTheEngineWelcomeList() {
        repository.save(serverWarp("spawn"));
        editor.open(player, viewer, "spawn", null);
        fireClick(WELCOME_SLOT, ClickType.LEFT);
        assertThat(holderSpecId(top())).isEqualTo(WarpWelcomeMessagesView.SPEC_ID);
    }

    @Test
    void clickingTheCategoryButtonOpensTheEngineCategorySelector() {
        repository.save(serverWarp("spawn"));
        editor.open(player, viewer, "spawn", null);
        fireClick(CATEGORY_SLOT, ClickType.LEFT);
        // The category selector is a spec of its own now, so the window carries that spec's id.
        assertThat(holderSpecId(top())).isEqualTo(WarpCategorySelectorMenu.SPEC_ID);
    }

    @Test
    void clickingCloseShutsTheWindow() {
        repository.save(serverWarp("spawn"));
        editor.open(player, viewer, "spawn", null);
        fireClick(CLOSE_SLOT, ClickType.LEFT);
        Inventory after = player.getOpenInventory().getTopInventory();
        assertThat(after == null || !(after.getHolder() instanceof MenuHolder)).isTrue();
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

    /** The action context the package-private duration seam needs: the editor's current subject for this warp. */
    private com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext actionContextSubject() {
        var ctx = com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext.of(viewer, null, 0);
        return new com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext(
                ctx, player, com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind.LEFT, Map.of());
    }

    private WarpEditTarget targetOf(String name, @Nullable PlayerRef owner) {
        Warp warp = repository.find(WarpName.of(name)).orElseThrow();
        return new WarpEditTarget(name, owner, WarpDisplay.of(warp));
    }

    private Map<Integer, Snapshot> baseline(boolean serverWarp) {
        Map<Integer, Snapshot> out = new LinkedHashMap<>();
        out.put(TELEPORT_SLOT, new Snapshot(Material.ENDER_PEARL, WarpsMessageKey.WARP_EDITOR_TELEPORT_NAME.key()));
        out.put(ICON_SLOT, new Snapshot(Material.ENDER_PEARL, WarpsMessageKey.WARP_EDITOR_ICON_NAME.key()));
        if (serverWarp) {
            // Category, lock, password, and welcome are server-warp concepts. A player warp carries none of them
            // after the surrogate-id rebuild, so all four are gated off for a player warp in warp-editor.conf.
            out.put(CATEGORY_SLOT, new Snapshot(Material.BOOK, WarpsMessageKey.WARP_EDITOR_CATEGORY_NAME.key()));
            out.put(LOCK_SLOT, new Snapshot(Material.LEVER, WarpsMessageKey.WARP_EDITOR_LOCK_NAME.key()));
            out.put(PASSWORD_SLOT, new Snapshot(Material.PAPER, WarpsMessageKey.WARP_EDITOR_PASSWORD_NAME.key()));
            out.put(WELCOME_SLOT, new Snapshot(Material.WRITABLE_BOOK, WarpsMessageKey.WARP_EDITOR_WELCOME_NAME.key()));
        }
        out.put(SOUNDS_SLOT, new Snapshot(Material.JUKEBOX, WarpsMessageKey.WARP_EDITOR_SOUNDS_NAME.key()));
        out.put(PARTICLES_SLOT, new Snapshot(Material.BLAZE_POWDER, WarpsMessageKey.WARP_EDITOR_PARTICLES_NAME.key()));
        out.put(WARMUP_SLOT, new Snapshot(Material.CLOCK, WarpsMessageKey.WARP_EDITOR_WARMUP_NAME.key()));
        out.put(COOLDOWN_SLOT, new Snapshot(Material.COMPASS, WarpsMessageKey.WARP_EDITOR_COOLDOWN_NAME.key()));
        out.put(CLOSE_SLOT, new Snapshot(Material.BARRIER, WarpsMessageKey.WARP_EDITOR_CLOSE.key()));
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

    private static Warp serverWarp(String name) {
        return Warp.create(WarpName.of(name), Position.of(WORLD, 0, 64, 0), OWNER, Instant.EPOCH);
    }

    private PlayerWarp playerWarp(String name) {
        return PlayerWarp.create(
                viewer, viewer.name(), PlayerWarpName.of(name), Position.of(WORLD, 0, 64, 0), Instant.EPOCH);
    }

    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "Owner");

    private record Snapshot(Material material, String name) {}

    /** A repository that records stored warps in insertion order. */
    private static final class RecordingRepository implements WarpRepository {
        private final List<Warp> warps = new CopyOnWriteArrayList<>();

        @Override
        public Optional<Warp> find(WarpName name) {
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

    /** A player-warp repository that records stored warps for the player-warp editor path. */
    private static final class RecordingPlayerWarps implements PlayerWarpRepository {
        private final List<PlayerWarp> warps = new CopyOnWriteArrayList<>();
        private final java.util.concurrent.atomic.AtomicLong ids = new java.util.concurrent.atomic.AtomicLong();

        @Override
        public Optional<PlayerWarp> findByName(PlayerWarpName name) {
            return warps.stream().filter(w -> w.name().equals(name)).findFirst();
        }

        @Override
        public Optional<PlayerWarp> findById(com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId id) {
            return warps.stream()
                    .filter(w -> w.id().filter(id::equals).isPresent())
                    .findFirst();
        }

        @Override
        public List<PlayerWarp> ownedBy(PlayerRef owner) {
            return warps.stream().filter(w -> w.owner().equals(owner)).toList();
        }

        @Override
        public List<PlayerWarp> publicOwnedBy(PlayerRef owner) {
            return List.of();
        }

        @Override
        public int count(PlayerRef owner) {
            return (int) warps.stream().filter(w -> w.owner().equals(owner)).count();
        }

        @Override
        public boolean existsByName(PlayerWarpName name) {
            return findByName(name).isPresent();
        }

        @Override
        public com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId save(PlayerWarp warp) {
            com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId id = warp.id()
                    .orElseGet(
                            () -> com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId.of(ids.incrementAndGet()));
            warps.removeIf(existing -> existing.name().equals(warp.name()));
            warps.add(warp.id().isPresent() ? warp : warp.withId(id));
            return id;
        }

        @Override
        public void deleteById(com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId id) {
            warps.removeIf(existing -> existing.id().filter(id::equals).isPresent());
        }

        @Override
        public void recordVisit(com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId id) {}

        @Override
        public void updateRating(
                com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId id,
                com.uxplima.uxmessentials.playerwarps.domain.RatingSummary summary) {}

        @Override
        public void refreshFavouriteCount(com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId id) {}
    }

    /** An empty category repository: the category selector reads it but this test never picks a category. */
    private static final class EmptyCategories
            implements com.uxplima.uxmessentials.warps.application.port.WarpCategoryRepository {
        @Override
        public Optional<com.uxplima.uxmessentials.warps.domain.WarpCategory> find(String id) {
            return Optional.empty();
        }

        @Override
        public List<com.uxplima.uxmessentials.warps.domain.WarpCategory> all() {
            return List.of();
        }

        @Override
        public void save(com.uxplima.uxmessentials.warps.domain.WarpCategory category) {}

        @Override
        public void delete(String id) {}
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
