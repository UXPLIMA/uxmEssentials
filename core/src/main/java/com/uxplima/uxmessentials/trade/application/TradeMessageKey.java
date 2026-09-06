package com.uxplima.uxmessentials.trade.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The trade context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code TRADE_REQUEST_SENT} ↔ {@code trade.request-sent}); the constant is the
 * compile-time handle, the catalog holds the text. There are no inline player-facing literals in the context, every
 * message resolves through one of these.
 *
 * <p>Per the i18n contract a disabled module still ships its keys so the catalog stays whole and the locale-parity
 * guard sees the full {@code en} key set. This covers the request/accept flow, the window lifecycle, the refusals,
 * and the trade window's own control text (its title and the confirm/partner-status items); the later phases (money,
 * blacklist, cross-server) add their own keys here as their verbs land.
 */
public enum TradeMessageKey implements MessageKey {

    // Request flow, /trade <player> and /trade accept|deny.
    TRADE_REQUEST_SENT("trade.request-sent"),
    TRADE_REQUEST_RECEIVED("trade.request-received"),
    TRADE_REQUEST_EXPIRED("trade.request-expired"),
    TRADE_NO_REQUEST("trade.no-request"),
    TRADE_ACCEPTED("trade.accepted"),
    TRADE_DENIED("trade.denied"),

    // Window lifecycle: the session opened, was aborted, or completed the swap.
    TRADE_CANCELLED("trade.cancelled"),
    TRADE_COMPLETED("trade.completed"),

    // Trade window, the dual-inventory GUI: its title and the confirm/status control items.
    TRADE_WINDOW_TITLE("trade.window.title"),
    TRADE_WINDOW_CONFIRM("trade.window.confirm"),
    TRADE_WINDOW_CONFIRMED("trade.window.confirmed"),
    TRADE_WINDOW_PARTNER_WAITING("trade.window.partner-waiting"),
    TRADE_WINDOW_PARTNER_CONFIRMED("trade.window.partner-confirmed"),

    // Trade window, the money button: the viewer's single "add money" button, its staked-amount line, the hint shown
    // when several currencies are allowed (right-click cycles the selected one), and the read-only display of the other
    // side's staked money.
    TRADE_WINDOW_MONEY("trade.window.money"),
    TRADE_WINDOW_MONEY_AMOUNT("trade.window.money-amount"),
    TRADE_WINDOW_MONEY_CYCLE("trade.window.money-cycle"),
    TRADE_WINDOW_PARTNER_MONEY("trade.window.partner-money"),

    // Trade window, the experience button: the viewer's "add experience" button, its staked-amount line, and the
    // read-only display of the other side's staked experience.
    TRADE_WINDOW_EXPERIENCE("trade.window.experience"),
    TRADE_WINDOW_EXPERIENCE_AMOUNT("trade.window.experience-amount"),
    TRADE_WINDOW_PARTNER_EXPERIENCE("trade.window.partner-experience"),

    // Money prompt, the amount capture and its rejections.
    TRADE_MONEY_PROMPT("trade.money-prompt"),
    TRADE_MONEY_INVALID("trade.money-invalid"),
    TRADE_INSUFFICIENT_FUNDS("trade.insufficient-funds"),

    // Experience prompt, the amount capture and its rejections.
    TRADE_EXPERIENCE_PROMPT("trade.experience-prompt"),
    TRADE_EXPERIENCE_INVALID("trade.experience-invalid"),

    // Refusals: the request or the open could not proceed.
    TRADE_ALREADY_TRADING("trade.already-trading"),
    TRADE_TARGET_BUSY("trade.target-busy"),
    TRADE_CANNOT_TRADE_SELF("trade.cannot-trade-self"),
    TRADE_TOO_FAR("trade.too-far"),
    TRADE_ON_COOLDOWN("trade.on-cooldown"),
    TRADE_ITEM_BLACKLISTED("trade.item-blacklisted"),
    TRADE_CROSS_SERVER_DISABLED("trade.cross-server-disabled"),

    // Cross-server trade: the bus rendezvous and the escrow-backed two-phase commit.
    TRADE_CROSS_SERVER_REQUEST_SENT("trade.cross-server-request-sent"),
    TRADE_CROSS_SERVER_INCOMING("trade.cross-server-incoming"),
    TRADE_CROSS_SERVER_ESCROWED("trade.cross-server-escrowed"),
    TRADE_CROSS_SERVER_COMPLETED("trade.cross-server-completed"),

    // The bare /trade root's usage line, shown to a sender who ran it without a target or subcommand.
    TRADE_USAGE("trade.usage");

    private final String key;

    TradeMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
