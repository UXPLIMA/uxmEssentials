package com.uxplima.uxmessentials.discordlink.adapter.inbound.command;

import java.util.Map;
import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.discordlink.adapter.DiscordLinkServices;
import com.uxplima.uxmessentials.discordlink.adapter.inbound.gui.DiscordStatusView;
import com.uxplima.uxmessentials.discordlink.application.DiscordlinkMessageKey;
import com.uxplima.uxmessentials.discordlink.domain.ConfirmedLink;
import com.uxplima.uxmessentials.discordlink.domain.LinkCode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /discordlink}: issue a one-time link code and tell the player how to redeem it in Discord; the
 * {@code status} subcommand reports the player's current binding instead, and the {@code gui} subcommand opens
 * the link-status panel. Issuing runs the {@code BeginLink} use case, the status path the {@code LinkStatus} use
 * case, and the panel surfaces the same use cases; the actual redemption happens in Discord via the bridge's
 * {@code /link} slash command, not here. The {@code uxmessentials.discord.link} node guards every path, and the
 * GUI subcommand additionally requires {@code uxmessentials.discord.gui}.
 */
@NullMarked
public final class DiscordLinkCommand extends DiscordLinkCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.discord.link";
    private static final String GUI_PERMISSION = "uxmessentials.discord.gui";

    private final DiscordStatusView view;

    public DiscordLinkCommand(DiscordLinkServices services, DiscordStatusView view) {
        super(services);
        this.view = java.util.Objects.requireNonNull(view, "view");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("discordlink")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.literal("status").executes(this::status))
                .then(Commands.literal("gui")
                        .requires(src -> src.getSender().hasPermission(GUI_PERMISSION))
                        .executes(this::openGui))
                .executes(this::begin)
                .build();
    }

    @Override
    public String description() {
        return "Generate a code to link your account to Discord.";
    }

    private int openGui(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        view.open(sender, ref(sender));
        return Command.SINGLE_SUCCESS;
    }

    private int begin(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef ref = ref(sender);
        if (!services.bridge().available()) {
            // No connected bridge means a code has nothing to redeem it, so refuse to mint one rather than
            // hand the player a dead end. status/gui still work: only the issuing path is gated.
            services.notifier().send(ref, DiscordlinkMessageKey.DISCORD_NOT_CONFIGURED);
            return Command.SINGLE_SUCCESS;
        }
        Optional<ConfirmedLink> existing = services.linkStatus().status(ref);
        if (existing.isPresent()) {
            services.notifier()
                    .send(
                            ref,
                            DiscordlinkMessageKey.DISCORD_LINK_ALREADY,
                            Map.of("discord", existing.get().discordId().value()));
            return Command.SINGLE_SUCCESS;
        }
        LinkCode code = services.beginLink().begin(ref);
        services.notifier().send(ref, DiscordlinkMessageKey.DISCORD_LINK_CODE, Map.of("code", code.value()));
        services.notifier().send(ref, DiscordlinkMessageKey.DISCORD_LINK_HOWTO, Map.of("code", code.value()));
        return Command.SINGLE_SUCCESS;
    }

    private int status(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef ref = ref(sender);
        services.linkStatus()
                .status(ref)
                .ifPresentOrElse(
                        link -> services.notifier()
                                .send(
                                        ref,
                                        DiscordlinkMessageKey.DISCORD_LINK_STATUS_LINKED,
                                        Map.of("discord", link.discordId().value())),
                        () -> services.notifier().send(ref, DiscordlinkMessageKey.DISCORD_LINK_STATUS_UNLINKED));
        return Command.SINGLE_SUCCESS;
    }
}
