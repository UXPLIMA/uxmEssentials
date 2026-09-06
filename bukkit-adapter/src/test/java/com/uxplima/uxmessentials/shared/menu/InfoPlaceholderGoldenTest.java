package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.InfoPlaceholders;
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
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * End-to-end golden of the time/inventory/statistic placeholder pack through the real {@link Menus} open path. A
 * viewer holds five diamonds and has two more storage slots filled, sits in a world whose time is 6000 ticks, and has
 * a MOB_KILLS statistic of seven; a custom menu whose one item reads those placeholders in its name and lore renders
 * against that live state. The rendered text proves each placeholder reads the viewer's own inventory / world / stats
 * on the render thread: {@code %held_item%}/{@code %held_amount%} show the main-hand stack, {@code %empty_slots%}/
 * {@code %used_slots%} the storage occupancy, {@code %world_time%}/{@code %world_time_formatted%} the tick and its
 * in-game clock, {@code %stat_MOB_KILLS%} the kill count, and {@code %server_date%} a non-empty real-clock date.
 */
class InfoPlaceholderGoldenTest {

    private static final String HOCON = """
            rows = 1
            items {
              panel {
                slots = ["0"],
                material = "PAPER",
                name = "%held_item% x%held_amount%",
                lore = ["%empty_slots%/%used_slots%", "%world_time% %world_time_formatted%",
                        "%stat_MOB_KILLS%", "%server_date%"]
              }
            }
            """;

    private ServerMock server;
    private PlayerMock viewer;
    private Menus menus;
    private MenuBindings bindings;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        viewer = server.addPlayer("Viewer");

        World world = viewer.getWorld();
        world.setTime(6000L);
        viewer.getInventory().setHeldItemSlot(0);
        viewer.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND, 5)); // fills held hotbar slot 0
        viewer.getInventory().setItem(10, new ItemStack(Material.BREAD, 3));
        viewer.getInventory().setItem(11, new ItemStack(Material.STONE));
        viewer.setStatistic(Statistic.MOB_KILLS, 7);

        TestMenuEngine engine = TestMenuEngine.create(new KeyMessages(), new SyncScheduler());
        InfoPlaceholders.register(engine.bindings());
        bindings = engine.bindings();
        menus = engine.menus();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void timeInventoryAndStatisticPlaceholdersRenderThroughTheOpenPath() {
        menus.registerSpec("panel", new MenuSpecLoader().parse(HOCON));
        menus.open(new PlayerRef(viewer.getUniqueId(), viewer.getName()), "panel", null);

        ItemStack item = topItem();
        assertThat(plainName(item)).isEqualTo("DIAMOND x5");

        List<String> lore = plainLore(item);
        assertThat(lore.get(0)).isEqualTo("33/3"); // three filled storage slots (0, 10, 11) of 36
        assertThat(lore.get(1)).isEqualTo("6000 12:00"); // 6000 ticks is midday
        assertThat(lore.get(2)).isEqualTo("7"); // the seeded MOB_KILLS statistic
        assertThat(lore.get(3)).matches("\\d{4}-\\d{2}-\\d{2}"); // a real-clock yyyy-MM-dd date
    }

    @Test
    void everyViewerStatePlaceholderFailsSoftToEmptyForAnOfflineViewer() {
        // A random UUID no online player owns: Bukkit.getPlayer returns null, so each viewer-state read is empty,
        // never a throw. server_date still resolves: it reads the real clock, not the viewer.
        MenuContext offline = MenuContext.of(new PlayerRef(UUID.randomUUID(), "Ghost"), null, 0);

        assertThat(bindings.placeholders().resolve("stat_MOB_KILLS", offline)).contains("");
        assertThat(bindings.placeholders().resolve("world_time", offline)).contains("");
        assertThat(bindings.placeholders().resolve("empty_slots", offline)).contains("");
        assertThat(bindings.placeholders().resolve("used_slots", offline)).contains("");
        assertThat(bindings.placeholders().resolve("held_item", offline)).contains("");
        assertThat(bindings.placeholders().resolve("held_amount", offline)).contains("");
        assertThat(bindings.placeholders().resolve("server_date", offline))
                .hasValueSatisfying(date -> assertThat(date).matches("\\d{4}-\\d{2}-\\d{2}"));
    }

    private ItemStack topItem() {
        Inventory top = viewer.getOpenInventory().getTopInventory();
        return Objects.requireNonNull(top.getItem(0), "item at slot 0");
    }

    private static String plainName(ItemStack item) {
        // The title reads off the tile wherever the canon puts it: the display name of a bare button, or the
        // first lore line of a titled tile, whose display name is deliberately blank.
        return TileText.title(item);
    }

    private static List<String> plainLore(ItemStack item) {
        // The body only: the title line the canon puts above it is asserted where the title is asserted.
        return TileText.body(item).stream()
                .map(line -> PlainTextComponentSerializer.plainText().serialize(line))
                .toList();
    }

    /** A synchronous scheduler that runs every hop inline so the open path completes within the test call. */
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

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }
}
