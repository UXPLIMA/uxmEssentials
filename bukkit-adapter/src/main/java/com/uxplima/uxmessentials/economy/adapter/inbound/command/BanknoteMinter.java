package com.uxplima.uxmessentials.economy.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.application.port.BanknoteStore;
import com.uxplima.uxmessentials.economy.domain.Banknote;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyleTags;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * Mints a physical banknote {@link ItemStack} carrying a fresh anti-dupe token and registers that token in the
 * {@link BanknoteStore} so the note can be redeemed exactly once. The minted item carries three persistent-data
 * tags (value, currency, and token) that the redemption path reads to credit the right wallet by the right
 * amount; the cosmetic name and lore resolve in the recipient's locale.
 *
 * <p>Minting alone neither debits nor credits any wallet: it produces the bearer instrument and records its
 * token. {@code /withdraw} debits the holder first and then mints; an admin-issued {@code /eco note} mints with
 * no debit. Both share this one minting path so the item shape and the dupe-token bookkeeping stay identical.
 */
@NullMarked
final class BanknoteMinter {

    private final NamespacedKey valueKey;
    private final NamespacedKey currencyKey;
    private final NamespacedKey tokenKey;
    private final MiniMessage miniMessage;
    private final Messages messages;
    private final BanknoteStore banknoteStore;

    BanknoteMinter(Plugin plugin, Messages messages, BanknoteStore banknoteStore) {
        Objects.requireNonNull(plugin, "plugin");
        this.valueKey = new NamespacedKey(plugin, "banknote_value");
        this.currencyKey = new NamespacedKey(plugin, "banknote_currency");
        this.tokenKey = new NamespacedKey(plugin, "banknote_token");
        this.miniMessage = MiniMessage.miniMessage();
        this.messages = Objects.requireNonNull(messages, "messages");
        this.banknoteStore = Objects.requireNonNull(banknoteStore, "banknoteStore");
    }

    /**
     * Mint a banknote of {@code amount} signed in {@code recipient}'s name, register its token for redemption,
     * and return the item. The recipient's locale resolves the cosmetic name and lore. Run off the tick thread
     * with the rest of the issuing command, since it touches the {@link BanknoteStore}.
     */
    ItemStack mint(PlayerRef recipient, Money amount) {
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(amount, "amount");
        UUID token = UUID.randomUUID();
        banknoteStore.register(new Banknote(token, amount, System.currentTimeMillis()));
        ItemStack banknote = ItemBuilder.of(Material.PAPER)
                .name(itemText(recipient, EconomyMessageKey.BANKNOTE_ITEM_NAME, Map.of()))
                .lore(List.of(
                        itemText(
                                recipient,
                                EconomyMessageKey.BANKNOTE_ITEM_LORE_VALUE,
                                Map.of(
                                        "value",
                                        amount.amount().toPlainString(),
                                        "currency",
                                        amount.currency().id().value())),
                        itemText(
                                recipient,
                                EconomyMessageKey.BANKNOTE_ITEM_LORE_SIGNATORY,
                                Map.of("signatory", recipient.name())),
                        itemText(recipient, EconomyMessageKey.BANKNOTE_ITEM_LORE_HINT, Map.of())))
                .build();
        tag(banknote, amount, token);
        return banknote;
    }

    private void tag(ItemStack banknote, Money amount, UUID token) {
        ItemMeta meta = banknote.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(valueKey, PersistentDataType.STRING, amount.amount().toPlainString());
        pdc.set(currencyKey, PersistentDataType.STRING, amount.currency().id().value());
        pdc.set(tokenKey, PersistentDataType.STRING, token.toString());
        banknote.setItemMeta(meta);
    }

    /** Resolve an item name/lore line in the recipient's locale and parse it to a non-italic Component. */
    private Component itemText(PlayerRef recipient, EconomyMessageKey key, Map<String, String> placeholders) {
        return miniMessage
                .deserialize(messages.resolve(recipient, key, placeholders), StyleTags.resolver())
                .decoration(TextDecoration.ITALIC, false);
    }
}
