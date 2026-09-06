package com.uxplima.uxmessentials.regions.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.regions.application.RegionsMessageKey;
import com.uxplima.uxmessentials.regions.application.port.RegionService;
import com.uxplima.uxmessentials.regions.domain.RegionMemberChange;
import com.uxplima.uxmessentials.regions.domain.RegionRef;
import com.uxplima.uxmessentials.regions.domain.RegionServiceException;
import com.uxplima.uxmessentials.regions.domain.RosterMember;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.Tiles;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The per-region members/owners editor: an engine-backed panel (the shared {@link EntityListView}, so it renders
 * through the menu engine and needs no raw-inventory allow-list entry) with one icon per roster entry, owners first,
 * then members. Clicking a uuid-backed entry removes it through {@link RegionService#applyMemberChange} (the same seam
 * a command would use), so the GUI holds no WorldGuard logic of its own; a group or legacy-name entry has no uuid to
 * key the removal, so it renders read-only and a click reports that it must be removed with WorldGuard's own commands.
 *
 * <p>The roster is read off the tick thread through the {@link RegionService} (WorldGuard's region store is queried on
 * the global region thread, never a viewer's region thread) and each entry's display name is resolved from the offline
 * profile cache in the same pass, then the panel opens on the staff member's own entity thread over that snapshot. A
 * removal applies the write on the global region thread, where WorldGuard mutations belong, then re-reads the roster
 * and re-opens the panel on the viewer's entity thread, so a fresh panel always reflects the roster that just changed.
 * A fresh {@link EntityListView} is built per open, so two staff editing different regions never share panel state.
 */
@NullMarked
public final class RegionRosterView {

    private final Menus menus;
    private final GuiText guiText;
    private final Scheduler scheduler;
    private final Messages messages;
    private final MessageSink messageSink;
    private final RegionService service;
    private final PlayerLookup playerLookup;
    private final EntityListLayout layout;

    public RegionRosterView(
            Menus menus,
            GuiText guiText,
            Scheduler scheduler,
            Messages messages,
            MessageSink messageSink,
            RegionService service,
            PlayerLookup playerLookup,
            EntityListLayout layout) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.messageSink = Objects.requireNonNull(messageSink, "messageSink");
        this.service = Objects.requireNonNull(service, "service");
        this.playerLookup = Objects.requireNonNull(playerLookup, "playerLookup");
        this.layout = Objects.requireNonNull(layout, "layout");
    }

    /** Read {@code region}'s roster off the tick thread, then open the editor for {@code staff}. */
    public void open(PlayerRef staff, RegionRef region) {
        Objects.requireNonNull(staff, "staff");
        Objects.requireNonNull(region, "region");
        scheduler.onGlobal(() -> {
            List<RosterRow> rows = readRows(region);
            scheduler.onEntity(staff, () -> openResolved(staff, region, rows));
        });
    }

    /** Snapshot the region's owners then members, each classified and resolved to a display name, off the tick thread. */
    private List<RosterRow> readRows(RegionRef region) {
        List<RosterRow> rows = new ArrayList<>();
        for (String identifier : service.owners(region)) {
            rows.add(toRow(RosterMember.classify(identifier, RegionMemberChange.Role.OWNER)));
        }
        for (String identifier : service.members(region)) {
            rows.add(toRow(RosterMember.classify(identifier, RegionMemberChange.Role.MEMBER)));
        }
        return rows;
    }

    private RosterRow toRow(RosterMember member) {
        return new RosterRow(member, displayFor(member));
    }

    /** A group's bare name, a uuid entry's cached owner name (falling back to the uuid), or a legacy name verbatim. */
    private String displayFor(RosterMember member) {
        if (member.group()) {
            return member.groupName();
        }
        UUID uuid = member.player();
        if (uuid == null) {
            return member.identifier();
        }
        return playerLookup.findByUuid(uuid).map(PlayerRef::name).orElse(member.identifier());
    }

    private void openResolved(PlayerRef staff, RegionRef region, List<RosterRow> rows) {
        Player viewer = Bukkit.getPlayer(staff.uuid());
        if (viewer == null || !viewer.isOnline()) {
            return;
        }
        EntityListView.<RosterRow>builder()
                .menus(menus)
                .guiText(guiText)
                .scheduler(scheduler)
                .layout(layout)
                .title(RegionsMessageKey.REGIONS_MEMBERS_TITLE)
                .emptyTitle(RegionsMessageKey.REGIONS_MEMBERS_EMPTY)
                .navNames(RegionsMessageKey.REGIONS_MEMBERS_PREV, RegionsMessageKey.REGIONS_MEMBERS_NEXT)
                .entities(() -> rows)
                .iconRenderer(this::icon)
                .onSelect((clicker, row) -> onClick(region, clicker, row))
                .build()
                .open(viewer, staff);
    }

    private ItemStack icon(PlayerRef viewer, RosterRow row) {
        Map<String, String> placeholders = Map.of("name", row.display());
        return ItemBuilder.of(iconMaterial(row.member()))
                .name(Tiles.blankName())
                .lore(Tiles.titled(
                        guiText.text(viewer, nameKey(row.member()), placeholders),
                        guiText.text(viewer, loreKey(row.member()), placeholders)))
                .build();
    }

    /** A removable entry is taken off the roster on the global thread; a non-removable one reports why it cannot be. */
    private void onClick(RegionRef region, Player clicker, RosterRow row) {
        PlayerRef ref = BukkitRefs.toRef(clicker);
        if (!row.member().removable()) {
            messageSink.deliver(ref, messages.resolve(ref, RegionsMessageKey.REGIONS_MEMBERS_NOT_REMOVABLE, Map.of()));
            return;
        }
        scheduler.onGlobal(() -> removeOnGlobal(ref, region, row.member()));
    }

    /** Apply the removal on the global thread, then re-read the roster and re-open the panel on the entity thread. */
    private void removeOnGlobal(PlayerRef ref, RegionRef region, RosterMember member) {
        try {
            service.applyMemberChange(member.removalFrom(region));
        } catch (RegionServiceException failure) {
            scheduler.onEntity(ref, () -> messageSink.deliver(ref, failedText(ref, region)));
            return;
        }
        List<RosterRow> refreshed = readRows(region);
        scheduler.onEntity(ref, () -> openResolved(ref, region, refreshed));
    }

    private String failedText(PlayerRef ref, RegionRef region) {
        return messages.resolve(ref, RegionsMessageKey.REGIONS_MEMBERS_FAILED, Map.of("id", region.id()));
    }

    private static MessageKey nameKey(RosterMember member) {
        if (member.group()) {
            return RegionsMessageKey.REGIONS_MEMBERS_GROUP;
        }
        return member.role() == RegionMemberChange.Role.OWNER
                ? RegionsMessageKey.REGIONS_MEMBERS_OWNER
                : RegionsMessageKey.REGIONS_MEMBERS_MEMBER;
    }

    private static MessageKey loreKey(RosterMember member) {
        if (!member.removable()) {
            return RegionsMessageKey.REGIONS_MEMBERS_LOCKED_INFO;
        }
        return member.role() == RegionMemberChange.Role.OWNER
                ? RegionsMessageKey.REGIONS_MEMBERS_OWNER_INFO
                : RegionsMessageKey.REGIONS_MEMBERS_MEMBER_INFO;
    }

    private static Material iconMaterial(RosterMember member) {
        if (member.group()) {
            return Material.OAK_SIGN;
        }
        return member.role() == RegionMemberChange.Role.OWNER ? Material.GOLDEN_HELMET : Material.PLAYER_HEAD;
    }
}
