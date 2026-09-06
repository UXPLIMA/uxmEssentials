package com.uxplima.uxmessentials.communication.adapter.inbound.command;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.communication.adapter.CommunicationSettings;
import com.uxplima.uxmessentials.communication.adapter.inbound.gui.AnnouncementEditorView;
import com.uxplima.uxmessentials.communication.adapter.outbound.AnnouncerTask;
import com.uxplima.uxmessentials.communication.adapter.outbound.BukkitAnnouncerBroadcaster;
import com.uxplima.uxmessentials.communication.application.BroadcastOptOut;
import com.uxplima.uxmessentials.communication.application.CommunicationMessageKey;
import com.uxplima.uxmessentials.communication.domain.Announcement;
import com.uxplima.uxmessentials.communication.domain.AnnouncerConfig;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.display.BroadcastChannel;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /announce} ({@code uxmessentials.announce.admin}, default op): the operator surface over the rotating
 * announcer. Bare {@code /announce} (and {@code /announce editor}) opens the DB-backed announcement editor, a GUI
 * to create, edit, enable/disable, world/permission-target, and delete announcements; the other subcommands stay:
 *
 * <ul>
 *   <li>{@code editor} (and the bare root via {@link #guiRoot()}), open the {@link AnnouncementEditorView}: a list
 *       of the store announcements with a create button, each opening a per-announcement property editor.
 *   <li>{@code reload}, re-read {@code announcer.conf} and swap the live config in, then re-arm the
 *       per-announcement override loops so a newly-added override fires (it is excluded from the shared rotation).
 *       The re-read is HOCON file I/O, so it runs off-tick on the {@code Scheduler} and the confirmation
 *       the count of announcements through {@link CommunicationMessageKey#ANNOUNCER_RELOADED}, bridges back to the
 *       global region for delivery, mirroring {@code /uxmess}'s off-tick reload commands.
 *   <li>{@code list}: list the announcement ids and the channels each pushes to. The set listed is the same merged
 *       set the announcer rotates: the file {@code announcer.conf} announcements plus the enabled editor-managed
 *       store announcements, so an announcement created in the GUI appears here too.
 *   <li>{@code preview <id>}. Show that announcement to the invoking player alone, bypassing the opt-out and
 *       condition gates; the id is resolved against the same merged set, so a GUI-created id previews. An unknown id
 *       answers with {@link CommunicationMessageKey#ANNOUNCE_PREVIEW_UNKNOWN}.
 *   <li>{@code toggle}. Flip the invoking player's broadcast opt-out, an alias for {@code /broadcasttoggle} so the
 *       opt-out lives under one verb too; it reuses the same {@link BroadcastOptOut} use case.
 * </ul>
 *
 * <p>The admin subcommands accept the console; {@code editor}, {@code preview}, and {@code toggle} act on the
 * invoking player and reject a console source. The announcement ids, channels, and lines are operator content; only
 * the framing (the editor's labels, reload confirmation, list header/entry/empty, unknown-id error) is a
 * parity-checked {@code MessageKey}.
 */
@NullMarked
public final class AnnounceCommand extends CommunicationCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.announce.admin";

    /**
     * The per-verb nodes under {@link #PERMISSION}. Authoring an announcement, re-reading the file, and reading the
     * rotation are three different acts: a build team may be trusted with the editor while only an administrator
     * reloads, and the opt-out is a personal switch that happens to live under this root. Each defaults to allowed,
     * so an existing {@code uxmessentials.announce.admin} grant is unchanged and an operator narrows by negating
     * one node.
     */
    private static final String EDITOR = PERMISSION + ".editor";

    private static final String RELOAD = PERMISSION + ".reload";

    private static final String LIST = PERMISSION + ".list";

    private static final String PREVIEW = PERMISSION + ".preview";

    private static final String TOGGLE = PERMISSION + ".toggle";

    private final CommunicationSettings settings;
    private final Supplier<AnnouncerConfig> mergedConfig;
    private final BukkitAnnouncerBroadcaster broadcaster;
    private final BroadcastOptOut optOut;
    private final AnnouncerTask announcer;
    private final AnnouncementEditorView editorView;
    private final Scheduler scheduler;

    public AnnounceCommand(
            CommunicationSettings settings,
            Supplier<AnnouncerConfig> mergedConfig,
            BukkitAnnouncerBroadcaster broadcaster,
            BroadcastOptOut optOut,
            AnnouncerTask announcer,
            AnnouncementEditorView editorView,
            Scheduler scheduler,
            Messages messages) {
        super(messages);
        this.settings = Objects.requireNonNull(settings, "settings");
        this.mergedConfig = Objects.requireNonNull(mergedConfig, "mergedConfig");
        this.broadcaster = Objects.requireNonNull(broadcaster, "broadcaster");
        this.optOut = Objects.requireNonNull(optOut, "optOut");
        this.announcer = Objects.requireNonNull(announcer, "announcer");
        this.editorView = Objects.requireNonNull(editorView, "editorView");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("announce")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.literal("editor")
                        .requires(src -> src.getSender().hasPermission(EDITOR))
                        .executes(this::openEditor))
                .then(Commands.literal("reload")
                        .requires(src -> src.getSender().hasPermission(RELOAD))
                        .executes(this::reload))
                .then(Commands.literal("list")
                        .requires(src -> src.getSender().hasPermission(LIST))
                        .executes(this::list))
                .then(Commands.literal("preview")
                        .requires(src -> src.getSender().hasPermission(PREVIEW))
                        .then(Commands.argument("id", StringArgumentType.word()).executes(this::preview)))
                .then(Commands.literal("toggle")
                        .requires(src -> src.getSender().hasPermission(TOGGLE))
                        .executes(this::toggle))
                .build();
    }

    @Override
    public String description() {
        return "Manage the rotating server announcer.";
    }

    /**
     * Bare {@code /announce} opens the editor GUI: the same screen {@code /announce editor} opens. The
     * {@code GuiRootBinding} installs this on the root when the command's catalog {@code gui} flag is on (the
     * untouched default), so an operator who has not turned the flag off types {@code /announce} and lands in the
     * editor; with it off the root falls through to the usage text instead.
     */
    @Override
    public Optional<Command<CommandSourceStack>> guiRoot() {
        return Optional.of(this::openEditor);
    }

    private int openEditor(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        editorView.open(sender, BukkitRefs.toRef(sender));
        return Command.SINGLE_SUCCESS;
    }

    private int reload(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        // Re-reading the three HOCON files is blocking I/O, so it runs off the tick thread; re-arming the override
        // loops then picks up any announcement newly given an interval-seconds override (otherwise it would be
        // excluded from the rotation with no loop of its own and silently never broadcast). The confirmation hops
        // back to the global region for delivery, like /uxmess's off-tick reload commands.
        scheduler.async(() -> {
            settings.reload();
            announcer.rearmOverrides();
            int count = settings.announcerConfig().announcementCount();
            scheduler.onGlobal(() -> feedback.send(
                    sender, CommunicationMessageKey.ANNOUNCER_RELOADED, Map.of("count", Integer.toString(count))));
        });
        return Command.SINGLE_SUCCESS;
    }

    private int list(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        // The merged set reads the enabled store announcements, a DB query, so it is resolved off the tick thread and
        // the framing hops back to the global region for delivery, the same off-tick shape as reload.
        scheduler.async(() -> {
            AnnouncerConfig config = mergedConfig.get();
            scheduler.onGlobal(() -> sendList(sender, config));
        });
        return Command.SINGLE_SUCCESS;
    }

    private void sendList(CommandSender sender, AnnouncerConfig config) {
        if (!config.hasAnnouncements()) {
            feedback.send(sender, CommunicationMessageKey.ANNOUNCE_LIST_EMPTY, Map.of());
            return;
        }
        feedback.send(
                sender,
                CommunicationMessageKey.ANNOUNCE_LIST_HEADER,
                Map.of("count", Integer.toString(config.announcementCount())));
        for (Announcement announcement : config.announcements()) {
            feedback.send(
                    sender,
                    CommunicationMessageKey.ANNOUNCE_LIST_ENTRY,
                    Map.of("id", announcement.id(), "channels", channels(announcement)));
        }
    }

    private int preview(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        String id = ctx.getArgument("id", String.class);
        // Resolve the id against the merged set (config + enabled store), a DB read, off the tick thread; the preview
        // delivery hops to the player's region inside the broadcaster, so the global hop here just routes the result.
        scheduler.async(() -> {
            Optional<Announcement> found = mergedConfig.get().announcements().stream()
                    .filter(announcement -> announcement.id().equalsIgnoreCase(id))
                    .findFirst();
            scheduler.onGlobal(() -> sendPreview(sender, id, found));
        });
        return Command.SINGLE_SUCCESS;
    }

    private void sendPreview(Player sender, String id, Optional<Announcement> found) {
        if (found.isEmpty()) {
            feedback.send(sender, CommunicationMessageKey.ANNOUNCE_PREVIEW_UNKNOWN, Map.of("id", id));
            return;
        }
        broadcaster.preview(found.get(), sender);
    }

    private int toggle(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        optOut.toggle(ref(sender));
        return Command.SINGLE_SUCCESS;
    }

    private static String channels(Announcement announcement) {
        return announcement.channels().stream()
                .map(BroadcastChannel::name)
                .sorted()
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }
}
