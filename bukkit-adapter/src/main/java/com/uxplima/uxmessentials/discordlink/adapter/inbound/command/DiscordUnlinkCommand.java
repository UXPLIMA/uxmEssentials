package com.uxplima.uxmessentials.discordlink.adapter.inbound.command;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.discordlink.adapter.DiscordLinkServices;
import com.uxplima.uxmessentials.discordlink.application.DiscordlinkMessageKey;
import com.uxplima.uxmessentials.discordlink.domain.DiscordLinkError;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /discordunlink}: remove the player's confirmed Discord binding. Runs the {@code Unlink} use case and
 * tells the player whether anything was unbound. A success when a binding existed, a not-linked notice when
 * there was none. The {@code uxmessentials.discord.link} node guards the command.
 */
@NullMarked
public final class DiscordUnlinkCommand extends DiscordLinkCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.discord.link";

    public DiscordUnlinkCommand(DiscordLinkServices services) {
        super(services);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("discordunlink")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return "Remove the link between your account and Discord.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef ref = ref(sender);
        DiscordlinkMessageKey key = services.unlink().unlink(ref).isOk()
                ? DiscordlinkMessageKey.DISCORD_LINK_UNLINKED
                : keyFor(DiscordLinkError.NOT_LINKED);
        services.notifier().send(ref, key);
        return Command.SINGLE_SUCCESS;
    }

    private static DiscordlinkMessageKey keyFor(DiscordLinkError error) {
        return error.messageKey();
    }
}
