package com.uxplima.uxmessentials.teleport.adapter.inbound.command;

import java.util.List;

import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.teleport.adapter.TeleportServices;
import com.uxplima.uxmessentials.teleport.adapter.inbound.command.AdminTpCommand.Pull;
import com.uxplima.uxmessentials.teleport.adapter.inbound.command.TpaResolveCommand.Mode;
import com.uxplima.uxmessentials.teleport.adapter.inbound.command.VerticalCommand.Kind;
import com.uxplima.uxmessentials.teleport.domain.RequestDirection;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the teleport context's full Brigadier command surface (docs/10-feature-modules.md §15.3) as
 * {@link CommandRegistration}s over the constructed {@link TeleportServices}. Collected in one greppable
 * table so the literal/permission pairing matches the permissions reference and the kernel's
 * {@code TeleportCommandSurface}; the plugin's {@code LifecycleEvents.COMMANDS} handler registers each.
 */
@NullMarked
public final class TeleportCommands {

    private TeleportCommands() {}

    /** Every teleport command, in surface-group order. */
    public static List<CommandRegistration> all(TeleportServices s, Messages m) {
        return List.of(
                // tpa lifecycle
                new TpaRequestCommand(
                        s,
                        m,
                        "tpa",
                        "uxmessentials.tpa.use",
                        "Request to teleport to a player",
                        RequestDirection.TO_TARGET),
                new TpaRequestCommand(
                        s,
                        m,
                        "tpahere",
                        "uxmessentials.tpahere.use",
                        "Ask a player to teleport to you",
                        RequestDirection.TO_REQUESTER),
                new TpaResolveCommand(
                        s,
                        m,
                        "tpaccept",
                        "uxmessentials.tpa.use",
                        "Accept a teleport request",
                        List.of("tpyes"),
                        Mode.ACCEPT),
                new TpaResolveCommand(
                        s, m, "tpdeny", "uxmessentials.tpa.use", "Deny a teleport request", List.of("tpno"), Mode.DENY),
                new TpaResolveCommand(
                        s,
                        m,
                        "tpcancel",
                        "uxmessentials.tpa.cancel",
                        "Withdraw your outgoing request",
                        List.of("tpacancel"),
                        Mode.CANCEL),
                new TpaListCommand(s, m),
                new TpToggleCommand(s, m),
                new TpSetToggleCommand(s, m, "tpon", true),
                new TpSetToggleCommand(s, m, "tpoff", false),
                new TpAutoCommand(s, m),
                new TpBlockCommand(s, m, "tpblock", "Block a player's requests", true),
                new TpBlockCommand(s, m, "tpunblock", "Unblock a player's requests", false),
                new TpaAllCommand(s, m),
                // back
                new BackCommand(s, m),
                new DeathBackCommand(s, m),
                // rtp. Note /rtp itself is built in TeleportWiring (it needs the RTP menu opener)
                new SetTprCommand(s, m),
                // spawn
                new SpawnCommand(s, m),
                new SetSpawnCommand(s, m),
                new SetMainSpawnCommand(s, m),
                new RemoveSpawnCommand(s, m),
                new MirrorSpawnCommand(s, m),
                // admin direct tp
                new AdminTpCommand(s, m, "tp", "uxmessentials.tp.use", "Teleport to a player", Pull.GO),
                new AdminTpCommand(s, m, "tphere", "uxmessentials.tp.use", "Pull a player to you", Pull.BRING),
                new AdminTpCommand(s, m, "goto", "uxmessentials.tp.use", "Teleport to a player", Pull.GO),
                new AdminTpCommand(s, m, "bring", "uxmessentials.tp.use", "Pull a player to you", Pull.BRING),
                new TpRandomPlayerCommand(s, m),
                new AdminTpCommand(s, m, "tpo", "uxmessentials.tp.others", "Teleport overriding no-tp flags", Pull.GO),
                new AdminTpCommand(
                        s, m, "tpohere", "uxmessentials.tp.others", "Pull a player overriding flags", Pull.BRING),
                new TpPosCommand(s, m),
                new TpAllCommand(s, m),
                new TpOfflineCommand(s, m, "tpoffline", "Teleport to a player's logout point"),
                new TpOfflineCommand(s, m, "tpofflinehere", "Bring a player from their logout point"),
                // vertical / positional
                new VerticalCommand(s, m, "top", "Teleport to the highest block above", Kind.TOP),
                new VerticalCommand(s, m, "bottom", "Teleport to the lowest safe block", Kind.BOTTOM),
                new VerticalCommand(s, m, "jump", "Teleport to the block you look at", Kind.JUMP),
                new VerticalCommand(s, m, "up", "Teleport up onto a placed block", Kind.UP),
                new VerticalCommand(s, m, "down", "Teleport down to the first block below", Kind.DOWN),
                new VerticalCommand(s, m, "ascend", "Teleport up to the next open space", Kind.ASCEND),
                new VerticalCommand(s, m, "descend", "Teleport down to the next open space", Kind.DESCEND),
                new VerticalCommand(s, m, "thru", "Teleport through the wall you are facing", Kind.THRU));
    }
}
