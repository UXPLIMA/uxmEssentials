package com.uxplima.uxmessentials.shared.menu.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ConditionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.RenderedSlot;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Renders a whole parsed spec into an inventory under MockBukkit. The spec mixes a static border, a paginated
 * list whose template name echoes the entry, and an item whose view condition always fails, so one populate call
 * exercises priority layering, the list source, pagination, and view gating together.
 */
class MenuRendererTest {

    private static final String HOCON = """
            rows = 1
            items {
              border { slots = ["0-2"], material = STONE, name = "" }
              things {
                slots = ["3-5"],
                list { source = "t:list", template { material = STONE, name = "%v%" } }
              }
              hidden { slot = 6, material = STONE, name = "X", view = ["always-false"] }
            }
            """;

    private static final Map<String, List<?>> RESOLVED = Map.of("t:list", List.of("a", "b", "c", "d"));

    private MenuRenderer renderer;
    private MenuSpec spec;
    private PlayerRef ref;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        GuiText guiText = new GuiText(new KeyMessages());
        PlaceholderRegistry placeholders = new PlaceholderRegistry();
        placeholders.register("v", ctx -> ctx.entry(String.class));
        ItemRenderer itemRenderer = new ItemRenderer(guiText, placeholders);
        ConditionRegistry conditions = new ConditionRegistry();
        conditions.register("always-false", (c, args) -> false);
        renderer = new MenuRenderer(itemRenderer, conditions);
        spec = new MenuSpecLoader().parse(HOCON);
        ref = new PlayerRef(UUID.randomUUID(), "P");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void rendersListPageZeroAndHidesFailingView() {
        Inventory inv = Bukkit.createInventory(null, 9);
        renderer.populate(inv, spec, MenuContext.of(ref, null, 0), (s, r) -> {}, RESOLVED);
        assertThat(plainName(inv, 3)).isEqualTo("a");
        assertThat(plainName(inv, 4)).isEqualTo("b");
        assertThat(plainName(inv, 5)).isEqualTo("c");
        assertThat(inv.getItem(6)).isNull(); // view failed -> not placed
        assertThat(inv.getItem(0)).isNotNull(); // border placed
    }

    @Test
    void rendersListPageOne() {
        Inventory inv = Bukkit.createInventory(null, 9);
        renderer.populate(inv, spec, MenuContext.of(ref, null, 1), (s, r) -> {}, RESOLVED);
        assertThat(plainName(inv, 3)).isEqualTo("d");
        assertThat(inv.getItem(4)).isNull();
    }

    @Test
    void reRenderingAShorterPageIntoTheSameWindowClearsTheStaleListSlots() {
        Inventory inv = Bukkit.createInventory(null, 9);
        // Page 0 fills content slots 3, 4, 5 with a, b, c.
        renderer.populate(inv, spec, MenuContext.of(ref, null, 0), (s, r) -> {}, RESOLVED);
        // Re-rendering page 1 into the same window must leave only its single entry, clearing the stale b and c.
        renderer.populate(inv, spec, MenuContext.of(ref, null, 1), (s, r) -> {}, RESOLVED);
        assertThat(plainName(inv, 3)).isEqualTo("d");
        assertThat(inv.getItem(4)).isNull();
        assertThat(inv.getItem(5)).isNull();
        assertThat(inv.getItem(0)).isNotNull(); // the static border outside the content slots is untouched
    }

    @Test
    void recordsRenderedSlotsForClickRouting() {
        Inventory inv = Bukkit.createInventory(null, 9);
        Map<Integer, RenderedSlot> sink = new HashMap<>();
        renderer.populate(inv, spec, MenuContext.of(ref, null, 0), sink::put, RESOLVED);
        assertThat(sink.get(3).entry()).isEqualTo("a");
    }

    private static String plainName(Inventory inv, int slot) {
        var item = inv.getItem(slot);
        if (item == null) {
            return "";
        }
        return PlainTextComponentSerializer.plainText()
                .serialize(item.getItemMeta().displayName());
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }
}
