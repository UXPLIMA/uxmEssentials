package com.uxplima.uxmessentials.custommenus;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.uxplima.uxmessentials.custommenus.adapter.convert.GuiPlusConverter;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Requirement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The key correctness proof for the GUIPlus converter: its output is not merely well-shaped HOCON, it is HOCON our own
 * {@link MenuSpecLoader} loads into a valid {@link MenuSpec}. A representative GUIPlus GUI is converted, written to a
 * {@code .conf} on disk, and loaded back through the real loader, so a passing test means an operator's converted menu
 * opens on the engine, title, rows, item, slots, the gesture-split click actions and the view gate intact, all as bare
 * {@code id:value} refs the loader parses.
 */
class GuiPlusRoundTripTest {

    private static final String MENU = """
            id: shop_menu
            type: chest
            rows: 3
            title: 'Shop'
            permission: shop.open
            scenes:
              '0':
                items:
                  '1':
                    slot: 11
                    item: DIAMOND
                    item-name: 'Diamond'
                    item-lore:
                      - 'Line one'
                    click-events:
                      message:
                        clickType: LEFT
                        message: hi
                      console_command:
                        commands:
                          - 'say x'
                      close-inventory: {}
                    conditions:
                      has-permission:
                        permission: shop.use
            """;

    @Test
    void convertedMenuWrittenToDiskLoadsAsAValidSpec(@TempDir Path dir) throws Exception {
        String hocon = new GuiPlusConverter().convert(MENU).hocon();
        Path file = dir.resolve("shop.conf");
        Files.writeString(file, hocon);

        MenuSpec spec = new MenuSpecLoader().load(file);

        assertThat(spec.title()).isEqualTo("Shop");
        assertThat(spec.rows()).isEqualTo(3);
        assertThat(spec.items()).containsKey("1");
    }

    @Test
    void theConvertedItemCarriesItsSlotsGestureSplitActionsAndView() {
        MenuSpec spec =
                new MenuSpecLoader().parse(new GuiPlusConverter().convert(MENU).hocon());
        MenuItemSpec diamond = spec.items().get("1");

        assertThat(diamond.material()).isEqualTo("DIAMOND");
        assertThat(diamond.slots().slots()).contains(11);
        assertThat(refToken(diamond.click().actions().get(ClickKind.LEFT))).containsExactly("message=hi");
        assertThat(refToken(diamond.click().actions().get(ClickKind.ANY))).containsExactly("console=say x", "close=");
        assertThat(diamond.view().requirements()).hasSize(1);
        Requirement gate = diamond.view().requirements().get(0);
        assertThat(gate.inverted()).isFalse();
        assertThat(gate.condition().id()).isEqualTo("perm");
        assertThat(gate.condition().value()).isEqualTo("shop.use");
    }

    /** Render each ref as {@code id=value} so the assertions read the parsed id and argument, not object identity. */
    private static List<String> refToken(List<Ref> refs) {
        return refs.stream().map(ref -> ref.id() + "=" + ref.value()).toList();
    }
}
