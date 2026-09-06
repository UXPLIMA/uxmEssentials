package com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui;

import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

import com.uxplima.uxmessentials.playerwarps.application.ManageBans;
import com.uxplima.uxmessentials.playerwarps.application.ManageMembers;
import com.uxplima.uxmessentials.playerwarps.application.ManageWhitelist;
import com.uxplima.uxmessentials.playerwarps.application.PlayerwarpsMessageKey;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpBanStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpMemberStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpWhitelistStore;
import com.uxplima.uxmessentials.playerwarps.domain.BanRecord;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.WarpMember;
import com.uxplima.uxmessentials.playerwarps.domain.WarpRole;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Registers and opens the three people-management sub-menus the manage panel's members / whitelist / bans buttons open:
 * {@code pwarp-members} (co-owners and managers), {@code pwarp-whitelist} (the guest list), and {@code pwarp-bans}
 * (barred players). Each is a snapshot list. A warp's people lists are bounded per-warp, so {@link #open} resolves the
 * warp off the tick thread, reads the one bounded store list, resolves each uuid's name, and hands the fully-resolved
 * rows in as the engine subject; the shared {@code playerwarps:people} list source only reads that subject, touching no
 * port off-thread. The window is then painted on the viewer's entity thread.
 *
 * <p>A row's left click removes that person through the same use case the {@code /pwarp} verb drives
 * {@link ManageMembers#removeMember}, {@link ManageWhitelist#unwhitelist}, {@link ManageBans#unban}, and each menu's
 * add button prompts for a player name through the engine's {@code input:} step and grants it: two buttons on the
 * members menu add a co-owner or a manager directly ({@link ManageMembers#addMember} with the fixed role, no nested
 * picker), the whitelist button whitelists, and the bans button imposes a permanent, reasonless ban
 * ({@link ManageBans#ban} with empty duration and reason: a timed or reasoned ban is the command's job). A
 * value-carrying add id is single-segment ({@code pwarp-mem-addco}, ...) because the engine splits the {@code %input%}
 * value on its first colon; the value-free remove/back ids stay namespaced. Every write runs off the tick thread, then
 * the sub-menu re-opens with the re-read rows. The back button returns to {@code pwarp-manage}. The manage panel only
 * shows each button to a viewer whose role holds the matching capability, so the gate is the manage panel's job and the
 * use cases re-check authority themselves regardless.
 */
@NullMarked
public final class PlayerWarpPeopleMenu {

    /** The engine spec id the shared {@code playerwarps:people} list source is declared under across the three specs. */
    private static final String PEOPLE_LIST = "playerwarps:people";

    private static final DateTimeFormatter BAN_UNTIL =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    private final Menus menus;
    private final Scheduler scheduler;
    private final PlayerWarpRepository repository;
    private final PlayerLookup players;
    private final WarpMemberStore memberStore;
    private final WarpWhitelistStore whitelistStore;
    private final WarpBanStore banStore;
    private final ManageMembers manageMembers;
    private final ManageWhitelist manageWhitelist;
    private final ManageBans manageBans;
    private final Messages messages;
    private final Notifier notifier;
    private final BiConsumer<PlayerRef, PlayerWarpName> openManage;

    public PlayerWarpPeopleMenu(
            Menus menus,
            Scheduler scheduler,
            PlayerWarpRepository repository,
            PlayerLookup players,
            WarpMemberStore memberStore,
            WarpWhitelistStore whitelistStore,
            WarpBanStore banStore,
            ManageMembers manageMembers,
            ManageWhitelist manageWhitelist,
            ManageBans manageBans,
            Messages messages,
            Notifier notifier,
            BiConsumer<PlayerRef, PlayerWarpName> openManage) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.players = Objects.requireNonNull(players, "players");
        this.memberStore = Objects.requireNonNull(memberStore, "memberStore");
        this.whitelistStore = Objects.requireNonNull(whitelistStore, "whitelistStore");
        this.banStore = Objects.requireNonNull(banStore, "banStore");
        this.manageMembers = Objects.requireNonNull(manageMembers, "manageMembers");
        this.manageWhitelist = Objects.requireNonNull(manageWhitelist, "manageWhitelist");
        this.manageBans = Objects.requireNonNull(manageBans, "manageBans");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.openManage = Objects.requireNonNull(openManage, "openManage");
    }

    /** Register the shared list source, the row placeholders, the click actions, and the three specs; once at wiring. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.list(PEOPLE_LIST, ctx -> subject(ctx).rows());
        bindings.placeholder("pwarp_people_name", ctx -> row(ctx).name());
        bindings.placeholder("pwarp_member_role", ctx -> row(ctx).role());
        bindings.placeholder("pwarp_ban_reason", ctx -> row(ctx).reason());
        bindings.placeholder("pwarp_ban_until", ctx -> row(ctx).until());
        bindings.action("playerwarps:members-remove", ctx -> remove(ctx, Kind.MEMBERS, manageMembers::removeMember));
        bindings.action(
                "playerwarps:whitelist-remove", ctx -> remove(ctx, Kind.WHITELIST, manageWhitelist::unwhitelist));
        bindings.action("playerwarps:bans-remove", ctx -> remove(ctx, Kind.BANS, manageBans::unban));
        bindings.action("pwarp-mem-addco", ctx -> add(ctx, Kind.MEMBERS, grant(WarpRole.CO_OWNER)));
        bindings.action("pwarp-mem-addmgr", ctx -> add(ctx, Kind.MEMBERS, grant(WarpRole.MANAGER)));
        bindings.action("pwarp-wl-add", ctx -> add(ctx, Kind.WHITELIST, manageWhitelist::whitelist));
        bindings.action("pwarp-ban-add", ctx -> add(ctx, Kind.BANS, this::banPermanent));
        bindings.action(
                "playerwarps:people-back",
                ctx -> openManage.accept(ctx.viewer(), subject(ctx).warp()));
        for (Kind kind : Kind.values()) {
            menus.registerSpec(kind.specId(), MenuSpecs.loadOrBundled(kind.resource(), dataFolder, 6, log));
        }
    }

    /** Open the members sub-menu for the warp named {@code name}, snapshotting its co-owners/managers off the tick. */
    public void openMembers(PlayerRef viewer, PlayerWarpName name) {
        open(viewer, name, Kind.MEMBERS);
    }

    /** Open the whitelist sub-menu for the warp named {@code name}, snapshotting its guest list off the tick thread. */
    public void openWhitelist(PlayerRef viewer, PlayerWarpName name) {
        open(viewer, name, Kind.WHITELIST);
    }

    /** Open the bans sub-menu for the warp named {@code name}, snapshotting its barred players off the tick thread. */
    public void openBans(PlayerRef viewer, PlayerWarpName name) {
        open(viewer, name, Kind.BANS);
    }

    /**
     * Resolve the warp and read the bounded store list for {@code kind} off the tick thread, resolving each uuid's
     * name there, then open the sub-menu with the rows as its subject. A warp that has since gone tells the viewer it
     * is not found and opens nothing.
     */
    private void open(PlayerRef viewer, PlayerWarpName name, Kind kind) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(name, "name");
        scheduler.async(() -> {
            Optional<PlayerWarp> found = repository.findByName(name);
            if (found.isEmpty()) {
                notifier.send(viewer, PlayerWarpError.NOT_FOUND.messageKey(), Map.of("warp", name.value()));
                return;
            }
            PlayerWarpId id = found.get().id().orElseThrow();
            List<PeopleRow> rows =
                    switch (kind) {
                        case MEMBERS -> memberRows(id);
                        case WHITELIST -> whitelistRows(id);
                        case BANS -> banRows(viewer, id);
                    };
            menus.open(viewer, kind.specId(), new PeopleView(name, rows));
        });
    }

    /** One row per member (co-owner / manager) with the resolved name and the role token as plain data. */
    private List<PeopleRow> memberRows(PlayerWarpId id) {
        List<PeopleRow> rows = new ArrayList<>();
        for (WarpMember member : memberStore.list(id)) {
            rows.add(new PeopleRow(
                    member.player(), name(member.player()), member.role().name().toLowerCase(Locale.ROOT), "", ""));
        }
        return rows;
    }

    /** One row per whitelisted player with the resolved name; role, reason and expiry are unused here. */
    private List<PeopleRow> whitelistRows(PlayerWarpId id) {
        List<PeopleRow> rows = new ArrayList<>();
        for (UUID player : whitelistStore.list(id)) {
            rows.add(new PeopleRow(player, name(player), "", "", ""));
        }
        return rows;
    }

    /** One row per ban with the resolved name, reason (or the catalog none) and expiry (or the catalog permanent). */
    private List<PeopleRow> banRows(PlayerRef viewer, PlayerWarpId id) {
        List<PeopleRow> rows = new ArrayList<>();
        for (BanRecord record : banStore.list(id)) {
            rows.add(new PeopleRow(
                    record.player(), name(record.player()), "", reason(viewer, record), until(viewer, record)));
        }
        return rows;
    }

    /** The ban's reason, or the catalog "no reason" phrase resolved in the viewer's locale when it carries none. */
    private String reason(PlayerRef viewer, BanRecord record) {
        return record.reason()
                .filter(text -> !text.isBlank())
                .orElseGet(() -> messages.resolve(viewer, PlayerwarpsMessageKey.PWARP_GUI_BANS_NO_REASON, Map.of()));
    }

    /** The ban's expiry as a UTC timestamp, or the catalog "permanent" phrase when the ban never lifts. */
    private String until(PlayerRef viewer, BanRecord record) {
        return record.until()
                .map(BAN_UNTIL::format)
                .orElseGet(() -> messages.resolve(viewer, PlayerwarpsMessageKey.PWARP_GUI_BANS_PERMANENT, Map.of()));
    }

    /** Resolve a uuid to its last-known name, falling back to the uuid string when the account is unknown. */
    private String name(UUID player) {
        return players.findByUuid(player).map(PlayerRef::name).orElseGet(player::toString);
    }

    /**
     * Remove the clicked row's person through {@code verb} off the tick thread, then re-open the sub-menu so the new
     * list shows. The target is built from the row's uuid (the identity every use case keys on); the row name is
     * informational only.
     */
    private void remove(MenuActionContext ctx, Kind kind, PeopleVerb verb) {
        PlayerRef viewer = ctx.viewer();
        PlayerWarpName warp = subject(ctx).warp();
        PeopleRow row = ctx.entry(PeopleRow.class);
        PlayerRef target = new PlayerRef(row.uuid(), row.name());
        scheduler.async(() -> {
            verb.apply(viewer, warp, target);
            open(viewer, warp, kind);
        });
    }

    /**
     * Resolve the typed name to a player (online first, then a profile that has played) off the tick thread and apply
     * {@code verb}, then re-open the sub-menu. A blank line or an unknown name changes nothing and re-opens; an unknown
     * name also sends the invalid-value notice, so a typo is never a stack trace.
     */
    private void add(MenuActionContext ctx, Kind kind, PeopleVerb verb) {
        PlayerRef viewer = ctx.viewer();
        PlayerWarpName warp = subject(ctx).warp();
        String typed = ctx.arg().strip();
        if (typed.isEmpty()) {
            open(viewer, warp, kind);
            return;
        }
        scheduler.async(() -> {
            Optional<PlayerRef> target = players.findByName(typed);
            if (target.isEmpty()) {
                notifier.send(viewer, PlayerwarpsMessageKey.PWARP_INVALID_NAME, Map.of("value", typed));
                open(viewer, warp, kind);
                return;
            }
            verb.apply(viewer, warp, target.get());
            open(viewer, warp, kind);
        });
    }

    /** A member-grant verb that fixes the role, so the two add buttons carry no nested role picker. */
    private PeopleVerb grant(WarpRole role) {
        return (actor, name, target) -> manageMembers.addMember(actor, name, target, role);
    }

    /** Impose a permanent, reasonless ban: the GUI's only ban shape; the command owns timed and reasoned bans. */
    private void banPermanent(PlayerRef actor, PlayerWarpName name, PlayerRef target) {
        manageBans.ban(actor, name, target, Optional.empty(), Optional.empty());
    }

    private PeopleView subject(MenuContext ctx) {
        return ctx.subject(PeopleView.class);
    }

    private PeopleView subject(MenuActionContext ctx) {
        return ctx.subject(PeopleView.class);
    }

    private PeopleRow row(MenuContext ctx) {
        return ctx.entry(PeopleRow.class);
    }

    /** One management verb shared by the row-remove and add flows: run a people action for one target on one warp. */
    @FunctionalInterface
    private interface PeopleVerb {
        void apply(PlayerRef actor, PlayerWarpName name, PlayerRef target);
    }

    /** Which sub-menu an open resolves: the spec id it opens under and the bundled resource it loads. */
    private enum Kind {
        MEMBERS("pwarp-members", "modules/playerwarps/gui/pwarp-members.conf"),
        WHITELIST("pwarp-whitelist", "modules/playerwarps/gui/pwarp-whitelist.conf"),
        BANS("pwarp-bans", "modules/playerwarps/gui/pwarp-bans.conf");

        private final String specId;
        private final String resource;

        Kind(String specId, String resource) {
            this.specId = specId;
            this.resource = resource;
        }

        String specId() {
            return specId;
        }

        String resource() {
            return resource;
        }
    }

    /**
     * The subject of an open people sub-menu: the warp being managed and the already-read, already-resolved rows. The
     * list source reads this, so the menu carries no store read or name lookup of its own once it opens; a mutating
     * click re-opens with a fresh snapshot.
     *
     * @param warp the warp whose people list is shown, the identity every use case is addressed by
     * @param rows the people, each with its display strings already resolved on the off-tick thread
     */
    public record PeopleView(PlayerWarpName warp, List<PeopleRow> rows) {

        public PeopleView {
            Objects.requireNonNull(warp, "warp");
            rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        }
    }

    /**
     * One person in a people list, fully resolved on the off-tick thread so the menu never touches a port again. The
     * same shape backs all three menus; a field a menu does not use (a member's has no ban reason) is an empty string.
     *
     * @param uuid the person's account id, the identity the remove/unban use case keys on
     * @param name the resolved display name, or the uuid string when the account is unknown
     * @param role the member's role token as plain lowercase data (members menu), else empty
     * @param reason the ban reason, or the catalog "no reason" phrase (bans menu), else empty
     * @param until the ban expiry as a UTC timestamp, or the catalog "permanent" phrase (bans menu), else empty
     */
    public record PeopleRow(UUID uuid, String name, String role, String reason, String until) {

        public PeopleRow {
            Objects.requireNonNull(uuid, "uuid");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(until, "until");
        }
    }
}
