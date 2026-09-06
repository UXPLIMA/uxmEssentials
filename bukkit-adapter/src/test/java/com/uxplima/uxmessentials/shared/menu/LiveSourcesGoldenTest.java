package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.LiveDataSources;
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
 * The ready-made live-source golden test: a custom menu whose only content is a {@code list { source = … }} over one
 * of the pre-registered roster sources renders a live tile per server object with zero feature code. Two cases go
 * through the real {@link Menus} open path (async list-resolution → global-thread snapshot → entity-thread render):
 * an {@code online-players} menu draws a {@code %online_player_skull%} head per online player named by
 * {@code %online_player_name%}, and a {@code worlds} menu draws a {@code %worlds_icon%} tile per loaded world named
 * by {@code %worlds_name%}. The snapshot hand-off resolves under the test scheduler through the latch path: it runs
 * {@code onGlobal} synchronously, so the source's {@link java.util.concurrent.CountDownLatch} completes at once.
 *
 * <p>MockBukkit models a UUID-owned player head as a {@link Material#PLAYER_HEAD} but does not carry a rendered skin,
 * so the head cases assert the material type plus the resolved display name. The skinning itself is uxmLib's
 * {@code SkullData}, proven elsewhere. The environment→icon variety (nether/end) is covered by the pure unit test;
 * MockBukkit's simple worlds are all {@code NORMAL}, so the golden case asserts the overworld's {@code GRASS_BLOCK}.
 */
class LiveSourcesGoldenTest {

    private static final String ONLINE_PLAYERS_HOCON = """
            rows = 1
            items {
              roster {
                slots = ["0-8"],
                list {
                  source = "online-players"
                  template { material = "%online_player_skull%", name = "%online_player_name%" }
                }
              }
            }
            """;

    private static final String WORLDS_HOCON = """
            rows = 1
            items {
              places {
                slots = ["0-8"],
                list {
                  source = "worlds"
                  template { material = "%worlds_icon%", name = "%worlds_name%" }
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
        server.addSimpleWorld("world");
        viewerPlayer = server.addPlayer("Alice");
        server.addPlayer("Bob");

        TestMenuEngine engine = TestMenuEngine.create(new KeyMessages(), new SyncScheduler());
        LiveDataSources.register(engine.bindings(), new SyncScheduler());
        menus = engine.menus();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void onlinePlayersSourceRendersAHeadPerPlayer() {
        menus.registerSpec("roster", new MenuSpecLoader().parse(ONLINE_PLAYERS_HOCON));
        menus.open(new PlayerRef(viewerPlayer.getUniqueId(), viewerPlayer.getName()), "roster", null);

        Inventory top = viewerPlayer.getOpenInventory().getTopInventory();
        List<String> headNames = new ArrayList<>();
        for (int slot = 0; slot < 9; slot++) {
            ItemStack item = top.getItem(slot);
            if (slot < 2) {
                assertThat(item).as("head at slot %s", slot).isNotNull();
                assertThat(item.getType()).isEqualTo(Material.PLAYER_HEAD);
                headNames.add(plainName(item));
            } else {
                assertThat(item)
                        .as("empty slot %s beyond the two players", slot)
                        .isNull();
            }
        }
        assertThat(headNames).containsExactlyInAnyOrder("Alice", "Bob");
    }

    @Test
    void worldsSourceRendersATilePerWorldWithItsEnvironmentIcon() {
        menus.registerSpec("places", new MenuSpecLoader().parse(WORLDS_HOCON));
        menus.open(new PlayerRef(viewerPlayer.getUniqueId(), viewerPlayer.getName()), "places", null);

        Inventory top = viewerPlayer.getOpenInventory().getTopInventory();
        ItemStack world = top.getItem(0);
        assertThat(world).isNotNull();
        // MockBukkit's addSimpleWorld world is NORMAL, so %worlds_icon% resolves to the overworld's grass block.
        assertThat(world.getType()).isEqualTo(Material.GRASS_BLOCK);
        assertThat(plainName(world)).isEqualTo("world");
        assertThat(top.getItem(1))
                .as("only one loaded world, so slot 1 stays empty")
                .isNull();
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

    /**
     * A synchronous scheduler that runs every hop inline, including {@code onGlobal}, so the live source's snapshot
     * latch is released the moment it is scheduled, exercising the helper's latch path (it reports itself off the
     * global thread, the {@link Scheduler} default).
     */
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
