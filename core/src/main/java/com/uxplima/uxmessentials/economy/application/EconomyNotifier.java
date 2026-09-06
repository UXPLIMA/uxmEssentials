package com.uxplima.uxmessentials.economy.application;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The shared {@link Notifier} plus the money formatting an economy message needs, so an economy use case
 * sends a {@link MessageKey} and renders a {@link Money} placeholder through one collaborator.
 *
 * <p>It also owns the configured {@link AmountFormat}, so every economy use case renders a {@link Money}
 * placeholder through {@link #amount(Money)} and the operator's {@code economy.amount-format} choice applies
 * uniformly across {@code /balance}, {@code /pay}, {@code /baltop}, and {@code /eco} without each use case
 * carrying the toggle.
 */
public final class EconomyNotifier {

    private final Notifier notifier;
    private final AmountFormat amountFormat;

    public EconomyNotifier(Messages messages, MessageSink sink) {
        this(messages, sink, AmountFormat.FULL);
    }

    public EconomyNotifier(Messages messages, MessageSink sink, AmountFormat amountFormat) {
        this.notifier = new Notifier(messages, sink);
        this.amountFormat = Objects.requireNonNull(amountFormat, "amountFormat");
    }

    /** Resolve {@code key} for {@code viewer} with {@code placeholders} and deliver it. */
    public void send(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        notifier.send(viewer, key, placeholders);
    }

    /** Resolve and deliver {@code key} with no placeholders. */
    public void send(PlayerRef viewer, MessageKey key) {
        notifier.send(viewer, key);
    }

    /** Render {@code money} with its symbol in the configured {@link AmountFormat}, the {@code {amount}} value. */
    public String amount(Money money) {
        return MoneyFormat.withSymbol(money, amountFormat);
    }

    /**
     * Render several per-currency totals as one comma-joined {@code {amount}} value, so a {@code /sellall} that
     * paid out in more than one currency reports each currency's proceeds in a single line. A single-element
     * collection renders identically to {@link #amount(Money)}.
     */
    public String amounts(java.util.Collection<Money> amounts) {
        Objects.requireNonNull(amounts, "amounts");
        java.util.List<String> rendered = new java.util.ArrayList<>();
        for (Money money : amounts) {
            rendered.add(MoneyFormat.withSymbol(money, amountFormat));
        }
        return String.join(", ", rendered);
    }
}
