package com.uxplima.uxmessentials.survival.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.Item;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.survival.adapter.outbound.PdcSurvivalToggles;
import com.uxplima.uxmessentials.survival.application.SurvivalConfig.TreeFeller;
import com.uxplima.uxmessentials.survival.domain.SellPrices;
import com.uxplima.uxmessentials.survival.domain.SmeltMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.block.BlockMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * Proves the Phase 4 gap fix: a tree-fell routes <em>every</em> felled log through the shared auto-drops pipeline, not
 * only the origin the {@code BlockBreakEvent} carries. Registers the same two listeners the wiring registers over one
 * pipeline, {@link AutoDropsListener} (origin) and {@link TreeFellerListener} (cascade), and asserts that with
 * auto-pickup on, the whole trunk lands in the inventory and nothing spills to the ground, where before the cascade
 * logs bypassed the pipeline and dropped as items.
 */
class TreeFellerAutoDropsIntegrationTest {

    private ServerMock server;
    private WorldMock world;
    private PlayerMock player;
    private PdcSurvivalToggles toggles;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        player = server.addPlayer("Steve");
        player.teleport(new Location(world, 0.5, 65, 0.5));
        var plugin = MockBukkit.createMockPlugin();
        player.addAttachment(plugin, "uxmessentials.survival.autopickup", true);
        player.addAttachment(plugin, "uxmessentials.survival.treefeller", true); // the fell now requires its use node
        toggles = new PdcSurvivalToggles();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aFellWithAutoPickupOnRoutesEveryLogToTheInventory() {
        logColumn(5); // five stacked logs, each dropping one OAK_LOG
        AutoDropsPipeline pipeline = new AutoDropsPipeline(
                true,
                false,
                new SmeltMap(Map.of()),
                false,
                new SellPrices(Map.of()),
                Optional.empty(),
                Optional.empty(),
                toggles);
        AutoDropsListener autoDrops = new AutoDropsListener(pipeline, new SyncScheduler(), false);
        TreeFellerListener treeFeller = new TreeFellerListener(config(), toggles, new SyncScheduler(), pipeline);

        BlockBreakEvent event = new BlockBreakEvent(world.getBlockAt(0, 64, 0), player);
        // Fire in the order the wiring registers them: the harvesting listener before the auto-drops listener. The
        // tree-feller fells the trunk while the break's drops are still live, then the auto-drops listener takes over
        // the origin's drop (setDropItems(false)); reversing them would let the suppressed-drops guard skip the fell.
        treeFeller.onBreak(event); // the four logs above cascade through the same pipeline
        autoDrops.onBreak(event); // the origin log's drop is taken over and routed

        // All five logs, origin plus the four-log cascade, are in the inventory, and none reached the ground.
        assertThat(player.getInventory().contains(Material.OAK_LOG, 5)).isTrue();
        assertThat(groundItems()).isEmpty();
        // The cascade cleared the trunk above the origin (the origin itself is left to the vanilla break).
        assertThat(world.getBlockAt(0, 65, 0).getType()).isEqualTo(Material.AIR);
        assertThat(world.getBlockAt(0, 68, 0).getType()).isEqualTo(Material.AIR);
    }

    private static TreeFeller config() {
        // enabled, require-axe off (bare hand fells), no durability drain, multiplier 1, cap 64, no hunger, no sneak,
        // no replant: the harvesting knobs are exercised elsewhere; here only the drop routing is under test.
        return new TreeFeller(true, false, false, 1, 64, 0.0, false, false);
    }

    private void logColumn(int height) {
        for (int i = 0; i < height; i++) {
            BlockMock log = world.getBlockAt(0, 64 + i, 0);
            log.setType(Material.OAK_LOG);
            log.setDrops(List.of(new ItemStack(Material.OAK_LOG)));
        }
        // Tree-feller now fells only a natural tree, so place a grown (non-persistent) leaf beside the trunk.
        BlockMock leaf = world.getBlockAt(1, 64, 0);
        leaf.setType(Material.OAK_LEAVES);
        Leaves data = (Leaves) leaf.getBlockData();
        data.setPersistent(false);
        leaf.setBlockData(data);
    }

    private List<ItemStack> groundItems() {
        return world.getEntities().stream()
                .filter(Item.class::isInstance)
                .map(entity -> ((Item) entity).getItemStack())
                .toList();
    }

    /** Runs every scheduler hop inline (the tree-feller sapling replant is not exercised here). */
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
