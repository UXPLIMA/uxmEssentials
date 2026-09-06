package com.uxplima.uxmessentials.custommenus.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.custommenus.application.CustomMenusMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ArgumentNodes;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * The operator-declared open command for one custom menu: a menu's {@code command {}} block ({@code /shop},
 * {@code /store}) opens that menu's spec through the public {@link Menus} facade, exactly as {@code /menu open
 * <id>} does, but with the menu's own name, aliases, permission gate, deny message and typed arguments.
 *
 * <p>When the menu declares a permission, the command node is gated with a Brigadier {@code requires} on that node,
 * so its visibility matches its execution gate: a sender who lacks the node neither sees nor runs it, closing the
 * gap where an unpermitted player still saw (and tab-completed) a command they could not use. A menu with no
 * permission stays open to everyone. The executor still owns the rest of the decision: a console sender is turned
 * away unless the block allows it, and it re-checks the permission (and draws the operator's deny line, or the
 * shared no-permission line) as defence in depth before a real player opens the menu.
 *
 * <p>A command that declares {@code arguments} hands them to the shared {@link ArgumentNodes} builder, which
 * chains one typed Brigadier node per argument in order under the literal. Each node completes against its type
 * (an online player, the material or loaded-world names, the online roster), so an operator gets autocomplete for
 * free, and a wrong-typed value ({@code /gift Steve xyz} where {@code amount} is an int) is rejected as a syntax
 * error before the menu opens. A trailing optional argument may be left out and reads back as the empty string.
 * The parsed values reach the menu as {@code %argument_<name>%} placeholders, keyed by argument name in open
 * order.
 */
@NullMarked
public final class MenuOpenCommand implements CommandRegistration {

    private final Menus menus;
    private final String menuId;
    private final OpenCommandSpec spec;
    private final CommandFeedback feedback;

    public MenuOpenCommand(Menus menus, String menuId, OpenCommandSpec spec, Messages messages) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.menuId = Objects.requireNonNull(menuId, "menuId");
        this.spec = Objects.requireNonNull(spec, "spec");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> literal = Commands.literal(spec.name());
        // Gate visibility on the menu's permission when it declares one, so the node is hidden from a sender who
        // cannot use it (its execution still re-checks). A permissionless menu stays open to everyone.
        spec.permission()
                .ifPresent(permission -> literal.requires(src -> src.getSender().hasPermission(permission)));
        if (spec.arguments().isEmpty()) {
            return literal.executes(this::open).build();
        }
        return literal.then(ArgumentNodes.chain(spec.arguments(), this::open)).build();
    }

    @Override
    public List<String> aliases() {
        return spec.aliases();
    }

    @Override
    public String description() {
        return spec.usage().orElseGet(() -> "Opens the " + menuId + " custom menu.");
    }

    private int open(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        // A non-player sender is turned away first unless the block explicitly allows the console.
        if (!(sender instanceof Player) && !spec.consoleAllowed()) {
            feedback.send(sender, CustomMenusMessageKey.MENU_CONSOLE_DENIED);
            return 0;
        }
        if (spec.permission().isPresent()
                && !sender.hasPermission(spec.permission().get())) {
            denyPermission(sender);
            return 0;
        }
        // Opening a menu needs a player window: a console that cleared the gate above has nobody to open for.
        if (!(sender instanceof Player player)) {
            feedback.send(sender, SharedMessageKey.COMMAND_PLAYERS_ONLY);
            return 0;
        }
        // Pre-open actions ride the engine's open-actions seam: opening the menu below runs its open-actions on open,
        // so a menu whose command opens it fires that menu's open-actions: no separate pre-open hook needed here.
        Map<String, String> arguments = ArgumentNodes.read(ctx, spec.arguments());
        menus.open(BukkitRefs.toRef(player), menuId, null, 0, arguments);
        return Command.SINGLE_SUCCESS;
    }

    /** Reject a failed permission check with the operator's configured deny line, or the shared default when none. */
    private void denyPermission(CommandSender sender) {
        spec.denyMessage()
                .ifPresentOrElse(
                        line -> sender.sendMessage(StyledText.render(line)),
                        () -> feedback.send(sender, SharedMessageKey.COMMAND_NO_PERMISSION));
    }
}
