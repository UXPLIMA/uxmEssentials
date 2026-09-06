package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

import com.uxplima.uxmessentials.messaging.adapter.inbound.gui.MessagingSettingsView;
import com.uxplima.uxmessentials.messaging.application.MessagingMessageKey;
import com.uxplima.uxmessentials.messaging.application.port.MessageToggleStore;
import com.uxplima.uxmessentials.messaging.application.port.SocialSpyStore;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.SettingsPanelView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ActionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ConditionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.EditorRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmlib.gui.Guis;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The messaging settings golden test, the pilot of the engine property-editor runtime. The migrated
 * {@link MessagingSettingsView} now opens through {@link Menus#openEditor}, so this asserts the engine-rendered
 * editor draws the exact panel the bespoke {@link SettingsPanelView} drew: same material and same plain name at
 * every slot, and the same value-lore for each toggle, for both toggle states. The old {@code SettingsPanelView}
 * is built directly here as the parity reference (it stays for the other settings panels and the increment-9
 * shim), so the two renders are compared live rather than against a frozen literal. A real click on the accept
 * slot through the engine's own {@link MenuListener} then proves the migrated path flips the toggle through the
 * same {@link MessageToggleStore} the {@code /msgtoggle} command drives and re-renders the slot to the new value.
 */
class MessagingSettingsGoldenTest {

    private static final Material FILLER = Material.BLACK_STAINED_GLASS_PANE;
    private static final List<Integer> SLOTS = List.of(11, 15);
    private static final int ACCEPT_SLOT = SLOTS.get(0);
    private static final int SOCIALSPY_SLOT = SLOTS.get(1);
    private static final int BACK_SLOT = 22;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private Messages messages;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        messages = new KeyMessages();
        guiText = new GuiText(messages);
        scheduler = new SyncScheduler();
        Guis.install(plugin);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSamePanelAsTheOldViewWhenAcceptIsOn() {
        assertParity(true);
    }

    @Test
    void engineRendersTheSamePanelAsTheOldViewWhenAcceptIsOff() {
        assertParity(false);
    }

    /** Snapshot the old {@link SettingsPanelView} and the engine editor for the same staff fixture; assert equal. */
    private void assertParity(boolean accepts) {
        Map<Integer, Snapshot> old = snapshotOldView(accepts, true);
        Map<Integer, Snapshot> engine = snapshotEngine(accepts, true);

        assertThat(engine.keySet()).containsExactlyInAnyOrderElementsOf(old.keySet());
        assertThat(engine).isEqualTo(old);
        // Both toggles render at their slots; staff sees accept and social-spy.
        assertThat(engine).containsKey(ACCEPT_SLOT);
        assertThat(engine).containsKey(SOCIALSPY_SLOT);
        assertThat(engine).containsKey(BACK_SLOT);
    }

    @Test
    void clickingTheAcceptToggleThroughTheEngineFlipsTheStoreAndReRendersTheSlot() {
        FakeToggles toggles = new FakeToggles(true);
        FakeSocialSpy socialSpy = new FakeSocialSpy(false);
        openEngine(toggles, socialSpy, true);

        Inventory before = player.getOpenInventory().getTopInventory();
        assertThat(toggles.accepts).isTrue();
        assertThat(valueLoreOf(before.getItem(ACCEPT_SLOT))).isEqualTo(plainOnOff(true));

        fireClick(ACCEPT_SLOT, ClickType.LEFT);

        // Flipped through the same MessageToggleStore the /msgtoggle command drives.
        assertThat(toggles.accepts).isFalse();
        Inventory after = player.getOpenInventory().getTopInventory();
        assertThat(after).isSameAs(before); // in-place re-render: no second openInventory, same holder
        assertThat(valueLoreOf(after.getItem(ACCEPT_SLOT))).isEqualTo(plainOnOff(false));
    }

    // --- snapshots ---

    /** Render the migrated, engine-backed view and snapshot its window. */
    private Map<Integer, Snapshot> snapshotEngine(boolean accepts, boolean staff) {
        openEngine(new FakeToggles(accepts), new FakeSocialSpy(false), staff);
        return snapshot(player.getOpenInventory().getTopInventory());
    }

    /** Build the engine + listener, build the migrated view, open it for the player, return the toggle stores. */
    private void openEngine(FakeToggles toggles, FakeSocialSpy socialSpy, boolean staff) {
        EditorRenderer editorRenderer = new EditorRenderer(guiText);
        ItemRenderer itemRenderer = new ItemRenderer(guiText, new PlaceholderRegistry());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, new ConditionRegistry());
        Menus menus = new Menus(renderer, scheduler, new ListSourceRegistry(), editorRenderer);
        MenuListener listener = new MenuListener(
                renderer,
                new ActionRegistry(),
                new ConditionRegistry(),
                scheduler,
                plugin,
                editorRenderer,
                menus.selectorOpener(),
                menus.confirmOpener());
        server.getPluginManager().registerEvents(listener, plugin);

        MessagingSettingsView view = new MessagingSettingsView(
                guiText, scheduler, layouts(), messages, toggles, socialSpy, perms(staff), menus);
        view.open(player, viewer);
    }

    /**
     * The frozen parity baseline the engine render is asserted against: the exact {@code (slot → material, name,
     * value-lore)} the bespoke {@code SettingsPanelView} drew for a staff fixture with social-spy off. The shim now
     * makes {@code SettingsPanelView} ride the engine, so a live "before" render is no longer a faithful "before";
     * the baseline is therefore the geometry + catalog keys read off the retired view, the same way the kit/warp
     * golden tests freeze their baseline. Names resolve to the catalog key itself ({@code KeyMessages} echoes it),
     * and each toggle's value-lore is {@code value=<on/off key>}, with the social-spy toggle always off here.
     */
    private Map<Integer, Snapshot> snapshotOldView(boolean accepts, boolean staff) {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        baseline.put(
                ACCEPT_SLOT,
                new Snapshot(
                        Material.WRITABLE_BOOK,
                        MessagingMessageKey.GUI_SETTINGS_ACCEPT.key(),
                        "value=" + onOff(viewer, accepts)));
        if (staff) {
            baseline.put(
                    SOCIALSPY_SLOT,
                    new Snapshot(
                            Material.ENDER_EYE,
                            MessagingMessageKey.GUI_SETTINGS_SOCIALSPY.key(),
                            "value=" + onOff(viewer, false)));
        }
        baseline.put(BACK_SLOT, new Snapshot(Material.ARROW, MessagingMessageKey.GUI_SETTINGS_BACK.key(), ""));
        return baseline;
    }

    private String onOff(PlayerRef who, boolean on) {
        return messages.resolve(
                who,
                on ? MessagingMessageKey.GUI_SETTINGS_VALUE_ON : MessagingMessageKey.GUI_SETTINGS_VALUE_OFF,
                Map.of());
    }

    /** The plain-text the value-lore renders for an on/off state, used to read the click-flip back. */
    private String plainOnOff(boolean on) {
        String value = onOff(viewer, on);
        return "value=" + value;
    }

    // --- harness ---

    private GuiLayouts layouts() {
        // No conf on disk and no bundled resource under the temp root, so the loader falls to the code default;
        // it carries the same geometry the production messaging-settings.conf ships, so the snapshot is faithful.
        return new GuiLayouts(java.nio.file.Path.of("nonexistent-" + UUID.randomUUID()), NOOP);
    }

    private static Permissions perms(boolean grant) {
        return new GrantingPermissions(grant);
    }

    private void fireClick(int slot, ClickType type) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event =
                new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** The slot -> (material, plain name, value-lore) map for every populated, non-filler slot of {@code inv}. */
    private static Map<Integer, Snapshot> snapshot(Inventory inv) {
        Map<Integer, Snapshot> out = new LinkedHashMap<>();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType() == FILLER) {
                continue;
            }
            out.put(slot, new Snapshot(item.getType(), plainName(item), valueLoreOrEmpty(item)));
        }
        return out;
    }

    private static String plainName(ItemStack item) {
        // The title reads off the tile wherever the canon puts it: the display name of a bare button, or the
        // first lore line of a titled tile, whose display name is deliberately blank.
        return TileText.title(item);
    }

    /** The first lore line as plain text, or empty for an item with no lore (the back button). */
    private static String valueLoreOrEmpty(ItemStack item) {
        // Under the title line the canon puts at the top of a titled tile: the body is what the value-lore is.
        List<Component> lore = TileText.body(item);
        if (lore.isEmpty()) {
            return "";
        }
        return PlainTextComponentSerializer.plainText().serialize(lore.get(0));
    }

    /** The first lore line of an item as plain text; the value-lore the editor wrote. */
    private static String valueLoreOf(ItemStack item) {
        List<Component> lore = TileText.body(item);
        assertThat(lore).isNotEmpty();
        return PlainTextComponentSerializer.plainText().serialize(lore.get(0));
    }

    private record Snapshot(Material material, String name, String valueLore) {}

    // --- fakes ---

    private static final class FakeToggles implements MessageToggleStore {
        private boolean accepts;

        FakeToggles(boolean accepts) {
            this.accepts = accepts;
        }

        @Override
        public boolean acceptsMessages(PlayerRef who) {
            return accepts;
        }

        @Override
        public boolean toggle(PlayerRef who) {
            accepts = !accepts;
            return accepts;
        }
    }

    private static final class FakeSocialSpy implements SocialSpyStore {
        private boolean spying;

        FakeSocialSpy(boolean spying) {
            this.spying = spying;
        }

        @Override
        public boolean isSpying(PlayerRef who) {
            return spying;
        }

        @Override
        public boolean toggle(PlayerRef who) {
            spying = !spying;
            return spying;
        }

        @Override
        public boolean toggleTarget(PlayerRef spy, PlayerRef target) {
            return false;
        }

        @Override
        public List<PlayerRef> activeSpies() {
            return List.of();
        }

        @Override
        public java.util.Set<PlayerRef> observersOf(PlayerRef sender, PlayerRef target) {
            return new java.util.HashSet<>();
        }
    }

    private static final class GrantingPermissions implements Permissions {
        private final boolean grant;

        GrantingPermissions(boolean grant) {
            this.grant = grant;
        }

        @Override
        public boolean has(PlayerRef who, String node) {
            return grant;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            if (key.key().equals("messaging.gui.settings.value-lore")) {
                return "value=" + placeholders.getOrDefault("value", "");
            }
            return key.key();
        }
    }

    private static final com.uxplima.uxmessentials.shared.application.port.Logger NOOP =
            new com.uxplima.uxmessentials.shared.application.port.Logger() {
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
