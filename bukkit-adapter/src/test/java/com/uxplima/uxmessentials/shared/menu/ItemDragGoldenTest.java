package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
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
 * End-to-end golden of the Phase-7 item-drag slot, driven config → open → click-with-cursor → effect through the
 * real {@link MenuSpecLoader} and the live click listener. The one item declares an {@code item-drag} block that
 * accepts two or more diamonds, fires a recording action exposing the dropped item as {@code %drag_material%}/
 * {@code %drag_amount%}, and consumes the matched amount. A held stack that passes the rules fires the actions with
 * the item resolved and reduces the cursor; a stack below the minimum, or of the wrong material, is a silent deny
 * that leaves the cursor untouched; and an empty cursor takes the item's ordinary click, never the drag.
 *
 * <p>MockBukkit models the view cursor ({@code getCursor}/{@code setCursor}), so the consume assertion runs for real
 * here: the same controlled write production does on the viewer's entity thread. The click stays cancelled the whole
 * time, so vanilla never moves the held item: the consume is the only mutation, and it cannot dupe.
 */
class ItemDragGoldenTest {

    private static final String HOCON = """
            rows = 1
            items { deposit {
              slot = 0, material = CHEST, name = "Deposit",
              click { left = ["record:normal"] }
              item-drag {
                rules { materials = ["DIAMOND"], min-amount = 2 }
                consume = true
                actions = ["record:%drag_material%x%drag_amount%"]
              }
            } }
            """;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private Menus menus;
    private List<String> notes;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Viewer");
        notes = new ArrayList<>();

        TestMenuEngine engine = TestMenuEngine.create(new KeyMessages(), new SyncScheduler());
        engine.bindings().action("record", ctx -> notes.add(ctx.arg()));
        menus = engine.menus();
        menus.registerSpec("deposit", new MenuSpecLoader().parse(HOCON));
        engine.installListener(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aMatchingCursorFiresTheDragActionsAndConsumesTheMatchedAmount() {
        open();
        setCursor(new ItemStack(Material.DIAMOND, 3));

        InventoryClickEvent event = click(0);

        assertThat(notes)
                .as("a matching drag fires its own actions with the dropped item exposed as %drag_*%")
                .containsExactly("DIAMONDx3");
        assertThat(cursor())
                .as("consume removes the matched amount (2) from the held stack, leaving one diamond")
                .isNotNull()
                .extracting(ItemStack::getType, ItemStack::getAmount)
                .containsExactly(Material.DIAMOND, 1);
        assertThat(event.isCancelled())
                .as("the click stays cancelled, so vanilla never moves the item. The consume is the only mutation")
                .isTrue();
    }

    @Test
    void aCursorBelowTheMinimumAmountIsSilentlyDeniedAndLeftUntouched() {
        open();
        setCursor(new ItemStack(Material.DIAMOND, 1));

        click(0);

        assertThat(notes)
                .as("one diamond is below the minimum of two, so no drag action runs")
                .isEmpty();
        assertThat(cursor())
                .as("a denied drag leaves the held stack exactly as it was")
                .isNotNull()
                .extracting(ItemStack::getType, ItemStack::getAmount)
                .containsExactly(Material.DIAMOND, 1);
    }

    @Test
    void aCursorOfAnUnwhitelistedMaterialIsDeniedAndLeftUntouched() {
        open();
        setCursor(new ItemStack(Material.DIRT, 5));

        click(0);

        assertThat(notes)
                .as("dirt is not on the material whitelist, so the drag denies")
                .isEmpty();
        assertThat(cursor())
                .as("a denied drag leaves the wrong-material stack untouched")
                .isNotNull()
                .extracting(ItemStack::getType, ItemStack::getAmount)
                .containsExactly(Material.DIRT, 5);
    }

    @Test
    void anEmptyCursorRunsTheItemsNormalClickNotTheDrag() {
        open();
        setCursor(null);

        click(0);

        assertThat(notes)
                .as("with no item on the cursor the slot runs its ordinary click, never the drag flow")
                .containsExactly("normal");
    }

    private void open() {
        menus.open(new PlayerRef(player.getUniqueId(), player.getName()), "deposit", null);
    }

    private void setCursor(ItemStack stack) {
        player.getOpenInventory().setCursor(stack);
    }

    private ItemStack cursor() {
        return player.getOpenInventory().getCursor();
    }

    private InventoryClickEvent click(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PLACE_ALL);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** A synchronous scheduler that runs every hop inline so the click's action dispatch completes within the call. */
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

        @Override
        public AutoCloseable repeatGlobal(Runnable task, Duration initialDelay, Duration period) {
            return () -> {};
        }
    }
}
