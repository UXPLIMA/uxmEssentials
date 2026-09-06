package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.LiveDataSources;
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
 * The read-only inventory/ender-chest viewer golden test: a custom menu whose only content is a
 * {@code list { source = self-inventory | self-enderchest }} over the raw-entry marker renders one live tile per
 * non-empty stack the viewer holds, with zero feature code. Both cases go through the real {@link Menus} open path
 * (async list-resolution → entity-thread snapshot → entity-thread render): the snapshot reads the viewer's own
 * inventory on their entity region thread, each cell being the actual {@link ItemStack} drawn by the
 * {@code EntryStackIconProvider} claiming {@code material = "entry"}, and {@code decor { amount = "%entry_amount%" }}
 * carries the real stack size through.
 *
 * <p>The snapshot hand-off resolves under the test scheduler: it reports itself off the viewer's region (the
 * {@link Scheduler} default {@code ownsEntity}) and runs {@code onEntity} synchronously, so the source's latch
 * completes the moment the read is scheduled. The read is defensive. The source clones each stack and the icon
 * provider clones again, so the rendered tiles can never mutate the viewer's real items.
 */
class InventoryViewerGoldenTest {

    private static final String INVENTORY_HOCON = """
            rows = 1
            items {
              bag {
                slots = ["0-8"],
                list {
                  source = "self-inventory"
                  template { material = "entry", decor { amount = "%entry_amount%" } }
                }
              }
            }
            """;

    private static final String ENDERCHEST_HOCON = """
            rows = 1
            items {
              stash {
                slots = ["0-8"],
                list {
                  source = "self-enderchest"
                  template { material = "entry", decor { amount = "%entry_amount%" } }
                }
              }
            }
            """;

    private ServerMock server;
    private PlayerMock viewerPlayer;
    private Menus menus;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        viewerPlayer = server.addPlayer("Alice");

        TestMenuEngine engine = TestMenuEngine.create(new KeyMessages(), new SyncScheduler());
        LiveDataSources.register(engine.bindings(), new SyncScheduler());
        menus = engine.menus();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void selfInventorySourceRendersATilePerHeldStack() {
        viewerPlayer.getInventory().addItem(new ItemStack(Material.DIAMOND, 3));
        viewerPlayer.getInventory().addItem(new ItemStack(Material.BREAD, 1));
        menus.registerSpec("bag", new MenuSpecLoader().parse(INVENTORY_HOCON));

        menus.open(new PlayerRef(viewerPlayer.getUniqueId(), viewerPlayer.getName()), "bag", null);

        Inventory top = viewerPlayer.getOpenInventory().getTopInventory();
        assertTile(top.getItem(0), Material.DIAMOND, 3);
        assertTile(top.getItem(1), Material.BREAD, 1);
        assertThat(top.getItem(2)).as("no third held stack, so the grid stops").isNull();
    }

    @Test
    void selfEnderchestSourceRendersATilePerStoredStack() {
        viewerPlayer.getEnderChest().addItem(new ItemStack(Material.EMERALD, 7));
        menus.registerSpec("stash", new MenuSpecLoader().parse(ENDERCHEST_HOCON));

        menus.open(new PlayerRef(viewerPlayer.getUniqueId(), viewerPlayer.getName()), "stash", null);

        Inventory top = viewerPlayer.getOpenInventory().getTopInventory();
        assertTile(top.getItem(0), Material.EMERALD, 7);
        assertThat(top.getItem(1))
                .as("only one stored stack, so slot 1 stays empty")
                .isNull();
    }

    @Test
    void renderedTilesAreClonesThatNeverMutateTheViewersItems() {
        viewerPlayer.getInventory().addItem(new ItemStack(Material.DIAMOND, 3));
        menus.registerSpec("bag", new MenuSpecLoader().parse(INVENTORY_HOCON));

        menus.open(new PlayerRef(viewerPlayer.getUniqueId(), viewerPlayer.getName()), "bag", null);

        Inventory top = viewerPlayer.getOpenInventory().getTopInventory();
        ItemStack tile = top.getItem(0);
        assertThat(tile).isNotNull();
        // Mutating the rendered tile must not touch the backing inventory item, the double clone (source + provider).
        tile.setAmount(1);
        assertThat(viewerPlayer.getInventory().getItem(0)).isEqualTo(new ItemStack(Material.DIAMOND, 3));
    }

    /** Assert a rendered tile carries the given material and amount, whatever cosmetic name/lore layered on top. */
    private static void assertTile(ItemStack tile, Material material, int amount) {
        assertThat(tile).isNotNull();
        assertThat(tile.getType()).isEqualTo(material);
        assertThat(tile.getAmount()).isEqualTo(amount);
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /**
     * A synchronous scheduler that runs every hop inline, including {@code onEntity}, so the storage source's
     * snapshot latch is released the moment it is scheduled, exercising the helper's latch path (it reports itself
     * off the viewer's entity region, the {@link Scheduler} default {@code ownsEntity}).
     */
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
