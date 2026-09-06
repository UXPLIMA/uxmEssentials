package com.uxplima.uxmessentials.regions.adapter.inbound.gui;

import java.util.Objects;

import com.uxplima.uxmessentials.regions.domain.RosterMember;
import org.jspecify.annotations.NullMarked;

/**
 * One row of the members/owners editor: a classified {@link RosterMember} paired with the display name resolved for it
 * once, off the tick thread, before the panel opens. A uuid entry's owning player name (from the offline profile
 * cache), a group's bare name, or a legacy name verbatim. The icon renderer paints from this snapshot and never
 * resolves a name on the entity thread; a click reads {@link #member()} to know whether and how to remove the entry.
 *
 * @param member the classified roster entry (its role and, when removable, its uuid)
 * @param display the resolved, viewer-facing label for the entry
 */
@NullMarked
public record RosterRow(RosterMember member, String display) {

    public RosterRow {
        Objects.requireNonNull(member, "member");
        Objects.requireNonNull(display, "display");
    }
}
