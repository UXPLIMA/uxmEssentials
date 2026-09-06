package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Material;
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
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.RequirementConditions;
import com.uxplima.uxmessentials.shared.adapter.outbound.currency.Currencies;
import com.uxplima.uxmessentials.shared.adapter.outbound.meta.PlayerMeta;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
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
 * The proof that a menu's {@code open-requirement} gates its open and its {@code open-actions} fire when it opens, end
 * to end: a real {@link MenuSpecLoader} parses the spec, a real {@link Menus} wired with the live {@link MenuBindings}
 * registries opens it, and a recording action / the requirement pack's real conditions observe the effect. It also
 * pins the backward-compatible path. A {@code Menus} built with the old registry-less constructor opens exactly as
 * before, neither gating nor firing open-actions, so the roughly ninety existing call-sites are unaffected.
 */
class MenuOpenActionsGoldenTest {

    private static final String RECORD_HOCON = """
            rows = 1
            open-actions = ["record:opened"]
            items { a { slot = 0, material = DIAMOND, name = "x" } }
            """;

    private static final String PLAIN_HOCON = """
            rows = 1
            items { a { slot = 0, material = DIAMOND, name = "x" } }
            """;

    private static final String GATE_HOCON = """
            rows = 1
            open-requirement = ["has-empty-slots:1"]
            items { a { slot = 0, material = DIAMOND, name = "x" } }
            """;

    private static final String BAD_GATE_HOCON = """
            rows = 1
            open-requirement = ["no-such-condition"]
            items { a { slot = 0, material = DIAMOND, name = "x" } }
            """;

    private static final String AND_HOCON = """
            rows = 1
            open-requirement = ["has-empty-slots:1", "no-such-condition"]
            items { a { slot = 0, material = DIAMOND, name = "x" } }
            """;

    private static final String ARG_HOCON = """
            rows = 1
            open-actions = ["record:%argument_who%"]
            items { a { slot = 0, material = DIAMOND, name = "x" } }
            """;

    private static final String LEGACY_HOCON = """
            rows = 1
            open-requirement = ["has-empty-slots:1"]
            open-actions = ["record:opened"]
            items { a { slot = 0, material = DIAMOND, name = "x" } }
            """;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private MenuBindings bindings;
    private MenuRenderer renderer;
    private Scheduler scheduler;
    private Menus menus;
    private MenuSpecLoader loader;
    private AtomicReference<String> captured;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Viewer");
        captured = new AtomicReference<>();

        Logger log = new NoopLogger();
        bindings = new MenuBindings();
        bindings.action("record", ctx -> captured.set(ctx.arg()));
        Currencies currencies = new Currencies(() -> null, log, "vault");
        RequirementConditions.register(bindings, currencies, new PlayerMeta(plugin), log);

        GuiText guiText = new GuiText(new KeyMessages());
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        scheduler = new SyncScheduler();
        // The production constructor: the façade is handed the action + condition registries so an open runs
        // open-actions and gates on open-requirement, the same registries the click listener resolves against.
        menus = new Menus(renderer, scheduler, bindings.lists(), null, bindings.actions(), bindings.conditions());
        loader = new MenuSpecLoader();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void openActionsRunWhenTheMenuOpens() {
        menus.registerSpec("rec", loader.parse(RECORD_HOCON));

        open("rec");

        assertThat(captured.get())
                .as("the spec's open-action fires on open, carrying its split-off value")
                .isEqualTo("opened");
    }

    @Test
    void aMenuWithNoOpenActionsFiresNothing() {
        menus.registerSpec("plain", loader.parse(PLAIN_HOCON));

        open("plain");

        assertThat(menuOpen()).as("the menu still opens").isTrue();
        assertThat(captured.get())
                .as("a spec that names no open-actions runs nothing on open")
                .isNull();
    }

    @Test
    void openRequirementOpensWhenTheViewerHasAFreeSlot() {
        menus.registerSpec("gate", loader.parse(GATE_HOCON));

        open("gate");

        assertThat(menuOpen())
                .as("a fresh viewer has an empty slot, so has-empty-slots:1 passes and the window shows")
                .isTrue();
        assertThat(topItem(0)).extracting(ItemStack::getType).isEqualTo(Material.DIAMOND);
    }

    @Test
    void openRequirementBlocksTheOpenWhenTheInventoryIsFull() {
        fillInventory();
        menus.registerSpec("gate", loader.parse(GATE_HOCON));

        open("gate");

        assertThat(menuOpen())
                .as("with no empty slot has-empty-slots:1 fails, so the window never shows")
                .isFalse();
    }

    @Test
    void anUnregisteredRequirementFailsClosedAndBlocksTheOpen() {
        menus.registerSpec("bad", loader.parse(BAD_GATE_HOCON));

        open("bad");

        assertThat(menuOpen())
                .as("an unregistered open-requirement is fail-closed, so the window never shows")
                .isFalse();
    }

    @Test
    void everyRequirementMustPassForTheGateToOpen() {
        // A fresh viewer clears has-empty-slots:1, but the second requirement is unregistered and so fails closed;
        // the AND gate blocks the open because one requirement failed.
        menus.registerSpec("and", loader.parse(AND_HOCON));

        open("and");

        assertThat(menuOpen())
                .as("the requirements are an AND gate, so one failing requirement blocks the open")
                .isFalse();
    }

    @Test
    void anArgumentTokenInAnOpenActionResolvesFromTheOpenArguments() {
        menus.registerSpec("arg", loader.parse(ARG_HOCON));

        menus.open(new PlayerRef(player.getUniqueId(), player.getName()), "arg", null, 0, Map.of("who", "Steve"));

        assertThat(captured.get())
                .as("%argument_who% in the open-action expands from the arguments the menu was opened with")
                .isEqualTo("Steve");
    }

    @Test
    void theOldConstructorSkipsGatingAndOpenActions() {
        // A Menus built the old way (null registries) is what the ~89 existing call-sites use. With a full inventory
        // the open-requirement would block if it were evaluated, and the open-action would fire if it ran; neither
        // happens, proving the registry-less path is byte-identical to before this seam existed.
        fillInventory();
        Menus legacy = new Menus(renderer, scheduler, bindings.lists());
        legacy.registerSpec("legacy", loader.parse(LEGACY_HOCON));

        legacy.open(new PlayerRef(player.getUniqueId(), player.getName()), "legacy", null);

        assertThat(menuOpen())
                .as("the old constructor skips the gate, so the window opens even with a full inventory")
                .isTrue();
        assertThat(captured.get())
                .as("the old constructor runs no open-actions")
                .isNull();
    }

    private void open(String specId) {
        menus.open(new PlayerRef(player.getUniqueId(), player.getName()), specId, null);
    }

    /**
     * Whether the viewer is looking at one of this engine's windows. When the gate blocks the open the viewer never
     * left their default view, whose top inventory is null in MockBukkit, so this guards the chain and reports a
     * gated open as closed rather than throwing.
     */
    private boolean menuOpen() {
        InventoryView view = player.getOpenInventory();
        Inventory top = view == null ? null : view.getTopInventory();
        return top != null && top.getHolder() instanceof MenuHolder;
    }

    private ItemStack topItem(int slot) {
        return player.getOpenInventory().getTopInventory().getItem(slot);
    }

    private void fillInventory() {
        for (int slot = 0; slot < 36; slot++) {
            player.getInventory().setItem(slot, new ItemStack(Material.STONE));
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
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
