package com.uxplima.uxmessentials.economy.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port deciding whether an owner is excluded from every {@code /baltop} leaderboard, admin float
 * accounts, NPC/shop banks, the server account, all holders of
 * {@code economy.baltop.exempt-permission} ({@code docs/11-economy-integration.md} §9.3). Exemption is
 * checked where the per-currency snapshot is built, not per render, so the hot {@code /baltop} path never
 * re-checks the node. Because it is permission-driven it survives a UUID change and needs no per-account
 * flag.
 */
public interface BaltopExemption {

    /** True when {@code owner} must not appear in any leaderboard. */
    boolean isExempt(PlayerRef owner);
}
