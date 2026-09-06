package com.uxplima.uxmessentials.bootstrap.command;

import java.nio.file.Path;
import java.util.Objects;

import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import net.kyori.adventure.text.minimessage.MiniMessage;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.migration.adapter.DataDirBackupSnapshot;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /backup}: an on-demand snapshot of the plugin's own data directory. It reuses the
 * {@link DataDirBackupSnapshot} the migration importer takes before a cutover (docs/12-migration §9), so a
 * backup is a timestamped copy of the bundled {@code .conf} state under the data folder, not a world or
 * off-host database backup, which stay the operator's responsibility. The reply says as much.
 *
 * <p>Cross-cutting and operator-facing rather than owned by a feature context, so it lives in the bootstrap
 * command surface alongside {@code /uxmess}; its diagnostic acknowledgements are plain operator text. The
 * snapshot runs off the tick thread through the kernel {@link Scheduler#async} port, the file copy never
 * touches the tick thread, and the command returns control immediately, reporting the location once the
 * copy finishes. Permission node {@code uxmessentials.admin.backup}.
 */
@NullMarked
public final class BackupCommand implements CommandRegistration {

    private static final String LITERAL = "backup";
    private static final String PERMISSION = "uxmessentials.admin.backup";
    private static final String DESCRIPTION = "Snapshot the plugin's data directory on demand.";

    private static final String STARTED = "Snapshotting the plugin data directory off-tick…";
    private static final String DONE = "Data snapshot written to ";
    private static final String SCOPE = "This backs up uxmEssentials config/data only, not worlds or the database.";
    private static final String FAILED = "Backup failed. See the server log for details.";

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final String BODY = "<#aeb8c4>";
    private static final String SUCCESS = "<#57e389>";
    private static final String ERROR = "<#ff6b6b>";

    private final DataDirBackupSnapshot snapshot;
    private final Scheduler scheduler;
    private final Logger log;

    public BackupCommand(DataDirBackupSnapshot snapshot, Scheduler scheduler, Logger log) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(LITERAL)
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return DESCRIPTION;
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        send(sender, BODY, STARTED);
        scheduler.async(() -> takeOffTick(sender));
        return Command.SINGLE_SUCCESS;
    }

    private void takeOffTick(CommandSender sender) {
        try {
            DataDirBackupSnapshot.Snapshot result = snapshot.takeSnapshot();
            Path location = result.location();
            scheduler.onGlobal(() -> {
                send(sender, SUCCESS, DONE + location);
                send(sender, BODY, SCOPE);
            });
        } catch (RuntimeException failure) {
            log.error("on-demand data backup failed", failure);
            scheduler.onGlobal(() -> send(sender, ERROR, FAILED));
        }
    }

    private static void send(CommandSender sender, String palette, String text) {
        sender.sendMessage(MINI_MESSAGE.deserialize(palette + MINI_MESSAGE.escapeTags(text)));
    }
}
