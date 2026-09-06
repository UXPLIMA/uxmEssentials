package com.uxplima.uxmessentials.regions.adapter.outbound;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.regions.application.port.RegionService;
import com.uxplima.uxmessentials.regions.domain.FlagDescriptor;
import com.uxplima.uxmessentials.regions.domain.FlagValue;
import com.uxplima.uxmessentials.regions.domain.RegionMemberChange;
import com.uxplima.uxmessentials.regions.domain.RegionRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link RegionService} bound when WorldGuard is absent: it reports {@link #available()} {@code false} and every
 * read answers empty, so the {@code /regions} command degrades to a "WorldGuard not installed" message and opens no
 * window. It names no {@code com.sk89q} type, so a server without WorldGuard carries none of its classes.
 *
 * <p>The mutations throw {@link UnsupportedOperationException}. They have no meaning without WorldGuard, and no
 * Phase 1 caller reaches them regardless; the exception makes a future miswiring fail loudly rather than silently
 * discarding an edit.
 */
@NullMarked
public final class NoWorldGuardRegionService implements RegionService {

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public List<RegionRef> regionsIn(WorldRef world) {
        Objects.requireNonNull(world, "world");
        return List.of();
    }

    @Override
    public Optional<RegionRef> region(WorldRef world, String id) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(id, "id");
        return Optional.empty();
    }

    @Override
    public List<RegionRef> regionsAt(Position position) {
        Objects.requireNonNull(position, "position");
        return List.of();
    }

    @Override
    public List<FlagValue> flags(RegionRef region) {
        Objects.requireNonNull(region, "region");
        return List.of();
    }

    @Override
    public List<FlagDescriptor> flagDescriptors(RegionRef region) {
        Objects.requireNonNull(region, "region");
        return List.of();
    }

    @Override
    public List<String> members(RegionRef region) {
        Objects.requireNonNull(region, "region");
        return List.of();
    }

    @Override
    public List<String> owners(RegionRef region) {
        Objects.requireNonNull(region, "region");
        return List.of();
    }

    @Override
    public int priority(RegionRef region) {
        Objects.requireNonNull(region, "region");
        return 0;
    }

    @Override
    public RegionRef create(WorldRef world, String id, Position min, Position max) {
        throw new UnsupportedOperationException("WorldGuard is not installed");
    }

    @Override
    public void setFlag(RegionRef region, FlagValue flag) {
        throw new UnsupportedOperationException("WorldGuard is not installed");
    }

    @Override
    public void applyMemberChange(RegionMemberChange change) {
        throw new UnsupportedOperationException("WorldGuard is not installed");
    }

    @Override
    public void setPriority(RegionRef region, int priority) {
        throw new UnsupportedOperationException("WorldGuard is not installed");
    }
}
