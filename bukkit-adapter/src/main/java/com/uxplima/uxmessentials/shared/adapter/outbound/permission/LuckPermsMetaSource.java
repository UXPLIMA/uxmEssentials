package com.uxplima.uxmessentials.shared.adapter.outbound.permission;

import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A {@link MetaSource} that reads LuckPerms meta for the value-bearing quota families. It is bound
 * only when LuckPerms is installed (bootstrap probes the {@code ServicesManager} before constructing
 * it), so loading this class never happens without the LuckPerms API present.
 *
 * <p>Meta is read from the loaded user's cached data, the same snapshot the platform uses for
 * permission checks, so the read is non-blocking and reflects the player's online resolution. A
 * value is reported only for a currently-loaded user; an unloaded user falls through to the numbered
 * nodes, matching the "meta is never required" contract.
 */
@NullMarked
public final class LuckPermsMetaSource implements MetaSource {

    private final LuckPerms luckPerms;
    private final Logger log;

    public LuckPermsMetaSource(LuckPerms luckPerms, Logger log) {
        this.luckPerms = Objects.requireNonNull(luckPerms, "luckPerms");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public OptionalLong metaValue(UUID who, String familyNode) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(familyNode, "familyNode");
        User user = luckPerms.getUserManager().getUser(who);
        if (user == null) {
            return OptionalLong.empty();
        }
        CachedMetaData metaData = user.getCachedData().getMetaData();
        return parse(metaData.getMetaValue(metaKey(familyNode)));
    }

    private OptionalLong parse(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(Long.parseLong(raw.trim()));
        } catch (NumberFormatException notNumeric) {
            log.debug("ignoring non-numeric LuckPerms meta value {}", raw);
            return OptionalLong.empty();
        }
    }

    /** Maps {@code uxmessentials.home.limit} to the meta key {@code home-limit}. */
    private static String metaKey(String familyNode) {
        String tail =
                familyNode.startsWith("uxmessentials.") ? familyNode.substring("uxmessentials.".length()) : familyNode;
        return tail.replace('.', '-');
    }
}
