package com.uxplima.uxmessentials.economy.adapter.inbound.listener;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.EconomyNotifier;
import com.uxplima.uxmessentials.economy.application.port.BanknoteStore;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Banknote;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The single dupe-safe redemption path for a physical banknote, shared by the right-click
 * {@link BanknoteListener} and the {@code /deposit} command so the two entry points can never both turn one
 * note into money on the same tick.
 *
 * <p>A minted banknote carries a unique per-note token in its persistent data, so one item carries exactly one
 * token and one value: redeeming consumes one note and credits one note's value. The sequence is ordered for
 * safety: the item is decremented <em>first</em> (optimistic removal), then the token is redeemed and the wallet
 * credited. If the redeem fails, the credit returns an error, or anything throws, the item is restored to its
 * prior amount and the token is re-registered, so a failed deposit can neither lose the note nor mint money.
 *
 * <p>A per-token in-flight set ({@link #inFlight}) gates the critical section: the first caller to claim a token
 * proceeds, a concurrent caller for the same token is rejected. The token is released in a {@code finally} so a
 * thrown exception never wedges a note as permanently un-depositable. All inventory mutation must already be on
 * the holder's entity thread before {@link #redeem} is called.
 */
@NullMarked
public final class BanknoteRedeemer {

    private final EconomyProvider economy;
    private final EconomyNotifier notifier;
    private final BanknoteStore banknoteStore;
    private final Logger log;
    private final NamespacedKey valueKey;
    private final NamespacedKey currencyKey;
    private final NamespacedKey tokenKey;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public BanknoteRedeemer(
            Plugin plugin, EconomyProvider economy, EconomyNotifier notifier, BanknoteStore banknoteStore, Logger log) {
        Objects.requireNonNull(plugin, "plugin");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.banknoteStore = Objects.requireNonNull(banknoteStore, "banknoteStore");
        this.log = Objects.requireNonNull(log, "log");
        this.valueKey = new NamespacedKey(plugin, "banknote_value");
        this.currencyKey = new NamespacedKey(plugin, "banknote_currency");
        this.tokenKey = new NamespacedKey(plugin, "banknote_token");
    }

    /** True when {@code item} carries the three banknote persistent-data tags. */
    public boolean isBanknote(ItemStack item) {
        Objects.requireNonNull(item, "item");
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(valueKey, PersistentDataType.STRING)
                && pdc.has(currencyKey, PersistentDataType.STRING)
                && pdc.has(tokenKey, PersistentDataType.STRING);
    }

    /**
     * Redeem one note from {@code item} for {@code owner}. Must be called on the owner's entity thread. The
     * result message is delivered through the notifier; the method itself returns nothing.
     */
    public void redeem(Player player, PlayerRef owner, ItemStack item) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(item, "item");

        Optional<Banknote> parsed = parse(item);
        if (parsed.isEmpty()) {
            notifier.send(owner, EconomyMessageKey.BANKNOTE_INVALID);
            return;
        }
        Banknote note = parsed.get();
        if (!inFlight.add(note.token())) {
            notifier.send(owner, EconomyMessageKey.BANKNOTE_INVALID);
            return;
        }
        try {
            apply(player, owner, item, note);
        } finally {
            inFlight.remove(note.token());
        }
    }

    private void apply(Player player, PlayerRef owner, ItemStack item, Banknote note) {
        int priorAmount = item.getAmount();
        item.setAmount(priorAmount - 1);
        boolean redeemed = false;
        try {
            if (!banknoteStore.redeem(note.token())) {
                notifier.send(owner, EconomyMessageKey.BANKNOTE_INVALID);
                item.setAmount(priorAmount);
                return;
            }
            redeemed = true;
            if (economy.credit(owner, note.money()).isOk()) {
                playPickup(player);
                notifier.send(
                        owner, EconomyMessageKey.BANKNOTE_DEPOSITED, Map.of("amount", notifier.amount(note.money())));
                return;
            }
            restore(item, priorAmount, note, true);
            notifier.send(owner, EconomyMessageKey.BANKNOTE_INVALID);
        } catch (RuntimeException failure) {
            log.error("banknote redemption failed for " + owner.uuid(), failure);
            // Re-register the token only when the redeem actually deleted it; if redeem threw before deleting, the
            // row still exists and re-registering it would be a primary-key violation that masks the real cause.
            restore(item, priorAmount, note, redeemed);
            if (!redeemed) {
                notifier.send(owner, EconomyMessageKey.BANKNOTE_INVALID);
            }
        }
    }

    private void restore(ItemStack item, int priorAmount, Banknote note, boolean wasRedeemed) {
        item.setAmount(priorAmount);
        if (wasRedeemed) {
            banknoteStore.register(new Banknote(note.token(), note.money(), System.currentTimeMillis()));
        }
    }

    private Optional<Banknote> parse(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String valueStr = pdc.get(valueKey, PersistentDataType.STRING);
        String currencyStr = pdc.get(currencyKey, PersistentDataType.STRING);
        String tokenStr = pdc.get(tokenKey, PersistentDataType.STRING);
        if (valueStr == null || currencyStr == null || tokenStr == null) {
            return Optional.empty();
        }
        BigDecimal value;
        UUID token;
        try {
            value = new BigDecimal(valueStr);
            token = UUID.fromString(tokenStr);
        } catch (IllegalArgumentException malformed) {
            log.warn("ignoring banknote with malformed value or token: {}", String.valueOf(malformed.getMessage()));
            return Optional.empty();
        }
        Currency currency = matchCurrency(currencyStr);
        if (currency == null) {
            return Optional.empty();
        }
        return Optional.of(new Banknote(token, Money.of(currency, value), System.currentTimeMillis()));
    }

    private @Nullable Currency matchCurrency(String currencyId) {
        return economy.currencies().stream()
                .filter(c -> c.id().value().equalsIgnoreCase(currencyId))
                .findFirst()
                .orElse(null);
    }

    private void playPickup(Player player) {
        org.bukkit.Location loc = Objects.requireNonNull(player.getLocation(), "player location");
        player.playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
    }
}
