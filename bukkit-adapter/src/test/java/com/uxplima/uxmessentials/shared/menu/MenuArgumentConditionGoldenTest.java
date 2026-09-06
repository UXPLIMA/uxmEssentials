package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.MenuVocabulary;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.RequirementConditions;
import com.uxplima.uxmessentials.shared.adapter.outbound.currency.Currencies;
import com.uxplima.uxmessentials.shared.adapter.outbound.meta.PlayerMeta;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The end-to-end proof that {@code %argument_<name>%} resolves in condition evaluation, the open-requirement gate, an
 * item's {@code view} block, and a click requirement all read the values a menu was opened with, so a typed
 * open-command's arguments can gate the whole menu. A real {@link MenuSpecLoader} parses the spec, a real {@link Menus}
 * wired with the live registries opens it carrying arguments, and the real {@link MenuRenderer}/{@link MenuListener}
 * evaluate the conditions against those arguments. It covers both an {@code expr} gate (which expands its own
 * placeholders after the argument is substituted) and a plain {@code has-empty-slots} gate (which reads the substituted
 * value directly), and pins that a menu opened with no arguments behaves exactly as before.
 */
class MenuArgumentConditionGoldenTest {

    private static final String OPEN_EXPR_HOCON = """
            rows = 1
            open-requirement = ["expr:%argument_amount% > 0"]
            items { a { slot = 0, material = DIAMOND, name = "x" } }
            """;

    private static final String OPEN_HAS_HOCON = """
            rows = 1
            open-requirement = ["has-empty-slots:%argument_need%"]
            items { a { slot = 0, material = DIAMOND, name = "x" } }
            """;

    private static final String VIEW_HOCON = """
            rows = 1
            items { a { slot = 0, material = DIAMOND, name = "x", view = ["expr:%argument_show% == 1"] } }
            """;

    private static final String CLICK_HOCON = """
            rows = 1
            items {
              a { slot = 0, material = DIAMOND, name = "x", click {
                left { click = ["record-note:ran"], requirements = ["expr:%argument_ok% == 1"],
                       deny = ["record-note:denied"] }
              } }
            }
            """;

    private static final String PLAIN_VIEW_HOCON = """
            rows = 1
            items { a { slot = 0, material = DIAMOND, name = "x", view = ["has-empty-slots:1"] } }
            """;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private MenuBindings bindings;
    private MenuRenderer renderer;
    private Scheduler scheduler;
    private Menus menus;
    private MenuSpecLoader loader;
    private List<String> notes;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Viewer");
        notes = new ArrayList<>();

        Logger log = new NoopLogger();
        bindings = new MenuBindings();
        bindings.action("record-note", ctx -> notes.add(ctx.arg()));
        Currencies currencies = new Currencies(() -> null, log, "vault");
        RequirementConditions.register(bindings, currencies, new PlayerMeta(plugin), log);
        MenuVocabulary.registerConditions(bindings, new FakePermissions(), log);

        GuiText guiText = new GuiText(new KeyMessages());
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        scheduler = new SyncScheduler();
        menus = new Menus(renderer, scheduler, bindings.lists(), null, bindings.actions(), bindings.conditions());
        loader = new MenuSpecLoader();
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void openRequirementOpensWhenTheArgumentSatisfiesTheExpression() {
        menus.registerSpec("open", loader.parse(OPEN_EXPR_HOCON));

        openWith("open", Map.of("amount", "5"));

        assertThat(menuOpen())
                .as("amount=5 makes expr:%argument_amount% > 0 true, so the window shows")
                .isTrue();
    }

    @Test
    void openRequirementBlocksWhenTheArgumentFailsTheExpression() {
        menus.registerSpec("open", loader.parse(OPEN_EXPR_HOCON));

        openWith("open", Map.of("amount", "0"));

        assertThat(menuOpen())
                .as("amount=0 makes expr:%argument_amount% > 0 false, so the window never shows")
                .isFalse();
    }

    @Test
    void openRequirementFailsClosedWhenTheArgumentIsMissing() {
        menus.registerSpec("open", loader.parse(OPEN_EXPR_HOCON));

        // Opened without the amount argument at all: %argument_amount% stays unresolved, the expression cannot be
        // evaluated, and the gate fails closed rather than opening on an empty operand.
        menus.open(new PlayerRef(player.getUniqueId(), player.getName()), "open", null);

        assertThat(menuOpen())
                .as("a missing argument leaves the expression unevaluable, so the gate blocks the open")
                .isFalse();
    }

    @Test
    void openRequirementReadsTheArgumentValueForANonExpressionCondition() {
        menus.registerSpec("open", loader.parse(OPEN_HAS_HOCON));

        openWith("open", Map.of("need", "1"));

        assertThat(menuOpen())
                .as("has-empty-slots:%argument_need% reads the substituted number 1, which a fresh viewer satisfies")
                .isTrue();
    }

    @Test
    void viewRendersWhenTheArgumentSatisfiesTheCondition() {
        menus.registerSpec("view", loader.parse(VIEW_HOCON));

        openWith("view", Map.of("show", "1"));

        assertThat(topItem(0))
                .as("show=1 makes the item's view condition pass, so it renders")
                .isNotNull()
                .extracting(ItemStack::getType)
                .isEqualTo(Material.DIAMOND);
    }

    @Test
    void viewHidesWhenTheArgumentFailsTheCondition() {
        menus.registerSpec("view", loader.parse(VIEW_HOCON));

        openWith("view", Map.of("show", "0"));

        assertThat(topItem(0))
                .as("show=0 fails the item's view condition, so the slot stays empty")
                .isNull();
    }

    @Test
    void clickRunsTheActionWhenTheArgumentSatisfiesTheRequirement() {
        menus.registerSpec("click", loader.parse(CLICK_HOCON));

        openWith("click", Map.of("ok", "1"));
        leftClick(0);

        assertThat(notes)
                .as("ok=1 satisfies the click requirement, so the main action runs alone")
                .containsExactly("ran");
    }

    @Test
    void clickRunsTheDenyWhenTheArgumentFailsTheRequirement() {
        menus.registerSpec("click", loader.parse(CLICK_HOCON));

        openWith("click", Map.of("ok", "0"));
        leftClick(0);

        assertThat(notes)
                .as("ok=0 fails the click requirement, so the deny action runs and the main does not")
                .containsExactly("denied");
    }

    @Test
    void anArgumentlessOpenBehavesExactlyAsBefore() {
        // No arguments carried: the identity fast-path in ActionArguments.resolve returns the same args map, so a
        // condition with no %argument_ token evaluates exactly as it did before this slice existed.
        menus.registerSpec("plain", loader.parse(PLAIN_VIEW_HOCON));

        menus.open(new PlayerRef(player.getUniqueId(), player.getName()), "plain", null);

        assertThat(menuOpen()).as("the menu opens with no arguments").isTrue();
        assertThat(topItem(0))
                .as("a fresh viewer passes has-empty-slots:1, so the item renders unchanged")
                .isNotNull()
                .extracting(ItemStack::getType)
                .isEqualTo(Material.DIAMOND);
    }

    private void openWith(String specId, Map<String, String> arguments) {
        menus.open(new PlayerRef(player.getUniqueId(), player.getName()), specId, null, 0, arguments);
    }

    private void leftClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /**
     * Whether the viewer is looking at one of this engine's windows. A blocked open leaves the viewer in their default
     * view, whose top inventory is null in MockBukkit, so this guards the chain and reports a gated open as closed.
     */
    private boolean menuOpen() {
        InventoryView view = player.getOpenInventory();
        Inventory top = view == null ? null : view.getTopInventory();
        return top != null && top.getHolder() instanceof MenuHolder;
    }

    private ItemStack topItem(int slot) {
        return player.getOpenInventory().getTopInventory().getItem(slot);
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class FakePermissions implements Permissions {
        @Override
        public boolean has(PlayerRef who, String node) {
            return false;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }

    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
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
