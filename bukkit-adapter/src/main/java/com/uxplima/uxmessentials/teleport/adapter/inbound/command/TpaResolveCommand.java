package com.uxplima.uxmessentials.teleport.adapter.inbound.command;

import java.util.List;
import java.util.Objects;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.teleport.adapter.TeleportRefs;
import com.uxplima.uxmessentials.teleport.adapter.TeleportServices;
import org.jspecify.annotations.NullMarked;

/**
 * The no-argument tpa resolution verbs ({@code /tpaccept}, {@code /tpdeny}, {@code /tpcancel}) each a
 * single call into {@link com.uxplima.uxmessentials.teleport.application.AcceptTeleport}. {@code accept}
 * passes the acceptor's live position as the warmup anchor (read here on the player's region thread); the
 * other verbs need only the invoking player. The verb is selected by a {@link Mode} so the three share
 * one node builder.
 */
@NullMarked
public final class TpaResolveCommand extends TeleportCommandSupport implements CommandRegistration {

    /** Which tpa-resolution verb this instance binds. */
    public enum Mode {
        ACCEPT,
        DENY,
        CANCEL
    }

    private final String literal;
    private final String permission;
    private final String description;
    private final List<String> aliases;
    private final Mode mode;

    public TpaResolveCommand(
            TeleportServices services,
            Messages messages,
            String literal,
            String permission,
            String description,
            List<String> aliases,
            Mode mode) {
        super(services, messages);
        this.literal = Objects.requireNonNull(literal, "literal");
        this.permission = Objects.requireNonNull(permission, "permission");
        this.description = Objects.requireNonNull(description, "description");
        this.aliases = List.copyOf(Objects.requireNonNull(aliases, "aliases"));
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal)
                .requires(src -> src.getSender().hasPermission(permission))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public List<String> aliases() {
        return aliases;
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef ref = ref(sender);
        switch (mode) {
            case ACCEPT -> services.acceptTeleport().accept(ref, anchor(sender));
            case DENY -> services.acceptTeleport().deny(ref);
            case CANCEL -> services.acceptTeleport().cancel(ref);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static Position anchor(Player acceptor) {
        return TeleportRefs.positionOf(acceptor);
    }
}
