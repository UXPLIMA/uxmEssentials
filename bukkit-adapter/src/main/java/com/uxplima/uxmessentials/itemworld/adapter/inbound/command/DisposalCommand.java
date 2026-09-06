package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.Map;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.MenuTitles;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyleTags;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.StorageGui;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /disposal} (alias {@code /trash}): open a throwaway window. Anything left inside when it closes is
 * discarded. The window is a fresh uxmLib {@link StorageGui} backed by nothing: items dropped into it are
 * never written to a real container and the menu has no close handler, so closing the view silently drops
 * them. Opening is entity-bound, so it is scheduled on the player's region thread through the kernel
 * {@code Scheduler}, then reported through {@link ItemworldMessageKey#DISPOSAL_OPENED}.
 */
@NullMarked
public final class DisposalCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.disposal.use";

    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public DisposalCommand(ItemworldServices services) {
        super(services, "disposal", SubFeatureGroup.CLEANUP, "Open a throwaway window.");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return describe();
    }

    @Override
    public java.util.List<String> aliases() {
        return java.util.List.of("trash");
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        Player player = player(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef viewer = ref(player);
        services.kernel().scheduler().onEntity(viewer, () -> {
            StorageGui disposal = Guis.storage()
                    .title(title(viewer))
                    .rows(services.disposalLayout().rows())
                    .build();
            disposal.open(player);
            reply(ctx, ItemworldMessageKey.DISPOSAL_OPENED);
        });
        return Command.SINGLE_SUCCESS;
    }

    private Component title(PlayerRef viewer) {
        // Centred and bare, like every other window title: this one opens a real container rather than a menu, so
        // it does not pass through the engine that would otherwise do it.
        return MenuTitles.centre(miniMessage.deserialize(
                services.kernel().messages().resolve(viewer, ItemworldMessageKey.DISPOSAL_TITLE, Map.of()),
                StyleTags.resolver()));
    }
}
