package com.uxplima.uxmessentials.communication.adapter.inbound.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.communication.adapter.ChatLock;
import com.uxplima.uxmessentials.communication.adapter.CommunicationSettings;
import com.uxplima.uxmessentials.communication.adapter.inbound.gui.AnnouncementEditorView;
import com.uxplima.uxmessentials.communication.adapter.outbound.AnnouncerTask;
import com.uxplima.uxmessentials.communication.adapter.outbound.BukkitAnnouncerBroadcaster;
import com.uxplima.uxmessentials.communication.adapter.outbound.BukkitInfoSender;
import com.uxplima.uxmessentials.communication.application.BroadcastOptOut;
import com.uxplima.uxmessentials.communication.application.InfoRegistry;
import com.uxplima.uxmessentials.communication.domain.AnnouncerConfig;
import com.uxplima.uxmessentials.communication.domain.InfoPage;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the communication context's Brigadier command surface. The static half is the plugin's own
 * {@code /broadcast} (an operator one-off announcement), {@code /broadcasttoggle} (a per-player opt-out), and
 * {@code /announce} (the rotating-announcer admin surface. Reload / list / preview / toggle); the dynamic half is
 * one {@link InfoPageCommand} per {@link InfoPage} in the config-derived {@link InfoRegistry} ({@code /rules},
 * {@code /motd}, {@code /info}, any custom page). The static literals are greppable so the permissions reference can
 * check them; the dynamic literals are operator data, each guarded by the fixed
 * {@code uxmessentials.communication.info.<name>} node shape.
 */
@NullMarked
public final class CommunicationCommands {

    /**
     * Prefix prepended to a manual {@code /broadcast} body so it reads like an announcement, not plain chat; this
     * is operator-facing MiniMessage content, not a parity-checked MessageKey. Public so the admin GUI's broadcast
     * button sends through the same prefix as the command.
     */
    public static final String BROADCAST_PREFIX = "<gray>[</gray><gold>Broadcast</gold><gray>]</gray> <white>";

    private CommunicationCommands() {}

    /**
     * Every communication command: the static {@code /broadcast} and {@code /broadcasttoggle} plus one per
     * configured info page.
     */
    public static List<CommandRegistration> all(
            BroadcastOptOut optOut,
            InfoRegistry registry,
            BukkitInfoSender infoSender,
            Notifier notifier,
            Messages messages,
            BukkitAnnouncerBroadcaster broadcaster,
            AnnouncerTask announcer,
            Supplier<AnnouncerConfig> mergedConfig,
            Scheduler scheduler,
            MessageSink sink,
            ChatLock chatLock,
            CommunicationSettings settings,
            AnnouncementEditorView editorView) {
        Objects.requireNonNull(optOut, "optOut");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(infoSender, "infoSender");
        Objects.requireNonNull(notifier, "notifier");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(broadcaster, "broadcaster");
        Objects.requireNonNull(announcer, "announcer");
        Objects.requireNonNull(mergedConfig, "mergedConfig");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(sink, "sink");
        Objects.requireNonNull(chatLock, "chatLock");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(editorView, "editorView");
        List<CommandRegistration> commands = new ArrayList<>();
        commands.add(new BroadcastCommand(broadcaster, BROADCAST_PREFIX));
        commands.add(new BroadcastWorldCommand(messages, broadcaster, BROADCAST_PREFIX));
        commands.add(new BroadcastToggleCommand(optOut, messages));
        commands.add(new AnnounceCommand(
                settings, mergedConfig, broadcaster, optOut, announcer, editorView, scheduler, messages));
        commands.add(new MeCommand(messages, notifier, scheduler));
        commands.add(new ClearChatCommand(messages, notifier, sink, scheduler));
        commands.add(new ToggleChatCommand(chatLock, notifier, messages));
        for (InfoPage page : registry.all()) {
            commands.add(new InfoPageCommand(page.command(), registry, infoSender, notifier, messages));
        }
        return List.copyOf(commands);
    }
}
