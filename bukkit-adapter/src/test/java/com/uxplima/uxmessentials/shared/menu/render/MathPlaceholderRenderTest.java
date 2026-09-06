package com.uxplima.uxmessentials.shared.menu.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemDecor;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemType;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.SlotSet;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.menu.TileText;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Exercises the {@code {math: <expr>}} pass through the real {@link ItemRenderer}. The math block is evaluated after
 * the {@code %token%} substitution, so a placeholder inside the expression is a literal number by the time the sandbox
 * sees it. Integer results drop the trailing {@code .0}, a fractional result keeps its decimals, an unparseable block
 * renders blank (fail-soft), and text carrying no math block, including a catalog-shaped {@code {token}}, is left
 * untouched, proving the two brace grammars do not collide.
 */
class MathPlaceholderRenderTest {

    private ItemRenderer renderer;
    private MenuContext ctx;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        PlaceholderRegistry placeholders = new PlaceholderRegistry();
        placeholders.register("coins", context -> "50");
        renderer = new ItemRenderer(new GuiText(new KeyMessages()), placeholders);
        ctx = MenuContext.of(new PlayerRef(UUID.randomUUID(), "P"), null, 0);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aConstantMathBlockEvaluatesToAnInteger() {
        assertThat(name("{math: 2+2}")).isEqualTo("4");
    }

    @Test
    void aMathBlockEvaluatesTheSubstitutedTokenFirst() {
        assertThat(name("Total: {math: %coins% * 2}")).isEqualTo("Total: 100");
    }

    @Test
    void aFractionalResultKeepsItsDecimals() {
        assertThat(name("{math: 5 / 2}")).isEqualTo("2.5");
    }

    @Test
    void anUnparseableMathBlockRendersBlank() {
        assertThat(name("value: {math: 2+}")).isEqualTo("value: ");
    }

    @Test
    void textWithoutAMathBlockIsUntouched() {
        assertThat(name("just text")).isEqualTo("just text");
    }

    @Test
    void aCatalogStyleTokenBraceIsNotTreatedAsMath() {
        assertThat(name("Rank: {rank}")).isEqualTo("Rank: {rank}");
    }

    @Test
    void mathAppliesToLoreLinesToo() {
        MenuItemSpec spec = item("x", List.of("x2 = {math: %coins% * 2}"));
        ItemStack rendered = renderer.render(spec, ctx);

        assertThat(plainLore(rendered)).containsExactly("x2 = 100");
    }

    private String name(String name) {
        return plainName(renderer.render(item(name, List.of()), ctx));
    }

    private static MenuItemSpec item(String name, List<String> lore) {
        return new MenuItemSpec(
                new SlotSet(List.of(0)),
                0,
                "STONE",
                name,
                lore,
                new ItemDecor(1, Optional.empty(), false, List.of()),
                List.of(),
                new ClickSpec(Map.of(), Map.of()),
                false,
                Optional.empty(),
                ItemType.NONE);
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

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }
}
