package com.uxplima.uxmessentials.kits.adapter.inbound.command;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.kits.adapter.KitServices;
import com.uxplima.uxmessentials.kits.adapter.inbound.gui.KitManagerMenu;
import com.uxplima.uxmessentials.kits.adapter.outbound.KitItemCodec;
import com.uxplima.uxmessentials.kits.application.KitsMessageKey;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.kits.domain.KitItem;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ListDisplayMode;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /kit}: the single entry point for everything a player or operator does with kits, structured as a
 * Brigadier subcommand tree (the same idiom {@code /warp} and {@code /home} use).
 *
 * <ul>
 *   <li>{@code /kit <name> [player]} claims a kit. With no trailing player the sender claims it for
 *       themselves; with one, and {@code uxmessentials.kit.others}, staff give the kit to that player. The
 *       per-kit permission, the one-time stamp, the cooldown, and the optional cost are the
 *       {@link com.uxplima.uxmessentials.kits.application.ClaimKit} use case's job. Gated by
 *       {@code uxmessentials.kit.use}.
 *   <li>{@code /kit list} opens the read-only browse menu listing the kits the player may claim (or the
 *       clickable chat list when the operator selects {@code chat} mode, and always for a console). Gated by
 *       {@code uxmessentials.kit.use}.
 *   <li>{@code /kit show <name>} previews a kit's contents without claiming it. Gated by
 *       {@code uxmessentials.kit.preview}.
 *   <li>{@code /kit create <name>} defines a new kit from the operator's current inventory. Gated by
 *       {@code uxmessentials.kit.edit}.
 *   <li>{@code /kit del <name>} removes a kit definition. Gated by {@code uxmessentials.kit.edit}.
 *   <li>{@code /kit editor [name [save]]} edits a kit's contents (manager GUI with no name, the editable
 *       window with one, the inventory-snapshot {@code save} flow with the trailing literal). Gated by
 *       {@code uxmessentials.kit.edit}.
 *   <li>{@code /kit reset <player> [kit]} clears a player's one-time-claim stamps. Gated by
 *       {@code uxmessentials.kit.reset}.
 * </ul>
 *
 * <p>The positional {@code <name>} could in principle collide with a subcommand literal (a kit literally
 * named {@code list}), but Brigadier matches literal children before the {@code name} argument, so the
 * literals always win, the same disambiguation {@code /warp} and {@code /pwarp} rely on, with no
 * reserved-name guard. Per-subcommand permissions are enforced by Brigadier {@code .requires(...)} on each
 * node, so only {@code kit} is a command-catalog id; the subcommands are literals inside the tree.
 */
@NullMarked
public final class KitCommand extends KitCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.kit.use";
    private static final String OTHERS_PERMISSION = "uxmessentials.kit.others";
    private static final String PREVIEW_PERMISSION = "uxmessentials.kit.preview";
    private static final String EDIT_PERMISSION = "uxmessentials.kit.edit";
    private static final String RESET_PERMISSION = "uxmessentials.kit.reset";

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final Supplier<ListDisplayMode> listDisplay;
    private final Supplier<ListDisplayMode> previewDisplay;
    private final Scheduler scheduler;

    public KitCommand(
            KitServices services,
            Messages messages,
            Supplier<ListDisplayMode> listDisplay,
            Supplier<ListDisplayMode> previewDisplay,
            Scheduler scheduler) {
        super(services, messages);
        this.listDisplay = Objects.requireNonNull(listDisplay, "listDisplay");
        this.previewDisplay = Objects.requireNonNull(previewDisplay, "previewDisplay");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("kit")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.literal("list").executes(this::runList))
                .then(Commands.literal("show")
                        .requires(src -> src.getSender().hasPermission(PREVIEW_PERMISSION))
                        .executes(ctx -> usage(ctx, "kit show", "<name>", "Preview a kit"))
                        .then(kitNameArgument("name").executes(this::show)))
                .then(Commands.literal("create")
                        .requires(src -> src.getSender().hasPermission(EDIT_PERMISSION))
                        .executes(ctx -> usage(ctx, "kit create", "<name>", "Create a kit from inventory"))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(this::create)))
                .then(Commands.literal("del")
                        .requires(src -> src.getSender().hasPermission(EDIT_PERMISSION))
                        .executes(ctx -> usage(ctx, "kit del", "<name>", "Delete a kit"))
                        .then(kitNameArgument("name").executes(this::delete)))
                .then(Commands.literal("editor")
                        .requires(src -> src.getSender().hasPermission(EDIT_PERMISSION))
                        .executes(this::openManager)
                        .then(kitNameArgument("name")
                                .executes(this::openEditor)
                                .then(Commands.literal("save").executes(this::saveEditor))))
                .then(Commands.literal("reset")
                        .requires(src -> src.getSender().hasPermission(RESET_PERMISSION))
                        .executes(ctx -> usage(ctx, "kit reset", "<player> [kit]", "Reset player kit cooldown"))
                        .then(playerSelectorArgument("player")
                                .executes(this::resetAll)
                                .then(kitNameArgument("kit").executes(this::resetOne))))
                .then(kitNameArgument("name")
                        .executes(this::claimSelf)
                        .then(playerSelectorArgument("player")
                                .requires(src -> src.getSender().hasPermission(OTHERS_PERMISSION))
                                .executes(this::give)))
                .build();
    }

    @Override
    public String description() {
        return "Claim a kit; list, preview, create, remove, edit or reset kits.";
    }

    /**
     * Bare {@code /kit} opens the browse menu: the same view {@code /kit list} opens. Installed on the root
     * only when the command's catalog {@code gui} flag is on; with it off the bare root falls back to the
     * usage line.
     */
    @Override
    public Optional<Command<CommandSourceStack>> guiRoot() {
        return Optional.of(this::runList);
    }

    private int claimSelf(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.claimKit().claim(ref(sender), KitId.of(ctx.getArgument("name", String.class)));
        return Command.SINGLE_SUCCESS;
    }

    private int give(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Optional<PlayerRef> target = resolveSelectorTarget(ctx, ctx.getSource().getSender());
        if (target.isEmpty()) {
            return 0;
        }
        PlayerRef actor = actorRef(sender);
        PlayerRef recipient = target.get();
        KitId kit = KitId.of(ctx.getArgument("name", String.class));
        // The grant mutates the RECIPIENT's inventory/world (BukkitKitGranter) and spawns effects in their world,
        // so it must run on the recipient's region thread, not the giving sender's. Hop onto the recipient's
        // entity thread. A self-claim goes through claimSelf and stays on the player's own thread.
        scheduler.onEntity(recipient, () -> services.claimKit().claimFor(actor, recipient, kit));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code /kit list}: open the kits browse menu, the read-only {@code PaginatedGui} the browse command
     * opens. A console has no inventory and the operator-selectable {@code chat} display mode both fall back
     * to the clickable chat list, sharing the same {@link com.uxplima.uxmessentials.kits.application.ListKits}
     * filter so the two presentations never disagree. The mode is read live so a reload takes effect at once.
     */
    private int runList(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player viewer)) {
            // A console has no inventory to open a menu in; show the chat list instead.
            return runChatList(ctx);
        }
        if (listDisplay.get() == ListDisplayMode.CHAT) {
            // Operator chose command/chat-only output: list in chat without opening an inventory.
            return runChatList(ctx);
        }
        PlayerRef viewerRef = ref(viewer);
        List<KitDefinition> kits = services.listKits().available(viewerRef);
        services.kitMenu().open(viewer, viewerRef, kits);
        return Command.SINGLE_SUCCESS;
    }

    private int runChatList(CommandContext<CommandSourceStack> ctx) {
        services.listKits().list(actorRef(ctx.getSource().getSender()));
        return Command.SINGLE_SUCCESS;
    }

    private int show(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        PlayerRef actor = actorRef(sender);
        KitId id = KitId.of(ctx.getArgument("name", String.class));
        services.showKit().show(actor, id).asValue().ifPresent(definition -> present(sender, actor, definition));
        return Command.SINGLE_SUCCESS;
    }

    /** Open the GUI preview in {@code gui} mode, or print the chat preview in {@code chat} mode. */
    private void present(CommandSender sender, PlayerRef actor, KitDefinition definition) {
        if (previewDisplay.get() == ListDisplayMode.GUI && sender instanceof Player player) {
            services.kitPreview().open(player, actor, definition);
        } else {
            renderPreview(sender, definition);
        }
    }

    private void renderPreview(CommandSender sender, KitDefinition definition) {
        feedback.send(
                sender,
                KitsMessageKey.KIT_PREVIEW_HEADER,
                Map.of("kit", definition.id().value()));
        List<KitItem> items = definition.items();
        for (int i = 0; i < items.size(); i++) {
            KitItem item = items.get(i);
            feedback.send(
                    sender,
                    KitsMessageKey.KIT_PREVIEW_ENTRY,
                    Map.of(
                            "slot", Integer.toString(i + 1),
                            "amount", Integer.toString(item.amount()),
                            "item", itemName(item)));
        }
    }

    private int create(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        KitId id = KitId.of(ctx.getArgument("name", String.class));
        KitDefinition definition = KitDefinition.repeatable(id, inventoryItems(sender), Duration.ZERO);
        services.createKit().create(ref(sender), definition);
        return Command.SINGLE_SUCCESS;
    }

    private int delete(CommandContext<CommandSourceStack> ctx) {
        services.delKit()
                .delete(actorRef(ctx.getSource().getSender()), KitId.of(ctx.getArgument("name", String.class)));
        return Command.SINGLE_SUCCESS;
    }

    private int openManager(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        KitManagerMenu manager = services.kitManager();
        if (manager != null) {
            manager.open(sender, ref(sender));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int openEditor(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        KitId id = KitId.of(ctx.getArgument("name", String.class));
        services.kitEditor()
                .open(ref(sender), id)
                .asValue()
                .ifPresent(kit -> services.kitEditorView().open(sender, ref(sender), kit));
        return Command.SINGLE_SUCCESS;
    }

    private int saveEditor(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        KitId id = KitId.of(ctx.getArgument("name", String.class));
        Optional<KitDefinition> existing =
                services.kitEditor().open(ref(sender), id).asValue();
        existing.ifPresent(kit -> services.kitEditor()
                .redefine(
                        ref(sender),
                        KitDefinition.builder()
                                .id(kit.id())
                                .items(inventoryItems(sender))
                                .cooldown(kit.cooldown())
                                .oneTime(kit.oneTime())
                                .permission(kit.permission())
                                .cost(kit.cost())
                                .build()));
        return Command.SINGLE_SUCCESS;
    }

    private int resetAll(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        PlayerRef actor = actorRef(sender);
        Optional<PlayerRef> target = resolveSelectorTarget(ctx, sender);
        if (target.isEmpty()) {
            return 0;
        }
        services.kitReset().resetAll(actor, target.get());
        return Command.SINGLE_SUCCESS;
    }

    private int resetOne(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        PlayerRef actor = actorRef(sender);
        Optional<PlayerRef> target = resolveSelectorTarget(ctx, sender);
        if (target.isEmpty()) {
            return 0;
        }
        services.kitReset().reset(actor, target.get(), KitId.of(ctx.getArgument("kit", String.class)));
        return Command.SINGLE_SUCCESS;
    }

    private static PlayerRef actorRef(CommandSender sender) {
        return sender instanceof Player player ? ref(player) : PlayerRef.system(sender.getName());
    }

    /** A readable name for a kit item: its custom display name when set, else the prettified material name. */
    private static String itemName(KitItem item) {
        ItemStack stack = KitItemCodec.decode(item);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            Component name = meta.displayName();
            if (name != null) {
                return PLAIN.serialize(name);
            }
        }
        return prettyMaterial(stack);
    }

    /** Title-cased material name, e.g. {@code DIAMOND_SWORD} reads "Diamond Sword". */
    private static String prettyMaterial(ItemStack stack) {
        String key = stack.getType().getKey().getKey().toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(key.length());
        boolean wordStart = true;
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c == '_') {
                out.append(' ');
                wordStart = true;
            } else {
                out.append(wordStart ? Character.toUpperCase(c) : c);
                wordStart = false;
            }
        }
        return out.toString();
    }
}
