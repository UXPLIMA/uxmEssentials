package com.uxplima.uxmessentials.regions.adapter.inbound.gui;

import java.util.Objects;

import com.uxplima.uxmessentials.regions.domain.RegionRef;
import org.jspecify.annotations.NullMarked;

/**
 * One row of the {@code /regions} list: a region and the three facts the list icon shows, its priority and how
 * many members and owners it carries. The counts are read from the {@link com.uxplima.uxmessentials.regions
 * .application.port.RegionService} once, off the tick thread, before the panel opens, so the icon renderer paints
 * from this snapshot and never re-queries WorldGuard on the entity thread.
 *
 * @param region the region this row represents
 * @param priority the region's priority (higher wins overlaps)
 * @param memberCount how many members the region has
 * @param ownerCount how many owners the region has
 */
@NullMarked
public record RegionRow(RegionRef region, int priority, int memberCount, int ownerCount) {

    public RegionRow {
        Objects.requireNonNull(region, "region");
    }
}
