package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import org.bukkit.Server;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.outbound.permission.LuckPermsAccess;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.Nullable;

/**
 * The ready-made {@code luckperms-groups} live source: every LuckPerms group as a menu entry, so a spec can page a
 * rank list or a rank-picker with no feature code. {@code list { source = luckperms-groups, template { … } }} draws
 * one tile per group. Registered once at startup into the shared {@link MenuBindings} alongside the other live
 * sources in {@link LiveDataSources}; on a server without LuckPerms the source serves an empty list, so the grid
 * renders blank rather than crashing.
 *
 * <p>LuckPerms is a soft-depend reached purely by reflection, exactly as the menu's JobsReborn and WorldGuard gates
 * are. The SDK is named only by string class-name (no {@code net.luckperms.api.*} import), so no field or method
 * signature here carries a LuckPerms type: constructing the helper on a server without LuckPerms loads none of its
 * classes, and the present-guard short-circuits before any reflection runs. Any {@link ReflectiveOperationException}
 * (the API absent, or its shape shifted under a version bump) or unchecked failure is logged exactly once through an
 * {@link AtomicBoolean} and degraded to an empty list: the source never throws into the render path. The permission
 * adapter's own LuckPerms meta source uses the SDK directly; the menu vocab is deliberately reflection-only to keep
 * the SDK off the class-load path of a LuckPerms-less server.
 *
 * <p>Threading: unlike the roster sources, this needs no region hop. A list source runs on the async list-resolution
 * thread ({@code Menus} resolves a spec's lists off the tick thread), and LuckPerms is async-safe by design
 * {@code getLoadedGroups()} and each group's cached data are safe to read off the main thread. So the read happens
 * directly on that async thread; no {@link com.uxplima.uxmessentials.shared.application.port.Scheduler} is involved
 * and no entity/world roster is touched. The per-entry placeholders read only the captured {@link GroupEntry}
 * snapshot, and produce raw group data (a name, a weight, a prefix), never a catalog message.
 */
public final class LuckPermsGroupSource {

    private static final String LUCKPERMS = "net.luckperms.api.LuckPerms";
    private static final String GROUP_MANAGER = "net.luckperms.api.model.group.GroupManager";
    private static final String GROUP = "net.luckperms.api.model.group.Group";
    private static final String CACHED_DATA_MANAGER = "net.luckperms.api.cacheddata.CachedDataManager";
    private static final String CACHED_META_DATA = "net.luckperms.api.cacheddata.CachedMetaData";

    /** Highest-weight (top rank) first, breaking ties by name so the list order is stable across resolves. */
    private static final Comparator<GroupEntry> RANK_ORDER =
            Comparator.comparingInt(GroupEntry::weight).reversed().thenComparing(GroupEntry::name);

    private LuckPermsGroupSource() {}

    /**
     * Register the {@code luckperms-groups} source and its {@code %lp_group_*%} placeholders into {@code bindings}.
     * {@code server} gates the reflective lookup on LuckPerms being present and resolves the API through the Bukkit
     * ServicesManager; {@code log} is the operator console logger the fail-closed degrade warns through once. Left a
     * standalone entry point (like the sibling vocab slices) so the {@link LiveDataSources} registration signature
     * stays untouched: the composition root calls both.
     */
    public static void register(MenuBindings bindings, Server server, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(log, "log");
        LuckPermsGroups groups = new LuckPermsGroups(server, log);
        bindings.list("luckperms-groups", ctx -> groups.groups());
        registerPlaceholders(bindings);
    }

    /** The {@code %lp_group_*%} placeholders, each reading one field off the bound group entry. */
    private static void registerPlaceholders(MenuBindings bindings) {
        bindings.placeholder("lp_group_name", ctx -> field(ctx, GroupEntry::name));
        bindings.placeholder("lp_group_display", ctx -> field(ctx, GroupEntry::displayName));
        bindings.placeholder("lp_group_weight", ctx -> field(ctx, entry -> String.valueOf(entry.weight())));
        bindings.placeholder("lp_group_prefix", ctx -> field(ctx, GroupEntry::prefix));
        bindings.placeholder("lp_group_suffix", ctx -> field(ctx, GroupEntry::suffix));
    }

    /** Read one field off the bound group entry, or empty when the placeholder is used off such a list. */
    private static String field(MenuContext ctx, Function<GroupEntry, String> field) {
        return ctx.entry()
                .filter(GroupEntry.class::isInstance)
                .map(GroupEntry.class::cast)
                .map(field)
                .orElse("");
    }

    /** Order groups highest-weight first, then by name for a stable tie, the natural rank-list order. */
    static List<GroupEntry> sortByRank(List<GroupEntry> groups) {
        List<GroupEntry> sorted = new ArrayList<>(groups);
        sorted.sort(RANK_ORDER);
        return sorted;
    }

    /**
     * The LuckPerms (plugin {@code LuckPerms}) loaded-group listing, reached purely by reflection. Names the SDK only
     * by string class-name, so no field or method signature carries a {@code net.luckperms} type: constructing this on
     * a server without LuckPerms loads none of its classes, and the present-guard short-circuits before any reflection
     * runs. Any {@link ReflectiveOperationException} or unchecked failure is logged exactly once and degraded to an
     * empty list: the same discipline the JobsReborn and WorldGuard gates use. The absent path plus this fail-closed
     * degrade are the tested contract; the happy path degrades safely if a version bump moves the chain.
     */
    private static final class LuckPermsGroups {

        private final Server server;
        private final Logger log;
        private final AtomicBoolean warned = new AtomicBoolean();

        LuckPermsGroups(Server server, Logger log) {
            this.server = server;
            this.log = log;
        }

        List<GroupEntry> groups() {
            if (!LuckPermsAccess.isPresent(server)) {
                return List.of();
            }
            try {
                return lookup();
            } catch (ReflectiveOperationException | RuntimeException failure) {
                degrade(failure);
                return List.of();
            }
        }

        private List<GroupEntry> lookup() throws ReflectiveOperationException {
            Object api = server.getServicesManager().load(Class.forName(LUCKPERMS));
            if (api == null) {
                return List.of(); // LuckPerms present but its service not registered yet
            }
            Object manager = read(LUCKPERMS, "getGroupManager", api);
            Object loaded = manager == null ? null : read(GROUP_MANAGER, "getLoadedGroups", manager);
            if (!(loaded instanceof Collection<?> groups)) {
                return List.of();
            }
            List<GroupEntry> entries = new ArrayList<>();
            for (Object group : groups) {
                entries.add(toEntry(group));
            }
            return sortByRank(entries);
        }

        private void degrade(Exception failure) {
            if (warned.compareAndSet(false, true)) {
                log.warn(
                        "event=menu_integration_reflection_failed integration={} reason={}",
                        "LuckPerms",
                        failure.toString());
            }
        }
    }

    /** Build one immutable entry from a reflective LuckPerms {@code Group}; a null display name falls back to the name. */
    private static GroupEntry toEntry(Object group) throws ReflectiveOperationException {
        String name = String.valueOf(read(GROUP, "getName", group));
        return new GroupEntry(
                name, displayName(group, name), weight(group), meta(group, "getPrefix"), meta(group, "getSuffix"));
    }

    /** The group's display name, or {@code fallback} when the SDK reports none (a {@code @Nullable String}). */
    private static String displayName(Object group, String fallback) throws ReflectiveOperationException {
        Object display = read(GROUP, "getDisplayName", group);
        return display instanceof String name && !name.isBlank() ? name : fallback;
    }

    /** The group's weight, reading the SDK's {@link OptionalInt} through {@code isPresent}/{@code getAsInt}; default 0. */
    private static int weight(Object group) throws ReflectiveOperationException {
        Object value = read(GROUP, "getWeight", group);
        return value instanceof OptionalInt present ? present.orElse(0) : 0;
    }

    /**
     * A group's cached prefix or suffix, best-effort. This chain ({@code getCachedData().getMetaData().get*()}) is the
     * most version-brittle of the reads, so it is guarded per group: any hiccup yields an empty string rather than
     * failing the whole entry, so a version bump that moves the meta chain still lists the group with a blank affix.
     */
    private static String meta(Object group, String affix) {
        try {
            Object cachedData = read(GROUP, "getCachedData", group);
            Object metaData = cachedData == null ? null : read(CACHED_DATA_MANAGER, "getMetaData", cachedData);
            Object value = metaData == null ? null : read(CACHED_META_DATA, affix, metaData);
            return value instanceof String text ? text : "";
        } catch (ReflectiveOperationException | RuntimeException hiccup) {
            return "";
        }
    }

    /**
     * Invoke the no-arg public method {@code method} declared on the SDK interface {@code type} against {@code target}.
     * Resolving the method from the public interface (not {@code target.getClass()}) sidesteps the access trap of a
     * non-public LuckPerms implementation class, while still naming the type by string only.
     */
    private static @Nullable Object read(String type, String method, Object target)
            throws ReflectiveOperationException {
        return Class.forName(type).getMethod(method).invoke(target);
    }

    /** An immutable snapshot of one LuckPerms group, safe to read off any thread once captured. */
    public record GroupEntry(String name, String displayName, int weight, String prefix, String suffix) {

        public GroupEntry {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(displayName, "displayName");
            Objects.requireNonNull(prefix, "prefix");
            Objects.requireNonNull(suffix, "suffix");
        }
    }
}
