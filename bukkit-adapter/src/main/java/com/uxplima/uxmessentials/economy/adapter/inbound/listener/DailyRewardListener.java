package com.uxplima.uxmessentials.economy.adapter.inbound.listener;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Credits a configurable daily login reward the first time a player joins each cooldown window. The "last
 * claimed" stamp is transient per-holder state, so it lives in the player's PDC under a single pre-created key
 * (never minted on the hot path): exactly the kind of cooldown stamp PDC is for. The PDC read/stamp runs on the
 * join (entity) thread where the player is safe to touch; the credit and the confirmation are bridged off-tick
 * through the kernel {@link Scheduler}, so the ledger write never blocks the join. Disabled by default and a
 * no-op for a non-positive amount.
 */
@NullMarked
public final class DailyRewardListener implements Listener {

    private final EconomyProvider economy;
    private final EconomyNotifier notifier;
    private final Scheduler scheduler;
    private final Currency currency;
    private final boolean enabled;
    private final BigDecimal amount;
    private final long cooldownMillis;
    private final NamespacedKey claimKey;

    public DailyRewardListener(
            Plugin plugin,
            EconomyProvider economy,
            EconomyNotifier notifier,
            Scheduler scheduler,
            Currency currency,
            boolean enabled,
            BigDecimal amount,
            Duration cooldown) {
        Objects.requireNonNull(plugin, "plugin");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.enabled = enabled;
        this.amount = Objects.requireNonNull(amount, "amount");
        this.cooldownMillis = Objects.requireNonNull(cooldown, "cooldown").toMillis();
        this.claimKey = new NamespacedKey(plugin, "daily_reward_claim");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled || amount.signum() <= 0) {
            return;
        }
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        Long last = player.getPersistentDataContainer().get(claimKey, PersistentDataType.LONG);
        if (last != null && now - last < cooldownMillis) {
            return;
        }
        player.getPersistentDataContainer().set(claimKey, PersistentDataType.LONG, now);
        PlayerRef ref = new PlayerRef(player.getUniqueId(), player.getName());
        Money reward = Money.of(currency, amount);
        scheduler.async(() -> {
            economy.ensureAccount(ref, currency);
            if (economy.credit(ref, reward).isOk()) {
                notifier.send(ref, EconomyMessageKey.DAILY_REWARD_GRANTED, Map.of("amount", notifier.amount(reward)));
            }
        });
    }
}
