/**
 * Pure reward model of the vote bounded context: the config-driven reward engine's data. An
 * {@code ItemReward} is the material/amount/name/lore of one item grant as plain strings, the adapter
 * builds the actual {@code ItemStack}. A {@code RewardSpec} is one reward bundle (chance, optional
 * permission, console commands, messages, broadcasts, item grants, world filter). A
 * {@code MilestoneReward} pairs a one-shot ({@code at}) or recurring ({@code every}) all-time vote count
 * with the spec it pays. A {@code RewardCatalog} groups the specs by trigger (per-vote, per-site,
 * first-vote, milestones). A {@code RewardGrant} is the resolved effects of one granted spec, ready for
 * the adapter to apply. No Bukkit, Paper, Kyori, or logging type appears here: item rewards are
 * string/data only.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.vote.domain.reward;
