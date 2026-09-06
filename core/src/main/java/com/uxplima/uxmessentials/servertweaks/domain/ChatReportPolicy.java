package com.uxplima.uxmessentials.servertweaks.domain;

/**
 * The pure decision behind the no-chat-reports tweak: given whether an incoming public-chat message already arrived
 * unsigned, decide whether the server should re-deliver it as an unsigned message instead of relaying the player's
 * signed one. Re-delivering public chat unsigned is the whole of what a server can do here without a client mod: a
 * message with no cryptographic signature carries nothing Mojang's chat-reporting system can act on, so it cannot be
 * reported.
 *
 * <p>The policy is deliberately narrow. It asks for re-delivery only when the tweak is enabled <em>and</em> the message
 * was actually signed. An already-unsigned message (secure chat off, or a system-sourced message) needs no rework and
 * is left to flow normally. What it cannot decide, because the server cannot, is the client's own behaviour: a vanilla
 * client still computes a signature for every message it sends; the server can only choose not to propagate it. Pure
 * Java (no Bukkit, Paper, Kyori, or SLF4J) so the adapter's chat listener reuses this verbatim while the Bukkit calls
 * that read the signature and re-broadcast the line stay adapter-side.
 */
public final class ChatReportPolicy {

    private final boolean enabled;

    /**
     * @param enabled whether the tweak is active at all; when {@code false} nothing is ever re-delivered
     */
    public ChatReportPolicy(boolean enabled) {
        this.enabled = enabled;
    }

    /** Whether the no-chat-reports tweak is switched on. */
    public boolean enabled() {
        return enabled;
    }

    /**
     * Whether this chat message should be re-delivered as an unsigned message so it cannot be reported.
     *
     * @param alreadyUnsigned {@code true} when the message reached the server without a signature (nothing to strip)
     * @return {@code true} only when the tweak is enabled and the message actually carried a signature
     */
    public boolean shouldDeliverUnsigned(boolean alreadyUnsigned) {
        return enabled && !alreadyUnsigned;
    }
}
