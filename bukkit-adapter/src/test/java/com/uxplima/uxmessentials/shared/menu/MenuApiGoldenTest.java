package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

import com.uxplima.uxmessentials.api.bukkit.menu.MenuApi;
import com.uxplima.uxmessentials.shared.adapter.inbound.api.EngineMenuApi;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers.IconProviderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers.IconProviders;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The end-to-end proof for the published {@link MenuApi} registration façade. A real {@link MenuBindings} + {@link
 * ItemRenderer} are wrapped in an {@link EngineMenuApi} and registered in the {@link ServerMock}'s services manager,
 * exactly as bootstrap does; every case loads the façade back through {@code getServicesManager().load(MenuApi.class)}
 * and drives it, so this exercises the same seam another plugin would use.
 *
 * <p>Each register* method is proved to reach the live engine: a custom action fires on a real click, a custom
 * placeholder expands in a rendered name, a custom requirement hides an item, and a custom list source pages entries
 * all through the real {@link Menus} open path. {@link MenuApi#buildItem} renders a spec to a stack, and a duplicate
 * registration throws, holding the register-once contract.
 */
class MenuApiGoldenTest {

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private Menus menus;
    private MenuBindings bindings;
    private MenuSpecLoader loader;
    private List<String> notes;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Viewer");
        notes = new ArrayList<>();

        bindings = new MenuBindings();
        GuiText guiText = new GuiText(new KeyMessages());
        IconProviderRegistry runtimeIcons = new IconProviderRegistry();
        ItemRenderer itemRenderer = new ItemRenderer(
                guiText, bindings.placeholders(), IconProviders.defaults().withRuntime(runtimeIcons));
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        Scheduler scheduler = new SyncScheduler();
        loader = new MenuSpecLoader();
        menus = new Menus(renderer, scheduler, bindings.lists());
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);

        // Register the façade exactly as bootstrap does, so every test loads it back the way a consumer would.
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
    void theFacadeIsResolvableFromTheServicesManager() {
        assertThat(api())
                .as("a consumer loads the façade from the ServicesManager")
                .isNotNull();
    }

    @Test
    void aCustomActionRegisteredThroughTheApiFiresOnClick() {
        // Register BEFORE the spec loads/validates so [my-award] is a known ref, then open and click the button.
        api().registerAction("my-award", ctx -> notes.add("awarded"));
        MenuSpec spec = loader.parse("""
                rows = 1
                items {
                  button { slot = 0, material = DIAMOND, name = "x", click {
                    left { click = ["my-award"] }
                  } }
                }
                """);
        assertThat(bindings.validate(List.of(spec)))
                .as("a spec naming a registered custom action passes load-time validation")
                .isEmpty();
        menus.registerSpec("menu", spec);

        open("menu");
        leftClick(0);

        assertThat(notes)
                .as("the custom action registered via the API ran on the click")
                .containsExactly("awarded");
    }

    @Test
    void aCustomPlaceholderRegisteredThroughTheApiExpandsInAName() {
        // A placeholder id must be a %\w+% token, so it uses an underscore where action/condition ids may hyphenate.
        api().registerPlaceholder("my_token", ctx -> "HELLO");
        menus.registerSpec("menu", loader.parse("""
                        rows = 1
                        items {
                          label { slot = 0, material = PAPER, name = "%my_token%" }
                        }
                        """));

        open("menu");

        assertThat(displayName(0))
                .as("the custom placeholder resolved into the rendered name")
                .isEqualTo("HELLO");
    }

    @Test
    void aCustomRequirementRegisteredThroughTheApiHidesTheItem() {
        api().registerRequirement("always-false", (ctx, args) -> false);
        menus.registerSpec("menu", loader.parse("""
                        rows = 1
                        items {
                          gated { slot = 0, material = DIAMOND, name = "x", view = ["always-false"] }
                        }
                        """));

        open("menu");

        assertThat(itemAt(0))
                .as("a failing custom view requirement hides the item")
                .isNull();
    }

    @Test
    void aCustomListSourceRegisteredThroughTheApiPagesItsEntries() {
        api().registerListSource("my-src", ctx -> List.of("a", "b"));
        menus.registerSpec("menu", loader.parse("""
                        rows = 1
                        items {
                          cells {
                            slots = ["0-8"],
                            list {
                              source = "my-src"
                              template { material = "PAPER", name = "entry" }
                            }
                          }
                        }
                        """));

        open("menu");

        assertThat(filledSlots())
                .as("the custom list source rendered one tile per entry")
                .isEqualTo(2);
    }

    @Test
    void buildItemRendersASpecToAMenuStyledStack() {
        MenuSpec spec = loader.parse("""
                rows = 1
                items {
                  icon { slot = 0, material = EMERALD, name = "Shiny" }
                }
                """);
        MenuItemSpec item = spec.items().get("icon");

        ItemStack built = api().buildItem(item.material(), item.name(), item.lore(), player);

        assertThat(built.getType()).as("buildItem honours the spec's material").isEqualTo(Material.EMERALD);
        assertThat(plainName(built)).as("buildItem resolves the spec's name").isEqualTo("Shiny");
    }

    @Test
    void aDuplicateRegistrationThrows() {
        api().registerAction("my-award", ctx -> notes.add("first"));
        assertThatThrownBy(() -> api().registerAction("my-award", ctx -> notes.add("second")))
                .as("a second registration under the same id fails loudly, holding register-once")
                .isInstanceOf(IllegalStateException.class);
    }

    private void open(String specId) {
        menus.open(new PlayerRef(player.getUniqueId(), player.getName()), specId, null);
    }

    private void leftClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    private ItemStack itemAt(int slot) {
        return player.getOpenInventory().getTopInventory().getItem(slot);
    }

    private int filledSlots() {
        int count = 0;
        for (int slot = 0; slot < 9; slot++) {
            if (itemAt(slot) != null) {
                count++;
            }
        }
        return count;
    }

    private String displayName(int slot) {
        return plainName(itemAt(slot));
    }

    private static String plainName(ItemStack item) {
        // The title reads off the tile wherever the canon puts it: the display name of a bare button, or the
        // first lore line of a titled tile, whose display name is deliberately blank.
        return TileText.title(item);
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class SyncScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }

        @Override
        public AutoCloseable repeatGlobal(Runnable task, Duration initialDelay, Duration period) {
            return () -> {};
        }
    }
}
