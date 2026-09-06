package com.uxplima.uxmessentials.homes.adapter.inbound.gui;

import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.homes.application.DeleteHome;
import com.uxplima.uxmessentials.homes.application.HomesMessageKey;
import com.uxplima.uxmessentials.homes.application.RelocateHome;
import com.uxplima.uxmessentials.homes.application.RenameHome;
import com.uxplima.uxmessentials.homes.application.SetHomeVisibility;
import com.uxplima.uxmessentials.homes.application.TeleportHome;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeLabel;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.ClaimService;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.claim.ClaimDecision;
import org.jspecify.annotations.NullMarked;

/**
 * Registers the per-home action menu (opened by clicking a home in the {@code /home} grid) with the menu engine and
 * opens it. The home is the engine subject; each button routes through the same homes use case the old
 * {@code HomeActionView} drove, teleport, delete, relocate-here, rename, change icon, toggle visibility, manage
 * invites, and back. The placeholders fill the title and info label from that home, and a pair of {@code home-public}
 * / {@code home-private} view conditions pick the right visibility cell.
 *
 * <p>Each mutating use case runs off the click thread (the write hits SQLite); the menu re-opens through {@link Menus}
 * with the (re-read) home subject so a flip or rename shows immediately. Delete and relocate optionally confirm
 * through {@link Menus#confirm} first, and teleport reads the destination region for claim access and an unsafe-tp
 * warning: exactly the threads and decisions the old view used. The icon picker, invited-players list, and the grid
 * are reached through injected seams so this menu does not need to know which renders them.
 */
@NullMarked
public final class HomeActionMenu {

    /** The engine spec id this menu registers and opens under. */
    public static final String SPEC_ID = "home-action";

    private static final String SPEC_RESOURCE = "modules/homes/gui/home-action.conf";
    private static final String BYPASS_UNSAFE_PERMISSION = "uxmessentials.home.bypass.unsafe";
    private static final String HOME_RENAME_INPUT_KEY = "home.rename";

    private final Menus menus;
    private final Messages messages;
    private final Notifier notifier;
    private final Permissions permissions;
    private final Scheduler scheduler;
    private final TeleportHome teleportHome;
    private final DeleteHome deleteHome;
    private final RelocateHome relocateHome;
    private final RenameHome renameHome;
    private final SetHomeVisibility setHomeVisibility;
    private final HomeRepository repository;
    private final TextInput textInput;
    private final DateTimeFormatter dateFormat;
    private final boolean confirmDelete;
    private final boolean confirmRelocate;
    private final boolean confirmUnsafeTeleport;
    private final boolean blockUnsafeRelocate;
    private final Predicate<Position> destinationUnsafe;
    private final ClaimService claimService;
    private final BiConsumer<PlayerRef, Home> openIconPicker;
    private final BiConsumer<PlayerRef, Home> openInvitesMenu;
    private final Consumer<Player> openList;

    public HomeActionMenu(Collaborators collaborators) {
        this.menus = collaborators.menus();
        this.messages = collaborators.messages();
        this.notifier = collaborators.notifier();
        this.permissions = collaborators.permissions();
        this.scheduler = collaborators.scheduler();
        this.teleportHome = collaborators.teleportHome();
        this.deleteHome = collaborators.deleteHome();
        this.relocateHome = collaborators.relocateHome();
        this.renameHome = collaborators.renameHome();
        this.setHomeVisibility = collaborators.setHomeVisibility();
        this.repository = collaborators.repository();
        this.textInput = collaborators.textInput();
        this.dateFormat = collaborators.dateFormat();
        this.confirmDelete = collaborators.confirmDelete();
        this.confirmRelocate = collaborators.confirmRelocate();
        this.confirmUnsafeTeleport = collaborators.confirmUnsafeTeleport();
        this.blockUnsafeRelocate = collaborators.blockUnsafeRelocate();
        this.destinationUnsafe = collaborators.destinationUnsafe();
        this.claimService = collaborators.claimService();
        this.openIconPicker = collaborators.openIconPicker();
        this.openInvitesMenu = collaborators.openInvitesMenu();
        this.openList = collaborators.openList();
    }

    /** Register the bindings the spec names and the spec itself; called once at homes wiring time. */
    public void register(
            MenuBindings bindings, Path dataFolder, com.uxplima.uxmessentials.shared.application.port.Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.placeholder("home_name", ctx -> label(home(ctx)));
        bindings.placeholder("home_world", ctx -> home(ctx).location().world().name());
        bindings.placeholder(
                "home_x", ctx -> Integer.toString(home(ctx).location().blockX()));
        bindings.placeholder(
                "home_y", ctx -> Integer.toString(home(ctx).location().blockY()));
        bindings.placeholder(
                "home_z", ctx -> Integer.toString(home(ctx).location().blockZ()));
        bindings.placeholder("home_created", ctx -> dateFormat.format(home(ctx).createdAt()));
        bindings.condition("homes:home-public", (ctx, args) -> home(ctx).isPublic());
        bindings.condition("homes:home-private", (ctx, args) -> !home(ctx).isPublic());
        bindings.action("homes:teleport", this::handleTeleport);
        bindings.action("homes:delete", this::handleDelete);
        bindings.action("homes:relocate", this::handleRelocate);
        bindings.action("homes:rename", this::rename);
        bindings.action("homes:open-icons", ctx -> openIconPicker.accept(ctx.viewer(), home(ctx)));
        bindings.action("homes:toggle-visibility", this::toggleVisibility);
        bindings.action("homes:open-invites", ctx -> openInvitesMenu.accept(ctx.viewer(), home(ctx)));
        bindings.action("homes:action-back", ctx -> openList.accept(ctx.player()));
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, 3, log));
    }

    /** Open the action menu for {@code home}; the live player is resolved by the engine. */
    public void open(PlayerRef viewer, Home home) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(home, "home");
        menus.open(viewer, SPEC_ID, home);
    }

    private Home home(MenuContext ctx) {
        return ctx.subject(Home.class);
    }

    private Home home(MenuActionContext ctx) {
        return ctx.subject(Home.class);
    }

    /**
     * Teleport to the home. The claim-access read runs on the destination region (claim providers read world state
     * there); when unsafe-tp confirm is on and the viewer lacks the bypass, an unsafe destination warns first.
     */
    private void handleTeleport(MenuActionContext ctx) {
        Player player = ctx.player();
        PlayerRef viewer = ctx.viewer();
        Home home = home(ctx);
        scheduler.onRegion(home.location(), () -> {
            ClaimDecision access = claimService.canAccess(viewer, home.location());
            if (!access.allowed()) {
                scheduler.onEntity(viewer, () -> {
                    notifier.send(viewer, claimMessageKey(access));
                    open(viewer, home);
                });
                return;
            }
            if (!confirmUnsafeTeleport || permissions.has(viewer, BYPASS_UNSAFE_PERMISSION)) {
                doTeleport(player, viewer, home);
                return;
            }
            promptUnsafeOrTeleport(player, viewer, home);
        });
    }

    private void promptUnsafeOrTeleport(Player player, PlayerRef viewer, Home home) {
        boolean unsafe = destinationUnsafe.test(home.location());
        scheduler.onEntity(viewer, () -> {
            if (!unsafe) {
                doTeleport(player, viewer, home);
                return;
            }
            confirm(
                    viewer,
                    text(viewer, HomesMessageKey.HOME_CONFIRM_UNSAFE_TP, Map.of()),
                    () -> doTeleport(player, viewer, home),
                    () -> open(viewer, home));
        });
    }

    private void doTeleport(Player player, PlayerRef viewer, Home home) {
        player.closeInventory();
        scheduler.async(() -> teleportHome.toSlot(viewer, home.slot()));
    }

    /** Delete the home, opening a confirm dialog first when {@code confirm-delete} is on. */
    private void handleDelete(MenuActionContext ctx) {
        PlayerRef viewer = ctx.viewer();
        Home home = home(ctx);
        if (confirmDelete) {
            confirm(
                    viewer,
                    text(viewer, HomesMessageKey.HOME_CONFIRM_DELETE, slotName(home)),
                    () -> doDelete(viewer, home),
                    () -> open(viewer, home));
        } else {
            doDelete(viewer, home);
        }
    }

    private void doDelete(PlayerRef viewer, Home home) {
        scheduler.async(() -> {
            deleteHome.delete(home.owner(), home.slot());
            scheduler.onEntity(viewer, () -> openList.accept(playerFor(viewer)));
        });
    }

    /** Re-anchor the home to the player's current position, optionally confirming first. */
    private void handleRelocate(MenuActionContext ctx) {
        Player player = ctx.player();
        PlayerRef viewer = ctx.viewer();
        Home home = home(ctx);
        Position at = com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs.toPosition(
                Objects.requireNonNull(player.getLocation(), "player location"));
        if (confirmRelocate) {
            confirm(
                    viewer,
                    text(viewer, HomesMessageKey.HOME_CONFIRM_RELOCATE, slotName(home)),
                    () -> doRelocate(viewer, home, at),
                    () -> open(viewer, home));
        } else {
            doRelocate(viewer, home, at);
        }
    }

    private void doRelocate(PlayerRef viewer, Home home, Position at) {
        scheduler.onRegion(at, () -> {
            if (blockUnsafeRelocate
                    && !permissions.has(viewer, BYPASS_UNSAFE_PERMISSION)
                    && destinationUnsafe.test(at)) {
                rejectRelocate(viewer, home, HomesMessageKey.HOME_UNSAFE_LOCATION);
                return;
            }
            ClaimDecision decision = claimService.canPlace(viewer, at);
            if (!decision.allowed()) {
                rejectRelocate(viewer, home, claimMessageKey(decision));
                return;
            }
            scheduler.async(() -> {
                relocateHome.relocate(home.owner(), home.slot(), at);
                scheduler.onEntity(viewer, () -> reopenFresh(viewer, home));
            });
        });
    }

    private void rejectRelocate(PlayerRef viewer, Home home, MessageKey key) {
        scheduler.onEntity(viewer, () -> {
            notifier.send(viewer, key);
            open(viewer, home);
        });
    }

    private void rename(MenuActionContext ctx) {
        Player player = ctx.player();
        PlayerRef viewer = ctx.viewer();
        Home home = home(ctx);
        player.closeInventory();
        textInput.prompt(
                player,
                viewer,
                InputRequest.of(HOME_RENAME_INPUT_KEY, HomesMessageKey.HOME_RENAME_PROMPT),
                text -> handleRenameInput(viewer, home, text),
                () -> open(viewer, home));
    }

    /**
     * Apply typed rename input. Blank or overlong text is rejected (the menu reopens unchanged); valid input renames
     * off-thread and reopens with the re-read home. Public so the golden test can drive it without a live prompt.
     */
    public void handleRenameInput(PlayerRef viewer, Home home, String input) {
        String text = input.strip();
        if (text.isBlank() || text.length() > HomeLabel.MAX_LENGTH) {
            notifier.send(viewer, HomesMessageKey.HOME_RENAME_TOO_LONG);
            open(viewer, home);
            return;
        }
        HomeLabel label = HomeLabel.of(text);
        scheduler.async(() -> {
            renameHome.rename(home.owner(), home.slot(), Optional.of(label));
            scheduler.onEntity(viewer, () -> reopenFresh(viewer, home));
        });
    }

    /** Flip the home public/private off-thread, then reopen with the re-read home so the cell reflects the change. */
    private void toggleVisibility(MenuActionContext ctx) {
        PlayerRef viewer = ctx.viewer();
        Home home = home(ctx);
        scheduler.async(() -> {
            setHomeVisibility.setVisibility(home.owner(), home.slot(), !home.isPublic());
            scheduler.onEntity(viewer, () -> reopenFresh(viewer, home));
        });
    }

    /** Re-read the home from the store and reopen the action menu, falling back to the stale subject when gone. */
    private void reopenFresh(PlayerRef viewer, Home home) {
        Home updated = repository.findSlot(home.owner(), home.slot()).orElse(home);
        open(viewer, updated);
    }

    private void confirm(PlayerRef viewer, Component title, Runnable onConfirm, Runnable onCancel) {
        menus.confirm(viewer, title, onConfirm, onCancel);
    }

    private Player playerFor(PlayerRef viewer) {
        return Objects.requireNonNull(
                org.bukkit.Bukkit.getPlayer(viewer.uuid()), "viewer must be online to reopen the grid");
    }

    private String label(Home home) {
        return home.label()
                .map(HomeLabel::value)
                .orElseGet(() -> Integer.toString(home.slot().displayNumber()));
    }

    private Map<String, String> slotName(Home home) {
        return Map.of("home", label(home), "slot", Integer.toString(home.slot().displayNumber()));
    }

    private Component text(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        return StyledText.render(messages.resolve(viewer, key, placeholders));
    }

    private static HomesMessageKey claimMessageKey(ClaimDecision decision) {
        return switch (decision) {
            case DENIED_FOREIGN -> HomesMessageKey.HOME_CLAIM_FOREIGN;
            case DENIED_REQUIRED -> HomesMessageKey.HOME_CLAIM_REQUIRED;
            case DENIED_TOO_CLOSE -> HomesMessageKey.HOME_CLAIM_TOO_CLOSE;
            case DENIED_ACCESS -> HomesMessageKey.HOME_CLAIM_ACCESS_DENIED;
            case ALLOWED -> throw new IllegalArgumentException("ALLOWED is not a denial");
        };
    }

    /**
     * The collaborators the action menu needs, grouped so the constructor stays readable. Built once at homes wiring
     * time; the three openers re-enter the icon picker, the invited-players list, and the {@code /home} grid without
     * this menu knowing which renders them.
     */
    public record Collaborators(
            Menus menus,
            Messages messages,
            Notifier notifier,
            Permissions permissions,
            Scheduler scheduler,
            TeleportHome teleportHome,
            DeleteHome deleteHome,
            RelocateHome relocateHome,
            RenameHome renameHome,
            SetHomeVisibility setHomeVisibility,
            HomeRepository repository,
            TextInput textInput,
            DateTimeFormatter dateFormat,
            boolean confirmDelete,
            boolean confirmRelocate,
            boolean confirmUnsafeTeleport,
            boolean blockUnsafeRelocate,
            Predicate<Position> destinationUnsafe,
            ClaimService claimService,
            BiConsumer<PlayerRef, Home> openIconPicker,
            BiConsumer<PlayerRef, Home> openInvitesMenu,
            Consumer<Player> openList) {}
}
