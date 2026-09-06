package com.uxplima.uxmessentials.economy.domain;

import java.util.Objects;

import com.uxplima.uxmessentials.economy.domain.event.WalletRejected;

/**
 * A refused wallet change, carrying both the modelled {@link EconomyError} the command adapter renders and
 * the {@link WalletRejected} event the aggregate minted. Pairing the two keeps the GLOSSARY invariant
 * intact, only the aggregate emits a {@code WalletRejected}, while still letting a refusal flow through
 * {@code Result.err(...)} as a value: the application layer reads {@link #error()} for the message and
 * publishes {@link #event()} so the refusal is observed exactly once, the same way an applied change is.
 *
 * @param error the modelled cause, carrying the {@code MessageKey} to render
 * @param event the {@code WalletRejected} the aggregate raised for this refusal
 */
public record Rejection(EconomyError error, WalletRejected event) {

    public Rejection {
        Objects.requireNonNull(error, "error");
        Objects.requireNonNull(event, "event");
        if (error != event.reason()) {
            throw new IllegalArgumentException("rejection error and event reason must agree");
        }
    }
}
