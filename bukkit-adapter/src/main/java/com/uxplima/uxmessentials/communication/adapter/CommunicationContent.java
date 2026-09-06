package com.uxplima.uxmessentials.communication.adapter;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.communication.domain.AdvancementNoticeConfig;
import com.uxplima.uxmessentials.communication.domain.AnnouncerConfig;
import com.uxplima.uxmessentials.communication.domain.ChatFormatPolicy;
import com.uxplima.uxmessentials.communication.domain.DeathCausePolicies;
import com.uxplima.uxmessentials.communication.domain.InfoPage;
import com.uxplima.uxmessentials.communication.domain.JoinGroupPolicies;
import com.uxplima.uxmessentials.communication.domain.MessagePolicy;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.ChannelDisplay;
import org.jspecify.annotations.NullMarked;

/**
 * The immutable, parsed operator content of the module's {@code join-quit.conf}, {@code announcer.conf}, and
 * {@code info-pages.conf} siblings: the three connection-message policies (join, quit, death), the rotating
 * announcer config, the optional first-join welcome and after-death info-page name, and the info pages. It is a
 * snapshot. A reload parses a fresh {@code CommunicationContent} and the
 * adapter swaps it behind its {@code AtomicReference} holders, so the connection listeners, the announcer timer,
 * and the info commands always read whole, consistent content.
 *
 * <p>Everything here is operator-authored MiniMessage data rendered by the adapter, never a plugin
 * {@code MessageKey} and never parity-checked. The policy/schedule/page records are the pure domain types the use
 * cases consume directly.
 *
 * @param join the join channel's per-group policy table (a default policy plus any per-group overrides)
 * @param quit the quit channel's policy
 * @param death the death channel's per-cause policy table (a default policy plus any per-cause overrides)
 * @param announcer the rotating announcer config
 * @param announcerDisplay the title/boss-bar timing the announcer's non-chat channels render with
 * @param advancements the advancement-notification config (filtering, template, channels, sound)
 * @param firstJoin the welcome policy broadcast only on a player's first-ever join ({@code DEFAULT} = none)
 * @param motd the personal MOTD lines sent to a joining player after the join line (empty = none)
 * @param deathInfoPage the optional info-page name shown to a dying player
 * @param motdOnJoin whether the legacy {@code motd} info page is sent to a player when they join
 * @param infoPages the operator's info pages (one auto-registered command each)
 * @param chat the public-chat format policy the chat renderer drives
 */
@NullMarked
public record CommunicationContent(
        JoinGroupPolicies join,
        MessagePolicy quit,
        DeathCausePolicies death,
        AnnouncerConfig announcer,
        ChannelDisplay announcerDisplay,
        AdvancementNoticeConfig advancements,
        MessagePolicy firstJoin,
        List<String> motd,
        Optional<String> deathInfoPage,
        boolean motdOnJoin,
        List<InfoPage> infoPages,
        ChatFormatPolicy chat) {

    public CommunicationContent {
        Objects.requireNonNull(join, "join");
        Objects.requireNonNull(quit, "quit");
        Objects.requireNonNull(death, "death");
        Objects.requireNonNull(announcer, "announcer");
        Objects.requireNonNull(announcerDisplay, "announcerDisplay");
        Objects.requireNonNull(advancements, "advancements");
        Objects.requireNonNull(firstJoin, "firstJoin");
        motd = List.copyOf(Objects.requireNonNull(motd, "motd"));
        Objects.requireNonNull(deathInfoPage, "deathInfoPage");
        infoPages = List.copyOf(Objects.requireNonNull(infoPages, "infoPages"));
        Objects.requireNonNull(chat, "chat");
    }

    /**
     * Fully inert content: every channel defers to vanilla, the announcer is silent, no info pages. The MOTD-on-join
     * default is off here. Inert content is the "module disabled / files unreadable" shape, where nothing should
     * fire; the shipped {@code info-pages.conf} turns it on for a normal install.
     */
    public static CommunicationContent inert() {
        return new CommunicationContent(
                JoinGroupPolicies.ofDefault(MessagePolicy.vanilla()),
                MessagePolicy.vanilla(),
                DeathCausePolicies.ofDefault(MessagePolicy.vanilla()),
                AnnouncerConfig.empty(),
                CommunicationContentCodec.defaultDisplay(),
                AdvancementNoticeConfig.disabled(),
                MessagePolicy.vanilla(),
                List.of(),
                Optional.empty(),
                false,
                List.of(),
                ChatFormatPolicy.disabled());
    }
}
