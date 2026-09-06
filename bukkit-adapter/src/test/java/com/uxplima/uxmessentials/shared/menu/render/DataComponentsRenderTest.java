package com.uxplima.uxmessentials.shared.menu.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
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
 * Golden renders of the native data-component {@code decor} block, rarity, tooltip-style, hide-tooltip,
 * enchant-glint, enchantable, attribute modifiers, food, and tool, driven through the real {@link ItemRenderer}
 * against MockBukkit. The components MockBukkit carries across a stack copy (rarity, hide-tooltip, enchant-glint,
 * enchantable, attribute modifiers) are asserted on a concrete {@link ItemStack}; the three its {@code ItemMeta}
 * copy constructor drops (tooltip-style, food, tool) are asserted only to render a valid item without throwing
 * they apply on real Paper, where the copy preserves them, and the renderer's fail-soft contract is what keeps the
 * test runtime from aborting either way.
 */
class DataComponentsRenderTest {

    private ItemRenderer renderer;
    private MenuContext ctx;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        renderer = new ItemRenderer(new GuiText(new KeyMessages()), new PlaceholderRegistry());
        ctx = MenuContext.of(new PlayerRef(UUID.randomUUID(), "P"), null, 0);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** Render the single item {@code x} of a one-row menu whose decor block is {@code decorBody}. */
    private ItemStack render(String material, String decorBody) {
        String hocon = "rows=1\nitems{ x{ slot=0, material=" + material + ", decor{ " + decorBody + " } } }";
        MenuSpec spec = new MenuSpecLoader().parse(hocon);
        return renderer.render(spec.items().get("x"), ctx);
    }

    private static Attribute attribute(String path) {
        return RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.ATTRIBUTE)
                .get(NamespacedKey.minecraft(path));
    }

    @Test
    void rarityApplies() {
        ItemStack it = render("DIAMOND", "rarity = EPIC");
        ItemMeta meta = it.getItemMeta();
        assertThat(meta.hasRarity()).isTrue();
        assertThat(meta.getRarity()).isEqualTo(ItemRarity.EPIC);
    }

    @Test
    void hideTooltipApplies() {
        ItemStack it = render("DIAMOND", "hide-tooltip = true");
        assertThat(it.getItemMeta().isHideTooltip()).isTrue();
    }

    @Test
    void enchantGlintOverrideApplies() {
        ItemStack on = render("DIAMOND", "enchant-glint = true");
        ItemStack off = render("DIAMOND", "enchant-glint = false");
        assertThat(on.getItemMeta().getEnchantmentGlintOverride()).isTrue();
        assertThat(off.getItemMeta().getEnchantmentGlintOverride()).isFalse();
    }

    @Test
    void enchantableApplies() {
        ItemStack it = render("DIAMOND_SWORD", "enchantable = 10");
        assertThat(it.getItemMeta().hasEnchantable()).isTrue();
        assertThat(it.getItemMeta().getEnchantable()).isEqualTo(10);
    }

    @Test
    void attributeModifierLandsOnTheItem() {
        ItemStack it = render("DIAMOND_SWORD", "attribute-modifiers = [\"generic.attack_damage:5:add_number:hand\"]");
        var modifiers = it.getItemMeta().getAttributeModifiers(attribute("attack_damage"));
        assertThat(modifiers).isNotNull();
        assertThat(modifiers)
                .anyMatch(m -> m.getAmount() == 5.0 && m.getOperation() == AttributeModifier.Operation.ADD_NUMBER);
    }

    @Test
    void multipleAttributeModifiersEachGetAUniqueKey() {
        // Two modifiers on the same attribute must not collide on key, or the second silently replaces the first.
        ItemStack it = render(
                "DIAMOND_SWORD",
                "attribute-modifiers = [\"attack_damage:5:add_number:hand\", \"attack_damage:2:add_number:any\"]");
        assertThat(it.getItemMeta().getAttributeModifiers(attribute("attack_damage")))
                .hasSize(2);
    }

    @Test
    void tooltipStyleFoodAndToolNeverAbortTheRender() {
        // These three lean on components MockBukkit-v26.2 drops on a stack copy (its ItemMeta copy constructor
        // carries rarity/hide-tooltip/glint/enchantable/attribute-modifiers but not tooltip-style/food/tool), so the
        // contract here is a valid item with no throw; they apply on real Paper, where the copy preserves them.
        assertThatCode(() -> render("DIAMOND", "tooltip-style = \"minecraft:fancy\""))
                .doesNotThrowAnyException();
        assertThatCode(() -> render("APPLE", "food { nutrition = 4, saturation = 2.4, can-always-eat = true }"))
                .doesNotThrowAnyException();
        assertThatCode(() -> render("DIAMOND_PICKAXE", "tool { default-mining-speed = 1.0, damage-per-block = 2 }"))
                .doesNotThrowAnyException();
        assertThat(render("APPLE", "food { nutrition = 4 }").getType()).isEqualTo(Material.APPLE);
    }

    @Test
    void unknownRarityIsSkippedNotFatal() {
        ItemStack it = render("DIAMOND", "rarity = NOT_A_RARITY");
        // The bad value is skipped like an unknown flag, so the item keeps its default rarity rather than EPIC and
        // still renders. (MockBukkit gives every item a non-null default rarity, so this asserts the value, not
        // hasRarity.)
        assertThat(it.getType()).isEqualTo(Material.DIAMOND);
        assertThat(it.getItemMeta().getRarity()).isNotEqualTo(ItemRarity.EPIC);
    }

    @Test
    void unknownAttributeIsSkippedNotFatal() {
        assertThatCode(() -> render("DIAMOND_SWORD", "attribute-modifiers = [\"not_an_attribute:5:add_number:hand\"]"))
                .doesNotThrowAnyException();
    }

    @Test
    void noDataComponentsRendersExactlyAsBefore() {
        ItemStack it = render("STONE", "amount = 1");
        ItemMeta meta = it.getItemMeta();
        // With DataComponents.NONE the renderer applies nothing: the new food/tool/hide-tooltip/attribute paths are
        // all gated on a declared value, so they stay absent. (rarity/glint carry a non-null MockBukkit per-item
        // default that no renderer touches, so they are not asserted here.)
        assertThat(it.getType()).isEqualTo(Material.STONE);
        assertThat(meta.isHideTooltip()).isFalse();
        assertThat(meta.hasAttributeModifiers()).isFalse();
        assertThat(meta.hasFood()).isFalse();
        assertThat(meta.hasTool()).isFalse();
    }

    @Test
    void richMetaStillAppliesAlongsideDataComponents() {
        // A spec mixing the previous feature's rich meta with data-components must apply both, not regress one.
        ItemStack it = render("DIAMOND_SWORD", "unbreakable = true, enchantments = [\"sharpness:5\"], rarity = EPIC");
        ItemMeta meta = it.getItemMeta();
        assertThat(meta.isUnbreakable()).isTrue();
        assertThat(meta.getRarity()).isEqualTo(ItemRarity.EPIC);
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }
}
