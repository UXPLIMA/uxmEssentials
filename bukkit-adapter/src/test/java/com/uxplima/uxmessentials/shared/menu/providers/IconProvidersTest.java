package com.uxplima.uxmessentials.shared.menu.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers.IconProviders;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.outbound.hooks.HeadQuery;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Renders single menu items through the real {@link ItemRenderer} under MockBukkit to prove the skull and
 * equipment icon providers claim their specs and that plain material specs still render unchanged. The default
 * (two-arg) renderer is used for the skull/equipment cases. Proving the preserved constructor transparently
 * gains those providers, and a HeadDatabase-enabled renderer with the absent hook proves {@code hdb:<id>}
 * degrades gracefully.
 */
class IconProvidersTest {

    private static final String TEXTURE_BASE64 = Base64.getEncoder()
            .encodeToString("{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/uxm\"}}}"
                    .getBytes(StandardCharsets.UTF_8));

    private ServerMock server;
    private PlayerMock player;
    private PlayerRef ref;
    private MenuContext ctx;
    private ItemRenderer renderer;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer();
        ref = new PlayerRef(player.getUniqueId(), player.getName());
        GuiText guiText = new GuiText(new KeyMessages());
        PlaceholderRegistry placeholders = new PlaceholderRegistry();
        placeholders.register("head", c -> "skull:Notch");
        // The plain two-arg constructor must keep working and silently gain the default skull + equipment chain.
        renderer = new ItemRenderer(guiText, placeholders);
        ctx = MenuContext.of(ref, null, 0);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void skullByNameRendersPlayerHeadOwnedByThatName() {
        ItemStack icon = render(renderer, "skull:Notch", "", 1);
        assertThat(icon.getType()).isEqualTo(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) icon.getItemMeta();
        assertThat(meta.hasOwner()).isTrue();
        assertThat(meta.getOwningPlayer()).isNotNull();
        assertThat(meta.getOwningPlayer().getName()).isEqualTo("Notch");
    }

    @Test
    void headPrefixIsAnAliasForSkull() {
        ItemStack icon = render(renderer, "head:Notch", "", 1);
        assertThat(icon.getType()).isEqualTo(Material.PLAYER_HEAD);
        assertThat(((SkullMeta) icon.getItemMeta()).hasOwner()).isTrue();
    }

    @Test
    void baseheadRendersPlayerHeadCarryingTheTexture() {
        ItemStack icon = render(renderer, "basehead:" + TEXTURE_BASE64, "", 1);
        assertThat(icon.getType()).isEqualTo(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) icon.getItemMeta();
        assertThat(meta.getPlayerProfile()).isNotNull();
        assertThat(meta.getPlayerProfile().getProperties())
                .anyMatch(property -> property.getName().equals("textures"));
    }

    @Test
    void skullSelfRendersTheViewersOwnHead() {
        ItemStack icon = render(renderer, "skull:self", "", 1);
        assertThat(icon.getType()).isEqualTo(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) icon.getItemMeta();
        assertThat(meta.getOwningPlayer()).isNotNull();
        // MockBukkit reconstructs the owning player's UUID from the stored name (offline hashing), so the
        // viewer's real UUID is not preserved; the owner's name is, and matching it proves it is the viewer.
        assertThat(meta.getOwningPlayer().getName()).isEqualTo(player.getName());
    }

    @Test
    void mainHandRendersTheViewersEquippedItem() {
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
        assertThat(render(renderer, "main_hand", "", 1).getType()).isEqualTo(Material.DIAMOND_SWORD);
        assertThat(render(renderer, "mainhand", "", 1).getType()).isEqualTo(Material.DIAMOND_SWORD);
    }

    @Test
    void emptyMainHandFallsBackToTheMaterialDefault() {
        // No item in hand: the equipment provider yields empty, so the spec falls through to the STONE fallback.
        assertThat(render(renderer, "main_hand", "", 1).getType()).isEqualTo(Material.STONE);
    }

    @Test
    void helmetRendersTheWornHelmetAndFallsBackWhenBareheaded() {
        assertThat(render(renderer, "helmet", "", 1).getType()).isEqualTo(Material.STONE);
        player.getInventory().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
        assertThat(render(renderer, "helmet", "", 1).getType()).isEqualTo(Material.DIAMOND_HELMET);
    }

    @Test
    void hdbWithAbsentHeadDatabaseFallsBackWithoutCrashing() {
        ItemRenderer hdbRenderer = new ItemRenderer(
                new GuiText(new KeyMessages()), placeholders(), IconProviders.withHeadDatabase(HeadQuery.ABSENT));
        assertThatCode(() -> {
                    ItemStack icon = render(hdbRenderer, "hdb:7", "", 1);
                    assertThat(icon.getType()).isEqualTo(Material.STONE);
                })
                .doesNotThrowAnyException();
    }

    @Test
    void plainMaterialSpecsStillRenderUnchanged() {
        assertThat(render(renderer, "DIAMOND", "", 1).getType()).isEqualTo(Material.DIAMOND);
        assertThat(render(renderer, "NOT_A_REAL_MATERIAL", "", 1).getType()).isEqualTo(Material.STONE);
    }

    @Test
    void barefacedPlayerHeadMaterialIsNotIntercepted() {
        ItemStack icon = render(renderer, "PLAYER_HEAD", "", 1);
        assertThat(icon.getType()).isEqualTo(Material.PLAYER_HEAD);
        // A material lookup, not the skull provider: a bare PLAYER_HEAD carries no owner.
        assertThat(((SkullMeta) icon.getItemMeta()).hasOwner()).isFalse();
    }

    @Test
    void materialPlaceholderResolvesToASkullSource() {
        // %head% resolves to skull:Notch, so a placeholder material reaches the skull provider exactly as before.
        ItemStack icon = render(renderer, "%head%", "", 1);
        assertThat(icon.getType()).isEqualTo(Material.PLAYER_HEAD);
        assertThat(((SkullMeta) icon.getItemMeta()).hasOwner()).isTrue();
    }

    @Test
    void nameAndAmountLayerOntoASkullBase() {
        ItemStack icon = render(renderer, "skull:Notch", "<red>Owner", 3);
        assertThat(icon.getType()).isEqualTo(Material.PLAYER_HEAD);
        assertThat(icon.getAmount()).isEqualTo(3);
        assertThat(((SkullMeta) icon.getItemMeta()).hasOwner()).isTrue();
        assertThat(PlainTextComponentSerializer.plainText()
                        .serialize(icon.getItemMeta().displayName()))
                .isEqualTo("Owner");
    }

    private ItemStack render(ItemRenderer itemRenderer, String material, String name, int amount) {
        MenuItemSpec item = item(material, name, amount);
        return itemRenderer.render(item, ctx);
    }

    private MenuItemSpec item(String material, String name, int amount) {
        String hocon = """
                rows = 1
                items {
                  x {
                    slot = 0,
                    material = %s,
                    name = %s,
                    decor { amount = %d }
                  }
                }
                """.formatted(quote(material), quote(name), amount);
        return new MenuSpecLoader().parse(hocon).items().get("x");
    }

    /** HOCON-quote a value so a colon-bearing material like {@code skull:Notch} is read as one string. */
    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private PlaceholderRegistry placeholders() {
        PlaceholderRegistry placeholders = new PlaceholderRegistry();
        placeholders.register("head", c -> "skull:Notch");
        return placeholders;
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }
}
