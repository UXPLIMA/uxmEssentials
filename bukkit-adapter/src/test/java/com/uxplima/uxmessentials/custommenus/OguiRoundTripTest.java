package com.uxplima.uxmessentials.custommenus;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.uxplima.uxmessentials.custommenus.adapter.convert.OguiConverter;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Requirement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The key correctness proof for the OGUI converter: its output is not merely well-shaped HOCON, it is HOCON our own
 * {@link MenuSpecLoader} loads into a valid {@link MenuSpec}. A representative OGUI GUI is converted, written to a
 * {@code .conf} on disk, and loaded back through the real loader, so a passing test means an operator's converted
 * menu opens on the engine, title, rows, item, slots, click actions and view gate intact, all as bare {@code id:value}
 * refs the loader parses.
 */
class OguiRoundTripTest {

    private static final String MENU = """
            shop:
              title: 'Shop'
              rows: 4
              commands: [ shop ]
              items:
                '10':
                  material: GRASS_BLOCK
                  name: '&aOverworld'
                  lore:
                    - '&7Click me'
                  commands:
                    - 'give {player} diamond 1'
                  close: true
                  conditions:
                    - type: WORLD
                      worlds:
                        - world
                        - lobby
                gift:
                  slot: 12
                  material: CHEST
                  name: '&bGift'
                  actions:
                    - type: PLAYER_MESSAGE
                      message: hi
                    - type: OPEN_GUI
                      gui: other
            """;

    @Test
    void convertedMenuWrittenToDiskLoadsAsAValidSpec(@TempDir Path dir) throws Exception {
        String hocon = new OguiConverter().convert(MENU).hocon();
        Path file = dir.resolve("shop.conf");
        Files.writeString(file, hocon);

        MenuSpec spec = new MenuSpecLoader().load(file);

        assertThat(spec.title()).isEqualTo("Shop");
        assertThat(spec.rows()).isEqualTo(4);
        assertThat(spec.items()).containsKeys("10", "gift");
    }

    @Test
    void theConvertedItemCarriesItsSlotsClickActionsAndWorldView() {
        MenuSpec spec =
                new MenuSpecLoader().parse(new OguiConverter().convert(MENU).hocon());
        MenuItemSpec overworld = spec.items().get("10");

        assertThat(overworld.material()).isEqualTo("GRASS_BLOCK");
        assertThat(overworld.name()).isEqualTo("&aOverworld");
        assertThat(overworld.slots().slots()).contains(10);
        assertThat(overworld.lore()).containsExactly("&7Click me");
        assertThat(refToken(overworld.click().actions().get(ClickKind.ANY)))
                .containsExactly("console=give %player% diamond 1", "close=");
        // The loader is registry-blind: it keeps a `world:world` token whole (only well-known generic heads split at
        // parse time), and the runtime re-splits it to the `world` condition against the live registry. Asserting the
        // whole token here proves the view loaded both world members and the OR-group minimum.
        assertThat(conditionIds(overworld.view().requirements())).containsExactly("world:world", "world:lobby");
        assertThat(overworld.view().minimum()).isEqualTo(1);
    }

    @Test
    void theTypedActionItemLoadsItsMessageAndOpenActions() {
        MenuSpec spec =
                new MenuSpecLoader().parse(new OguiConverter().convert(MENU).hocon());
        MenuItemSpec gift = spec.items().get("gift");

        assertThat(refToken(gift.click().actions().get(ClickKind.ANY))).containsExactly("message=hi", "open=other");
    }

    /** Render each ref as {@code id=value} so the assertions read the parsed id and argument, not object identity. */
    private static List<String> refToken(List<Ref> refs) {
        return refs.stream().map(ref -> ref.id() + "=" + ref.value()).toList();
    }

    /** The parse-time id of each view requirement's condition ref: a registry-blind {@code world:world} stays whole. */
    private static List<String> conditionIds(List<Requirement> requirements) {
        return requirements.stream().map(req -> req.condition().id()).toList();
    }
}
