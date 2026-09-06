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
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
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
 * The end-to-end proof that a click gesture's {@code else} chain walks like an if / else-if / else ladder. The real
 * {@link MenuSpecLoader} reads the nested {@code else} grammar, the real {@link RequirementConditions} back the
 * {@code has-empty-slots} gates, and the live {@link MenuListener} evaluates the chain, so these drive config → open →
 * click → effect, not any part in isolation. The viewer's real inventory drives which gate holds, and a recording
 * {@code record-note} action captures exactly which branch ran (and in what order), so the walk order is observable.
 */
class ClickElseChainGoldenTest {

    // if (empty slot) A; else if (inventory full) B; else C.
    private static final String IF_ELSEIF_ELSE = """
            rows = 1
            items {
              a { slot = 0, material = DIAMOND, name = "x", click {
                left {
                  click = ["record-note:A"]
                  requirements = ["has-empty-slots:1"]
                  else {
                    requirements = ["!has-empty-slots:1"]
                    click = ["record-note:B"]
                    else { click = ["record-note:C"] }
                  }
                }
                right = ["record-note:bare"]
              } }
            }
            """;

    // Both gated branches ask for impossibly many empty slots, so both fail and the terminal else always wins.
    private static final String ALL_GATES_FAIL = """
            rows = 1
            items {
              a { slot = 0, material = DIAMOND, name = "x", click {
                left {
                  click = ["record-note:A"]
                  requirements = ["has-empty-slots:99"]
                  else {
                    requirements = ["has-empty-slots:98"]
                    click = ["record-note:B"]
                    else { click = ["record-note:C"] }
                  }
                }
              } }
            }
            """;

    // A failing block that has BOTH a deny and an else: the else must win, the block deny must not run.
    private static final String ELSE_BEATS_DENY = """
            rows = 1
            items {
              a { slot = 0, material = DIAMOND, name = "x", click {
                left {
                  click = ["record-note:main"]
                  requirements = ["has-empty-slots:99"]
                  deny = ["record-note:blockDeny"]
                  else { click = ["record-note:elseRan"] }
                }
              } }
            }
            """;

    // A failing block with a deny and NO else: the block deny runs, exactly as before else-chains existed.
    private static final String DENY_WITHOUT_ELSE = """
            rows = 1
            items {
              a { slot = 0, material = DIAMOND, name = "x", click {
                left {
                  click = ["record-note:main"]
                  requirements = ["has-empty-slots:99"]
                  deny = ["record-note:blockDeny"]
                }
              } }
            }
            """;

    // The first else's own requirement carries a per-requirement deny; it must fire as the branch is evaluated, then
    // the chain continues to the terminal else.
    private static final String BRANCH_PER_REQ_DENY = """
            rows = 1
            items {
              a { slot = 0, material = DIAMOND, name = "x", click {
                left {
                  click = ["record-note:main"]
                  requirements = ["has-empty-slots:99"]
                  else {
                    requirements = [ { require = "has-empty-slots:99", deny = ["record-note:branchReqDeny"] } ]
                    click = ["record-note:elseMain"]
                    else { click = ["record-note:terminal"] }
                  }
                }
              } }
            }
            """;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private Menus menus;
    private List<String> notes;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Viewer");
        notes = new ArrayList<>();

        Logger log = new NoopLogger();
        MenuBindings bindings = new MenuBindings();
        bindings.action("record-note", ctx -> notes.add(ctx.arg()));
        Currencies currencies = new Currencies(() -> null, log, "vault");
        RequirementConditions.register(bindings, currencies, new PlayerMeta(plugin), log);

        GuiText guiText = new GuiText(new KeyMessages());
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        Scheduler scheduler = new SyncScheduler();
        menus = new Menus(renderer, scheduler, bindings.lists());
        MenuSpecLoader loader = new MenuSpecLoader();
        menus.registerSpec("if-elseif-else", loader.parse(IF_ELSEIF_ELSE));
        menus.registerSpec("all-fail", loader.parse(ALL_GATES_FAIL));
        menus.registerSpec("else-beats-deny", loader.parse(ELSE_BEATS_DENY));
        menus.registerSpec("deny-without-else", loader.parse(DENY_WITHOUT_ELSE));
        menus.registerSpec("branch-req-deny", loader.parse(BRANCH_PER_REQ_DENY));
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theFirstBranchWinsWhenTheMainGatePasses() {
        open("if-elseif-else");

        leftClick(0);

        assertThat(notes)
                .as("an empty slot satisfies the main gate, so the first branch runs and the chain stops")
                .containsExactly("A");
    }

    @Test
    void theSecondBranchWinsWhenOnlyItsGateHolds() {
        fillInventory();
        open("if-elseif-else");

        leftClick(0);

        assertThat(notes)
                .as("a full inventory fails the main gate but satisfies the else's !has-empty-slots gate")
                .containsExactly("B");
    }

    @Test
    void theTerminalElseWinsWhenEveryGatedBranchFails() {
        open("all-fail");

        leftClick(0);

        assertThat(notes)
                .as("both gated branches want impossibly many empty slots, so the unconditional terminal else runs")
                .containsExactly("C");
    }

    @Test
    void anElseIsPreferredOverTheBlockDenyOnFailure() {
        open("else-beats-deny");

        leftClick(0);

        assertThat(notes)
                .as("a failing block with an else runs the else, not its own block deny")
                .containsExactly("elseRan");
    }

    @Test
    void aFailingBlockWithoutAnElseStillRunsItsDeny() {
        open("deny-without-else");

        leftClick(0);

        assertThat(notes)
                .as("with no else, the failing block falls back to its deny exactly as before")
                .containsExactly("blockDeny");
    }

    @Test
    void aBranchesOwnPerRequirementDenyFiresAsTheChainWalks() {
        open("branch-req-deny");

        leftClick(0);

        assertThat(notes)
                .as("the else's per-requirement deny fires as it is evaluated, then the walk continues to the terminal")
                .containsExactly("branchReqDeny", "terminal");
    }

    @Test
    void aBareGestureWithNoRequirementOrElseStillRunsItsActions() {
        open("if-elseif-else");

        rightClick(0);

        assertThat(notes)
                .as("a plain action list is unaffected by the else machinery")
                .containsExactly("bare");
    }

    private void fillInventory() {
        for (int slot = 0; slot < 36; slot++) {
            player.getInventory().setItem(slot, new ItemStack(Material.STONE));
        }
    }

    private void open(String specId) {
        menus.open(new PlayerRef(player.getUniqueId(), player.getName()), specId, null);
    }

    private void leftClick(int slot) {
        click(slot, ClickType.LEFT);
    }

    private void rightClick(int slot) {
        click(slot, ClickType.RIGHT);
    }

    private void click(int slot, ClickType type) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event =
                new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
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
