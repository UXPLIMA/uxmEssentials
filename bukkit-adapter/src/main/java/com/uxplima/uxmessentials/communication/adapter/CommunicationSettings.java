package com.uxplima.uxmessentials.communication.adapter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.communication.application.InfoRegistry;
import com.uxplima.uxmessentials.communication.domain.AdvancementNoticeConfig;
import com.uxplima.uxmessentials.communication.domain.AnnouncerConfig;
import com.uxplima.uxmessentials.communication.domain.ChatFormatPolicy;
import com.uxplima.uxmessentials.communication.domain.DeathCausePolicies;
import com.uxplima.uxmessentials.communication.domain.InfoPage;
import com.uxplima.uxmessentials.communication.domain.JoinGroupPolicies;
import com.uxplima.uxmessentials.communication.domain.MessagePolicy;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.ChannelDisplay;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * The communication context's operator content, loaded once at wiring time from the {@code join-quit.conf},
 * {@code announcer.conf}, {@code advancements.conf}, {@code info-pages.conf}, and {@code chat.conf} siblings under
 * {@code modules/communication/} and held in an
 * {@link AtomicReference} so a reload swaps a fresh {@link CommunicationContent} whole, readers see either the
 * previous or the new content, never a half-applied tree (CLAUDE.md "swapped atomically via AtomicReference on
 * reload"). The three files are merged at the root into one tree before the codec reads them, so the parsed model
 * is identical to the old single-file layout. An absent directory, an absent sibling, or an unreadable file yields
 * fully inert content for that part, so a server that enables the module without authoring the files gets the
 * do-nothing defaults: every channel defers to vanilla and the announcer is silent.
 *
 * <p>The policy and schedule suppliers handed to the use cases read the live content on each call, so a reload
 * takes effect on the next join/quit/death or announcer tick with no re-wiring. The info pages are operator
 * content rendered through MiniMessage by the adapter; nothing here is a {@code MessageKey} or parity-checked.
 */
@NullMarked
public final class CommunicationSettings {

    private static final List<String> CONTENT_FILES =
            List.of("join-quit.conf", "announcer.conf", "advancements.conf", "info-pages.conf", "chat.conf");

    /** The reserved info-page name shown on join when {@code motd-on-join} is on. */
    private static final String MOTD_PAGE = "motd";

    private final Path moduleDir;
    private final Logger log;
    private final AtomicReference<CommunicationContent> content;

    public CommunicationSettings(Path moduleDir, Logger log) {
        this.moduleDir = Objects.requireNonNull(moduleDir, "moduleDir");
        this.log = Objects.requireNonNull(log, "log");
        this.content = new AtomicReference<>(CommunicationContentCodec.read(mergedContent(moduleDir, log, false)));
    }

    /** The live join-channel per-group policy table; read fresh by {@code ResolveJoinMessage} each connection. */
    public JoinGroupPolicies joinPolicies() {
        return current().join();
    }

    /** The live default join-channel policy (the {@code join} block itself), sans its per-group overrides. */
    public MessagePolicy joinPolicy() {
        return current().join().defaultPolicy();
    }

    /** The live first-join welcome policy; read fresh by {@code ResolveJoinMessage} on a first-ever join. */
    public MessagePolicy firstJoinPolicy() {
        return current().firstJoin();
    }

    /** The live personal MOTD lines sent to a joining player; empty when no {@code motd} list is configured. */
    public List<String> motdLines() {
        return current().motd();
    }

    /** The live quit-channel policy. */
    public MessagePolicy quitPolicy() {
        return current().quit();
    }

    /** The live death-channel default policy (the {@code death} block itself), sans its per-cause overrides. */
    public MessagePolicy deathPolicy() {
        return current().death().defaultPolicy();
    }

    /** The live death-channel per-cause policy table; read fresh by {@code ResolveDeathMessage} each death. */
    public DeathCausePolicies deathCausePolicies() {
        return current().death();
    }

    /** The live announcer config; read fresh by the announcer timer each tick. */
    public AnnouncerConfig announcerConfig() {
        return current().announcer();
    }

    /** The announcer's title/boss-bar display timing; read once at wiring time to build the channel broadcaster. */
    public ChannelDisplay announcerDisplay() {
        return current().announcerDisplay();
    }

    /** The live advancement-notification config; read fresh by the advancement listener on each earned advancement. */
    public AdvancementNoticeConfig advancementNotices() {
        return current().advancements();
    }

    /** The live public-chat format policy; read fresh by the chat renderer on each message. */
    public ChatFormatPolicy chatFormatPolicy() {
        return current().chat();
    }

    /** The optional info-page name shown to a dying player when send-info-after-death is configured. */
    public Optional<String> deathInfoPage() {
        return current().deathInfoPage();
    }

    /**
     * Whether a joining player is sent the legacy {@code motd} info page; on by default, gated by {@code motd-on-join}.
     * This is the fallback the join listener uses only when no dedicated {@link #motdLines()} list is configured.
     */
    public boolean motdOnJoin() {
        return current().motdOnJoin();
    }

    /** The live {@code motd} info page, or empty when no {@code motd} entry is configured. */
    public Optional<InfoPage> motdPage() {
        return InfoRegistry.of(current().infoPages()).find(MOTD_PAGE);
    }

    /** A fresh {@link InfoRegistry} over the live info pages; the dynamic info commands are built from this. */
    public InfoRegistry infoRegistry() {
        return InfoRegistry.of(current().infoPages());
    }

    /** Re-read the content sibling files and swap the parsed content atomically. */
    public void reload() {
        content.set(CommunicationContentCodec.read(mergedContent(moduleDir, log, true)));
    }

    private CommunicationContent current() {
        return Objects.requireNonNull(content.get(), "content");
    }

    private static ConfigurationNode mergedContent(Path moduleDir, Logger log, boolean failOnReadError) {
        ConfigurationNode merged = CommentedConfigurationNode.root();
        for (String name : CONTENT_FILES) {
            mergeFile(merged, moduleDir.resolve(name), log, failOnReadError);
        }
        return merged;
    }

    private static void mergeFile(ConfigurationNode target, Path file, Logger log, boolean failOnReadError) {
        if (!Files.exists(file)) {
            return;
        }
        try {
            target.mergeFrom(
                    HoconConfigurationLoader.builder().path(file).build().load());
        } catch (ConfigurateException failure) {
            log.error("failed to load " + file + "; communication runs inert", failure);
            if (failOnReadError) {
                throw new IllegalStateException("failed to parse communication config " + file, failure);
            }
        }
    }
}
