package com.uxplima.uxmessentials.survival.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.survival.adapter.inbound.listener.TreeFellerListener;
import com.uxplima.uxmessentials.survival.adapter.inbound.listener.VeinminerListener;
import com.uxplima.uxmessentials.survival.adapter.outbound.PdcSurvivalToggles;
import com.uxplima.uxmessentials.survival.application.SurvivalConfig.TreeFeller;
import com.uxplima.uxmessentials.survival.application.SurvivalConfig.Veinminer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * MockBukkit coverage of the survival harvesting listeners over a real world: breaking one log fells the connected
 * trunk (capped at {@code max-blocks}, respecting the axe/sneak gates and the per-player toggle), and breaking one
 * configured ore veins the connected run. The origin block is left to the vanilla break. The listener takes only the
 * connected extras, so the assertions read the blocks above the origin.
 */
class SurvivalAdapterTest {

    private ServerMock server;
    private WorldMock world;
    private PlayerMock player;
    private PdcSurvivalToggles toggles;
    private CapturingScheduler scheduler;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        player = server.addPlayer("Steve");
        player.teleport(new Location(world, 0.5, 65, 0.5));
        // Both mechanics now require their use permission on top of the per-player toggle; grant them so the
        // toggle/sneak/axe behaviour under test still fires. The permission-denied path has its own cases below.
        player.addAttachment(plugin, "uxmessentials.survival.treefeller", true);
        player.addAttachment(plugin, "uxmessentials.survival.veinminer", true);
        toggles = new PdcSurvivalToggles();
        scheduler = new CapturingScheduler();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // --- tree-feller ---------------------------------------------------------------------------------------------

    @Test
    void treeFellerFellsTheConnectedTrunkCappedAtMaxBlocksAndDrainsTheAxe() {
        giveAxe();
        logColumn(0, 64, 8); // eight stacked logs on dirt
        TreeFellerListener listener = new TreeFellerListener(treeFeller(cfg -> cfg.maxBlocks(5)), toggles, scheduler);

        listener.onBreak(breakOf(blockAt(0, 64, 0)));

        // max-blocks 5 counts the origin, so exactly the four logs above it come down; the rest of the trunk stands.
        assertThat(typeAt(0, 65, 0)).isEqualTo(Material.AIR);
        assertThat(typeAt(0, 68, 0)).isEqualTo(Material.AIR);
        assertThat(typeAt(0, 69, 0)).isEqualTo(Material.OAK_LOG);
        // Four extra logs felled at multiplier 1 = four durability points on the axe.
        assertThat(axeDamage()).isEqualTo(4);
    }

    @Test
    void treeFellerBreaksASingleLogWhenTheToggleIsOff() {
        giveAxe();
        logColumn(0, 64, 6);
        toggles.toggleTreeFeller(player, true); // flip the default-on toggle to off
        TreeFellerListener listener = new TreeFellerListener(treeFeller(cfg -> cfg), toggles, scheduler);

        listener.onBreak(breakOf(blockAt(0, 64, 0)));

        // Toggled off: nothing cascades: the log above the origin is untouched.
        assertThat(typeAt(0, 65, 0)).isEqualTo(Material.OAK_LOG);
    }

    @Test
    void treeFellerDoesNothingWithoutAnAxeWhenRequireAxeIsOn() {
        player.getInventory().setItemInMainHand(new ItemStack(Material.STONE)); // not an axe
        logColumn(0, 64, 6);
        TreeFellerListener listener =
                new TreeFellerListener(treeFeller(cfg -> cfg.requireAxe(true)), toggles, scheduler);

        listener.onBreak(breakOf(blockAt(0, 64, 0)));

        assertThat(typeAt(0, 65, 0)).isEqualTo(Material.OAK_LOG);
    }

    @Test
    void treeFellerRequiresSneakingWhenSneakRequiredIsOn() {
        giveAxe();
        logColumn(0, 64, 6);
        TreeFellerListener listener =
                new TreeFellerListener(treeFeller(cfg -> cfg.sneakRequired(true)), toggles, scheduler);

        // Not sneaking: no fell.
        player.setSneaking(false);
        listener.onBreak(breakOf(blockAt(0, 64, 0)));
        assertThat(typeAt(0, 65, 0)).isEqualTo(Material.OAK_LOG);

        // Sneaking: the trunk comes down.
        player.setSneaking(true);
        listener.onBreak(breakOf(blockAt(0, 64, 0)));
        assertThat(typeAt(0, 65, 0)).isEqualTo(Material.AIR);
    }

    @Test
    void treeFellerDoesNothingWithoutTheUsePermissionEvenWithTheToggleOn() {
        PlayerMock denied = server.addPlayer("NoPerm"); // never granted uxmessentials.survival.treefeller
        denied.teleport(new Location(world, 0.5, 65, 0.5));
        denied.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_AXE));
        logColumn(0, 64, 6); // toggle defaults on, so only the missing permission can stop the fell
        TreeFellerListener listener = new TreeFellerListener(treeFeller(cfg -> cfg), toggles, scheduler);

        listener.onBreak(new BlockBreakEvent(blockAt(0, 64, 0), denied));

        assertThat(typeAt(0, 65, 0)).isEqualTo(Material.OAK_LOG);
    }

    @Test
    void treeFellerReplantsASaplingAtTheBaseOnceTheOriginIsCleared() {
        giveAxe();
        logColumn(0, 64, 3);
        TreeFellerListener listener =
                new TreeFellerListener(treeFeller(cfg -> cfg.replantSaplings(true)), toggles, scheduler);

        listener.onBreak(breakOf(blockAt(0, 64, 0)));
        // The vanilla break clears the origin log a tick later; then the scheduled replant runs on its region.
        blockAt(0, 64, 0).setType(Material.AIR);
        scheduler.runCaptured();

        assertThat(typeAt(0, 64, 0)).isEqualTo(Material.OAK_SAPLING);
    }

    @Test
    void treeFellerDoesNotFellAPlacedLogStructureWithNoNaturalLeaf() {
        giveAxe();
        bareLogColumn(0, 64, 6); // a player-built log pillar: no leaves anywhere, so not a natural tree
        TreeFellerListener listener = new TreeFellerListener(treeFeller(cfg -> cfg), toggles, scheduler);

        listener.onBreak(breakOf(blockAt(0, 64, 0)));

        // No natural leaf beside the trunk: the fell is refused and the logs above the origin still stand.
        assertThat(typeAt(0, 65, 0)).isEqualTo(Material.OAK_LOG);
        assertThat(typeAt(0, 68, 0)).isEqualTo(Material.OAK_LOG);
    }

    @Test
    void treeFellerFellsATrunkWithANaturalLeafBesideIt() {
        giveAxe();
        bareLogColumn(0, 64, 6);
        naturalLeaf(1, 69, false); // a grown, non-persistent leaf beside the crown makes it a natural tree

        TreeFellerListener listener = new TreeFellerListener(treeFeller(cfg -> cfg), toggles, scheduler);
        listener.onBreak(breakOf(blockAt(0, 64, 0)));

        assertThat(typeAt(0, 65, 0)).isEqualTo(Material.AIR);
        assertThat(typeAt(0, 68, 0)).isEqualTo(Material.AIR);
    }

    @Test
    void treeFellerDoesNotFellATrunkWrappedOnlyInPlayerPlacedPersistentLeaves() {
        giveAxe();
        bareLogColumn(0, 64, 6);
        naturalLeaf(1, 69, true); // persistent (player-placed) leaves are not a natural tree

        TreeFellerListener listener = new TreeFellerListener(treeFeller(cfg -> cfg), toggles, scheduler);
        listener.onBreak(breakOf(blockAt(0, 64, 0)));

        assertThat(typeAt(0, 65, 0)).isEqualTo(Material.OAK_LOG);
    }

    @Test
    void treeFellerDoesNothingInCreativeMode() {
        giveAxe();
        logColumn(0, 64, 6); // a natural tree (logColumn adds a leaf), so only the creative gate can stop the fell
        player.setGameMode(GameMode.CREATIVE);
        TreeFellerListener listener = new TreeFellerListener(treeFeller(cfg -> cfg), toggles, scheduler);

        listener.onBreak(breakOf(blockAt(0, 64, 0)));

        assertThat(typeAt(0, 65, 0)).isEqualTo(Material.OAK_LOG);
    }

    // --- veinminer -----------------------------------------------------------------------------------------------

    @Test
    void veinminerBreaksTheConnectedVeinCappedAtMaxBlocks() {
        oreColumn(Material.COAL_ORE, 0, 64, 8);
        player.setSneaking(true);
        VeinminerListener listener =
                new VeinminerListener(veinminer(cfg -> cfg.maxBlocks(5)), Set.of(Material.COAL_ORE), toggles);

        listener.onBreak(breakOf(blockAt(0, 64, 0)));

        assertThat(typeAt(0, 65, 0)).isEqualTo(Material.AIR);
        assertThat(typeAt(0, 68, 0)).isEqualTo(Material.AIR);
        assertThat(typeAt(0, 69, 0)).isEqualTo(Material.COAL_ORE);
    }

    @Test
    void veinminerBreaksASingleBlockWhenTheToggleIsOff() {
        oreColumn(Material.IRON_ORE, 0, 64, 6);
        player.setSneaking(true);
        toggles.toggleVeinminer(player, true); // flip the default-on toggle to off
        VeinminerListener listener = new VeinminerListener(veinminer(cfg -> cfg), Set.of(Material.IRON_ORE), toggles);

        listener.onBreak(breakOf(blockAt(0, 64, 0)));

        assertThat(typeAt(0, 65, 0)).isEqualTo(Material.IRON_ORE);
    }

    @Test
    void veinminerRequiresSneakingWhenSneakRequiredIsOn() {
        oreColumn(Material.COAL_ORE, 0, 64, 6);
        VeinminerListener listener =
                new VeinminerListener(veinminer(cfg -> cfg.sneakRequired(true)), Set.of(Material.COAL_ORE), toggles);

        player.setSneaking(false);
        listener.onBreak(breakOf(blockAt(0, 64, 0)));
        assertThat(typeAt(0, 65, 0)).isEqualTo(Material.COAL_ORE);

        player.setSneaking(true);
        listener.onBreak(breakOf(blockAt(0, 64, 0)));
        assertThat(typeAt(0, 65, 0)).isEqualTo(Material.AIR);
    }

    @Test
    void veinminerDoesNothingWithoutTheUsePermissionEvenWithTheToggleOn() {
        PlayerMock denied = server.addPlayer("NoPerm"); // never granted uxmessentials.survival.veinminer
        denied.teleport(new Location(world, 0.5, 65, 0.5));
        oreColumn(Material.COAL_ORE, 0, 64, 6);
        denied.setSneaking(true); // toggle defaults on and no sneak gate here, so only the permission can stop the vein
        VeinminerListener listener = new VeinminerListener(veinminer(cfg -> cfg), Set.of(Material.COAL_ORE), toggles);

        listener.onBreak(new BlockBreakEvent(blockAt(0, 64, 0), denied));

        assertThat(typeAt(0, 65, 0)).isEqualTo(Material.COAL_ORE);
    }

    @Test
    void veinminerDoesNothingInCreativeMode() {
        oreColumn(Material.COAL_ORE, 0, 64, 6);
        player.setSneaking(true);
        player.setGameMode(GameMode.CREATIVE);
        VeinminerListener listener = new VeinminerListener(veinminer(cfg -> cfg), Set.of(Material.COAL_ORE), toggles);

        listener.onBreak(breakOf(blockAt(0, 64, 0)));

        assertThat(typeAt(0, 65, 0)).isEqualTo(Material.COAL_ORE);
    }

    // --- helpers -------------------------------------------------------------------------------------------------

    /** Build a tree-feller config with sensible test defaults, letting a case override individual knobs. */
    private static TreeFeller treeFeller(java.util.function.UnaryOperator<TreeFellerBuilder> tune) {
        TreeFellerBuilder b = tune.apply(new TreeFellerBuilder());
        return new TreeFeller(
                true,
                b.requireAxe,
                b.durabilityDrain,
                b.durabilityMultiplier,
                b.maxBlocks,
                0.0,
                b.sneakRequired,
                b.replantSaplings);
    }

    private static Veinminer veinminer(java.util.function.UnaryOperator<VeinminerBuilder> tune) {
        VeinminerBuilder b = tune.apply(new VeinminerBuilder());
        return new Veinminer(true, List.of("COAL_ORE", "IRON_ORE"), b.maxBlocks, false, 0.0, b.sneakRequired);
    }

    private void giveAxe() {
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_AXE));
    }

    private int axeDamage() {
        ItemStack axe = player.getInventory().getItemInMainHand();
        return axe.getItemMeta() instanceof Damageable meta ? meta.getDamage() : 0;
    }

    private void logColumn(int x, int baseY, int height) {
        bareLogColumn(x, baseY, height);
        // A grown tree carries leaves; tree-feller now fells only a trunk with a natural leaf beside it, so a plain
        // "log column" fixture must include one to still read as a fellable tree.
        naturalLeaf(x + 1, baseY, false);
    }

    /** A bare trunk of {@code height} logs on dirt, with no leaves: a player-built pillar rather than a grown tree. */
    private void bareLogColumn(int x, int baseY, int height) {
        blockAt(x, baseY - 1, 0).setType(Material.DIRT); // soil under the trunk for the replant test
        for (int i = 0; i < height; i++) {
            blockAt(x, baseY + i, 0).setType(Material.OAK_LOG);
        }
    }

    /** Place an oak-leaves block at {@code (x, y, 0)} with the given player-placed (persistent) flag. */
    private Block naturalLeaf(int x, int y, boolean persistent) {
        Block leaf = blockAt(x, y, 0);
        leaf.setType(Material.OAK_LEAVES);
        Leaves data = (Leaves) leaf.getBlockData();
        data.setPersistent(persistent);
        leaf.setBlockData(data);
        return leaf;
    }

    private void oreColumn(Material ore, int x, int baseY, int height) {
        for (int i = 0; i < height; i++) {
            blockAt(x, baseY + i, 0).setType(ore);
        }
    }

    private Block blockAt(int x, int y, int z) {
        return world.getBlockAt(x, y, z);
    }

    private Material typeAt(int x, int y, int z) {
        return blockAt(x, y, z).getType();
    }

    private BlockBreakEvent breakOf(Block block) {
        return new BlockBreakEvent(block, player);
    }

    /** A mutable builder of tree-feller test knobs: the config record itself is final and positional. */
    private static final class TreeFellerBuilder {
        private boolean requireAxe = false;
        private boolean durabilityDrain = true;
        private int durabilityMultiplier = 1;
        private int maxBlocks = 64;
        private boolean sneakRequired = false;
        private boolean replantSaplings = false;

        TreeFellerBuilder requireAxe(boolean value) {
            this.requireAxe = value;
            return this;
        }

        TreeFellerBuilder maxBlocks(int value) {
            this.maxBlocks = value;
            return this;
        }

        TreeFellerBuilder sneakRequired(boolean value) {
            this.sneakRequired = value;
            return this;
        }

        TreeFellerBuilder replantSaplings(boolean value) {
            this.replantSaplings = value;
            return this;
        }
    }

    private static final class VeinminerBuilder {
        private int maxBlocks = 64;
        private boolean sneakRequired = false;

        VeinminerBuilder maxBlocks(int value) {
            this.maxBlocks = value;
            return this;
        }

        VeinminerBuilder sneakRequired(boolean value) {
            this.sneakRequired = value;
            return this;
        }
    }

    /** Runs entity/global/async work inline and captures region-scheduled tasks (the tree-feller replant). */
    private static final class CapturingScheduler implements Scheduler {
        private final List<Runnable> captured = new ArrayList<>();

        void runCaptured() {
            List<Runnable> tasks = List.copyOf(captured);
            captured.clear();
            tasks.forEach(Runnable::run);
        }

        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            captured.add(task);
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
