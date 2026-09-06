package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.moderation.adapter.inbound.gui.ModerationActiveMenu;
import com.uxplima.uxmessentials.moderation.adapter.inbound.gui.PunishmentDetailView;
import com.uxplima.uxmessentials.moderation.adapter.inbound.gui.PunishmentKind;
import com.uxplima.uxmessentials.moderation.adapter.inbound.gui.PunishmentRevoker;
import com.uxplima.uxmessentials.moderation.application.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.domain.BanEntry;
import com.uxplima.uxmessentials.moderation.domain.IpBan;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.JailEntry;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.ModerationProfile;
import com.uxplima.uxmessentials.moderation.domain.MuteEntry;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.SeenRecord;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.moderation.domain.Warn;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.EditorRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.gui.Guis;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The active-punishments golden test: the engine-rendered management list must draw the exact grid the original
 * {@code ActivePunishmentsView} drew. The store holds three active sanctions, a ban on "Mallory", a mute on "Eve",
 * and a jail on "Trent" (so the list draws a BARRIER, a BOOK, and an IRON_BARS icon (content slots 0, 1 and 2) the
 * per-kind icon material each row resolves to, with the target name surfacing through the {@code mod_active_player}
 * token) and the two ARROW nav buttons (slots 48 and 50). The engine window is snapshotted as
 * {@code (slot -> material, plain name)} and asserted equal, slot for slot, to the baseline the old view produced for
 * this fixture, then frozen here as the contract so the old class could be deleted. Then a left click on the ban icon
 * through the engine's own {@link MenuListener} proves the migrated path opens that punishment's
 * {@link PunishmentDetailView}. Now itself an engine editor reached through the same {@code Menus}: the detail
 * editor's distinctive PLAYER_HEAD target label and LAVA_BUCKET revoke button replace the list, and the target label
 * carries the clicked punishment's target name.
 *
 * <p>The {@code KeyMessages} catalog surfaces the entry name's {@code mod_active_player} token and the detail value
 * line's {@code value} token, so a target's name appears both in the list label and in the opened detail editor;
 * every other key renders verbatim. A real rendering difference (a wrong key, a wrong material, a misplaced cell, a
 * lost name token, or a detail view opened for the wrong entry) therefore still shows up as a mismatch.
 */
class ModerationActiveListGoldenTest {

    private static final UUID MALLORY = UUID.randomUUID();
    private static final UUID EVE = UUID.randomUUID();
    private static final UUID TRENT = UUID.randomUUID();

    /** The detail editor's read-only target label sits at the first property slot; the revoke is the delete slot. */
    private static final int DETAIL_TARGET_SLOT = 10;

    private static final int DETAIL_REVOKE_SLOT = 26;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private FakeRepository repository;
    private RecordingRevoker revoker;
    private PunishmentDetailView detail;
    private MenuBindings bindings;
    private Menus menus;

    private final java.nio.file.Path dataFolder = java.nio.file.Path.of("nonexistent");

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Staff");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        repository = new FakeRepository();
        revoker = new RecordingRevoker();
        Guis.install(plugin);
        // One editor-capable engine and one listener for both the engine list and the detail editor it drills into,
        // so the migrated detail view opens through the same Menus that renders the list.
        EditorRenderer editorRenderer = new EditorRenderer(guiText);
        bindings = new MenuBindings();
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        menus = new Menus(renderer, scheduler, bindings.lists(), editorRenderer);
        MenuListener listener = new MenuListener(
                renderer,
                bindings.actions(),
                bindings.conditions(),
                scheduler,
                plugin,
                editorRenderer,
                menus.selectorOpener(),
                menus.confirmOpener());
        server.getPluginManager().registerEvents(listener, plugin);
        detail = new PunishmentDetailView(
                menus,
                guiText,
                scheduler,
                revoker,
                Clock.system(ZoneOffset.UTC),
                detailLayout(),
                (p, v) -> {},
                (p, pun) -> {});
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSameIconGridAndNavAsTheOldView() {
        repository.ban(MALLORY, Instant.now().plus(Duration.ofDays(7)));
        repository.mute(EVE);
        repository.jail(TRENT, "north");

        Map<Integer, Snapshot> baseline = oldViewBaseline();
        Map<Integer, Snapshot> engine = snapshotEngine();

        assertThat(engine.keySet()).containsExactlyInAnyOrderElementsOf(baseline.keySet());
        assertThat(engine).isEqualTo(baseline);
    }

    @Test
    void clickingAPunishmentThroughTheEngineOpensItsDetailView() {
        repository.ban(MALLORY, Instant.now().plus(Duration.ofDays(7)));
        openEngine();

        fireClick(0); // content slot 0 holds Mallory's ban; a left click must open its detail view

        Inventory inv = player.getOpenInventory().getTopInventory();
        // The detail editor replaced the list: its read-only target head and its confirm-gated revoke button are both
        // present, and the target label carries the clicked punishment's target name.
        assertThat(inv.getItem(DETAIL_TARGET_SLOT)).isNotNull();
        assertThat(inv.getItem(DETAIL_TARGET_SLOT).getType()).isEqualTo(Material.PLAYER_HEAD);
        assertThat(inv.getItem(DETAIL_REVOKE_SLOT).getType()).isEqualTo(Material.LAVA_BUCKET);
        assertThat(plainLore(inv.getItem(DETAIL_TARGET_SLOT))).contains("Mallory");
    }

    /**
     * The slot -> (material, plain name) map the deleted {@code ActivePunishmentsView} produced for this fixture (a
     * ban on "Mallory", a mute on "Eve", a jail on "Trent"), captured while both paths rendered it identically and
     * frozen here: a BARRIER, a BOOK and an IRON_BARS icon (content slots 0, 1 and 2. The names surface through the
     * {@code mod_active_player} token) and the two nav ARROWs (slots 48 and 50).
     */
    private static Map<Integer, Snapshot> oldViewBaseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        baseline.put(0, new Snapshot(Material.BARRIER, "Mallory"));
        baseline.put(1, new Snapshot(Material.BOOK, "Eve"));
        baseline.put(2, new Snapshot(Material.IRON_BARS, "Trent"));
        baseline.put(48, new Snapshot(Material.ARROW, "moderation.gui.list.prev"));
        baseline.put(50, new Snapshot(Material.ARROW, "moderation.gui.list.next"));
        return baseline;
    }

    private Map<Integer, Snapshot> snapshotEngine() {
        openEngine();
        return snapshot(player.getOpenInventory().getTopInventory());
    }

    private void openEngine() {
        ModerationActiveMenu menu = new ModerationActiveMenu(
                menus,
                scheduler,
                repository,
                new FakeLookup(),
                new KeyMessages(),
                Clock.system(ZoneOffset.UTC),
                detail);
        menu.register(bindings, dataFolder, NOOP);
        menu.open(viewer);
    }

    private void fireClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** The detail editor's layout: six property slots across the upper rows, a back button, and a revoke delete slot. */
    private static EntityEditorLayout detailLayout() {
        return new EntityEditorLayout(
                3,
                List.of(10, 11, 12, 13, 14, 16),
                22,
                java.util.OptionalInt.of(26),
                Material.ARROW,
                Material.LAVA_BUCKET,
                Material.BLACK_STAINED_GLASS_PANE);
    }

    /** The slot -> (material, plain name) map for every non-empty, non-filler slot of {@code inv}. */
    private static Map<Integer, Snapshot> snapshot(Inventory inv) {
        Map<Integer, Snapshot> out = new LinkedHashMap<>();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType() == Material.BLACK_STAINED_GLASS_PANE) {
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

    private static String plainLore(ItemStack item) {
        List<Component> lore = Objects.requireNonNull(item.getItemMeta()).lore();
        if (lore == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (Component line : lore) {
            out.append(PlainTextComponentSerializer.plainText().serialize(line));
        }
        return out.toString();
    }

    /** What one rendered slot looks like for comparison: its material and the plain-text of its display name. */
    private record Snapshot(Material material, String name) {}

    /** A sanction store implementing only what the GUI reads; the rest throws so an unexpected read is loud. */
    private static final class FakeRepository implements ModerationRepository {
        private final List<BanEntry> bans = new ArrayList<>();
        private final List<MuteEntry> mutes = new ArrayList<>();
        private final List<JailEntry> jails = new ArrayList<>();

        void ban(UUID target, Instant until) {
            bans.add(new BanEntry(target, Issuer.console("Console"), Optional.of("test"), until));
        }

        void mute(UUID target) {
            mutes.add(new MuteEntry(target, Issuer.console("Console"), Optional.of("test"), Optional.empty()));
        }

        void jail(UUID target, String jail) {
            jails.add(new JailEntry(
                    target, jail, Issuer.console("Console"), Optional.empty(), Optional.empty(), Optional.empty()));
        }

        @Override
        public List<BanEntry> activeBans(Instant now, int limit) {
            return List.copyOf(bans);
        }

        @Override
        public List<MuteEntry> activeMutes(Instant now, int limit) {
            return List.copyOf(mutes);
        }

        @Override
        public List<JailEntry> activeJails(Instant now, int limit) {
            return List.copyOf(jails);
        }

        @Override
        public ModerationProfile load(PlayerRef target) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MuteState loadMute(PlayerRef target) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JailState loadJail(PlayerRef target) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TempbanState loadTempban(PlayerRef target) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void saveMute(PlayerRef target, MuteState mute) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void saveJail(PlayerRef target, JailState jail) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void saveTempban(PlayerRef target, TempbanState tempban) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int appendWarn(PlayerRef target, Warn warn) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Warn> warns(PlayerRef target, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int clearWarns(PlayerRef target) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int clearWarnsByActor(PlayerRef target, PlayerRef actor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void saveIpBan(IpBan ban) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeIpBan(String ip) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<IpBan> activeIpBan(String ip, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void recordSeen(PlayerRef who, Optional<String> ip, Instant at) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<SeenRecord> seen(PlayerRef who) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void ensureUserExists(PlayerRef target, Instant at) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isLockedDown() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setLockedDown(boolean enabled) {
            throw new UnsupportedOperationException();
        }
    }

    /** Records every revoke so the test could assert the detail view's revoke seam; unused by the open-detail path. */
    private static final class RecordingRevoker implements PunishmentRevoker {
        private final List<UUID> revoked = new ArrayList<>();

        @Override
        public void revoke(PlayerRef actor, PlayerRef target, PunishmentKind kind) {
            revoked.add(target.uuid());
        }
    }

    private static final class FakeLookup implements PlayerLookup {
        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            if (uuid.equals(MALLORY)) {
                return Optional.of(new PlayerRef(uuid, "Mallory"));
            }
            if (uuid.equals(EVE)) {
                return Optional.of(new PlayerRef(uuid, "Eve"));
            }
            if (uuid.equals(TRENT)) {
                return Optional.of(new PlayerRef(uuid, "Trent"));
            }
            return Optional.empty();
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return true;
        }
    }

    /**
     * Surfaces the list entry name's {@code mod_active_player} token and the detail value line's {@code value} token,
     * so a target's name appears in the rendered list label and in the opened detail editor; else the bare key. The
     * engine resolves a {@code @key} line through a lambda {@link MessageKey} carrying only the key string, so the
     * match is by {@link MessageKey#key()} rather than enum identity.
     */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            if (key.key().equals(ModerationMessageKey.MOD_GUI_LIST_ENTRY_NAME.key())) {
                return placeholders.getOrDefault("mod_active_player", "");
            }
            if (key.key().equals(ModerationMessageKey.MOD_GUI_DETAIL_VALUE_LORE.key())) {
                return placeholders.getOrDefault("value", "");
            }
            return key.key();
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
