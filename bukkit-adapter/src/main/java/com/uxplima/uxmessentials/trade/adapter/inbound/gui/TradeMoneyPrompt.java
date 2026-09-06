package com.uxplima.uxmessentials.trade.adapter.inbound.gui;

import java.util.function.Consumer;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The narrow capability the trade window uses to capture a money amount without depending on the concrete text-input
 * seam: it hands over the live player, their viewer reference, and the currency being staked, and gets exactly one
 * callback back: the typed line on submit, or {@code onCancel}. Production wires it over {@code TextInput.prompt} (so
 * the operator's per-key anvil/chat/sign/dialog mode and the Folia entity-thread hop are honoured); a test wires a
 * synchronous fake. Defined here so {@link TradeView} stays decoupled from the input package and testable without
 * constructing the whole seam (mirrors the menu engine's {@code MenuTextPrompt}).
 */
@NullMarked
@FunctionalInterface
public interface TradeMoneyPrompt {

    /**
     * Prompt {@code player} for an amount of {@code currencyId}, then run exactly one callback on the viewer's entity
     * thread: {@code onSubmit} with the typed line, or {@code onCancel} if they aborted.
     */
    void prompt(Player player, PlayerRef viewer, String currencyId, Consumer<String> onSubmit, Runnable onCancel);
}
