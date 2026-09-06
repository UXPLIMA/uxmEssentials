package com.uxplima.uxmessentials.vote.application;

import static java.util.stream.Collectors.toUnmodifiableSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import com.uxplima.uxmessentials.vote.domain.reward.MilestoneReward;
import com.uxplima.uxmessentials.vote.domain.reward.RewardCatalog;
import com.uxplima.uxmessentials.vote.domain.reward.RewardGrant;
import com.uxplima.uxmessentials.vote.domain.reward.RewardSpec;
import com.uxplima.uxmessentials.vote.domain.reward.StreakReward;

/**
 * The pure config-driven reward engine: given a {@link RewardCatalog} (fixed at construction) and a
 * {@link RewardContext} (one vote's facts), it resolves the ordered list of {@link RewardGrant}s the
 * adapter must apply for that vote. It is a function of its inputs, no RNG, no Bukkit, no I/O; the
 * randomness and the permission/world facts arrive through the context seams so a test can drive it
 * deterministically.
 *
 * <p>Candidate specs are collected in a fixed order. All per-vote specs, then the per-site specs for the
 * vote's service, then (only when this is the voter's first ever vote) the first-vote specs, then each
 * milestone whose threshold the all-time count just hit, then (only when this vote advanced the streak)
 * each streak reward the current streak length matches. Each candidate is then gated in order: world,
 * permission, chance. A spec is granted only when it clears every gate, and the returned grants keep the
 * candidate order.
 *
 * <p>The world gate treats an offline voter (empty {@code worldName}) as having no world to satisfy a
 * filter against: a spec with a world filter is skipped offline, while a spec with no world filter passes
 * regardless of online status.
 *
 * <p>Distinct from the per-spec world filter, the engine also holds a global set of disabled worlds: a
 * vote cast in one of those worlds earns nothing at all, no matter what specs would otherwise match. The
 * set is matched case-insensitively and an empty set disables nothing.
 */
public final class RewardEngine {

    private final RewardCatalog catalog;
    private final Set<String> disabledWorlds;

    /**
     * @param catalog        the fixed reward catalog this engine resolves against; must not be {@code null}
     * @param disabledWorlds worlds in which a vote earns no rewards at all, matched case-insensitively;
     *                       an empty set disables nothing
     */
    public RewardEngine(RewardCatalog catalog, Set<String> disabledWorlds) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(disabledWorlds, "disabledWorlds");
        this.disabledWorlds =
                disabledWorlds.stream().map(s -> s.toLowerCase(Locale.ROOT)).collect(toUnmodifiableSet());
    }

    /** Resolve the grants {@code ctx} earns from the catalog, in candidate order (per-vote, site, first-vote, milestones). */
    public List<RewardGrant> resolve(RewardContext ctx) {
        Objects.requireNonNull(ctx, "ctx");
        if (!ctx.worldName().isBlank()
                && disabledWorlds.contains(ctx.worldName().toLowerCase(Locale.ROOT))) {
            return List.of();
        }
        List<RewardGrant> grants = new ArrayList<>();
        for (RewardSpec spec : candidates(ctx)) {
            if (passesAllGates(spec, ctx)) {
                grants.add(RewardGrant.of(spec));
            }
        }
        return grants;
    }

    private List<RewardSpec> candidates(RewardContext ctx) {
        long alltime = ctx.totalsAfter().alltime();
        List<RewardSpec> candidates = new ArrayList<>(catalog.perVote());
        candidates.addAll(catalog.forSite(ctx.service()));
        if (alltime == 1) {
            candidates.addAll(catalog.firstVote());
        }
        for (MilestoneReward milestone : catalog.milestones()) {
            if (milestone.matches(alltime)) {
                candidates.add(milestone.reward());
            }
        }
        if (ctx.streakAdvanced()) {
            long streak = ctx.totalsAfter().currentStreak();
            for (StreakReward streakReward : catalog.streaks()) {
                if (streakReward.matches(streak)) {
                    candidates.add(streakReward.reward());
                }
            }
        }
        return candidates;
    }

    private boolean passesAllGates(RewardSpec spec, RewardContext ctx) {
        return passesWorld(spec, ctx) && passesPermission(spec, ctx) && passesChance(spec, ctx);
    }

    private boolean passesWorld(RewardSpec spec, RewardContext ctx) {
        if (spec.worlds().isEmpty()) {
            return true;
        }
        // A world-filtered spec needs an online voter in a matching world; an offline voter has no world.
        return ctx.online() && !ctx.worldName().isEmpty() && spec.appliesInWorld(ctx.worldName());
    }

    private boolean passesPermission(RewardSpec spec, RewardContext ctx) {
        return spec.requiredPermission().map(ctx.hasPermission()::test).orElse(true);
    }

    private boolean passesChance(RewardSpec spec, RewardContext ctx) {
        return ctx.rollPasses().test(spec.chancePercent());
    }
}
