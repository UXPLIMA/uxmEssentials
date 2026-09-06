package com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

import com.uxplima.uxmessentials.playerwarps.application.ArchivePlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.BuySponsorship;
import com.uxplima.uxmessentials.playerwarps.application.EditPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.PlayerwarpsMessageKey;
import com.uxplima.uxmessentials.playerwarps.application.SponsorConfig;
import com.uxplima.uxmessentials.playerwarps.application.TransferPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.WarpAuthorization;
import com.uxplima.uxmessentials.playerwarps.application.WithdrawEarnings;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.DisplayName;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.playerwarps.domain.WarpCapability;
import com.uxplima.uxmessentials.playerwarps.domain.WarpDescription;
import com.uxplima.uxmessentials.playerwarps.domain.WarpRole;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.warps.domain.WarpCost;
import org.jspecify.annotations.NullMarked;

/**
 * Registers and opens the capability-gated single-warp management panel ({@code pwarp-manage}) the detail panel's
 * manage button opens. The warp and the viewer's {@link WarpRole} on it are the engine subject: {@link #open} resolves
 * both once off the tick thread (a bounded {@code findByName} plus one role read, never a full-table scan) snapshots
 * them into an immutable {@link Subject}, and hands that to the engine, which paints the window on the viewer's entity
 * thread. A viewer with no role (a stranger) is refused there, so the panel only opens for the owner or a delegate.
 *
 * <p>Every button is drawn only when the viewer's role grants the button's {@link WarpCapability}: the
 * {@code pwarp-viewer-can:<CAP>} view condition reads the role off the subject and answers {@link WarpRole#can}, a pure
 * read with no port call at render. So a {@code MANAGER} sees only the metadata buttons, a {@code CO_OWNER} everything
 * but transfer and delete, and the {@code OWNER} the full set. The same policy the use cases enforce, mirrored in the
 * UI so a manager never even sees the money or lifecycle controls. The gating is only presentation; each use case still
 * re-checks authority itself.
 *
 * <p>Each button routes through the same P4 use case the {@code /pwarp} verbs drive: metadata, access, password, and
 * price through {@link EditPlayerWarp}; the bank through {@link WithdrawEarnings}; ownership through
 * {@link TransferPlayerWarp}; delete (a recoverable archive) through {@link ArchivePlayerWarp}. A value-carrying click
 * id is single-segment ({@code pwarp-rename}, {@code pwarp-price}, ...) because the engine splits a value token on its
 * first colon; value-free ids stay namespaced. The password rides the engine's {@code input:} step into the set action
 * and is only ever handed to the use case: never echoed, logged, or placed in a message. Every write runs off the tick
 * thread, then the panel re-opens with the re-read subject so the new state shows.
 */
@NullMarked
public final class PlayerWarpManageMenu {

    /** The engine spec id the management panel registers and opens under. */
    public static final String MANAGE_SPEC_ID = "pwarp-manage";

    private static final String MANAGE_RESOURCE = "modules/playerwarps/gui/pwarp-manage.conf";

    private final Menus menus;
    private final Scheduler scheduler;
    private final PlayerWarpRepository repository;
    private final WarpAuthorization authorization;
    private final EditPlayerWarp editPlayerWarp;
    private final WithdrawEarnings withdrawEarnings;
    private final TransferPlayerWarp transferPlayerWarp;
    private final ArchivePlayerWarp archivePlayerWarp;
    private final BuySponsorship buySponsorship;
    private final PlayerLookup players;
    private final Messages messages;
    private final Notifier notifier;
    private final BiConsumer<PlayerRef, PlayerWarpName> openView;
    private final BiConsumer<PlayerRef, PlayerWarpName> openMembers;
    private final BiConsumer<PlayerRef, PlayerWarpName> openWhitelist;
    private final BiConsumer<PlayerRef, PlayerWarpName> openBans;
    private final BiConsumer<PlayerRef, PlayerWarpName> openIcon;
    private final boolean sponsorEnabled;
    private final int sponsorDefaultDays;

    public PlayerWarpManageMenu(
            Menus menus,
            Scheduler scheduler,
            PlayerWarpRepository repository,
            WarpAuthorization authorization,
            EditPlayerWarp editPlayerWarp,
            WithdrawEarnings withdrawEarnings,
            TransferPlayerWarp transferPlayerWarp,
            ArchivePlayerWarp archivePlayerWarp,
            BuySponsorship buySponsorship,
            PlayerLookup players,
            Messages messages,
            Notifier notifier,
            SponsorConfig sponsorConfig,
            BiConsumer<PlayerRef, PlayerWarpName> openView,
            BiConsumer<PlayerRef, PlayerWarpName> openMembers,
            BiConsumer<PlayerRef, PlayerWarpName> openWhitelist,
            BiConsumer<PlayerRef, PlayerWarpName> openBans,
            BiConsumer<PlayerRef, PlayerWarpName> openIcon) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.editPlayerWarp = Objects.requireNonNull(editPlayerWarp, "editPlayerWarp");
        this.withdrawEarnings = Objects.requireNonNull(withdrawEarnings, "withdrawEarnings");
        this.transferPlayerWarp = Objects.requireNonNull(transferPlayerWarp, "transferPlayerWarp");
        this.archivePlayerWarp = Objects.requireNonNull(archivePlayerWarp, "archivePlayerWarp");
        this.buySponsorship = Objects.requireNonNull(buySponsorship, "buySponsorship");
        this.players = Objects.requireNonNull(players, "players");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        Objects.requireNonNull(sponsorConfig, "sponsorConfig");
        this.sponsorEnabled = sponsorConfig.enabled();
        this.sponsorDefaultDays = sponsorConfig.durationDays();
        this.openView = Objects.requireNonNull(openView, "openView");
        this.openMembers = Objects.requireNonNull(openMembers, "openMembers");
        this.openWhitelist = Objects.requireNonNull(openWhitelist, "openWhitelist");
        this.openBans = Objects.requireNonNull(openBans, "openBans");
        this.openIcon = Objects.requireNonNull(openIcon, "openIcon");
    }

    /** Register the placeholders, the capability condition, the click actions, and the spec; called once at wiring time. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.placeholder("pwarp_manage_name", this::name);
        bindings.placeholder("pwarp_manage_lore", this::lore);
        // The capability gate: pwarp-viewer-can:<CAP> reads the role off the subject and answers role.can(CAP). It is a
        // single-segment id because the engine splits the :CAP value on the first colon. A namespaced head would keep
        // the whole feature:action:value token and never resolve. The sponsor button is drawn only when the sponsor
        // sub-group is enabled, which the subject carries from config.
        bindings.condition("pwarp-viewer-can", this::viewerCan);
        bindings.condition(
                "playerwarps:manage-sponsor", (ctx, args) -> subject(ctx).sponsorEnabled());
        registerActions(bindings);
        menus.registerSpec(MANAGE_SPEC_ID, MenuSpecs.loadOrBundled(MANAGE_RESOURCE, dataFolder, 6, log));
    }

    /** Bind every management verb. Value-carrying ids are single-segment; the value-free ones stay namespaced. */
    private void registerActions(MenuBindings bindings) {
        bindings.action("pwarp-rename", this::rename);
        bindings.action("pwarp-displayname", ctx -> editOptional(ctx, DisplayName::of, editPlayerWarp::setDisplayName));
        bindings.action(
                "pwarp-description", ctx -> editOptional(ctx, WarpDescription::of, editPlayerWarp::setDescription));
        // The icon button opens the pwarp-icon palette selector for the subject warp rather than prompting for a
        // material; the selector's pick routes back through EditPlayerWarp#setIcon, the same use case a typed edit did.
        bindings.action(
                "playerwarps:manage-icon",
                ctx -> openIcon.accept(ctx.viewer(), subject(ctx).name()));
        bindings.action("pwarp-category", ctx -> editOptional(ctx, s -> s, editPlayerWarp::setCategory));
        bindings.action("pwarp-price", this::setPrice);
        bindings.action("pwarp-password", this::setPassword);
        bindings.action("pwarp-transfer", this::transfer);
        bindings.action("pwarp-sponsor", this::buySponsor);
        bindings.action("playerwarps:manage-access", this::cycleAccess);
        bindings.action("playerwarps:manage-clearpw", this::clearPassword);
        bindings.action("playerwarps:manage-move", this::move);
        bindings.action("playerwarps:manage-withdraw", this::withdraw);
        bindings.action("playerwarps:manage-delete", this::delete);
        bindings.action("playerwarps:manage-back", this::back);
        bindings.action(
                "playerwarps:manage-members",
                ctx -> openMembers.accept(ctx.viewer(), subject(ctx).name()));
        bindings.action(
                "playerwarps:manage-whitelist",
                ctx -> openWhitelist.accept(ctx.viewer(), subject(ctx).name()));
        bindings.action(
                "playerwarps:manage-bans",
                ctx -> openBans.accept(ctx.viewer(), subject(ctx).name()));
    }

    /**
     * Open the management panel for the warp named {@code name}, on {@code viewer}'s behalf. The subject is resolved
     * off the tick thread, one bounded warp read plus the viewer's role, then the engine paints the window on the
     * viewer's entity thread. A warp that has since gone tells the viewer it is not found; a viewer who holds no role
     * on it is refused rather than shown an empty panel.
     */
    public void open(PlayerRef viewer, PlayerWarpName name) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(name, "name");
        scheduler.async(() -> {
            Optional<PlayerWarp> found = repository.findByName(name);
            if (found.isEmpty()) {
                notifier.send(viewer, PlayerWarpError.NOT_FOUND.messageKey(), Map.of("warp", name.value()));
                return;
            }
            PlayerWarp warp = found.get();
            Optional<WarpRole> role = authorization.roleFor(warp, viewer.uuid());
            if (role.isEmpty()) {
                notifier.send(viewer, PlayerWarpError.NO_PERMISSION.messageKey(), Map.of("warp", name.value()));
                return;
            }
            menus.open(viewer, MANAGE_SPEC_ID, new Subject(warp, role.get(), sponsorEnabled));
        });
    }

    /** Whether the viewer's role grants the capability the button carries as its value; a bad value fails closed. */
    private boolean viewerCan(MenuContext ctx, Map<String, String> args) {
        return capability(args.getOrDefault("value", ""))
                .map(capability -> subject(ctx).viewerRole().can(capability))
                .orElse(false);
    }

    /** Rename the warp to the typed line, reopening under the new name on success and the old one on any refusal. */
    private void rename(MenuActionContext ctx) {
        PlayerRef viewer = ctx.viewer();
        PlayerWarpName current = subject(ctx).name();
        Optional<PlayerWarpName> parsed = parseName(ctx.arg());
        if (parsed.isEmpty()) {
            notifier.send(viewer, PlayerwarpsMessageKey.PWARP_INVALID_NAME, Map.of("value", ctx.arg()));
            open(viewer, current);
            return;
        }
        PlayerWarpName newName = parsed.get();
        scheduler.async(() -> {
            boolean renamed = editPlayerWarp.rename(viewer, current, newName).isOk();
            open(viewer, renamed ? newName : current);
        });
    }

    /**
     * Apply a metadata edit that takes an {@code Optional<T>}: a blank or {@code "-"} line clears (empty), any other
     * line is built through {@code factory} and, when it validates, set. A value the value object rejects sends the
     * invalid-value notice and changes nothing, so a bad line is never a stack trace. The panel reopens either way.
     */
    private <T> void editOptional(MenuActionContext ctx, Function<String, T> factory, EditVerb<T> edit) {
        PlayerRef viewer = ctx.viewer();
        PlayerWarpName name = subject(ctx).name();
        Optional<String> raw = optionalText(ctx.arg());
        if (raw.isEmpty()) {
            scheduler.async(() -> {
                edit.apply(viewer, name, Optional.empty());
                open(viewer, name);
            });
            return;
        }
        T value;
        try {
            value = factory.apply(raw.get());
        } catch (IllegalArgumentException invalid) {
            notifier.send(viewer, PlayerwarpsMessageKey.PWARP_INVALID_NAME, Map.of("value", raw.get()));
            open(viewer, name);
            return;
        }
        scheduler.async(() -> {
            edit.apply(viewer, name, Optional.of(value));
            open(viewer, name);
        });
    }

    /** Cycle the access axis one step (PUBLIC → PASSWORD → WHITELIST → PRIVATE → PUBLIC), then reopen the panel. */
    private void cycleAccess(MenuActionContext ctx) {
        PlayerRef viewer = ctx.viewer();
        Subject subject = subject(ctx);
        PlayerWarpName name = subject.name();
        WarpAccess next = nextAccess(subject.warp().access());
        scheduler.async(() -> {
            editPlayerWarp.setAccess(viewer, name, next);
            open(viewer, name);
        });
    }

    /** Store the typed line as the warp's password, off the tick thread; a blank line is ignored, never stored. */
    private void setPassword(MenuActionContext ctx) {
        PlayerRef viewer = ctx.viewer();
        PlayerWarpName name = subject(ctx).name();
        String password = ctx.arg();
        if (password.isBlank()) {
            open(viewer, name);
            return;
        }
        scheduler.async(() -> {
            editPlayerWarp.setPassword(viewer, name, password);
            open(viewer, name);
        });
    }

    /** Clear the warp's password (the confirm's accept branch), then reopen the panel. */
    private void clearPassword(MenuActionContext ctx) {
        PlayerRef viewer = ctx.viewer();
        PlayerWarpName name = subject(ctx).name();
        scheduler.async(() -> {
            editPlayerWarp.clearPassword(viewer, name);
            open(viewer, name);
        });
    }

    /** Set the entry price parsed from {@code amount [currency]}; an unparsable line notices and changes nothing. */
    private void setPrice(MenuActionContext ctx) {
        PlayerRef viewer = ctx.viewer();
        PlayerWarpName name = subject(ctx).name();
        Optional<WarpCost> price = parsePrice(ctx.arg());
        if (price.isEmpty()) {
            notifier.send(viewer, PlayerwarpsMessageKey.PWARP_INVALID_NAME, Map.of("value", ctx.arg()));
            open(viewer, name);
            return;
        }
        scheduler.async(() -> {
            editPlayerWarp.setPrice(viewer, name, price.get());
            open(viewer, name);
        });
    }

    /** Re-anchor the warp where the viewer stands; the position is read here on the entity thread, the save off it. */
    private void move(MenuActionContext ctx) {
        PlayerRef viewer = ctx.viewer();
        PlayerWarpName name = subject(ctx).name();
        Position at = BukkitRefs.toPosition(Objects.requireNonNull(ctx.player().getLocation(), "player location"));
        scheduler.async(() -> {
            editPlayerWarp.moveHere(viewer, name, at);
            open(viewer, name);
        });
    }

    /** Pay the warp's earnings bank out to the owner, then reopen so the new balance shows. */
    private void withdraw(MenuActionContext ctx) {
        PlayerRef viewer = ctx.viewer();
        PlayerWarpName name = subject(ctx).name();
        scheduler.async(() -> {
            withdrawEarnings.withdraw(viewer, name);
            open(viewer, name);
        });
    }

    /**
     * Hand the warp to the player named on the typed line (the confirm's accept branch). An unknown name notices and
     * changes nothing; a successful transfer strips the viewer's owner role, so the panel closes back to the public
     * detail view rather than reopening a panel the viewer may no longer manage.
     */
    private void transfer(MenuActionContext ctx) {
        PlayerRef viewer = ctx.viewer();
        PlayerWarpName name = subject(ctx).name();
        String targetName = ctx.arg().strip();
        if (targetName.isEmpty()) {
            open(viewer, name);
            return;
        }
        scheduler.async(() -> {
            Optional<PlayerRef> newOwner = players.findByName(targetName);
            if (newOwner.isEmpty()) {
                notifier.send(viewer, PlayerwarpsMessageKey.PWARP_INVALID_NAME, Map.of("value", targetName));
                open(viewer, name);
                return;
            }
            if (transferPlayerWarp.transfer(viewer, name, newOwner.get()).isOk()) {
                openView.accept(viewer, name);
            } else {
                open(viewer, name);
            }
        });
    }

    /**
     * Buy sponsorship for the warp with the term typed on the prompt (blank or unparsable falls back to the configured
     * default). The purchase, the guarded owner debit and the slot write, runs off the tick thread through
     * {@link BuySponsorship}, which delivers its own success or refusal notice; the panel reopens so the new state shows.
     */
    private void buySponsor(MenuActionContext ctx) {
        PlayerRef viewer = ctx.viewer();
        PlayerWarpName name = subject(ctx).name();
        int days = parseDays(ctx.arg());
        scheduler.async(() -> {
            buySponsorship.buy(viewer, name, days);
            open(viewer, name);
        });
    }

    /** The term from a typed line, or the configured default when it is blank or not a positive integer. */
    private int parseDays(String raw) {
        try {
            return Math.max(1, Integer.parseInt(raw.strip()));
        } catch (NumberFormatException notANumber) {
            return sponsorDefaultDays;
        }
    }

    /** Archive the warp (a recoverable delete; the confirm's accept branch) off the tick thread; the window stays closed. */
    private void delete(MenuActionContext ctx) {
        PlayerRef viewer = ctx.viewer();
        PlayerWarpName name = subject(ctx).name();
        scheduler.async(() -> archivePlayerWarp.archive(viewer, name));
    }

    /** Back to the pwarp-view detail panel for the same warp. */
    private void back(MenuActionContext ctx) {
        openView.accept(ctx.viewer(), subject(ctx).name());
    }

    /** The subject warp's display name for the info tile and the window title. */
    private String name(MenuContext ctx) {
        return messages.resolve(
                ctx.viewer(),
                PlayerwarpsMessageKey.PWARP_GUI_MANAGE_ENTRY_NAME,
                Map.of("warp", displayName(subject(ctx))));
    }

    /**
     * The info tile's management lore as the catalog lines joined by {@code \n}: owner, access, an optional price,
     * the earnings bank balance, and an optional category. The engine's multi-line placeholder expansion splits this
     * back into one lore line each, rendered as MiniMessage.
     */
    private String lore(MenuContext ctx) {
        PlayerWarp warp = subject(ctx).warp();
        PlayerRef viewer = ctx.viewer();
        List<String> lines = new ArrayList<>();
        lines.add(line(viewer, PlayerwarpsMessageKey.PWARP_GUI_MANAGE_LORE_OWNER, Map.of("owner", warp.ownerName())));
        lines.add(line(
                viewer,
                PlayerwarpsMessageKey.PWARP_GUI_MANAGE_LORE_ACCESS,
                Map.of("access", warp.access().name().toLowerCase(Locale.ROOT))));
        if (warp.price().amount().signum() > 0) {
            lines.add(line(
                    viewer,
                    PlayerwarpsMessageKey.PWARP_GUI_MANAGE_LORE_PRICE,
                    Map.of(
                            "amount",
                            warp.price().amount().toPlainString(),
                            "currency",
                            warp.price().currencyId())));
        }
        lines.add(line(
                viewer,
                PlayerwarpsMessageKey.PWARP_GUI_MANAGE_LORE_BANK,
                Map.of(
                        "amount",
                        warp.earnings().amount().toPlainString(),
                        "currency",
                        warp.earnings().currencyId())));
        warp.categoryId()
                .ifPresent(category -> lines.add(line(
                        viewer, PlayerwarpsMessageKey.PWARP_GUI_MANAGE_LORE_CATEGORY, Map.of("category", category))));
        return String.join("\n", lines);
    }

    private static String displayName(Subject subject) {
        PlayerWarp warp = subject.warp();
        return warp.displayName()
                .map(DisplayName::value)
                .orElseGet(() -> warp.name().value());
    }

    /** The next access value in the cycle PUBLIC → PASSWORD → WHITELIST → PRIVATE → PUBLIC. */
    private static WarpAccess nextAccess(WarpAccess current) {
        return switch (current) {
            case PUBLIC -> WarpAccess.PASSWORD;
            case PASSWORD -> WarpAccess.WHITELIST;
            case WHITELIST -> WarpAccess.PRIVATE;
            case PRIVATE -> WarpAccess.PUBLIC;
        };
    }

    /** A capability from a spec value token, or empty when it is blank or names no capability (so the gate fails closed). */
    private static Optional<WarpCapability> capability(String token) {
        try {
            return Optional.of(WarpCapability.valueOf(token.strip().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException notACapability) {
            return Optional.empty();
        }
    }

    /** A warp name from raw input, or empty when the shape is invalid (so a bad line notices rather than throws). */
    private static Optional<PlayerWarpName> parseName(String raw) {
        try {
            return Optional.of(PlayerWarpName.of(raw));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    /**
     * A price parsed from {@code amount [currency]}: a non-negative decimal amount and an optional currency id. A blank
     * line, a non-numeric amount, or a negative amount yields empty, so the caller notices rather than pricing wrong.
     */
    private static Optional<WarpCost> parsePrice(String raw) {
        String[] parts = raw.strip().split("\\s+", 2);
        if (parts[0].isEmpty()) {
            return Optional.empty();
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(parts[0]);
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
        if (amount.signum() < 0) {
            return Optional.empty();
        }
        return Optional.of(parts.length > 1 ? WarpCost.of(amount, parts[1].strip()) : WarpCost.of(amount));
    }

    /** Interpret a free-text line as optional: a blank or {@code "-"} clears (empty), anything else is the trimmed text. */
    private static Optional<String> optionalText(String raw) {
        String trimmed = raw.strip();
        return trimmed.isEmpty() || trimmed.equals("-") ? Optional.empty() : Optional.of(trimmed);
    }

    private Subject subject(MenuContext ctx) {
        return ctx.subject(Subject.class);
    }

    private Subject subject(MenuActionContext ctx) {
        return ctx.subject(Subject.class);
    }

    private String line(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        return messages.resolve(viewer, key, placeholders);
    }

    /** One metadata edit verb the {@link #editOptional} shape drives: set (or clear) a value object on the warp. */
    @FunctionalInterface
    private interface EditVerb<T> {
        void apply(PlayerRef actor, PlayerWarpName name, Optional<T> value);
    }

    /**
     * The subject of an open management panel: the snapshotted warp and the viewer's role on it. Resolved on the async
     * thread at open and read only by the render-time capability condition and the click actions on the viewer's entity
     * thread, so it touches no per-viewer mutable state.
     *
     * @param warp the warp the panel manages, snapshotted at open
     * @param viewerRole the role the viewer holds on the warp (OWNER / CO_OWNER / MANAGER), the capability gate reads it
     * @param sponsorEnabled whether the P6 sponsor sub-feature is enabled; false until P6 wires the flag, so the sponsor
     *     button never renders yet
     */
    public record Subject(PlayerWarp warp, WarpRole viewerRole, boolean sponsorEnabled) {

        public Subject {
            Objects.requireNonNull(warp, "warp");
            Objects.requireNonNull(viewerRole, "viewerRole");
        }

        /** The warp's server-wide-unique name, the identity every use case is addressed by. */
        public PlayerWarpName name() {
            return warp.name();
        }
    }
}
