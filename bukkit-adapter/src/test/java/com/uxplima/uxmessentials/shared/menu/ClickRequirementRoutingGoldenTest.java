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
 * The end-to-end proof for slice 2 of click requirements: per-requirement success/deny actions, optional (non-blocking)
 * requirements, and {@code stop-at-success} short-circuiting. The real {@link MenuSpecLoader} reads the map form, the
 * real {@link RequirementConditions} back the {@code has-empty-slots} gate, and the live {@link MenuListener} evaluates
 * the block, so these drive config → open → click → effect. A recording {@code record} action captures which branch
 * ran and in what order, and the viewer's real inventory drives whether each gate holds.
 */
class ClickRequirementRoutingGoldenTest {

    private static final String PER_REQ = """
            rows = 1
            items {
              a { slot = 0, material = DIAMOND, name = "x", click {
                left {
                  click = ["record:ran"]
                  requirements = [ { require = "has-empty-slots:1", success = ["record:has-room"],
                                     deny = ["record:full"] } ]
                }
              } }
            }
            """;

    private static final String OPTIONAL = """
            rows = 1
            items {
              a { slot = 0, material = DIAMOND, name = "x", click {
                left {
                  click = ["record:ran"]
                  requirements = [ "has-empty-slots:1",
                                   { require = "has-empty-slots:99", optional = true, deny = ["record:opt-failed"] } ]
                  deny = ["record:blocked"]
                }
              } }
            }
            """;

    private static final String STOP_ON = """
            rows = 1
            items {
              a { slot = 0, material = DIAMOND, name = "x", click {
                left {
                  click = ["record:ran"]
                  requirements = [ "has-empty-slots:1",
                                   { require = "has-empty-slots:1", success = ["record:second"] } ]
                  minimum = 1
                  stop-at-success = true
                }
              } }
            }
            """;

    private static final String STOP_ON_AND = """
            rows = 1
            items {
              a { slot = 0, material = DIAMOND, name = "x", click {
                left {
                  click = ["record:ran"]
                  requirements = [ "has-empty-slots:0", "has-empty-slots:1" ]
                  deny = ["record:blocked"]
                  stop-at-success = true
                }
              } }
            }
            """;

    private static final String STOP_OFF = """
            rows = 1
            items {
              a { slot = 0, material = DIAMOND, name = "x", click {
                left {
                  click = ["record:ran"]
                  requirements = [ "has-empty-slots:1",
                                   { require = "has-empty-slots:1", success = ["record:second"] } ]
                  minimum = 1
                  stop-at-success = false
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
        bindings.action("record", ctx -> notes.add(ctx.arg()));
        Currencies currencies = new Currencies(() -> null, log, "vault");
        RequirementConditions.register(bindings, currencies, new PlayerMeta(plugin), log);

        GuiText guiText = new GuiText(new KeyMessages());
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        Scheduler scheduler = new SyncScheduler();
        menus = new Menus(renderer, scheduler, bindings.lists());
        MenuSpecLoader loader = new MenuSpecLoader();
        menus.registerSpec("per-req", loader.parse(PER_REQ));
        menus.registerSpec("optional", loader.parse(OPTIONAL));
        menus.registerSpec("stop-on", loader.parse(STOP_ON));
        menus.registerSpec("stop-on-and", loader.parse(STOP_ON_AND));
        menus.registerSpec("stop-off", loader.parse(STOP_OFF));
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aPassingRequirementRunsItsSuccessActionAndTheMain() {
        open("per-req");

        leftClick(0);

        assertThat(notes)
                .as("an empty slot passes the requirement, so its success runs then the main action")
                .containsExactly("has-room", "ran");
    }

    @Test
    void aFailingRequirementRunsItsDenyActionAndNotTheMain() {
        fillInventory();
        open("per-req");

        leftClick(0);

        assertThat(notes)
                .as("a full inventory fails the mandatory requirement, so its per-req deny runs and the main does not")
                .containsExactly("full");
    }

    @Test
    void anOptionalFailureDoesNotBlockButStillRunsItsDeny() {
        open("optional");

        leftClick(0);

        assertThat(notes)
                .as("the mandatory gate passes; the optional one fails without blocking, yet its deny still runs")
                .containsExactly("opt-failed", "ran");
    }

    @Test
    void aMandatoryFailureBlocksEvenWhenAnOptionalDenyRuns() {
        fillInventory();
        open("optional");

        leftClick(0);

        assertThat(notes)
                .as("the full inventory fails the mandatory gate, so the block fails and its deny runs, not the main")
                .containsExactly("opt-failed", "blocked");
    }

    @Test
    void stopAtSuccessSkipsLaterRequirementsOnceTheMinimumIsMet() {
        open("stop-on");

        leftClick(0);

        assertThat(notes)
                .as("minimum 1 is met by the first requirement, so the loop breaks and the second never runs")
                .containsExactly("ran");
    }

    /**
     * With no minimum the block is an AND, and no running tally can settle it before the last requirement is asked,
     * so {@code stop-at-success} must not break out of the walk. Dropping the positive-minimum half of that guard
     * leaves the break threshold at the raw minimum, which a zero pass count already meets: the loop would stop
     * after the first requirement and the block would pass on it alone, with the rest of the gate never evaluated.
     */
    @Test
    void stopAtSuccessDoesNotShortCircuitABlockWithNoMinimum() {
        fillInventory();
        open("stop-on-and");

        leftClick(0);

        assertThat(notes)
                .as("the second requirement still decides an AND block, whatever the first one answered")
                .containsExactly("blocked");
    }

    @Test
    void withoutStopAtSuccessEveryRequirementIsStillEvaluated() {
        open("stop-off");

        leftClick(0);

        assertThat(notes)
                .as("without the short-circuit the second requirement is evaluated and its success runs")
                .containsExactly("second", "ran");
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
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
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
