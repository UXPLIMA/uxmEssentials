package com.uxplima.uxmessentials.ranks.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.ranks.application.CurrentRank;
import com.uxplima.uxmessentials.ranks.application.RanksMessageKey;
import com.uxplima.uxmessentials.ranks.application.Rankup;
import com.uxplima.uxmessentials.ranks.application.RankupResult;
import com.uxplima.uxmessentials.ranks.domain.Rank;
import com.uxplima.uxmessentials.ranks.domain.RankLadder;
import com.uxplima.uxmessentials.ranks.domain.RankStanding;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@code /ranks} ladder panel (config-gated on {@code gui.enabled}), registered with the menu engine and
 * opened from the command. A three-row window: the player's current rank and prestige, the next rank with its
 * cost and requirements, a rank-up button that runs the {@link Rankup} pipeline, and a close button. It holds no
 * new domain logic. It resolves the viewer's {@link RankStanding} through {@link CurrentRank} off the tick thread
 * when the panel opens, precomputes the display strings into the {@link RanksSubject} the display items read
 * through the {@code ranks_*} placeholders, then opens the panel through {@link Menus} (which hops to the viewer's
 * entity thread to touch the live inventory). Every visible string resolves from the ranks catalog.
 *
 * <p>The rank-up button routes through the very same {@link Rankup} use case {@code /rankup} runs, on the click's
 * own entity thread (requirement and cost evaluation are owning-region-thread operations, exactly the thread the
 * click dispatches on), reports the typed outcome through the catalog, then reopens the panel so the standing
 * redraws. So the panel and the command always agree, and a refused rankup leaves the standing unmoved.
 */
@NullMarked
public final class RanksPanelMenu {

    /** The engine spec id this menu registers and opens under. */
    public static final String SPEC_ID = "ranks-panel";

    private static final String SPEC_RESOURCE = "modules/ranks/gui/ranks-panel.conf";

    /** The dash shown for the next rank's cost and requirements when there is no next rank (or none authored). */
    private static final String NO_REQUIREMENTS = "-";

    private final Menus menus;
    private final Rankup rankup;
    private final CurrentRank currentRank;
    private final RankLadder ladder;
    private final Scheduler scheduler;
    private final Messages messages;
    private final CommandFeedback feedback;

    public RanksPanelMenu(
            Menus menus,
            Rankup rankup,
            CurrentRank currentRank,
            RankLadder ladder,
            Scheduler scheduler,
            Messages messages) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.rankup = Objects.requireNonNull(rankup, "rankup");
        this.currentRank = Objects.requireNonNull(currentRank, "currentRank");
        this.ladder = Objects.requireNonNull(ladder, "ladder");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.feedback = new CommandFeedback(messages);
    }

    /** Register the panel's per-display placeholders and the rank-up action the spec names, and the spec itself. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.placeholder("ranks_current", ctx -> subject(ctx).current());
        bindings.placeholder("ranks_prestige", ctx -> subject(ctx).prestige());
        bindings.placeholder("ranks_next", ctx -> subject(ctx).next());
        bindings.placeholder("ranks_next_cost", ctx -> subject(ctx).nextCost());
        bindings.placeholder("ranks_next_requirements", ctx -> subject(ctx).nextRequirements());
        bindings.action("ranks:rankup", this::onRankup);
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, 3, log));
    }

    /**
     * Open the panel for {@code player}. The standing is resolved off the tick thread, the display strings are
     * precomputed into the subject, then the panel is opened through the engine, which renders it on the viewer's
     * entity thread. A player whose ladder holds no ranks (an operator cleared {@code ranks.conf}) sees the empty
     * standing marker rather than a crash.
     */
    public void open(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        scheduler.async(() -> menus.open(viewer, SPEC_ID, subjectFor(viewer)));
    }

    /** Resolve the viewer's standing and precompute every display string the panel renders. */
    private RanksSubject subjectFor(PlayerRef viewer) {
        Optional<RankStanding> standing = currentRank.of(viewer.uuid());
        if (standing.isEmpty()) {
            String max = messages.resolve(viewer, RanksMessageKey.RANKS_GUI_MAX, Map.of());
            return new RanksSubject(max, "0", max, NO_REQUIREMENTS, NO_REQUIREMENTS);
        }
        RankStanding held = standing.get();
        String current = held.rank().displayName();
        String prestige = Integer.toString(held.prestige().level());
        Optional<Rank> next = ladder.next(held.rank().id());
        if (next.isEmpty()) {
            String max = messages.resolve(viewer, RanksMessageKey.RANKS_GUI_MAX, Map.of());
            return new RanksSubject(current, prestige, max, NO_REQUIREMENTS, NO_REQUIREMENTS);
        }
        Rank target = next.get();
        return new RanksSubject(
                current, prestige, target.displayName(), Long.toString(target.cost()), requirementsOf(target));
    }

    /** The next rank's raw requirement lines joined for display, or the dash when it names none. */
    private static String requirementsOf(Rank target) {
        return target.requirements().isEmpty() ? NO_REQUIREMENTS : String.join(", ", target.requirements());
    }

    /**
     * Run the rankup pipeline for the clicker on their own entity thread, report the outcome through the catalog
     * (the same lines {@code /rankup} shows), then reopen the panel so the standing redraws. Charging strictly
     * before advancing lives in {@link Rankup}, so a refused rankup here neither charges nor moves the pointer.
     */
    private void onRankup(MenuActionContext ctx) {
        Player player = ctx.player();
        PlayerRef who = BukkitRefs.toRef(player);
        render(player, rankup.rankUp(who));
        open(player, who);
    }

    private void render(Player player, RankupResult result) {
        switch (result.status()) {
            case RANKED_UP ->
                feedback.send(player, RanksMessageKey.RANKS_RANKUP_SUCCESS, Map.of("rank", newRankName(result)));
            case ALREADY_MAX -> feedback.send(player, RanksMessageKey.RANKS_RANKUP_MAX);
            case REQUIREMENTS_NOT_MET -> feedback.send(player, RanksMessageKey.RANKS_RANKUP_REQUIREMENTS);
            case CANNOT_AFFORD -> feedback.send(player, RanksMessageKey.RANKS_RANKUP_COST);
        }
    }

    /** The advanced-to rank's display name; present exactly when the outcome is {@code RANKED_UP}. */
    private static String newRankName(RankupResult result) {
        return result.newRank().map(Rank::displayName).orElseThrow();
    }

    private RanksSubject subject(MenuContext ctx) {
        return ctx.subject(RanksSubject.class);
    }

    /**
     * The subject of an open ranks panel: the precomputed display strings the panel's items render through the
     * {@code ranks_*} placeholders. Everything is resolved at open time, so the render touches no port.
     *
     * @param current the current rank's display name (the empty-standing marker when the ladder holds no ranks)
     * @param prestige the player's prestige level
     * @param next the next rank's display name, or the max-rank marker when the player is at the top
     * @param nextCost the next rank's cost as a plain number, or the dash at the top rank
     * @param nextRequirements the next rank's requirement lines joined for display, or the dash when there are none
     */
    public record RanksSubject(String current, String prestige, String next, String nextCost, String nextRequirements) {

        public RanksSubject {
            Objects.requireNonNull(current, "current");
            Objects.requireNonNull(prestige, "prestige");
            Objects.requireNonNull(next, "next");
            Objects.requireNonNull(nextCost, "nextCost");
            Objects.requireNonNull(nextRequirements, "nextRequirements");
        }
    }
}
