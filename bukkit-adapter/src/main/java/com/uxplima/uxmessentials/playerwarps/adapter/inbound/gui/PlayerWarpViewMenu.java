package com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.playerwarps.application.FavouritePlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.PlayerwarpsMessageKey;
import com.uxplima.uxmessentials.playerwarps.application.RatePlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.UsePlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpFavouriteStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpMemberStore;
import com.uxplima.uxmessentials.playerwarps.domain.DisplayName;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Registers and opens the per-warp detail panel ({@code pwarp-view}) a browse tile opens, and the five-star rating
 * menu ({@code pwarp-rate}) behind its rate button. The clicked warp is the engine subject: {@link #open} resolves it
 * once off the tick thread. A bounded {@code findByName} plus the viewer's favourite and membership flags, never a
 * full-table scan, snapshots it into an immutable {@link Subject}, and hands that to the engine, which then paints the
 * window on the viewer's entity thread. Opening the panel is read-only; only a button click runs a use case.
 *
 * <p>Each button routes through the same use case the {@code /pwarp} command drives. Teleport goes through
 * {@link UsePlayerWarp}: an open card teleports straight with no password, while a PASSWORD-access card the viewer is
 * neither owner nor member of is shown a locked teleport button whose click prompts for the password through the
 * engine's {@code input:} step and threads the typed line into {@code useFor(..., Optional.of(entered))}, the plaintext
 * is only ever handed to the use case, never echoed, logged, or placed in a message. Favourite / unfavourite toggle
 * through {@link FavouritePlayerWarp}, the pair of buttons picked by a {@code favourited} view condition. Rate opens
 * {@code pwarp-rate}, whose star buttons call {@link RatePlayerWarp}; the rate button is hidden for the owner (a
 * {@code not-owner} view condition), and the use case rejects an owner rating defensively even so. Every write runs off
 * the tick thread, then the panel re-opens with the re-read subject so the new state shows.
 */
@NullMarked
public final class PlayerWarpViewMenu {

    /** The engine spec id the detail panel registers and opens under. */
    public static final String VIEW_SPEC_ID = "pwarp-view";

    /** The engine spec id the five-star rating menu registers and opens under. */
    public static final String RATE_SPEC_ID = "pwarp-rate";

    private static final String VIEW_RESOURCE = "modules/playerwarps/gui/pwarp-view.conf";
    private static final String RATE_RESOURCE = "modules/playerwarps/gui/pwarp-rate.conf";

    private final Menus menus;
    private final Scheduler scheduler;
    private final PlayerWarpRepository repository;
    private final WarpFavouriteStore favourites;
    private final WarpMemberStore members;
    private final UsePlayerWarp usePlayerWarp;
    private final FavouritePlayerWarp favouritePlayerWarp;
    private final RatePlayerWarp ratePlayerWarp;
    private final Messages messages;
    private final Notifier notifier;
    private final BiConsumer<Player, PlayerRef> openBrowse;
    private final BiConsumer<PlayerRef, PlayerWarpName> openManage;

    public PlayerWarpViewMenu(
            Menus menus,
            Scheduler scheduler,
            PlayerWarpRepository repository,
            WarpFavouriteStore favourites,
            WarpMemberStore members,
            UsePlayerWarp usePlayerWarp,
            FavouritePlayerWarp favouritePlayerWarp,
            RatePlayerWarp ratePlayerWarp,
            Messages messages,
            Notifier notifier,
            BiConsumer<Player, PlayerRef> openBrowse,
            BiConsumer<PlayerRef, PlayerWarpName> openManage) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.favourites = Objects.requireNonNull(favourites, "favourites");
        this.members = Objects.requireNonNull(members, "members");
        this.usePlayerWarp = Objects.requireNonNull(usePlayerWarp, "usePlayerWarp");
        this.favouritePlayerWarp = Objects.requireNonNull(favouritePlayerWarp, "favouritePlayerWarp");
        this.ratePlayerWarp = Objects.requireNonNull(ratePlayerWarp, "ratePlayerWarp");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.openBrowse = Objects.requireNonNull(openBrowse, "openBrowse");
        this.openManage = Objects.requireNonNull(openManage, "openManage");
    }

    /** Register the placeholders, view conditions, click actions, and both specs; called once at wiring time. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.placeholder("pwarp_view_name", this::name);
        bindings.placeholder("pwarp_view_lore", this::lore);
        bindings.condition(
                "playerwarps:no-password", (ctx, args) -> !subject(ctx).passwordNeeded());
        bindings.condition(
                "playerwarps:password-required", (ctx, args) -> subject(ctx).passwordNeeded());
        bindings.condition(
                "playerwarps:not-favourited", (ctx, args) -> !subject(ctx).viewerFavourited());
        bindings.condition("playerwarps:favourited", (ctx, args) -> subject(ctx).viewerFavourited());
        bindings.condition("playerwarps:not-owner", (ctx, args) -> !subject(ctx).viewerOwner());
        // The manage button shows only to a viewer who owns the warp or holds a role on it (a member: owner, co-owner,
        // or manager), the same set the pwarp-manage capability gate admits, so a stranger never sees it.
        bindings.condition(
                "playerwarps:viewer-member", (ctx, args) -> subject(ctx).viewerMember());
        // Teleport and rate carry a value in their ref token (the typed password as %input%, the star count), which the
        // engine can only split off a single-segment head. A namespaced feature:action id would keep the whole
        // feature:action:value token as its id and never resolve. So these two use single-segment ids; the value-free
        // actions stay namespaced.
        bindings.action("pwarp-teleport", this::teleport);
        bindings.action("pwarp-rate", this::rate);
        bindings.action("playerwarps:view-favourite", ctx -> toggleFavourite(ctx, true));
        bindings.action("playerwarps:view-unfavourite", ctx -> toggleFavourite(ctx, false));
        bindings.action("playerwarps:view-rate", ctx -> menus.open(ctx.viewer(), RATE_SPEC_ID, subject(ctx)));
        bindings.action(
                "playerwarps:view-manage",
                ctx -> openManage.accept(ctx.viewer(), subject(ctx).name()));
        bindings.action("playerwarps:view-back", ctx -> openBrowse.accept(ctx.player(), ctx.viewer()));
        bindings.action(
                "playerwarps:rate-back", ctx -> open(ctx.viewer(), subject(ctx).name()));
        menus.registerSpec(VIEW_SPEC_ID, MenuSpecs.loadOrBundled(VIEW_RESOURCE, dataFolder, 3, log));
        menus.registerSpec(RATE_SPEC_ID, MenuSpecs.loadOrBundled(RATE_RESOURCE, dataFolder, 3, log));
    }

    /**
     * Open the detail panel for the warp named {@code name}, on {@code viewer}'s behalf. The subject is resolved off
     * the tick thread (one bounded warp read plus the viewer's favourite/membership flags), then the engine paints the
     * window on the viewer's entity thread; a warp that has since gone tells the viewer it is not found and opens
     * nothing.
     */
    public void open(PlayerRef viewer, PlayerWarpName name) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(name, "name");
        scheduler.async(() -> resolve(viewer, name)
                .ifPresentOrElse(
                        subject -> menus.open(viewer, VIEW_SPEC_ID, subject),
                        () -> notifier.send(
                                viewer, PlayerWarpError.NOT_FOUND.messageKey(), Map.of("warp", name.value()))));
    }

    /** Snapshot the warp and the viewer's relationship to it (favourited, owner, member) into an immutable subject. */
    private Optional<Subject> resolve(PlayerRef viewer, PlayerWarpName name) {
        Optional<PlayerWarp> found = repository.findByName(name);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        PlayerWarp warp = found.get();
        PlayerWarpId id = warp.id().orElseThrow();
        boolean owner = warp.owner().uuid().equals(viewer.uuid());
        boolean member = owner || members.roleOf(id, viewer.uuid()).isPresent();
        return Optional.of(new Subject(warp, favourites.contains(viewer.uuid(), id), owner, member));
    }

    /**
     * Teleport the viewer to the subject warp through the {@link UsePlayerWarp} gate. The action arg carries the typed
     * password on the locked card's {@code input:} chain (as {@code %input%}) and is blank on the open card, so a blank
     * arg passes {@link Optional#empty()} and a typed one {@code Optional.of(entered)}. The plaintext is never echoed
     * or logged. The write runs off the tick thread; the window closes on the viewer's entity thread this click runs on.
     */
    private void teleport(MenuActionContext ctx) {
        Subject subject = subject(ctx);
        PlayerRef viewer = ctx.viewer();
        PlayerWarpName name = subject.name();
        Optional<String> password = ctx.arg().isBlank() ? Optional.empty() : Optional.of(ctx.arg());
        ctx.player().closeInventory();
        scheduler.async(() -> usePlayerWarp.useFor(viewer, name, password));
    }

    /** Star or un-star the subject warp off the tick thread, then re-open the panel so the button pair flips. */
    private void toggleFavourite(MenuActionContext ctx, boolean add) {
        PlayerRef viewer = ctx.viewer();
        PlayerWarpName name = subject(ctx).name();
        scheduler.async(() -> {
            if (add) {
                favouritePlayerWarp.favourite(viewer, name);
            } else {
                favouritePlayerWarp.unfavourite(viewer, name);
            }
            open(viewer, name);
        });
    }

    /**
     * Award the subject warp the star count carried in the action arg (1..5 from the rate spec's buttons) through
     * {@link RatePlayerWarp}, off the tick thread, then re-open the detail panel so the new rating shows. A
     * non-numeric arg is ignored rather than rated, so a malformed spec never rates zero stars.
     */
    private void rate(MenuActionContext ctx) {
        int stars = parseStars(ctx.arg());
        if (stars < 1) {
            return;
        }
        PlayerRef viewer = ctx.viewer();
        PlayerWarpName name = subject(ctx).name();
        scheduler.async(() -> {
            ratePlayerWarp.rate(viewer, name, stars);
            open(viewer, name);
        });
    }

    /** The subject warp's display name, resolved from the catalog for the info item and the window. */
    private String name(MenuContext ctx) {
        Subject subject = subject(ctx);
        return resolve(
                ctx.viewer(), PlayerwarpsMessageKey.PWARP_GUI_VIEW_ENTRY_NAME, Map.of("warp", displayName(subject)));
    }

    /**
     * The subject warp's full detail lore as the catalog lines joined by {@code \n} in a fixed order, owner, world,
     * optional server, visits, unique visitors, rating, favourites, optional price, access. The engine's multi-line
     * placeholder expansion splits this back into one lore component per line, each rendered as MiniMessage.
     */
    private String lore(MenuContext ctx) {
        PlayerWarp warp = subject(ctx).warp();
        PlayerRef viewer = ctx.viewer();
        List<String> lines = new ArrayList<>();
        lines.add(line(viewer, PlayerwarpsMessageKey.PWARP_GUI_VIEW_LORE_OWNER, Map.of("owner", warp.ownerName())));
        lines.add(line(
                viewer,
                PlayerwarpsMessageKey.PWARP_GUI_VIEW_LORE_WORLD,
                Map.of("world", warp.location().world().name())));
        warp.serverId()
                .ifPresent(server -> lines.add(
                        line(viewer, PlayerwarpsMessageKey.PWARP_GUI_VIEW_LORE_SERVER, Map.of("server", server))));
        lines.add(line(
                viewer,
                PlayerwarpsMessageKey.PWARP_GUI_VIEW_LORE_VISITS,
                Map.of("visits", Long.toString(warp.visits().count()))));
        lines.add(line(
                viewer,
                PlayerwarpsMessageKey.PWARP_GUI_VIEW_LORE_UNIQUE,
                Map.of("unique", Integer.toString(warp.visits().uniqueVisitors()))));
        lines.add(line(
                viewer,
                PlayerwarpsMessageKey.PWARP_GUI_VIEW_LORE_RATING,
                Map.of(
                        "rating",
                        rating(warp.ratings().average()),
                        "count",
                        Integer.toString(warp.ratings().count()))));
        lines.add(line(
                viewer,
                PlayerwarpsMessageKey.PWARP_GUI_VIEW_LORE_FAVOURITES,
                Map.of("favourites", Integer.toString(warp.favouriteCount()))));
        if (warp.price().amount().signum() > 0) {
            lines.add(line(
                    viewer,
                    PlayerwarpsMessageKey.PWARP_GUI_VIEW_LORE_PRICE,
                    Map.of(
                            "amount",
                            warp.price().amount().toPlainString(),
                            "currency",
                            warp.price().currencyId())));
        }
        lines.add(line(
                viewer,
                PlayerwarpsMessageKey.PWARP_GUI_VIEW_LORE_ACCESS,
                Map.of("access", warp.access().name().toLowerCase(Locale.ROOT))));
        return String.join("\n", lines);
    }

    private static String displayName(Subject subject) {
        PlayerWarp warp = subject.warp();
        return warp.displayName()
                .map(DisplayName::value)
                .orElseGet(() -> warp.name().value());
    }

    /** The mean rating rounded to one decimal, as a plain data string (never a chat-format call). */
    private static String rating(double average) {
        return Double.toString(Math.round(average * 10.0) / 10.0);
    }

    /** The star count in an action arg, or {@code 0} when it is blank or not a whole number (so a bad arg is ignored). */
    private static int parseStars(String arg) {
        try {
            return Integer.parseInt(arg.strip());
        } catch (NumberFormatException notANumber) {
            return 0;
        }
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

    private String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        return messages.resolve(viewer, key, placeholders);
    }

    /**
     * The subject of an open detail panel (and the rating menu it opens): the snapshotted warp and the viewer's
     * relationship to it. Resolved on the async thread at open and read only by the render-time conditions and the
     * click actions on the viewer's entity thread, so it touches no per-viewer mutable state.
     *
     * @param warp the warp the panel is about, snapshotted at open
     * @param viewerFavourited whether the viewer has already starred the warp (picks the favourite/unfavourite cell)
     * @param viewerOwner whether the viewer owns the warp (hides the rate button)
     * @param viewerMember whether the viewer owns or holds a role on the warp (a member skips the password prompt)
     */
    public record Subject(PlayerWarp warp, boolean viewerFavourited, boolean viewerOwner, boolean viewerMember) {

        public Subject {
            Objects.requireNonNull(warp, "warp");
        }

        /** The warp's server-wide-unique name, the identity every use case is addressed by. */
        public PlayerWarpName name() {
            return warp.name();
        }

        /** Whether the viewer must supply a password to teleport: a PASSWORD-access warp they are not a member of. */
        public boolean passwordNeeded() {
            return warp.access() == WarpAccess.PASSWORD && !viewerMember;
        }
    }
}
