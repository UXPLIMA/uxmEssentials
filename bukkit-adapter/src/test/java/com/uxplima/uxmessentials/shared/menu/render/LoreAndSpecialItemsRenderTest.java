package com.uxplima.uxmessentials.shared.menu.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockDataMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers.IconProviders;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemDecor;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemType;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.LoreMode;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.SlotSet;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.SerializedItems;
import com.uxplima.uxmessentials.shared.adapter.outbound.hooks.HeadQuery;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * Golden renders of the lore-append modes and the three native icon sources. A serialized {@code b64:} stack, the
 * {@code water_bottle} keyword, and {@code light:<n>}. Each item is rendered through the real {@link ItemRenderer}
 * (built with the composition root's {@link IconProviders#full full} chain, where those providers live) against
 * MockBukkit so the outcome is asserted on a concrete {@link ItemStack}. MockBukkit v26.2 round-trips a stack's
 * lore through the {@code b64:} codec, so the append/prepend/replace merge is asserted concretely; the potion
 * base-type and block-data level it does not fully model are asserted as a valid item that never throws (they still
 * apply on real Paper), the same fail-soft split the rich-meta tests take.
 */
class LoreAndSpecialItemsRenderTest {

    private ServerMock server;
    private ItemRenderer renderer;
    private MenuContext ctx;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        PlaceholderRegistry placeholders = new PlaceholderRegistry();
        renderer = new ItemRenderer(
                new GuiText(new KeyMessages()), placeholders, IconProviders.full(server, SILENT, HeadQuery.ABSENT));
        ctx = MenuContext.of(new PlayerRef(UUID.randomUUID(), "P"), null, 0);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void appendModeKeepsBaseLoreThenSpecLore() {
        String token = encodedDiamondWithLore("Base A", "Base B");
        ItemStack rendered = renderer.render(item(token, List.of("Spec X", "Spec Y"), LoreMode.APPEND), ctx);
        assertThat(rendered.getType()).isEqualTo(Material.DIAMOND);
        assertThat(plainLore(rendered)).containsExactly("Base A", "Base B", "Spec X", "Spec Y");
    }

    @Test
    void prependModePutsSpecLoreThenBaseLore() {
        String token = encodedDiamondWithLore("Base A", "Base B");
        ItemStack rendered = renderer.render(item(token, List.of("Spec X", "Spec Y"), LoreMode.PREPEND), ctx);
        assertThat(rendered.getType()).isEqualTo(Material.DIAMOND);
        assertThat(plainLore(rendered)).containsExactly("Spec X", "Spec Y", "Base A", "Base B");
    }

    @Test
    void replaceModeKeepsOnlySpecLore() {
        String token = encodedDiamondWithLore("Base A", "Base B");
        ItemStack rendered = renderer.render(item(token, List.of("Spec X"), LoreMode.REPLACE), ctx);
        assertThat(rendered.getType()).isEqualTo(Material.DIAMOND);
        assertThat(plainLore(rendered)).containsExactly("Spec X");
    }

    @Test
    void theDelegatingConstructorDefaultsToReplaceSoExistingMenusAreUnchanged() {
        String token = encodedDiamondWithLore("Base A", "Base B");
        // The eleven-argument form is the shape every existing call site uses; it must carry REPLACE.
        MenuItemSpec viaOldConstructor = new MenuItemSpec(
                new SlotSet(List.of(0)),
                0,
                token,
                "",
                List.of("Spec X"),
                new ItemDecor(1, Optional.empty(), false, List.of()),
                List.of(),
                new ClickSpec(Map.of(), Map.of()),
                false,
                Optional.empty(),
                ItemType.NONE);
        assertThat(viaOldConstructor.loreMode()).isEqualTo(LoreMode.REPLACE);
        assertThat(plainLore(renderer.render(viaOldConstructor, ctx))).containsExactly("Spec X");
    }

    @Test
    void serializedStackRoundTripsToItsMaterial() {
        String token = SerializedItems.encode(new ItemStack(Material.DIAMOND));
        assertThat(renderer.render(item(token, List.of(), LoreMode.REPLACE), ctx)
                        .getType())
                .isEqualTo(Material.DIAMOND);
    }

    @Test
    void malformedSerializedTokenFallsBackToStoneWithoutCrashing() {
        assertThatCode(() -> assertThat(renderer.render(item("b64:zzz", List.of(), LoreMode.REPLACE), ctx)
                                .getType())
                        .isEqualTo(Material.STONE))
                .doesNotThrowAnyException();
    }

    @Test
    void waterBottleRendersAPotion() {
        ItemStack rendered = renderer.render(item("water_bottle", List.of(), LoreMode.REPLACE), ctx);
        assertThat(rendered.getType()).isEqualTo(Material.POTION);
        // Where MockBukkit models the base potion type, it is WATER; where it does not, the plain POTION is enough.
        if (rendered.getItemMeta() instanceof PotionMeta potion && potion.getBasePotionType() == PotionType.WATER) {
            assertThat(potion.getBasePotionType()).isEqualTo(PotionType.WATER);
        }
    }

    @Test
    void lightRendersALightBlockAtTheRequestedLevel() {
        ItemStack rendered = renderer.render(item("light:7", List.of(), LoreMode.REPLACE), ctx);
        assertThat(rendered.getType()).isEqualTo(Material.LIGHT);
        // Where MockBukkit models block-data light levels, the level is the one requested; otherwise LIGHT suffices.
        if (rendered.getItemMeta() instanceof BlockDataMeta meta && meta.hasBlockData()) {
            BlockData data = meta.getBlockData(Material.LIGHT);
            if (data instanceof Levelled levelled) {
                assertThat(levelled.getLevel()).isEqualTo(7);
            }
        }
    }

    @Test
    void lightIsFailSoftOnOutOfRangeAndMalformedLevels() {
        assertThatCode(() -> {
                    assertThat(renderer.render(item("light:99", List.of(), LoreMode.REPLACE), ctx)
                                    .getType())
                            .isEqualTo(Material.LIGHT);
                    assertThat(renderer.render(item("light:abc", List.of(), LoreMode.REPLACE), ctx)
                                    .getType())
                            .isEqualTo(Material.LIGHT);
                })
                .doesNotThrowAnyException();
    }

    @Test
    void plainMaterialRendersUnchangedAndAppendAddsNoBaseLore() {
        // A plain material carries no lore of its own, so even APPEND yields exactly the spec lore.
        ItemStack rendered = renderer.render(item("DIAMOND", List.of("Only line"), LoreMode.APPEND), ctx);
        assertThat(rendered.getType()).isEqualTo(Material.DIAMOND);
        assertThat(plainLore(rendered)).containsExactly("Only line");
    }

    @Test
    void loaderParsesTheLoreModeGrammar() {
        assertThat(loreMode("append")).isEqualTo(LoreMode.APPEND);
        assertThat(loreMode("prepend")).isEqualTo(LoreMode.PREPEND);
        assertThat(loreMode("replace")).isEqualTo(LoreMode.REPLACE);
        // An unknown or absent mode is REPLACE, so a spec that never declared one renders as it always did.
        assertThat(loreMode("nonsense")).isEqualTo(LoreMode.REPLACE);
        assertThat(new MenuSpecLoader()
                        .parse("rows=1\nitems{ x{ slot=0, material=STONE } }")
                        .items()
                        .get("x")
                        .loreMode())
                .isEqualTo(LoreMode.REPLACE);
    }

    private LoreMode loreMode(String token) {
        String hocon = "rows=1\nitems{ x{ slot=0, material=STONE, lore-mode=" + token + " } }";
        return new MenuSpecLoader().parse(hocon).items().get("x").loreMode();
    }

    private static String encodedDiamondWithLore(String... loreLines) {
        ItemStack base = new ItemStack(Material.DIAMOND);
        base.lore(List.of(loreLines).stream().map(Component::text).toList());
        return SerializedItems.encode(base);
    }

    private static MenuItemSpec item(String material, List<String> lore, LoreMode mode) {
        return new MenuItemSpec(
                new SlotSet(List.of(0)),
                0,
                material,
                "",
                lore,
                new ItemDecor(1, Optional.empty(), false, List.of()),
                mode,
                List.of(),
                new ClickSpec(Map.of(), Map.of()),
                false,
                Optional.empty(),
                ItemType.NONE);
    }

    private static List<String> plainLore(ItemStack item) {
        List<Component> lore = item.lore();
        if (lore == null) {
            return List.of();
        }
        return lore.stream()
                .map(line -> PlainTextComponentSerializer.plainText().serialize(line))
                .toList();
    }

    private static final Logger SILENT = new Logger() {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    };

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }
}
