package com.uxplima.uxmessentials.vote.adapter.outbound;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.RewardApplier;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.QueuedReward;
import com.uxplima.uxmessentials.vote.domain.reward.ItemReward;
import com.uxplima.uxmessentials.vote.domain.reward.RewardGrant;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The Bukkit {@link RewardApplier}: turns one resolved {@link RewardGrant} into its real effects.
 *
 * <p>For an <b>online</b> voter the live effects run on the voter's own entity region thread (so the inventory
 * mutation and command dispatch are region-correct): each command is dispatched from the console with the
 * {@code {player}} placeholder substituted, each message line is sent to the voter as a parsed MiniMessage
 * component, each broadcast line is sent to every online player (with {@code {player}} expanded to the voter's
 * name), and each {@link ItemReward} is built into an {@link ItemStack} and added to the voter's inventory,
 * with any overflow dropped at their feet, the same give-or-drop policy the kits context uses. A reward whose
 * material name does not resolve is skipped with one warn line, never throwing, so one typo cannot abort the
 * rest of the grant.
 *
 * <p>For an <b>offline</b> voter only the grant's commands are durable, so they are queued through
 * {@link VoteRepository#enqueue(QueuedReward)} (the existing offline batch the join handler drains) with the
 * {@code {player}} placeholder left raw: the drain substitutes it when the voter next joins. The enqueue is a
 * database write, so it hops off the tick thread via the {@link Scheduler}. Messages, broadcasts, and items are
 * skipped offline: there is no inventory to fill and no viewer to message. An offline queue cap bounds how many
 * queued reward commands one voter may stack up while away ({@code 0} disables it). The cap is checked against
 * {@link VoteRepository#queuedCount(PlayerRef)}, which counts command rows, not batches; a vote past the cap
 * still counts toward totals and the party, only its durable offline reward is dropped.
 *
 * <p>Reward message and broadcast lines are operator-authored config content (MiniMessage with a {@code
 * {player}} token), not {@code MessageKey} catalog entries, so they are rendered with MiniMessage directly here
 * exactly as the {@code vote-links} display renders its configured links.
 */
@NullMarked
public final class BukkitRewardApplier implements RewardApplier {

    private static final String PLAYER_TOKEN = "{player}";

    private final VoteRepository repository;
    private final BukkitRewardDispatcher dispatcher;
    private final Scheduler scheduler;
    private final int offlineLimit;
    private final MiniMessage miniMessage;
    private final Logger log;

    /**
     * @param offlineLimit the most queued reward commands an offline voter may have stored at once; {@code 0}
     *                     (or any non-positive value) means no cap. The limit is compared against
     *                     {@link VoteRepository#queuedCount(PlayerRef)}, which counts command rows. A vote that
     *                     would exceed the cap still counts toward totals and the party, only its durable
     *                     offline reward is dropped
     */
    public BukkitRewardApplier(
            VoteRepository repository,
            BukkitRewardDispatcher dispatcher,
            Scheduler scheduler,
            int offlineLimit,
            Logger log) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.offlineLimit = offlineLimit;
        this.log = Objects.requireNonNull(log, "log");
        this.miniMessage = MiniMessage.miniMessage();
    }

    @Override
    public void apply(PlayerRef voter, boolean online, RewardGrant grant) {
        Objects.requireNonNull(voter, "voter");
        Objects.requireNonNull(grant, "grant");
        if (online) {
            applyOnline(voter, grant);
        } else {
            queueOffline(voter, grant);
        }
    }

    private void queueOffline(PlayerRef voter, RewardGrant grant) {
        // Only the commands are durable for an offline voter; the {player} token stays raw so the join-time
        // drain substitutes it then. Skip messages/broadcasts/items: there is no viewer and no inventory.
        if (grant.commands().isEmpty()) {
            return;
        }
        // The count probe and the enqueue are both DB reads/writes, so they share one async hop. A capped
        // queue at or over its limit drops only this batch's offline reward; the vote itself still counted.
        scheduler.async(() -> {
            if (offlineLimit > 0 && repository.queuedCount(voter) >= offlineLimit) {
                log.debug(
                        "event=vote_offline_reward_capped player={} limit={}",
                        voter.name(),
                        Integer.toString(offlineLimit));
                return;
            }
            repository.enqueue(new QueuedReward(voter, grant.commands(), Instant.now()));
        });
    }

    private void applyOnline(PlayerRef voter, RewardGrant grant) {
        dispatcher.dispatch(grant.commands(), voter.name());
        broadcast(grant, voter.name());
        scheduler.onEntity(voter, () -> deliverLiveTo(voter, grant));
    }

    private void deliverLiveTo(PlayerRef voter, RewardGrant grant) {
        @Nullable Player player = Bukkit.getPlayer(voter.uuid());
        if (player == null) {
            return;
        }
        for (String line : grant.messages()) {
            player.sendMessage(render(line, voter.name()));
        }
        for (ItemReward item : grant.items()) {
            giveOrDrop(player, item);
        }
    }

    private void broadcast(RewardGrant grant, String voterName) {
        if (grant.broadcasts().isEmpty()) {
            return;
        }
        for (String line : grant.broadcasts()) {
            Component component = render(line, voterName);
            scheduler.onGlobal(() -> {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    online.sendMessage(component);
                }
            });
        }
    }

    private void giveOrDrop(Player player, ItemReward item) {
        @Nullable ItemStack stack = build(item);
        if (stack == null) {
            return;
        }
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
        overflow.values().forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
    }

    private @Nullable ItemStack build(ItemReward item) {
        @Nullable Material material = Material.matchMaterial(item.material().toUpperCase(Locale.ROOT));
        if (material == null || material.isAir()) {
            log.warn("event=vote_reward_item_skipped reason=unknown_material material={}", item.material());
            return null;
        }
        ItemBuilder builder = ItemBuilder.of(material);
        item.name().ifPresent(name -> builder.name(miniMessage.deserialize(name)));
        if (!item.lore().isEmpty()) {
            builder.lore(item.lore().stream().map(miniMessage::deserialize).toList());
        }
        ItemStack stack = builder.build();
        // Set the amount on the built stack rather than the builder: a reward may exceed a single max stack,
        // and addItem then splits it into stacks; the builder's amount() rejects an oversized request.
        stack.setAmount(item.amount());
        return stack;
    }

    private Component render(String line, String voterName) {
        return miniMessage.deserialize(line.replace(PLAYER_TOKEN, voterName));
    }
}
