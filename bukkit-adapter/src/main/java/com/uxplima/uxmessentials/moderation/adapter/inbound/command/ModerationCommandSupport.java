package com.uxplima.uxmessentials.moderation.adapter.inbound.command;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.context.CommandContext;
import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.moderation.application.ModerationMessageKey;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Shared collaborators every moderation Brigadier command holds: the constructed {@link ModerationServices},
 * the {@link Messages} catalog and the {@link MessageSink} for the resolution rejections a command renders
 * directly. Concrete command classes extend this so each stays focused on building its node and mapping its
 * arguments to one use-case call.
 *
 * <p>{@link #actor} maps the sender to a {@link PlayerRef}, a console actor gets a stable nil-UUID ref so an
 * offline {@code /jail}/{@code /banip} from console still attributes an issuer. {@link #targetByName} resolves
 * a target online-first, then from the profile cache so an offline target can still be muted/jailed/banned
 * (the offline-jail/offline-banip paths). The optional reason is the greedy trailing argument.
 */
@NullMarked
abstract class ModerationCommandSupport {

    static final MessageKey UNKNOWN_PLAYER = ModerationMessageKey.UNKNOWN_TARGET;

    final ModerationServices services;
    final Messages messages;
    final MessageSink sink;

    ModerationCommandSupport(ModerationServices services, Messages messages, MessageSink sink) {
        this.services = Objects.requireNonNull(services, "services");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    /** The actor: the live player, or a stable system ref for a console/command-block sender. */
    final PlayerRef actor(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        return sender instanceof Player player
                ? new PlayerRef(player.getUniqueId(), player.getName())
                : PlayerRef.system(sender.getName());
    }

    /** The target by name, online or known offline, or empty (after a feedback line) when never seen. */
    final Optional<PlayerRef> targetByName(CommandContext<CommandSourceStack> ctx, String name) {
        Optional<PlayerRef> target = services.targets().resolve(name);
        if (target.isEmpty()) {
            notify(ctx, UNKNOWN_PLAYER, Map.of("player", name));
        }
        return target;
    }

    /** The greedy trailing reason argument, or empty when the command was invoked without one. */
    static Optional<String> optionalReason(CommandContext<CommandSourceStack> ctx) {
        try {
            String raw = ctx.getArgument("reason", String.class);
            return raw.isBlank() ? Optional.empty() : Optional.of(raw.trim());
        } catch (IllegalArgumentException noReason) {
            return Optional.empty();
        }
    }

    /** The {@code -s} token a staff member prefixes a sanction reason with to suppress the staff broadcast. */
    static final String SILENT_TOKEN = "-s";

    /**
     * Split a leading {@code -s} silent flag off the greedy reason. A sanction's broadcast is suppressed when
     * the reason begins with {@code -s} (mirroring LiteBans) or when the module defaults to silent; the token is
     * stripped so it never leaks into the stored reason or the target's notice.
     *
     * @param reason the raw greedy reason as typed (may carry a leading {@code -s})
     * @param silentByDefault the module default applied when no flag is present
     */
    static SilentReason silentReason(Optional<String> reason, boolean silentByDefault) {
        if (reason.isEmpty()) {
            return new SilentReason(silentByDefault, Optional.empty());
        }
        String raw = reason.get().trim();
        if (raw.equals(SILENT_TOKEN)) {
            return new SilentReason(true, Optional.empty());
        }
        if (raw.startsWith(SILENT_TOKEN + " ")) {
            String stripped = raw.substring(SILENT_TOKEN.length()).trim();
            return new SilentReason(true, stripped.isBlank() ? Optional.empty() : Optional.of(stripped));
        }
        return new SilentReason(silentByDefault, Optional.of(raw));
    }

    /** A parsed sanction reason: whether the broadcast is suppressed, and the reason with any {@code -s} removed. */
    record SilentReason(boolean silent, Optional<String> reason) {

        SilentReason {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** Send {@code key} to the command sender, rendered in their locale. */
    final void notify(CommandContext<CommandSourceStack> ctx, MessageKey key, Map<String, String> placeholders) {
        sink.deliver(actor(ctx), messages.resolve(actor(ctx), key, placeholders));
    }

    /** The live player behind {@code target}, if connected: for a command that must act on a session. */
    static @Nullable Player onlinePlayer(CommandContext<CommandSourceStack> ctx, PlayerRef target) {
        return ctx.getSource().getSender().getServer().getPlayer(target.uuid());
    }

    /**
     * The invoking player, or {@code null} (after sending the players-only reply) for a console source. Used
     * by the position-capturing commands ({@code /setjail}) a console cannot run.
     */
    final @Nullable Player player(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (sender instanceof Player player) {
            return player;
        }
        notify(ctx, SharedMessageKey.COMMAND_PLAYERS_ONLY, Map.of());
        return null;
    }

    /**
     * The player's current {@link Position}. Paper marks {@code Player#getLocation()} nullable (null only for
     * an entity with no world, which a connected player never is), so the non-null assertion lives here once.
     */
    static Position position(Player player) {
        return BukkitRefs.toPosition(Objects.requireNonNull(player.getLocation(), "player location"));
    }
}
