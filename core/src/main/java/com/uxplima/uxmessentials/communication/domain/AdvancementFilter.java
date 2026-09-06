package com.uxplima.uxmessentials.communication.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * The pure decision logic for the advancement-notification feature: given an {@link AdvancementNoticeConfig} and the
 * facts about a single earned advancement, decide whether the server announces it and which template renders it. It
 * holds no state and touches no Bukkit type. The adapter extracts the advancement key, its recipe-ness, and whether
 * the vanilla display marks it announce-to-chat, then defers every yes/no decision here.
 *
 * <p>The advancement is identified by its namespaced key as a string (for example {@code minecraft:end/kill_dragon}).
 * The key is lowercased ({@link Locale#ROOT}) before any comparison, matching the lowercase normalization
 * {@link AdvancementNoticeConfig} applies to the operator's allow/deny entries and per-advancement keys, so matching
 * is case-insensitive on both sides.
 */
public final class AdvancementFilter {

    private AdvancementFilter() {}

    /**
     * Whether the advancement described by {@code key}/{@code isRecipe}/{@code announceable} should be announced under
     * {@code cfg}. The gates are applied in order: the feature must be enabled; the key must not be deny-listed; when
     * the allow-list is non-empty the key must appear in it; recipe unlocks are dropped when
     * {@link AdvancementNoticeConfig#excludeRecipes()} is set; non-announceable advancements are dropped when
     * {@link AdvancementNoticeConfig#onlyAnnounceable()} is set. An advancement passing every gate is announced.
     *
     * @param cfg the active configuration
     * @param key the advancement's namespaced key (compared case-insensitively)
     * @param isRecipe whether the advancement is a recipe-unlock advancement
     * @param announceable whether the vanilla display marks the advancement as announce-to-chat
     */
    public static boolean shouldAnnounce(
            AdvancementNoticeConfig cfg, String key, boolean isRecipe, boolean announceable) {
        Objects.requireNonNull(cfg, "cfg");
        Objects.requireNonNull(key, "key");
        String normalized = key.toLowerCase(Locale.ROOT);
        if (!cfg.enabled()) {
            return false;
        }
        if (cfg.deny().contains(normalized)) {
            return false;
        }
        if (!cfg.allow().isEmpty() && !cfg.allow().contains(normalized)) {
            return false;
        }
        if (cfg.excludeRecipes() && isRecipe) {
            return false;
        }
        if (cfg.onlyAnnounceable() && !announceable) {
            return false;
        }
        return true;
    }

    /**
     * The template that renders the advancement {@code key}: its per-advancement override when one is configured,
     * otherwise the global {@link AdvancementNoticeConfig#template()}. The key is lowercased before lookup so an
     * override registered under any case is found.
     */
    public static String templateFor(AdvancementNoticeConfig cfg, String key) {
        Objects.requireNonNull(cfg, "cfg");
        Objects.requireNonNull(key, "key");
        return cfg.perAdvancementTemplates().getOrDefault(key.toLowerCase(Locale.ROOT), cfg.template());
    }
}
