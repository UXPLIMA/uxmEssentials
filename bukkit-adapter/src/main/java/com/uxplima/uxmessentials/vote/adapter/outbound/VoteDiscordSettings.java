package com.uxplima.uxmessentials.vote.adapter.outbound;

import java.util.Locale;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.vote.domain.VotePeriod;
import org.jspecify.annotations.NullMarked;

/**
 * The parsed {@code discord { ... }} block of {@code modules/vote/config.conf}: whether Discord output is
 * wired at all, the webhook URL it posts to, and the per-feature toggles and message templates for the
 * per-vote, per-party, and scheduled top-voter embeds.
 *
 * <p>{@link #enabled()} is derived: it is {@code true} only when {@code webhookUrl} is non-blank. An empty
 * URL therefore disables every piece of Discord output, nothing is subscribed and no task is scheduled,
 * which is the default, so an installed-but-unconfigured server pays zero overhead and leaks no secret.
 *
 * @param enabled whether Discord output is active (derived: the webhook URL is non-blank)
 * @param webhookUrl the channel's incoming-webhook URL (a secret: never logged)
 * @param voteEnabled whether a per-vote embed is posted
 * @param voteTemplate the per-vote embed description, with {@code {player}} and {@code {service}} tokens
 * @param partyEnabled whether a per-party embed is posted
 * @param partyTemplate the per-party embed description, with a {@code {threshold}} token
 * @param topVoter the scheduled top-voter leaderboard settings
 */
@NullMarked
public record VoteDiscordSettings(
        boolean enabled,
        String webhookUrl,
        boolean voteEnabled,
        String voteTemplate,
        boolean partyEnabled,
        String partyTemplate,
        TopVoter topVoter) {

    public VoteDiscordSettings {
        Objects.requireNonNull(webhookUrl, "webhookUrl");
        Objects.requireNonNull(voteTemplate, "voteTemplate");
        Objects.requireNonNull(partyTemplate, "partyTemplate");
        Objects.requireNonNull(topVoter, "topVoter");
    }

    /**
     * The scheduled top-voter leaderboard: whether it runs, the period and row count it queries, how often
     * it posts, and the embed title.
     *
     * @param enabled whether the recurring top-voter embed is scheduled
     * @param period the leaderboard window queried from the repository
     * @param limit how many ranked rows the embed lists (at least one)
     * @param intervalMinutes how often the embed is posted, in minutes (at least one)
     * @param title the embed title
     */
    public record TopVoter(boolean enabled, VotePeriod period, int limit, long intervalMinutes, String title) {

        public TopVoter {
            Objects.requireNonNull(period, "period");
            Objects.requireNonNull(title, "title");
            if (limit < 1) {
                throw new IllegalArgumentException("limit must be at least one: " + limit);
            }
            if (intervalMinutes < 1) {
                throw new IllegalArgumentException("intervalMinutes must be at least one: " + intervalMinutes);
            }
        }
    }

    /**
     * Parse the {@code discord} block from the module-scoped config. A blank {@code webhook-url} (the
     * default) yields a disabled settings object; every other field is tolerant and falls back to its
     * documented default. The top-voter {@code period} is parsed case-insensitively and falls back to
     * {@link VotePeriod#MONTHLY} on an unknown value; {@code limit} and {@code interval-minutes} are clamped
     * to at least one so the nested record never rejects a sloppy config.
     */
    public static VoteDiscordSettings fromConfig(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        String webhookUrl = config.getString("discord.webhook-url", "").strip();
        boolean enabled = !webhookUrl.isBlank();
        boolean voteEnabled = config.getBoolean("discord.vote.enabled", true);
        String voteTemplate = config.getString("discord.vote.template", "{player} just voted on {service}!");
        boolean partyEnabled = config.getBoolean("discord.party.enabled", true);
        String partyTemplate = config.getString("discord.party.template", "The vote party fired at {threshold} votes!");
        TopVoter topVoter = topVoterFrom(config);
        return new VoteDiscordSettings(
                enabled, webhookUrl, voteEnabled, voteTemplate, partyEnabled, partyTemplate, topVoter);
    }

    private static TopVoter topVoterFrom(ConfigStore config) {
        boolean enabled = config.getBoolean("discord.top-voter.enabled", false);
        VotePeriod period = parsePeriod(config.getString("discord.top-voter.period", "MONTHLY"));
        int limit = Math.max(1, config.getInt("discord.top-voter.limit", 10));
        long intervalMinutes = Math.max(1L, config.getLong("discord.top-voter.interval-minutes", 1440L));
        String title = config.getString("discord.top-voter.title", "Top Voters");
        return new TopVoter(enabled, period, limit, intervalMinutes, title);
    }

    private static VotePeriod parsePeriod(String raw) {
        try {
            return VotePeriod.valueOf(raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return VotePeriod.MONTHLY;
        }
    }
}
