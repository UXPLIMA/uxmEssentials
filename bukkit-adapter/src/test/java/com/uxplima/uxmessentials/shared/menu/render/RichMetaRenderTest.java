package com.uxplima.uxmessentials.shared.menu.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Map;
import java.util.UUID;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;

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
 * Golden renders of the native rich-meta {@code decor} block: a HOCON spec is parsed and rendered through the real
 * {@link ItemRenderer} against MockBukkit so each meta type is asserted on a concrete {@link ItemStack}. Types
 * MockBukkit-v1.21 cannot fully model (armour trim, item-model, base potion type, and registry-backed banner
 * patterns) are asserted only to render a valid item without throwing. They still apply on real Paper, where the
 * registries and meta exist; the renderer's fail-soft contract is what keeps the test runtime from aborting.
 */
class RichMetaRenderTest {

    private ItemRenderer renderer;
    private MenuContext ctx;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        PlaceholderRegistry placeholders = new PlaceholderRegistry();
        placeholders.register("count", c -> "5");
        placeholders.register("md", c -> "6");
        renderer = new ItemRenderer(new GuiText(new KeyMessages()), placeholders);
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

    private static Enchantment enchant(String path) {
        return RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.ENCHANTMENT)
                .get(NamespacedKey.minecraft(path));
    }

    @Test
    void appliesEnchantsAtTheirLevels() {
        ItemStack it = render("DIAMOND_SWORD", "enchantments = [\"sharpness:5\", \"unbreaking:3\"]");
        assertThat(it.getItemMeta().getEnchantLevel(enchant("sharpness"))).isEqualTo(5);
        assertThat(it.getItemMeta().getEnchantLevel(enchant("unbreaking"))).isEqualTo(3);
    }

    @Test
    void storedEnchantsLandOnTheBook() {
        ItemStack it = render("ENCHANTED_BOOK", "stored-enchantments = [\"mending:1\"]");
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) it.getItemMeta();
        assertThat(meta.getStoredEnchantLevel(enchant("mending"))).isEqualTo(1);
    }

    @Test
    void unbreakableMarksTheItem() {
        ItemStack it = render("DIAMOND_SWORD", "unbreakable = true");
        assertThat(it.getItemMeta().isUnbreakable()).isTrue();
    }

    @Test
    void leatherColorParsesHexRgbAndNamedForms() {
        ItemStack hex = render("LEATHER_CHESTPLATE", "leather-color = \"#A1FF33\"");
        ItemStack rgb = render("LEATHER_CHESTPLATE", "leather-color = \"161,255,51\"");
        ItemStack named = render("LEATHER_CHESTPLATE", "leather-color = RED");

        assertThat(((LeatherArmorMeta) hex.getItemMeta()).getColor()).isEqualTo(Color.fromRGB(0xA1FF33));
        assertThat(((LeatherArmorMeta) rgb.getItemMeta()).getColor()).isEqualTo(Color.fromRGB(161, 255, 51));
        assertThat(((LeatherArmorMeta) named.getItemMeta()).getColor()).isEqualTo(org.bukkit.DyeColor.RED.getColor());
    }

    @Test
    void potionEffectAndColourApply() {
        ItemStack it = render("POTION", "potion { type = STRENGTH, color = \"#00AAFF\", effects = [\"speed:1:600\"] }");
        PotionMeta meta = (PotionMeta) it.getItemMeta();
        assertThat(meta.getColor()).isEqualTo(Color.fromRGB(0x00AAFF));
        assertThat(meta.hasCustomEffect(org.bukkit.potion.PotionEffectType.SPEED))
                .isTrue();
    }

    @Test
    void damageApplies() {
        ItemStack it = render("DIAMOND_SWORD", "damage = 100");
        assertThat(((Damageable) it.getItemMeta()).getDamage()).isEqualTo(100);
    }

    @Test
    void dynamicAmountResolvesThroughThePlaceholder() {
        ItemStack it = render("DIAMOND", "amount = \"%count%\"");
        assertThat(it.getAmount()).isEqualTo(5);
    }

    @Test
    @SuppressWarnings(
            "deprecation") // the int custom-model-data accessor is the value ItemBuilder.customModelData(int) sets
    void dynamicModelDataResolvesThroughThePlaceholder() {
        ItemStack it = render("DIAMOND", "model-data = \"%md%\"");
        assertThat(it.getItemMeta().hasCustomModelData()).isTrue();
        assertThat(it.getItemMeta().getCustomModelData()).isEqualTo(6);
    }

    @Test
    void bannerTrimAndItemModelNeverAbortTheRender() {
        // These four lean on registries/meta MockBukkit-v1.21 may not model; the contract is a valid item, no throw.
        assertThatCode(() -> render("WHITE_BANNER", "banner { patterns = [\"stripe_top:red\"] }"))
                .doesNotThrowAnyException();
        assertThatCode(() -> render("DIAMOND_CHESTPLATE", "trim { material = diamond, pattern = sentry }"))
                .doesNotThrowAnyException();
        assertThatCode(() -> render("DIAMOND_SWORD", "item-model = \"minecraft:stick\""))
                .doesNotThrowAnyException();
        assertThat(render("DIAMOND_SWORD", "item-model = \"minecraft:stick\"").getType())
                .isEqualTo(Material.DIAMOND_SWORD);
    }

    @Test
    void unknownEnchantIsSkippedNotFatal() {
        ItemStack it = render("STONE", "enchantments = [\"not_a_real_enchant:5\", \"sharpness:2\"]");
        // The bad token is skipped like an unknown flag; the valid one still applies and the render succeeds.
        assertThat(it.getType()).isEqualTo(Material.STONE);
        assertThat(it.getItemMeta().getEnchantLevel(enchant("sharpness"))).isEqualTo(2);
    }

    @Test
    void noRichMetaRendersExactlyAsBefore() {
        ItemStack it = render("STONE", "amount = 1");
        assertThat(it.getType()).isEqualTo(Material.STONE);
        assertThat(it.getAmount()).isEqualTo(1);
        assertThat(it.getItemMeta().isUnbreakable()).isFalse();
        assertThat(it.getItemMeta().hasEnchants()).isFalse();
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }
}
