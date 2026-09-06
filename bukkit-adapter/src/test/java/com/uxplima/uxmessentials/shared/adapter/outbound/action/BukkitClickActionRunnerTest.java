package com.uxplima.uxmessentials.shared.adapter.outbound.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.ClickActionEconomy;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.action.ClickAction;
import com.uxplima.uxmessentials.shared.domain.action.ClickActionType;
import com.uxplima.uxmessentials.shared.domain.action.ClickTrigger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Covers {@link BukkitClickActionRunner} as a sequenced executor with gates and a delay continuation. The first
 * suite pins the original behaviour (trigger filtering, ordered command dispatch, fail-soft); the rest pin the
 * richer action types: a {@code DELAY} schedules the tail and aborts on a viewer who logged off; the
 * {@code CHANCE}/{@code PERMISSION}/{@code CONDITION}/{@code COST} gates abort the remaining chain on a failed
 * verdict (a malformed gate spec is skipped, never aborting); and {@code GIVE} delivers an item with overflow
 * dropping.
 */
class BukkitClickActionRunnerTest {

    /** The cost-denied catalog key the owning context supplies; pinned here as the npc context's key. */
    private static final MessageKey COST_DENIED = () -> "npc.action.cost-denied";

    private ServerMock server;
    private PlayerMock player;
    private RecordingRunner commandRunner;
    private RecordingConnector connector;
    private CapturingScheduler scheduler;
    private FakePermissions permissions;
    private RecordingEconomy economy;
    private RecordingMessages messages;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        player = server.addPlayer("Steve");
        commandRunner = new RecordingRunner();
        connector = new RecordingConnector();
        scheduler = new CapturingScheduler();
        permissions = new FakePermissions();
        economy = new RecordingEconomy();
        messages = new RecordingMessages();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** Deserialize the {@code b64:} token shape this test builds, mirroring the npc context's equipment codec. */
    private static Optional<ItemStack> resolveSerialized(String token) {
        try {
            byte[] bytes = Base64.getDecoder().decode(token.substring("b64:".length()));
            return Optional.of(ItemStack.deserializeBytes(bytes));
        } catch (RuntimeException corrupt) {
            return Optional.empty();
        }
    }

    private BukkitClickActionRunner runnerWith(Optional<ClickActionEconomy> eco) {
        return new BukkitClickActionRunner(
                commandRunner,
                connector,
                scheduler,
                permissions,
                eco,
                messages,
                COST_DENIED,
                BukkitClickActionRunnerTest::resolveSerialized,
                new NoopLogger());
    }

    private BukkitClickActionRunner runner() {
        return runnerWith(Optional.of(economy));
    }

    // --- original behaviour ---------------------------------------------------------------------------------

    @Test
    void runsOnlyTheActionsWhoseTriggerMatchesARightClick() {
        runner().run(
                        player,
                        List.of(
                                new ClickAction(ClickTrigger.RIGHT_CLICK, ClickActionType.RUN_PLAYER, "right"),
                                new ClickAction(ClickTrigger.LEFT_CLICK, ClickActionType.RUN_PLAYER, "left"),
                                new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "any")),
                        false);

        assertThat(commandRunner.playerCommands).containsExactly("right", "any");
    }

    @Test
    void runsAPlayerOpActionThroughTheOpRunner() {
        runner().run(
                        player,
                        List.of(new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER_AS_OP, "fly")),
                        false);

        assertThat(commandRunner.opCommands).containsExactly("fly");
        assertThat(commandRunner.playerCommands).isEmpty();
    }

    @Test
    void runsOnlyTheActionsWhoseTriggerMatchesAnAttack() {
        runner().run(
                        player,
                        List.of(
                                new ClickAction(ClickTrigger.RIGHT_CLICK, ClickActionType.RUN_PLAYER, "right"),
                                new ClickAction(ClickTrigger.LEFT_CLICK, ClickActionType.RUN_PLAYER, "left"),
                                new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "any")),
                        true);

        assertThat(commandRunner.playerCommands).containsExactly("left", "any");
    }

    @Test
    void dispatchesConsoleAndPlayerCommandsSubstitutingThePlayerToken() {
        runner().run(
                        player,
                        List.of(
                                new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_CONSOLE, "give {player} diamond"),
                                new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "msg {player} hi")),
                        false);

        assertThat(commandRunner.consoleCommands).containsExactly("give Steve diamond");
        assertThat(commandRunner.playerCommands).containsExactly("msg Steve hi");
    }

    @Test
    void sendsAConnectRequestForAConnectAction() {
        runner().run(player, List.of(new ClickAction(ClickTrigger.ANY, ClickActionType.CONNECT, "lobby")), false);

        assertThat(connector.servers).containsExactly("lobby");
    }

    @Test
    void oneMalformedEffectMidChainIsSkippedAndTheRestStillRun() {
        runner().run(
                        player,
                        List.of(
                                new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "first"),
                                new ClickAction(ClickTrigger.ANY, ClickActionType.SOUND, "not.a.real.sound.key.at.all"),
                                new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "third")),
                        false);

        assertThat(commandRunner.playerCommands).containsExactly("first", "third");
    }

    // --- DELAY ----------------------------------------------------------------------------------------------

    @Test
    void delaySchedulesTheTailAndRunningItContinuesTheChain() {
        runner().run(
                        player,
                        List.of(
                                new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "before"),
                                new ClickAction(ClickTrigger.ANY, ClickActionType.DELAY, "40"),
                                new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "after")),
                        false);

        // The pre-delay action ran inline; the tail is parked on the scheduler, not yet run.
        assertThat(commandRunner.playerCommands).containsExactly("before");
        assertThat(scheduler.pendingDelays()).isEqualTo(1);

        scheduler.runAllDelayed();
        assertThat(commandRunner.playerCommands).containsExactly("before", "after");
    }

    @Test
    void multipleDelaysEachResumeTheChainAtTheRightIndex() {
        runner().run(
                        player,
                        List.of(
                                new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "one"),
                                new ClickAction(ClickTrigger.ANY, ClickActionType.DELAY, "20"),
                                new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "two"),
                                new ClickAction(ClickTrigger.ANY, ClickActionType.DELAY, "20"),
                                new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "three")),
                        false);

        // First leg runs up to the first delay, then parks.
        assertThat(commandRunner.playerCommands).containsExactly("one");
        assertThat(scheduler.pendingDelays()).isEqualTo(1);

        // Resuming the first delay runs "two" and parks again at the second delay: no skipped or repeated action.
        scheduler.runDelayedOnce();
        assertThat(commandRunner.playerCommands).containsExactly("one", "two");
        assertThat(scheduler.pendingDelays()).isEqualTo(1);

        // Resuming the second delay runs the tail exactly once.
        scheduler.runDelayedOnce();
        assertThat(commandRunner.playerCommands).containsExactly("one", "two", "three");
        assertThat(scheduler.pendingDelays()).isEqualTo(0);
    }

    @Test
    void delayAbortsWhenTheViewerLoggedOffDuringTheWait() {
        runner().run(
                        player,
                        List.of(
                                new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "before"),
                                new ClickAction(ClickTrigger.ANY, ClickActionType.DELAY, "40"),
                                new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "after")),
                        false);

        scheduler.disconnect(player); // the viewer logs off while the tail is parked
        scheduler.runAllDelayed();

        assertThat(commandRunner.playerCommands).containsExactly("before");
    }

    // --- CHANCE ---------------------------------------------------------------------------------------------

    @Test
    void chanceOfOneHundredAlwaysContinues() {
        runner().run(
                        player,
                        List.of(
                                new ClickAction(ClickTrigger.ANY, ClickActionType.CHANCE, "100"),
                                new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "reward")),
                        false);

        assertThat(commandRunner.playerCommands).containsExactly("reward");
    }

    @Test
    void chanceOfZeroAlwaysAbortsTheRest() {
        runner().run(
                        player,
                        List.of(
                                new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "always"),
                                new ClickAction(ClickTrigger.ANY, ClickActionType.CHANCE, "0"),
                                new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "reward")),
                        false);

        assertThat(commandRunner.playerCommands).containsExactly("always");
    }

    // --- PERMISSION -----------------------------------------------------------------------------------------

    @Test
    void permissionGateContinuesWhenHeld() {
        permissions.grant(player, "npc.vip");
        runner().run(
                        player,
                        List.of(
                                new ClickAction(ClickTrigger.ANY, ClickActionType.PERMISSION, "npc.vip"),
                                new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "vip")),
                        false);

        assertThat(commandRunner.playerCommands).containsExactly("vip");
    }

    @Test
    void permissionGateAbortsWhenLacked() {
        runner().run(
                        player,
                        List.of(
                                new ClickAction(ClickTrigger.ANY, ClickActionType.PERMISSION, "npc.vip"),
                                new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "vip")),
                        false);

        assertThat(commandRunner.playerCommands).isEmpty();
    }

    // --- CONDITION ------------------------------------------------------------------------------------------

    @Test
    void conditionTrueContinues() {
        runner().run(
                        player,
                        List.of(
                                new ClickAction(ClickTrigger.ANY, ClickActionType.CONDITION, "5 > 3"),
                                new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "ok")),
                        false);

        assertThat(commandRunner.playerCommands).containsExactly("ok");
    }

    @Test
    void conditionFalseAborts() {
        runner().run(
                        player,
                        List.of(
                                new ClickAction(ClickTrigger.ANY, ClickActionType.CONDITION, "1 > 3"),
                                new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "no")),
                        false);

        assertThat(commandRunner.playerCommands).isEmpty();
    }

    @Test
    void conditionDoesStringEqualityWhenNotNumeric() {
        runner().run(
                        player,
                        List.of(
                                new ClickAction(ClickTrigger.ANY, ClickActionType.CONDITION, "alpha == alpha"),
                                new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "same"),
                                new ClickAction(ClickTrigger.ANY, ClickActionType.CONDITION, "alpha == beta"),
                                new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "unreached")),
                        false);

        assertThat(commandRunner.playerCommands).containsExactly("same");
    }

    @Test
    void greaterOrEqualIsNotMisparsedAsAStrayGreaterThan() {
        // "5 >= 5" must read the whole ">=" operator (5 >= 5 is true); a ">"-then-"=" misparse would compare
        // "5" against "= 5" and read false, aborting the chain.
        runner().run(
                        player,
                        List.of(
                                new ClickAction(ClickTrigger.ANY, ClickActionType.CONDITION, "5 >= 5"),
                                new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "boundary-ok"),
                                new ClickAction(ClickTrigger.ANY, ClickActionType.CONDITION, "5 <= 4"),
                                new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "unreached")),
                        false);

        assertThat(commandRunner.playerCommands).containsExactly("boundary-ok");
    }

    @Test
    void malformedConditionSkipsTheGateAndContinues() {
        runner().run(
                        player,
                        List.of(
                                new ClickAction(
                                        ClickTrigger.ANY, ClickActionType.CONDITION, "garbage with no operator"),
                                new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "still-runs")),
                        false);

        assertThat(commandRunner.playerCommands).containsExactly("still-runs");
    }

    // --- COST -----------------------------------------------------------------------------------------------

    @Test
    void costDebitsOnceAndContinuesWhenAffordable() {
        economy.affordable = true;
        runner().run(
                        player,
                        List.of(
                                new ClickAction(ClickTrigger.ANY, ClickActionType.COST, "50"),
                                new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "bought")),
                        false);

        assertThat(economy.withdrawals).containsExactly(new BigDecimal("50"));
        assertThat(commandRunner.playerCommands).containsExactly("bought");
    }

    @Test
    void costAbortsAndMessagesWhenUnaffordable() {
        economy.affordable = false;
        runner().run(
                        player,
                        List.of(
                                new ClickAction(ClickTrigger.ANY, ClickActionType.COST, "50"),
                                new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "bought")),
                        false);

        assertThat(economy.withdrawals).containsExactly(new BigDecimal("50")); // charged exactly once (the failed try)
        assertThat(commandRunner.playerCommands).isEmpty();
        assertThat(messages.sentKeys).contains("npc.action.cost-denied");
    }

    @Test
    void aGateThatThrowsStopsTheChainWithoutEscapingToTheCaller() {
        economy.explode = true;
        runner().run(
                        player,
                        List.of(
                                new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "before"),
                                new ClickAction(ClickTrigger.ANY, ClickActionType.COST, "50"),
                                new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "after")),
                        false);

        // The throwing gate fails closed: actions before it ran, the rest are stopped, and no throwable reached us.
        assertThat(commandRunner.playerCommands).containsExactly("before");
    }

    @Test
    void costGateIsSkippedWhenNoEconomyProviderIsPresent() {
        runnerWith(Optional.empty())
                .run(
                        player,
                        List.of(
                                new ClickAction(ClickTrigger.ANY, ClickActionType.COST, "50"),
                                new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "free")),
                        false);

        assertThat(commandRunner.playerCommands).containsExactly("free");
    }

    // --- GIVE -----------------------------------------------------------------------------------------------

    @Test
    void giveAddsTheItemToTheViewerInventory() {
        runner().run(player, List.of(new ClickAction(ClickTrigger.ANY, ClickActionType.GIVE, "DIAMOND:3")), false);

        assertThat(player.getInventory().contains(Material.DIAMOND, 3)).isTrue();
    }

    @Test
    void giveWithUnknownMaterialSkipsAndStillRunsTheRest() {
        runner().run(
                        player,
                        List.of(
                                new ClickAction(ClickTrigger.ANY, ClickActionType.GIVE, "NOT_A_REAL_MATERIAL"),
                                new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "next")),
                        false);

        assertThat(commandRunner.playerCommands).containsExactly("next");
    }

    @Test
    void giveOverflowDropsAtTheViewerLocation() {
        // Fill every inventory slot so nothing fits, forcing an overflow drop.
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            player.getInventory().setItem(slot, new ItemStack(Material.STONE, 64));
        }

        runner().run(player, List.of(new ClickAction(ClickTrigger.ANY, ClickActionType.GIVE, "DIAMOND:5")), false);

        long droppedDiamonds = player.getWorld().getEntities().stream()
                .filter(e -> e instanceof org.bukkit.entity.Item)
                .map(e -> ((org.bukkit.entity.Item) e).getItemStack())
                .filter(stack -> stack.getType() == Material.DIAMOND)
                .mapToInt(ItemStack::getAmount)
                .sum();
        assertThat(droppedDiamonds).isEqualTo(5);
    }

    @Test
    void giveDeliversAFullSerializedItemWithItsNbtIntact() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        var meta = sword.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text("Excalibur"));
        meta.addEnchant(org.bukkit.enchantments.Enchantment.SHARPNESS, 5, true);
        sword.setItemMeta(meta);
        String token = "b64:" + Base64.getEncoder().encodeToString(sword.serializeAsBytes());

        runner().run(player, List.of(new ClickAction(ClickTrigger.ANY, ClickActionType.GIVE, token)), false);

        ItemStack given = player.getInventory().getItem(0);
        assertThat(given).isNotNull();
        assertThat(given.getType()).isEqualTo(Material.DIAMOND_SWORD);
        assertThat(given.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.SHARPNESS))
                .isEqualTo(5);
        assertThat(given.getItemMeta().displayName()).isEqualTo(net.kyori.adventure.text.Component.text("Excalibur"));
    }

    @Test
    void giveWithACorruptSerializedTokenSkipsAndStillRunsTheRest() {
        runner().run(
                        player,
                        List.of(
                                new ClickAction(ClickTrigger.ANY, ClickActionType.GIVE, "b64:not-valid-base64-@@@"),
                                new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "next")),
                        false);

        assertThat(commandRunner.playerCommands).containsExactly("next");
    }

    // --- RANDOM ---------------------------------------------------------------------------------------------

    @Test
    void randomGroupOfOneAlwaysRunsThatSingleAction() {
        runner().run(
                        player,
                        List.of(
                                new ClickAction(ClickTrigger.ANY, ClickActionType.RANDOM, "1"),
                                new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "only")),
                        false);

        assertThat(commandRunner.playerCommands).containsExactly("only");
    }

    @Test
    void randomRunsExactlyOneOfTheGroupAndSkipsTheRestThenContinues() {
        for (int trial = 0; trial < 60; trial++) {
            commandRunner.playerCommands.clear();
            runner().run(
                            player,
                            List.of(
                                    new ClickAction(ClickTrigger.ANY, ClickActionType.RANDOM, "3"),
                                    new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "a"),
                                    new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "b"),
                                    new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "c"),
                                    new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "after")),
                            false);

            // Exactly one of the three group members ran, then the chain continued past the whole group.
            assertThat(commandRunner.playerCommands).hasSize(2);
            assertThat(commandRunner.playerCommands.get(1)).isEqualTo("after");
            assertThat(commandRunner.playerCommands.get(0)).isIn("a", "b", "c");
        }
    }

    @Test
    void randomWithAFixedPickerRunsTheChosenIndex() {
        // Inject a deterministic picker: always the second member (offset 1) of the group.
        BukkitClickActionRunner deterministic = new BukkitClickActionRunner(
                commandRunner,
                connector,
                scheduler,
                permissions,
                Optional.of(economy),
                messages,
                COST_DENIED,
                BukkitClickActionRunnerTest::resolveSerialized,
                new NoopLogger(),
                bound -> 1);
        deterministic.run(
                player,
                List.of(
                        new ClickAction(ClickTrigger.ANY, ClickActionType.RANDOM, "3"),
                        new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "a"),
                        new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "b"),
                        new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "c"),
                        new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "after")),
                false);

        assertThat(commandRunner.playerCommands).containsExactly("b", "after");
    }

    @Test
    void randomCountPastTheEndClampsToTheRemainingActions() {
        // RANDOM 5 with only two members left: the group is those two, exactly one runs, nothing else follows.
        for (int trial = 0; trial < 40; trial++) {
            commandRunner.playerCommands.clear();
            runner().run(
                            player,
                            List.of(
                                    new ClickAction(ClickTrigger.ANY, ClickActionType.RANDOM, "5"),
                                    new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "x"),
                                    new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "y")),
                            false);

            assertThat(commandRunner.playerCommands).hasSize(1);
            assertThat(commandRunner.playerCommands.get(0)).isIn("x", "y");
        }
    }

    @Test
    void randomCountOfZeroSkipsTheMarkerAndRunsTheFollowingActionsNormally() {
        runner().run(
                        player,
                        List.of(
                                new ClickAction(ClickTrigger.ANY, ClickActionType.RANDOM, "0"),
                                new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "a"),
                                new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "b")),
                        false);

        assertThat(commandRunner.playerCommands).containsExactly("a", "b");
    }

    @Test
    void aGateChosenInsideARandomGroupThatDeniesStopsTheRestOfTheChain() {
        // The group's chosen member is a CHANCE 0 gate (offset 0): a denied gate must stop the whole chain, exactly
        // as it would inline: the action after the group must not run.
        BukkitClickActionRunner deterministic = new BukkitClickActionRunner(
                commandRunner,
                connector,
                scheduler,
                permissions,
                Optional.of(economy),
                messages,
                COST_DENIED,
                BukkitClickActionRunnerTest::resolveSerialized,
                new NoopLogger(),
                bound -> 0);
        deterministic.run(
                player,
                List.of(
                        new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "before"),
                        new ClickAction(ClickTrigger.ANY, ClickActionType.RANDOM, "2"),
                        new ClickAction(ClickTrigger.ANY, ClickActionType.CHANCE, "0"),
                        new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "in-group"),
                        new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "after")),
                false);

        // The chosen gate denied: "before" ran, the gate stopped the chain, and "after" (past the group) did not run.
        assertThat(commandRunner.playerCommands).containsExactly("before");
    }

    @Test
    void randomTriggerFiltersTheMarkerItself() {
        // The RANDOM marker carries a trigger; on a non-matching click it is filtered out before the group runs,
        // so the would-be group members run as ordinary actions (no random pick happens).
        runner().run(
                        player,
                        List.of(
                                new ClickAction(ClickTrigger.LEFT_CLICK, ClickActionType.RANDOM, "2"),
                                new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "a"),
                                new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "b")),
                        false);

        assertThat(commandRunner.playerCommands).containsExactly("a", "b");
    }

    // --- ordering -------------------------------------------------------------------------------------------

    @Test
    void aGateStopsTheActionsAfterItButNotThoseBefore() {
        runner().run(
                        player,
                        List.of(
                                new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "first"),
                                new ClickAction(ClickTrigger.ANY, ClickActionType.CHANCE, "0"),
                                new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "second")),
                        false);

        assertThat(commandRunner.playerCommands).containsExactly("first");
    }

    // --- TitleSpec (TITLE value parsing) --------------------------------------------------------------------

    @Test
    void titleWithNoSubtitleOrTimesUsesTheVanillaDefaults() {
        BukkitClickActionRunner.TitleSpec spec = BukkitClickActionRunner.TitleSpec.parse("Welcome");

        assertThat(spec.title()).isEqualTo("Welcome");
        assertThat(spec.subtitle()).isEmpty();
        assertThat(spec.fadeInTicks()).isEqualTo(10);
        assertThat(spec.stayTicks()).isEqualTo(70);
        assertThat(spec.fadeOutTicks()).isEqualTo(20);
    }

    @Test
    void titleWithASubtitleButNoTimesKeepsTheDefaults() {
        BukkitClickActionRunner.TitleSpec spec = BukkitClickActionRunner.TitleSpec.parse("Title|Sub");

        assertThat(spec.title()).isEqualTo("Title");
        assertThat(spec.subtitle()).isEqualTo("Sub");
        assertThat(spec.fadeInTicks()).isEqualTo(10);
        assertThat(spec.stayTicks()).isEqualTo(70);
        assertThat(spec.fadeOutTicks()).isEqualTo(20);
    }

    @Test
    void titleWithFullTimesParsesAllThree() {
        BukkitClickActionRunner.TitleSpec spec = BukkitClickActionRunner.TitleSpec.parse("Title|Sub|5|40|15");

        assertThat(spec.title()).isEqualTo("Title");
        assertThat(spec.subtitle()).isEqualTo("Sub");
        assertThat(spec.fadeInTicks()).isEqualTo(5);
        assertThat(spec.stayTicks()).isEqualTo(40);
        assertThat(spec.fadeOutTicks()).isEqualTo(15);
    }

    @Test
    void titleWithAMalformedTimingTailFallsBackToTheDefaults() {
        // A non-numeric tail must not abort the title nor half-apply: the timings fall back to vanilla defaults and
        // the whole text after the first '|' is taken as the subtitle (the operator did not author valid timings).
        BukkitClickActionRunner.TitleSpec spec = BukkitClickActionRunner.TitleSpec.parse("Title|Sub|fast|slow|nope");

        assertThat(spec.title()).isEqualTo("Title");
        assertThat(spec.subtitle()).isEqualTo("Sub|fast|slow|nope");
        assertThat(spec.fadeInTicks()).isEqualTo(10);
        assertThat(spec.stayTicks()).isEqualTo(70);
        assertThat(spec.fadeOutTicks()).isEqualTo(20);
    }

    @Test
    void titleWithAPartialTimingTailFallsBackToTheDefaults() {
        // Only fade-in given (no full stay/fade-out trio): the spec is incomplete, so all three timings fall back.
        BukkitClickActionRunner.TitleSpec spec = BukkitClickActionRunner.TitleSpec.parse("Title|Sub|5");

        assertThat(spec.title()).isEqualTo("Title");
        assertThat(spec.subtitle()).isEqualTo("Sub|5");
        assertThat(spec.fadeInTicks()).isEqualTo(10);
        assertThat(spec.stayTicks()).isEqualTo(70);
        assertThat(spec.fadeOutTicks()).isEqualTo(20);
    }

    @Test
    void titleWithCustomTimesDispatchesWithoutThrowing() {
        // End-to-end: a TITLE action carrying custom times builds a valid Title.Times and dispatches fail-soft, so
        // the chain continues to the next action. (MockBukkit's PlayerMock does not capture the Adventure title's
        // times, so the parsed timings themselves are pinned by the TitleSpec.parse tests above; this asserts the
        // apply path is well-formed.)
        runner().run(
                        player,
                        List.of(
                                new ClickAction(ClickTrigger.ANY, ClickActionType.TITLE, "Hi|there|5|40|15"),
                                new ClickAction(ClickTrigger.ANY, ClickActionType.RUN_PLAYER, "after")),
                        false);

        assertThat(commandRunner.playerCommands).containsExactly("after");
    }

    // --- SoundSpec (unchanged behaviour) --------------------------------------------------------------------

    @Test
    void keepsTheNamespaceOfANamespacedSoundKey() {
        BukkitClickActionRunner.SoundSpec spec =
                BukkitClickActionRunner.SoundSpec.parse("minecraft:entity.player.levelup");

        assertThat(spec.key()).isEqualTo("minecraft:entity.player.levelup");
        assertThat(spec.volume()).isEqualTo(1.0f);
        assertThat(spec.pitch()).isEqualTo(1.0f);
    }

    // --- fakes ----------------------------------------------------------------------------------------------

    private static final class RecordingRunner implements ClickCommandRunner {
        private final List<String> consoleCommands = new ArrayList<>();
        private final List<String> playerCommands = new ArrayList<>();
        private final List<String> opCommands = new ArrayList<>();

        @Override
        public void runAsConsole(String command) {
            consoleCommands.add(command);
        }

        @Override
        public void runAsPlayer(Player player, String command) {
            playerCommands.add(command);
        }

        @Override
        public void runAsPlayerOp(Player player, String command) {
            opCommands.add(command);
        }
    }

    private static final class RecordingConnector implements ServerConnector {
        private final List<String> servers = new ArrayList<>();

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public void connect(Player player, String server) {
            servers.add(server);
        }
    }

    /**
     * Captures the delayed continuations the DELAY action parks via {@link Scheduler#asyncAfter}; running them
     * resumes the chain synchronously in the test. An entity hop ({@code onEntity}) runs inline unless the player
     * has been disconnected, mirroring the Folia adapter's offline no-op.
     */
    private static final class CapturingScheduler implements Scheduler {
        private final Deque<Runnable> delayed = new ArrayDeque<>();
        private final Set<java.util.UUID> offline = new HashSet<>();

        int pendingDelays() {
            return delayed.size();
        }

        void disconnect(Player who) {
            offline.add(who.getUniqueId());
        }

        void runAllDelayed() {
            while (!delayed.isEmpty()) {
                delayed.poll().run();
            }
        }

        /** Run exactly the delays parked so far, leaving any new ones a resumed leg parks for the next pass. */
        void runDelayedOnce() {
            for (Runnable task : new ArrayList<>(delayed)) {
                delayed.remove(task);
                task.run();
            }
        }

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
            if (!offline.contains(player.uuid())) {
                task.run();
            }
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            delayed.add(task);
        }
    }

    private static final class FakePermissions implements Permissions {
        private final Set<String> granted = new HashSet<>();

        void grant(Player who, String node) {
            granted.add(who.getUniqueId() + ":" + node);
        }

        @Override
        public boolean has(PlayerRef who, String node) {
            return granted.contains(who.uuid() + ":" + node);
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who,
                QuotaFamily family,
                com.uxplima.uxmessentials.shared.domain.@org.jspecify.annotations.Nullable WorldRef world,
                long def) {
            return QuotaResult.limited(def);
        }
    }

    private static final class RecordingEconomy implements ClickActionEconomy {
        private final List<BigDecimal> withdrawals = new ArrayList<>();
        private boolean affordable = true;
        private boolean explode = false;

        @Override
        public boolean withdraw(PlayerRef who, BigDecimal amount, String currencyId) {
            if (explode) {
                throw new IllegalStateException("economy backend is down");
            }
            withdrawals.add(amount);
            return affordable;
        }
    }

    private static final class RecordingMessages implements Messages {
        private final List<String> sentKeys = new ArrayList<>();

        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            sentKeys.add(key.key());
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
}
