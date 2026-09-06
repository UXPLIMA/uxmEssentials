package com.uxplima.uxmessentials.moderation.adapter.inbound.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.moderation.application.DelJail;
import com.uxplima.uxmessentials.moderation.application.ListJails;
import com.uxplima.uxmessentials.moderation.application.SetJail;
import com.uxplima.uxmessentials.moderation.application.port.JailLocator;
import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputTestKit;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.menu.TestMenuEngine;
import com.uxplima.uxmlib.gui.Guis;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of {@link JailListView} (capability B): the list renders one icon per defined jail name,
 * clicking a jail opens the engine-rendered edit screen (drawn slot for slot through the shipped
 * {@code moderation-jail-edit} spec and the real {@code MenuListener}) whose re-anchor button saves the jail at
 * the viewer's location through {@code SetJail}, whose teleport button reuses {@code Sanctions.sendToJail}, whose
 * delete button removes it through {@code DelJail}, and whose back button reopens the list; the create path saves
 * a new jail at the viewer's location. The use cases are Mockito mocks; the synchronous scheduler runs each hop
 * inline.
 */
class JailListViewTest {

    private static final int FIRST_JAIL_SLOT = 0;
    private static final int EDIT_ROWS = 3;
    private static final int EDIT_ANCHOR_SLOT = 11;
    private static final int EDIT_GOTO_SLOT = 13;
    private static final int EDIT_DELETE_SLOT = 15;
    private static final int EDIT_BACK_SLOT = 18;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock staff;
    private PlayerRef staffRef;

    private ModerationServices services;
    private SetJail setJail;
    private DelJail delJail;
    private Sanctions sanctions;
    private JailLocator jailLocator;
    private JailListView view;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        staff = server.addPlayer("Staff");
        staffRef = new PlayerRef(staff.getUniqueId(), staff.getName());

        services = mock(ModerationServices.class);
        setJail = mock(SetJail.class);
        delJail = mock(DelJail.class);
        sanctions = mock(Sanctions.class);
        jailLocator = mock(JailLocator.class);
        ListJails listJails = mock(ListJails.class);
        when(services.setJail()).thenReturn(setJail);
        when(services.delJail()).thenReturn(delJail);
        when(services.listJails()).thenReturn(listJails);
        when(listJails.names()).thenReturn(List.of("alcatraz"));
        when(jailLocator.locate("alcatraz"))
                .thenReturn(java.util.Optional.of(new JailLocator.JailCoords("world", 10, 64, -20)));

        TextInput textInput = TextInputTestKit.create(
                plugin,
                new GuiText(new KeyMessages()),
                new SyncScheduler(),
                java.nio.file.Path.of("nonexistent"),
                new NoopLogger());
        Guis.install(plugin);
        TestMenuEngine engine = TestMenuEngine.create(new KeyMessages(), new SyncScheduler());
        engine.installListener(plugin);
        view = new JailListView(
                engine.menus(),
                new GuiText(new KeyMessages()),
                new KeyMessages(),
                new SyncScheduler(),
                services,
                sanctions,
                jailLocator,
                textInput,
                EntityListLayout.withCreate(Material.IRON_BARS, 49, Material.ANVIL));
        view.register(engine.bindings(), specDir(), new NoopLogger());
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void rendersAnIconPerDefinedJail() {
        view.open(staff, staffRef);

        Inventory menu = staff.getOpenInventory().getTopInventory();
        assertThat(menu.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(menu.getItem(FIRST_JAIL_SLOT).getType()).isEqualTo(Material.IRON_BARS);
    }

    @Test
    void clickingAJailRendersTheEngineEditScreenSlotForSlot() {
        view.open(staff, staffRef);
        fireClick(FIRST_JAIL_SLOT); // open the edit screen for "alcatraz"

        Inventory edit = staff.getOpenInventory().getTopInventory();
        assertThat(edit.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(edit.getSize()).isEqualTo(EDIT_ROWS * 9);
        assertThat(edit.getItem(EDIT_ANCHOR_SLOT).getType()).isEqualTo(Material.COMPASS);
        assertThat(edit.getItem(EDIT_GOTO_SLOT).getType()).isEqualTo(Material.ENDER_PEARL);
        assertThat(edit.getItem(EDIT_DELETE_SLOT).getType()).isEqualTo(Material.LAVA_BUCKET);
        assertThat(edit.getItem(EDIT_BACK_SLOT).getType()).isEqualTo(Material.ARROW);
        // Every non-button cell is the grey-glass backdrop, exactly as the old SimpleGui edit screen filled it.
        assertThat(edit.getItem(0).getType()).isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
    }

    @Test
    void backReopensTheJailList() {
        view.open(staff, staffRef);
        fireClick(FIRST_JAIL_SLOT); // open the edit screen for "alcatraz"

        fireClick(EDIT_BACK_SLOT);

        Inventory reopened = staff.getOpenInventory().getTopInventory();
        assertThat(reopened.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(reopened.getItem(FIRST_JAIL_SLOT).getType()).isEqualTo(Material.IRON_BARS);
    }

    @Test
    void reAnchorSavesTheJailAtTheViewersLocation() {
        view.open(staff, staffRef);
        fireClick(FIRST_JAIL_SLOT); // open the edit screen for "alcatraz"

        fireClick(EDIT_ANCHOR_SLOT);

        verify(setJail).set(eq(staffRef), eq("alcatraz"), any(Position.class));
    }

    @Test
    void deleteRemovesTheJail() {
        view.open(staff, staffRef);
        fireClick(FIRST_JAIL_SLOT);

        fireClick(EDIT_DELETE_SLOT);

        verify(delJail).delete(eq(staffRef), eq("alcatraz"));
    }

    @Test
    void createSavesANewJailAtTheViewersLocation() {
        view.createJail(staff, staffRef, "shawshank");

        verify(setJail).set(eq(staffRef), eq("shawshank"), any(Position.class));
    }

    @Test
    void goToTeleportsTheViewerToTheJail() {
        view.open(staff, staffRef);
        fireClick(FIRST_JAIL_SLOT); // open the edit screen for "alcatraz"

        fireClick(EDIT_GOTO_SLOT);

        verify(sanctions).sendToJail(eq(staffRef), eq("alcatraz"));
    }

    @Test
    void listIconLoreShowsTheJailCoordinates() {
        view.open(staff, staffRef);

        Inventory menu = staff.getOpenInventory().getTopInventory();
        String lore = loreOf(menu.getItem(FIRST_JAIL_SLOT));
        assertThat(lore).contains("world 10, 64, -20");
    }

    private static String loreOf(org.bukkit.inventory.ItemStack item) {
        net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer plain =
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        StringBuilder out = new StringBuilder();
        java.util.List<net.kyori.adventure.text.Component> lore = java.util.Objects.requireNonNull(item.lore(), "lore");
        for (net.kyori.adventure.text.Component line : lore) {
            out.append(plain.serialize(line)).append('\n');
        }
        return out.toString();
    }

    private void fireClick(int slot) {
        InventoryView inventoryView = staff.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                inventoryView, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** The bundled spec directory under the source tree, so the view loads the shipped jail-edit spec. */
    private static java.nio.file.Path specDir() {
        java.nio.file.Path repoRoot = java.nio.file.Path.of("").toAbsolutePath();
        while (repoRoot != null && !java.nio.file.Files.exists(repoRoot.resolve("settings.gradle.kts"))) {
            repoRoot = repoRoot.getParent();
        }
        java.util.Objects.requireNonNull(repoRoot, "repo root");
        return repoRoot.resolve("bukkit-adapter/src/main/resources");
    }

    /**
     * Echoes the key and any placeholder values it carried, so a rendered lore line exposes the substituted
     * values (the coordinates) the production catalog would interpolate: there is no catalog file under test.
     */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, java.util.Map<String, String> placeholders) {
            String coords = placeholders.get("coords");
            return coords == null ? key.key() : key.key() + " " + coords;
        }
    }

    private static final class NoopLogger implements Logger {
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
