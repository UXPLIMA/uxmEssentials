package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

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
 * A static "page info" item resolves {@code %max_page%} to the page count its sibling list paginates across, even
 * though static items render before the list pages. Seven entries over three content slots span three pages, so the
 * indicator reads {@code 1/3} on page zero and {@code 2/3} after a flip. The renderer stamps the page count onto the
 * context it draws the static item with, mirroring the one {@code populateList} would have computed.
 */
class MaxPagePlaceholderTest {

    private static final String HOCON = """
            rows = 2
            items {
              info { slot = 0, material = PAPER, name = "%page%/%max_page%" }
              things {
                slots = ["9-11"],
                list { source = "t:list", template { material = STONE, name = "%v%" } }
              }
            }
            """;

    private static final Map<String, List<?>> RESOLVED = Map.of("t:list", List.of("a", "b", "c", "d", "e", "f", "g"));

    private MenuRenderer renderer;
    private MenuSpec spec;
    private PlayerRef ref;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        GuiText guiText = new GuiText(new KeyMessages());
        PlaceholderRegistry placeholders = new PlaceholderRegistry();
        placeholders.register("v", ctx -> ctx.entry(String.class));
        placeholders.register("page", ctx -> String.valueOf(ctx.page() + 1));
        placeholders.register("max_page", ctx -> String.valueOf(ctx.pageCount()));
        ItemRenderer itemRenderer = new ItemRenderer(guiText, placeholders);
        renderer = new MenuRenderer(itemRenderer, new ConditionRegistry());
        spec = new MenuSpecLoader().parse(HOCON);
        ref = new PlayerRef(UUID.randomUUID(), "P");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void maxPageOnFirstPageReadsTotalPageCount() {
        Inventory inv = Bukkit.createInventory(null, 18);
        renderer.populate(inv, spec, MenuContext.of(ref, null, 0), (s, r) -> {}, RESOLVED);
        assertThat(plainName(inv, 0)).isEqualTo("1/3");
    }

    @Test
    void maxPageOnSecondPageKeepsTheSameTotal() {
        Inventory inv = Bukkit.createInventory(null, 18);
        renderer.populate(inv, spec, MenuContext.of(ref, null, 1), (s, r) -> {}, RESOLVED);
        assertThat(plainName(inv, 0)).isEqualTo("2/3");
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
