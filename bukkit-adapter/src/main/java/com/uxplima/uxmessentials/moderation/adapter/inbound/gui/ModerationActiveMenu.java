package com.uxplima.uxmessentials.moderation.adapter.inbound.gui;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.moderation.application.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.domain.BanEntry;
import com.uxplima.uxmessentials.moderation.domain.JailEntry;
import com.uxplima.uxmessentials.moderation.domain.MuteEntry;
import com.uxplima.uxmessentials.moderation.domain.SanctionDuration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Registers the active-punishments management list (the management GUI's entry point, {@code /mod} with no args and
 * the {@code /uxmess gui} hub entry) with the menu engine and opens it. A paginated grid of one icon per currently
 * active ban, mute, and jail, each showing the target, the kind, the issuer, the reason and the remaining time. A
 * left click opens that entry's still-bespoke {@link PunishmentDetailView}, the confirm-gated detail/revoke screen.
 *
 * <p>The three active reads ({@code activeBans} / {@code activeMutes} / {@code activeJails}) are DB queries, each
 * target's display name resolves through the {@link PlayerLookup}, and the kind label and {@code permanent} marker
 * resolve through the {@link Messages} catalog: none of which belongs in an off-thread list source. So {@link #open}
 * runs the reads off the tick thread, resolves every name, kind label and remaining string there in the viewer's
 * locale, and hands the fully-resolved rows in as the menu subject; the {@code moderation:active} list source only
 * reads that subject. The {@code mod_active_icon} / {@code mod_active_player} / {@code mod_active_type} /
 * {@code mod_active_issuer} / {@code mod_active_reason} / {@code mod_active_remaining} placeholders fill each icon
 * from the bound row, prefixed so they never collide with the history- and jailed-list tokens that carry the same
 * field names. A {@code moderation:open-detail} left action hands the clicked {@link ActivePunishment} to the detail
 * view. There is no create button. Punishments are issued by the {@code /ban} / {@code /mute} / {@code /jail}
 * commands, not the list. Every label resolves from the moderation catalog, so no user-facing text lives here. The
 * geometry mirrors the original paginated list: a grid across the top five rows and the two nav arrows on the bottom.
 */
@NullMarked
public final class ModerationActiveMenu {

    /** The engine spec id this menu registers and opens under. */
    public static final String SPEC_ID = "moderation-active";

    /** Disk-first then bundled, mirroring the GUI-layout loader, so an operator edit to the spec takes effect. */
    private static final String SPEC_RESOURCE = "modules/moderation/gui/moderation-active.conf";

    /** Mirrors the per-list page cap the {@code /banlist} read uses, so the off-thread read stays bounded. */
    private static final int PAGE_LIMIT = 50;

    private final Menus menus;
    private final Scheduler scheduler;
    private final ModerationRepository repository;
    private final PlayerLookup players;
    private final Messages messages;
    private final Clock clock;
    private final PunishmentDetailView detail;

    public ModerationActiveMenu(
            Menus menus,
            Scheduler scheduler,
            ModerationRepository repository,
            PlayerLookup players,
            Messages messages,
            Clock clock,
            PunishmentDetailView detail) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.players = Objects.requireNonNull(players, "players");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.detail = Objects.requireNonNull(detail, "detail");
    }

    /** Register the per-row list source, the placeholders and the open-detail action the spec names, plus the spec. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.list("moderation:active", ctx -> subject(ctx).rows());
        bindings.placeholder("mod_active_icon", ctx -> row(ctx).icon());
        bindings.placeholder("mod_active_player", ctx -> row(ctx).player());
        bindings.placeholder("mod_active_type", ctx -> row(ctx).type());
        bindings.placeholder("mod_active_issuer", ctx -> row(ctx).issuer());
        bindings.placeholder("mod_active_reason", ctx -> row(ctx).reason());
        bindings.placeholder("mod_active_remaining", ctx -> row(ctx).remaining());
        bindings.action("moderation:open-detail", this::openDetail);
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, 6, log));
    }

    /** Resolve the active punishments (and each name, kind label and remaining string) off-thread, then open. */
    public void open(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        scheduler.async(() -> menus.open(viewer, SPEC_ID, snapshot(viewer)));
    }

    /** Read the active bans/mutes/jails and resolve every display string on the off-tick thread in the viewer's locale. */
    private ActiveView snapshot(PlayerRef viewer) {
        Instant now = clock.instant();
        List<ActiveRow> rows = new ArrayList<>();
        for (BanEntry ban : repository.activeBans(now, PAGE_LIMIT)) {
            rows.add(row(viewer, ActivePunishment.ofBan(ban, name(ban.target()))));
        }
        for (MuteEntry mute : repository.activeMutes(now, PAGE_LIMIT)) {
            rows.add(row(viewer, ActivePunishment.ofMute(mute, name(mute.target()))));
        }
        for (JailEntry jail : repository.activeJails(now, PAGE_LIMIT)) {
            rows.add(row(viewer, ActivePunishment.ofJail(jail, name(jail.target()))));
        }
        return new ActiveView(rows);
    }

    private ActiveRow row(PlayerRef viewer, ActivePunishment punishment) {
        return new ActiveRow(
                punishment,
                icon(punishment),
                punishment.targetName(),
                catalog(viewer, punishment.kind().label()),
                punishment.issuer().name(),
                punishment.reason().filter(r -> !r.isBlank()).orElse(""),
                remaining(viewer, punishment));
    }

    /** Left-click a row: open the clicked entry's bespoke detail/revoke screen on the viewer's entity thread. */
    private void openDetail(MenuActionContext ctx) {
        detail.open(ctx.player(), ctx.viewer(), ctx.entry(ActiveRow.class).punishment());
    }

    private ActiveView subject(MenuContext ctx) {
        return ctx.subject(ActiveView.class);
    }

    private ActiveRow row(MenuContext ctx) {
        return ctx.entry(ActiveRow.class);
    }

    /** The per-kind icon material name, mirroring the deleted view's switch so the grid renders identically. */
    private static String icon(ActivePunishment punishment) {
        return switch (punishment.kind()) {
            case BAN -> "BARRIER";
            case MUTE -> "BOOK";
            case JAIL -> "IRON_BARS";
        };
    }

    private String remaining(PlayerRef viewer, ActivePunishment punishment) {
        if (punishment.isPermanent()) {
            return catalog(viewer, ModerationMessageKey.MOD_GUI_VALUE_PERMANENT);
        }
        Duration left = punishment
                .until()
                .map(until -> Duration.between(clock.instant(), until))
                .or(punishment::remaining)
                .orElse(Duration.ZERO);
        return SanctionDuration.format(left.isNegative() ? Duration.ZERO : left);
    }

    private String name(java.util.UUID target) {
        return players.findByUuid(target).map(PlayerRef::name).orElseGet(target::toString);
    }

    /** Resolve a catalog key to its final string in the viewer's locale; the kind labels carry no MiniMessage tags. */
    private String catalog(PlayerRef viewer, ModerationMessageKey key) {
        return messages.resolve(viewer, key, Map.of());
    }

    /**
     * The subject of an open active-punishments list: the already-read, already-resolved rows. The list source reads
     * this, so the menu carries no DB read or name lookup of its own once it opens.
     *
     * @param rows the active punishments, each with its display strings already resolved
     */
    public record ActiveView(List<ActiveRow> rows) {

        public ActiveView {
            rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        }
    }

    /**
     * One active punishment, fully resolved on the off-tick thread so the menu never touches a port again: the source
     * {@link ActivePunishment} (handed to the detail view on a left click) plus the icon material and the display
     * strings the placeholders read.
     *
     * @param punishment the source projection, passed to the detail/revoke screen on an open-detail click
     * @param icon the per-kind icon material name (a barrier for a ban, a book for a mute, iron bars for a jail)
     * @param player the target's resolved display name, or the UUID string when the account is unknown
     * @param type the kind label resolved in the viewer's locale (Ban / Mute / Jail)
     * @param issuer who issued the sanction
     * @param reason the free-text reason, or empty when none was given
     * @param remaining the formatted remaining time, or the catalog {@code permanent} marker for an indefinite one
     */
    public record ActiveRow(
            ActivePunishment punishment,
            String icon,
            String player,
            String type,
            String issuer,
            String reason,
            String remaining) {

        public ActiveRow {
            Objects.requireNonNull(punishment, "punishment");
            Objects.requireNonNull(icon, "icon");
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(issuer, "issuer");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(remaining, "remaining");
        }
    }
}
