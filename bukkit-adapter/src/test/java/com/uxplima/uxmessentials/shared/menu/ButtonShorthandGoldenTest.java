package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.MenuVocabulary;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The proof that the two operator convenience shorthands on a menu item, {@code permission = "<node>"} and
 * {@code pages = "<ranges>"}: expand into the item's {@code view} visibility gate and are honoured end to end. The
 * real {@link MenuSpecLoader}, the generic {@code perm}/{@code on-page} conditions, {@link MenuRenderer} and
 * {@link Menus} all run, so what the operator writes decides what the viewer sees: an item with a {@code permission}
 * key is hidden until the viewer holds the node, and an item with a {@code pages} key shows only on the listed pages.
 */
class ButtonShorthandGoldenTest {

    private static final String VIP_NODE = "uxmessentials.vip";

    /** Six entries over the three content slots make exactly two pages, so the {@code pages = "2"} item has a page 2. */
    private static final List<String> ENTRIES = List.of("a", "b", "c", "d", "e", "f");

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private Menus menus;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Viewer");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());

        Logger log = new NoopLogger();
        MenuBindings bindings = new MenuBindings();
        MenuVocabulary.registerConditions(bindings, new BukkitPermissions(), log);
        // A deterministic two-page list so the pages shorthand has a real page 2 to reveal its item on.
        bindings.list("shorthand-pages", ctx -> ENTRIES);

        GuiText guiText = new GuiText(new KeyMessages());
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        Scheduler scheduler = new SyncScheduler();
        menus = new Menus(renderer, scheduler, bindings.lists());
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aPermissionShorthandHidesTheItemUntilTheViewerHoldsTheNode() {
        menus.registerSpec(
                "perm-menu",
                new MenuSpecLoader()
                        .parse("rows = 1\nitems {\n  gated { slot = 0, material = DIAMOND, name = \"x\","
                                + " permission = \"" + VIP_NODE + "\" }\n}\n"));

        menus.open(viewer, "perm-menu", null);
        assertThat(topItem(0))
                .as("a viewer without the node never sees the permission-gated item")
                .isNull();

        player.addAttachment(plugin, VIP_NODE, true);
        menus.open(viewer, "perm-menu", null);
        assertThat(topItem(0))
                .as("granting the node reveals the item on the next open")
                .isNotNull()
                .extracting(ItemStack::getType)
                .isEqualTo(Material.DIAMOND);
    }

    @Test
    void aPagesShorthandHidesTheItemOffItsPageAndShowsItOnIt() {
        menus.registerSpec(
                "paged-menu",
                new MenuSpecLoader()
                        .parse("rows = 1\nitems {\n"
                                + "  roster { slots = [0, 1, 2], material = STONE,"
                                + " list { source = \"shorthand-pages\", template { material = PAPER, name = \"e\" } } }\n"
                                + "  gated { slot = 8, material = DIAMOND, name = \"x\", pages = \"2\" }\n"
                                + "}\n"));

        menus.open(viewer, "paged-menu", null, 0);
        assertThat(topItem(8))
                .as("a pages = \"2\" item is hidden on page 1 (one-based)")
                .isNull();

        menus.open(viewer, "paged-menu", null, 1);
        assertThat(topItem(8))
                .as("the same item shows on page 2, the page its range lists")
                .isNotNull()
                .extracting(ItemStack::getType)
                .isEqualTo(Material.DIAMOND);
    }

    private ItemStack topItem(int slot) {
        return player.getOpenInventory().getTopInventory().getItem(slot);
    }

    /** Reads the viewer's live Bukkit permission state, exactly as the production permissions adapter does. */
    private static final class BukkitPermissions implements Permissions {
        @Override
        public boolean has(PlayerRef who, String node) {
            Player live = Bukkit.getPlayer(who.uuid());
            return live != null && live.hasPermission(node);
        }

        @Override
        public QuotaResult resolveQuota(PlayerRef who, QuotaFamily family, WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
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
