package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

import com.uxplima.uxmessentials.api.bukkit.menu.MenuApi;
import com.uxplima.uxmessentials.shared.adapter.inbound.api.EngineMenuApi;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers.IconProviderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers.IconProviders;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
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
 * The end-to-end proof for runtime {@link org.bukkit.inventory.ItemStack} provider registration through the public
 * {@link MenuApi}. The renderer is built over an {@link IconProviders} chain wired to a live
 * {@link IconProviderRegistry}, and the façade shares that same registry, exactly as bootstrap wires them, so a
 * provider a plugin registers <em>after</em> the renderer exists is still seen on the next render.
 *
 * <p>Three properties are proved: an unclaimed {@code test:foo} spec renders the {@link Material#STONE} fallback
 * before anything is registered; registering a provider that claims it turns the same spec into a {@link
 * Material#BEACON}, proving the live late registration reaches the already-built renderer; and a runtime provider
 * that tries to claim {@code skull:steve} cannot shadow the built-in skull provider, which still renders a player
 * head, proving the runtime tail is consulted only after the built-ins.
 */
class IconProviderRegistrationGoldenTest {

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private MenuSpecLoader loader;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Viewer");
        loader = new MenuSpecLoader();

        MenuBindings bindings = new MenuBindings();
        GuiText guiText = new GuiText(new KeyMessages());
        IconProviderRegistry runtimeIcons = new IconProviderRegistry();
        // Build the renderer over the runtime-backed chain BEFORE any registration, so a later registerIconProvider
        // proves it reaches the already-built renderer through the live registry reference.
        ItemRenderer itemRenderer = new ItemRenderer(
                guiText, bindings.placeholders(), IconProviders.defaults().withRuntime(runtimeIcons));
        MenuApi api = new EngineMenuApi(bindings, itemRenderer, runtimeIcons);
        server.getServicesManager().register(MenuApi.class, api, plugin, ServicePriority.Normal);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private MenuApi api() {
        return Objects.requireNonNull(server.getServicesManager().load(MenuApi.class), "MenuApi service");
    }

    @Test
    void anUnclaimedSpecRendersTheMaterialFallbackBeforeRegistration() {
        assertThat(build("test:foo").getType())
                .as("no built-in provider claims test:, and nothing is registered, so it falls to STONE")
                .isEqualTo(Material.STONE);
    }

    @Test
    void aRegisteredProviderClaimsItsSpecLiveOnTheAlreadyBuiltRenderer() {
        assertThat(build("test:foo").getType())
                .as("nothing claims test:foo yet")
                .isEqualTo(Material.STONE);

        api().registerIconProvider((spec, ctx) ->
                spec.equalsIgnoreCase("test:foo") ? Optional.of(new ItemStack(Material.BEACON)) : Optional.empty());

        assertThat(build("test:foo").getType())
                .as("the provider registered through the API now claims test:foo, seen by the renderer built earlier")
                .isEqualTo(Material.BEACON);
    }

    @Test
    void aRegisteredProviderCannotShadowABuiltInPrefix() {
        // A runtime provider that would claim skull:steve is registered, but the built-in skull provider is
        // consulted first, so skull:steve still renders as a player head, not the runtime BEACON.
        api().registerIconProvider((spec, ctx) ->
                spec.equalsIgnoreCase("skull:steve") ? Optional.of(new ItemStack(Material.BEACON)) : Optional.empty());

        ItemStack icon = build("skull:steve");

        assertThat(icon.getType())
                .as("the built-in skull provider wins over a runtime provider claiming the same prefix")
                .isEqualTo(Material.PLAYER_HEAD);
        assertThat(((SkullMeta) icon.getItemMeta()).hasOwner())
                .as("it is the built-in skull head, owned by the requested name")
                .isTrue();
    }

    private ItemStack build(String material) {
        MenuSpec spec = loader.parse("""
                rows = 1
                items {
                  icon { slot = 0, material = %s, name = "x" }
                }
                """.formatted(quote(material)));
        MenuItemSpec item = spec.items().get("icon");
        return api().buildItem(item.material(), item.name(), item.lore(), player);
    }

    /** HOCON-quote a value so a colon-bearing material like {@code skull:steve} is read as one string. */
    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }
}
