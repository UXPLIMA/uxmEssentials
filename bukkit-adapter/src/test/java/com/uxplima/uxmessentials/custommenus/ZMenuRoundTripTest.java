package com.uxplima.uxmessentials.custommenus;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.uxplima.uxmessentials.custommenus.adapter.convert.ZMenuConverter;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The key correctness proof for the zMenu converter: its output is not merely well-shaped HOCON, it is HOCON our own
 * {@link MenuSpecLoader} loads into a valid {@link MenuSpec}. A representative zMenu inventory is converted, written to
 * a {@code .conf} on disk, and loaded back through the real loader, so a passing test means an operator's converted
 * menu opens on the engine, title, rows, item, slots and click actions intact.
 */
class ZMenuRoundTripTest {

    private static final String MENU = """
            name: 'Cookies'
            size: 36
            items:
              cookie:
                slot: 13
                item:
                  material: COOKIE
                  name: '&8Cookie'
                  lore:
                    - '&7Click me'
                actions:
                  - type: message
                    messages: [hi]
                  - type: console
                    commands: [say x]
                  - type: inventory
                    inventory: other
            """;

    @Test
    void convertedMenuWrittenToDiskLoadsAsAValidSpec(@TempDir Path dir) throws Exception {
        String hocon = new ZMenuConverter().convert(MENU).hocon();
        Path file = dir.resolve("cookies.conf");
        Files.writeString(file, hocon);

        MenuSpec spec = new MenuSpecLoader().load(file);

        assertThat(spec.title()).isEqualTo("Cookies");
        assertThat(spec.rows()).isEqualTo(4);
        assertThat(spec.items()).containsKey("cookie");
    }

    @Test
    void theConvertedItemCarriesItsSlotsAndClickActions() {
        MenuSpec spec =
                new MenuSpecLoader().parse(new ZMenuConverter().convert(MENU).hocon());
        MenuItemSpec cookie = spec.items().get("cookie");

        assertThat(cookie.material()).isEqualTo("COOKIE");
        assertThat(cookie.name()).isEqualTo("&8Cookie");
        assertThat(cookie.slots().slots()).contains(13);
        assertThat(cookie.lore()).containsExactly("&7Click me");
        assertThat(refToken(cookie.click().actions().get(ClickKind.ANY)))
                .containsExactly("message=hi", "console=say x", "open=other");
    }

    /** Render each ref as {@code id=value} so the assertions read the parsed id and argument, not object identity. */
    private static List<String> refToken(List<Ref> refs) {
        return refs.stream().map(ref -> ref.id() + "=" + ref.value()).toList();
    }
}
