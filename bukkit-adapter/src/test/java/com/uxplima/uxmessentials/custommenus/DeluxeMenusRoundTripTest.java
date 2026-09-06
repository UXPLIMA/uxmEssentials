package com.uxplima.uxmessentials.custommenus;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.uxplima.uxmessentials.custommenus.adapter.convert.DeluxeMenusConverter;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Requirement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The key correctness proof for the DeluxeMenus converter: its output is not merely well-shaped HOCON, it is HOCON
 * our own {@link MenuSpecLoader} loads into a valid {@link MenuSpec}. A representative DeluxeMenus menu is converted,
 * written to a {@code .conf} on disk, and loaded back through the real loader, so a passing test means an operator's
 * converted menu opens on the engine, title, rows, item, slots, click actions and view gate intact.
 */
class DeluxeMenusRoundTripTest {

    private static final String MENU = """
            menu_title: 'Test Menu'
            size: 9
            open_command: testmenu
            open_requirement:
              requirements:
                perm:
                  type: has permission
                  permission: test.open
            items:
              stone:
                material: STONE
                slot: 0
                display_name: '&aStone'
                lore:
                  - '&7line one'
                  - '&7line two'
                left_click_commands:
                  - '[message] hi'
                  - '[console] say x'
                  - '[openguimenu] other'
                view_requirement:
                  requirements:
                    vip:
                      type: has permission
                      permission: menu.vip
            """;

    @Test
    void convertedMenuWrittenToDiskLoadsAsAValidSpec(@TempDir Path dir) throws Exception {
        String hocon = new DeluxeMenusConverter().convert(MENU).hocon();
        Path file = dir.resolve("testmenu.conf");
        Files.writeString(file, hocon);

        MenuSpec spec = new MenuSpecLoader().load(file);

        assertThat(spec.title()).isEqualTo("Test Menu");
        assertThat(spec.rows()).isEqualTo(1);
        assertThat(refToken(spec.openRequirement())).containsExactly("perm=test.open");
        assertThat(spec.items()).containsKey("stone");
    }

    @Test
    void theConvertedItemCarriesItsSlotsClickActionsAndViewGate() {
        MenuSpec spec = new MenuSpecLoader()
                .parse(new DeluxeMenusConverter().convert(MENU).hocon());
        MenuItemSpec stone = spec.items().get("stone");

        assertThat(stone.material()).isEqualTo("STONE");
        assertThat(stone.name()).isEqualTo("&aStone");
        assertThat(stone.slots().slots()).contains(0);
        assertThat(stone.lore()).containsExactly("&7line one", "&7line two");
        assertThat(refToken(stone.click().actions().get(ClickKind.LEFT)))
                .containsExactly("message=hi", "console=say x", "open=other");
        List<Requirement> view = stone.view().requirements();
        assertThat(view).hasSize(1);
        assertThat(view.get(0).condition().id()).isEqualTo("perm");
        assertThat(view.get(0).condition().value()).isEqualTo("menu.vip");
    }

    /** Render each ref as {@code id=value} so the assertions read the parsed id and argument, not object identity. */
    private static List<String> refToken(List<Ref> refs) {
        return refs.stream().map(ref -> ref.id() + "=" + ref.value()).toList();
    }
}
