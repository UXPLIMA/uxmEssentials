package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.bukkit.inventory.Inventory;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.LuckPermsGroupSource;
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
 * The {@code luckperms-groups} fail-safe golden test: a custom menu whose only content is a
 * {@code list { source = luckperms-groups }} opens cleanly on a server with no LuckPerms and renders an empty grid
 * no tiles, no crash, no thrown exception. Real LuckPerms is a soft-depend not on the test classpath, so the absent
 * path is the primary tested behaviour (the reflective happy path is proven only in production, as the Jobs/WorldGuard
 * gates are). The open goes through the real {@link Menus} path (async list-resolution → render); with the source
 * present-guard short-circuiting on the LuckPerms-less mock, the list resolves empty and the grid stays blank.
 */
class LuckPermsGroupSourceGoldenTest {

    private static final String GROUPS_HOCON = """
            rows = 1
            items {
              ranks {
                slots = ["0-8"],
                list {
                  source = "luckperms-groups"
                  template { material = "NAME_TAG", name = "%lp_group_display%" }
                }
              }
            }
            """;

    private ServerMock server;
    private PlayerMock viewerPlayer;
    private Menus menus;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        viewerPlayer = server.addPlayer("Alice");

        TestMenuEngine engine = TestMenuEngine.create(new KeyMessages(), new SyncScheduler());
        LuckPermsGroupSource.register(engine.bindings(), server, new NoopLogger());
        menus = engine.menus();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void absentLuckPermsRendersAnEmptyGrid() {
        menus.registerSpec("ranks", new MenuSpecLoader().parse(GROUPS_HOCON));
        menus.open(new PlayerRef(viewerPlayer.getUniqueId(), viewerPlayer.getName()), "ranks", null);

        Inventory top = viewerPlayer.getOpenInventory().getTopInventory();
        for (int slot = 0; slot < 9; slot++) {
            assertThat(top.getItem(slot))
                    .as("no LuckPerms, so the group grid is empty at slot %s", slot)
                    .isNull();
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** Discards every log line; this test asserts the rendered grid, not log output. */
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

    /** A synchronous scheduler that runs every hop inline, so the async list resolution completes at open time. */
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
    }
}
