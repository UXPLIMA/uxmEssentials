package com.uxplima.uxmessentials.vote.adapter.outbound;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.vote.application.port.VoteRanking;
import com.uxplima.uxmessentials.vote.domain.event.VotePartyTriggered;
import com.uxplima.uxmessentials.vote.domain.event.VoteReceived;
import com.uxplima.uxmlib.discord.DiscordEmbed;
import com.uxplima.uxmlib.discord.DiscordWebhook;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Posts vote notifications to a Discord channel through an incoming-webhook URL, reusing uxmLib's
 * {@link DiscordWebhook}. As a {@link Consumer} on the in-process domain-event bus it turns a
 * {@link VoteReceived} into a per-vote embed and a {@link VotePartyTriggered} into a per-party embed; the
 * scheduled top-voter leaderboard is posted separately via {@link #postTopVoter(List, Function)}.
 *
 * <p><b>A webhook failure never breaks vote handling.</b> Two layers guarantee this. First, a malformed
 * URL is caught in the constructor: the notifier marks itself disabled, logs once, and {@link #accept}
 * becomes a no-op: it never throws. Second, every send is fire-and-forget: the {@link DiscordWebhook}
 * future is never awaited (no {@code .get()} on the vote thread), and a {@code whenComplete} callback logs
 * a delivery failure at debug rather than letting it propagate, so a network error or a non-2xx status is
 * swallowed after the vote has already been credited.
 *
 * <p>The embed-construction helpers ({@link #buildVoteEmbed}, {@link #buildPartyEmbed},
 * {@link #buildTopVoterEmbed}) are pure static functions so the embed shape is unit-testable without any
 * HTTP.
 */
@NullMarked
public final class VoteDiscordNotifier implements Consumer<DomainEvent> {

    // The Discord brand "blurple", used as the embed colour bar so the notifications read as a set.
    private static final int EMBED_COLOR = 0x5865F2;

    private final @Nullable DiscordWebhook webhook;
    private final VoteDiscordSettings settings;
    private final Logger log;

    /**
     * Build a notifier for {@code settings}. A malformed {@link VoteDiscordSettings#webhookUrl()} does not
     * throw: the notifier is constructed disabled (the webhook is null), the failure is logged once without
     * the secret URL, and every later send is skipped.
     */
    public VoteDiscordNotifier(VoteDiscordSettings settings, Logger log) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.log = Objects.requireNonNull(log, "log");
        this.webhook = createWebhook(settings, log);
    }

    private static @Nullable DiscordWebhook createWebhook(VoteDiscordSettings settings, Logger log) {
        if (!settings.enabled()) {
            return null;
        }
        try {
            return new DiscordWebhook(settings.webhookUrl());
        } catch (IllegalArgumentException malformed) {
            // Never log the URL itself: it is a secret. Report only that Discord output is disabled.
            log.warn("Vote Discord webhook URL is malformed; Discord vote notifications are disabled.");
            return null;
        }
    }

    /** Whether this notifier will actually post: false when disabled or the URL was malformed. */
    public boolean active() {
        return webhook != null;
    }

    @Override
    public void accept(DomainEvent event) {
        Objects.requireNonNull(event, "event");
        if (webhook == null) {
            return;
        }
        if (event instanceof VoteReceived received && settings.voteEnabled()) {
            send(buildVoteEmbed(settings.voteTemplate(), received), "vote");
        } else if (event instanceof VotePartyTriggered party && settings.partyEnabled()) {
            send(buildPartyEmbed(settings.partyTemplate(), party), "party");
        }
    }

    /**
     * Post the scheduled top-voter leaderboard embed. A no-op when disabled or when {@code rankings} is
     * empty (an empty leaderboard is not worth a Discord message). {@code nameResolver} maps each ranked
     * player's UUID to a display name.
     */
    public void postTopVoter(List<VoteRanking> rankings, Function<UUID, String> nameResolver) {
        Objects.requireNonNull(rankings, "rankings");
        Objects.requireNonNull(nameResolver, "nameResolver");
        if (webhook == null || rankings.isEmpty()) {
            return;
        }
        send(buildTopVoterEmbed(settings.topVoter().title(), rankings, nameResolver), "top-voter");
    }

    /**
     * Fire-and-forget the embed: the future is never awaited, so the vote thread is never blocked, and a
     * delivery failure (a thrown stage error or a non-2xx status) is logged at debug and swallowed rather
     * than propagated back into vote handling.
     */
    private void send(DiscordEmbed embed, String kind) {
        DiscordWebhook target = Objects.requireNonNull(webhook, "webhook");
        // Fire-and-forget: the future is intentionally not awaited so the vote thread never blocks. The
        // whenComplete callback logs a delivery failure rather than letting it escape into vote handling.
        var ignored = target.sendEmbed(embed).whenComplete((status, error) -> {
            if (error != null) {
                log.debug("Discord {} notification failed to send: {}", kind, error.toString());
            } else if (status == null || status < 200 || status >= 300) {
                log.debug("Discord {} notification was not delivered (HTTP status {})", kind, status);
            }
        });
    }

    /** Build the per-vote embed. Pure: {@code {player}} and {@code {service}} are substituted in. */
    public static DiscordEmbed buildVoteEmbed(String template, VoteReceived event) {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(event, "event");
        String description = template.replace("{player}", event.voter().name()).replace("{service}", event.service());
        return DiscordEmbed.colored("New Vote", description, EMBED_COLOR);
    }

    /** Build the per-party embed. Pure: {@code {threshold}} is substituted in. */
    public static DiscordEmbed buildPartyEmbed(String template, VotePartyTriggered event) {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(event, "event");
        String description = template.replace("{threshold}", Integer.toString(event.threshold()));
        return DiscordEmbed.colored("Vote Party", description, EMBED_COLOR);
    }

    /**
     * Build the top-voter leaderboard embed. Pure: one field per ranked player ("#1 Name" / vote count),
     * with names resolved through {@code nameResolver}. The caller is responsible for passing a non-empty
     * ranking list.
     */
    public static DiscordEmbed buildTopVoterEmbed(
            String title, List<VoteRanking> rankings, Function<UUID, String> nameResolver) {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(rankings, "rankings");
        Objects.requireNonNull(nameResolver, "nameResolver");
        DiscordEmbed.Builder builder = DiscordEmbed.builder().title(title).color(EMBED_COLOR);
        for (int i = 0; i < rankings.size(); i++) {
            VoteRanking row = rankings.get(i);
            String name = nameResolver.apply(row.player().uuid());
            builder.field("#" + (i + 1) + " " + name, Long.toString(row.votes()), true);
        }
        return builder.build();
    }
}
