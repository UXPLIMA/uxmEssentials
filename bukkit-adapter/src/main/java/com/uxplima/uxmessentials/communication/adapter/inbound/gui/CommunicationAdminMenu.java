package com.uxplima.uxmessentials.communication.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.communication.adapter.ChatLock;
import com.uxplima.uxmessentials.communication.adapter.outbound.BukkitAnnouncerBroadcaster;
import com.uxplima.uxmessentials.communication.application.CommunicationMessageKey;
import com.uxplima.uxmessentials.communication.domain.Announcement;
import com.uxplima.uxmessentials.communication.domain.AnnouncerConfig;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Registers the communication admin panel ({@code /communication gui}, and the communication entry on the
 * {@code /uxmess gui} hub) with the menu engine and opens it. The panel is the same four-button surface the
 * commands expose: the chat-lock toggle flips the live {@link ChatLock} the {@code /togglechat} command flips,
 * the clearchat button runs the {@code /clearchat} fan-out behind a confirm, the broadcast button captures a line
 * through the shared {@link TextInput} prompt and sends it through the same {@link BukkitAnnouncerBroadcaster} as
 * {@code /broadcast}, and the announcer button opens the read-only announcer list (the {@code /announce list}
 * surface). Two specs cover the two windows: {@code communication-admin} draws the panel and
 * {@code communication-announcer} the read-only list.
 *
 * <p>The menu holds no domain logic of its own: every action routes through a surface the commands already use.
 * Every visible string is a {@link CommunicationMessageKey}; the clearchat fan-out runs on the kernel
 * {@link Scheduler}, and the broadcast prompt's callbacks are hopped onto the viewer's region thread by the
 * {@link TextInput} seam, exactly as the original view did.
 */
@NullMarked
public final class CommunicationAdminMenu {

    /** The engine spec ids the panel and its read-only announcer list register and open under. */
    public static final String PANEL_SPEC_ID = "communication-admin";

    public static final String ANNOUNCER_SPEC_ID = "communication-announcer";

    private static final String PANEL_RESOURCE = "modules/communication/gui/communication-admin.conf";
    private static final String ANNOUNCER_RESOURCE = "modules/communication/gui/communication-announcer.conf";

    private static final String CLEARCHAT_EXEMPT = "uxmessentials.communication.clearchat.exempt";
    private static final int CLEARCHAT_BLANK_LINES = 100;
    private static final String BROADCAST_INPUT_KEY = "communication.broadcast";

    private final Menus menus;
    private final GuiText guiText;
    private final Scheduler scheduler;
    private final Messages messages;
    private final ChatLock chatLock;
    private final BukkitAnnouncerBroadcaster broadcaster;
    private final String broadcastPrefix;
    private final Supplier<AnnouncerConfig> announcerConfig;
    private final Notifier notifier;
    private final MessageSink sink;
    private final TextInput textInput;

    public CommunicationAdminMenu(
            Menus menus,
            GuiText guiText,
            Scheduler scheduler,
            Messages messages,
            ChatLock chatLock,
            BukkitAnnouncerBroadcaster broadcaster,
            String broadcastPrefix,
            Supplier<AnnouncerConfig> announcerConfig,
            Notifier notifier,
            MessageSink sink,
            TextInput textInput) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.chatLock = Objects.requireNonNull(chatLock, "chatLock");
        this.broadcaster = Objects.requireNonNull(broadcaster, "broadcaster");
        this.broadcastPrefix = Objects.requireNonNull(broadcastPrefix, "broadcastPrefix");
        this.announcerConfig = Objects.requireNonNull(announcerConfig, "announcerConfig");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
    }

    /** Register the bindings the two specs name and the specs themselves; called once at communication wiring time. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.placeholder("communication_lock_state", this::lockStateLore);
        bindings.action("communication:chat-lock", this::toggleChatLock);
        bindings.action("communication:clearchat", this::confirmClearChat);
        bindings.action("communication:broadcast", this::promptBroadcast);
        bindings.action("communication:open-announcer", this::openAnnouncer);
        bindings.action("communication:close", ctx -> ctx.player().closeInventory());
        bindings.list("communication:announcements", this::announcements);
        bindings.placeholder(
                "announcement_id", ctx -> ctx.entry(Announcement.class).id());
        bindings.placeholder("announcement_lines", this::announcementLines);
        bindings.placeholder("announcement_channels", this::announcementChannels);
        menus.registerSpec(PANEL_SPEC_ID, MenuSpecs.loadOrBundled(PANEL_RESOURCE, dataFolder, 3, log));
        menus.registerSpec(ANNOUNCER_SPEC_ID, MenuSpecs.loadOrBundled(ANNOUNCER_RESOURCE, dataFolder, 3, log));
    }

    /** Open the admin panel for {@code viewer} (the live player resolved by the engine). */
    public void open(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        menus.open(viewer, PANEL_SPEC_ID, null);
    }

    /** The chat-lock value lore: the locked/unlocked word, redrawn in place after each flip. */
    private String lockStateLore(MenuContext ctx) {
        return messages.resolve(
                ctx.viewer(),
                chatLock.isLocked()
                        ? CommunicationMessageKey.GUI_VALUE_LOCKED
                        : CommunicationMessageKey.GUI_VALUE_UNLOCKED,
                Map.of());
    }

    /** Flip the live {@link ChatLock} the {@code /togglechat} command flips; the panel re-renders the new state. */
    private void toggleChatLock(MenuActionContext ctx) {
        chatLock.toggle();
    }

    /** Confirm-gate the {@code /clearchat} fan-out; cancel reopens the panel. */
    private void confirmClearChat(MenuActionContext ctx) {
        Player player = ctx.player();
        PlayerRef viewer = ctx.viewer();
        Component title = guiText.text(viewer, CommunicationMessageKey.GUI_CLEARCHAT_CONFIRM);
        menus.confirm(viewer, title, () -> doClearChat(player), () -> open(viewer));
    }

    /**
     * Flush online players' chat exactly as {@code /clearchat} does. A screenful of blank lines (skipping the
     * exempt) through the {@link MessageSink}, then the cleared/by notices through the {@link Notifier},
     * each hopping to the viewer's region thread inside the sink. Package-private so the golden test can drive it.
     */
    void doClearChat(Player actor) {
        scheduler.onGlobal(() -> {
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (!viewer.hasPermission(CLEARCHAT_EXEMPT)) {
                    PlayerRef ref = BukkitRefs.toRef(viewer);
                    for (int line = 0; line < CLEARCHAT_BLANK_LINES; line++) {
                        sink.deliver(ref, "");
                    }
                    notifier.send(ref, CommunicationMessageKey.CLEARCHAT_CLEARED);
                }
            }
            notifier.send(
                    BukkitRefs.toRef(actor), CommunicationMessageKey.CLEARCHAT_BY, Map.of("player", actor.getName()));
        });
    }

    /** Capture a broadcast line in chat through the shared text-input seam, then reopen the panel. */
    private void promptBroadcast(MenuActionContext ctx) {
        Player player = ctx.player();
        PlayerRef viewer = ctx.viewer();
        player.closeInventory();
        textInput.prompt(
                player,
                viewer,
                InputRequest.of(BROADCAST_INPUT_KEY, CommunicationMessageKey.GUI_BROADCAST_PROMPT),
                this::submitBroadcast,
                () -> open(viewer));
    }

    /**
     * Send the typed line through the broadcaster (mirroring {@code /broadcast}); a blank line is a no-op.
     * Package-private so the golden test can drive the broadcast seam without opening a live prompt.
     */
    void submitBroadcast(String raw) {
        Objects.requireNonNull(raw, "raw");
        if (!raw.isBlank()) {
            broadcaster.broadcast(broadcastPrefix + raw.strip());
        }
    }

    /** Open the read-only announcer list (the {@code /announce list} surface). */
    private void openAnnouncer(MenuActionContext ctx) {
        menus.open(ctx.viewer(), ANNOUNCER_SPEC_ID, null);
    }

    /** The announcement list, read fresh from the merged config each open (a config read, no Bukkit API). */
    private List<Announcement> announcements(MenuContext ctx) {
        return announcerConfig.get().announcements();
    }

    private String announcementLines(MenuContext ctx) {
        return Integer.toString(ctx.entry(Announcement.class).lines().size());
    }

    private String announcementChannels(MenuContext ctx) {
        return channels(ctx.entry(Announcement.class));
    }

    private static String channels(Announcement announcement) {
        return announcement.channels().stream()
                .map(Enum::name)
                .sorted()
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }
}
