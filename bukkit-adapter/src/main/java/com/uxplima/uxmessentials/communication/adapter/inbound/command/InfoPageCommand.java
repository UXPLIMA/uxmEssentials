package com.uxplima.uxmessentials.communication.adapter.inbound.command;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.communication.adapter.outbound.BukkitInfoSender;
import com.uxplima.uxmessentials.communication.application.CommunicationMessageKey;
import com.uxplima.uxmessentials.communication.application.InfoPageView;
import com.uxplima.uxmessentials.communication.application.InfoRegistry;
import com.uxplima.uxmessentials.communication.application.ShowInfoPage;
import com.uxplima.uxmessentials.communication.domain.InfoPage;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * One auto-registered info-page command. {@code /rules}, {@code /motd}, {@code /info}, or any custom page the
 * operator declared. Built dynamically at module start, one per {@link InfoPage} in the {@link InfoRegistry}, each
 * guarded by its per-page permission node {@code uxmessentials.communication.info.<name>}. The page body is
 * operator-authored MiniMessage content rendered through the {@link BukkitInfoSender}, never a plugin
 * {@code MessageKey}.
 *
 * <p>The command is paginated: {@code /<name> [page]} shows one page of the body, and a page longer than its
 * configured size is framed by the plugin's own {@link CommunicationMessageKey#INFO_PAGE_HEADER} and
 * {@link CommunicationMessageKey#INFO_PAGE_FOOTER} chrome (the only translated strings here. The body stays raw
 * operator content). The {@link ShowInfoPage} use case clamps an over-large page request into range, so
 * {@code /info 99} on a two-page document serves page two rather than an error.
 *
 * <p>The page is resolved from the live registry on each run rather than captured, so a
 * {@code /uxmess reload communication} that swaps a fresh registry in serves the new body without re-registering
 * the command. A registry that no longer carries this command's page (an operator removed it before reload took
 * the literal down) replies with the plugin's own {@link CommunicationMessageKey#INFO_PAGE_MISSING} string.
 */
@NullMarked
public final class InfoPageCommand extends CommunicationCommandSupport implements CommandRegistration {

    private static final String PERMISSION_PREFIX = "uxmessentials.communication.info.";

    private final String command;
    private final InfoRegistry registry;
    private final BukkitInfoSender infoSender;
    private final Notifier notifier;

    public InfoPageCommand(
            String command, InfoRegistry registry, BukkitInfoSender infoSender, Notifier notifier, Messages messages) {
        super(messages);
        this.command = Objects.requireNonNull(command, "command");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.infoSender = Objects.requireNonNull(infoSender, "infoSender");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        String permission = PERMISSION_PREFIX + command;
        return Commands.literal(command)
                .requires(src -> src.getSender().hasPermission(permission))
                .executes(ctx -> show(ctx, 1))
                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                        .executes(ctx -> show(ctx, ctx.getArgument("page", Integer.class))))
                .build();
    }

    @Override
    public String description() {
        return "Show the " + command + " info page.";
    }

    private int show(CommandContext<CommandSourceStack> ctx, int requestedPage) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        Optional<InfoPage> page = registry.find(command);
        if (page.isEmpty()) {
            notifier.send(ref(sender), CommunicationMessageKey.INFO_PAGE_MISSING, Map.of("page", command));
            return Command.SINGLE_SUCCESS;
        }
        render(ref(sender), ShowInfoPage.view(page.get(), requestedPage));
        return Command.SINGLE_SUCCESS;
    }

    private void render(PlayerRef viewer, InfoPageView view) {
        if (view.isPaginated()) {
            notifier.send(viewer, CommunicationMessageKey.INFO_PAGE_HEADER, headerPlaceholders(view));
        }
        infoSender.send(viewer, view.lines());
        if (view.isPaginated() && view.page() < view.pageCount()) {
            notifier.send(viewer, CommunicationMessageKey.INFO_PAGE_FOOTER, footerPlaceholders(view));
        }
    }

    private Map<String, String> headerPlaceholders(InfoPageView view) {
        return Map.of(
                "command", view.command(),
                "page", Integer.toString(view.page()),
                "pages", Integer.toString(view.pageCount()));
    }

    private Map<String, String> footerPlaceholders(InfoPageView view) {
        return Map.of("command", view.command(), "next", Integer.toString(view.page() + 1));
    }
}
