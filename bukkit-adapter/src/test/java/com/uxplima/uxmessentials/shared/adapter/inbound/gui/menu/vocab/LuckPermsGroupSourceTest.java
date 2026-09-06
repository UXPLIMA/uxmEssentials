package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.LiveDataSources.WorldEntry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.LuckPermsGroupSource.GroupEntry;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * Coverage of the {@code luckperms-groups} source's testable surface. Real LuckPerms is not on the test classpath
 * (it is a soft-depend), so the reflective happy path cannot be unit-tested; instead this pins the contract a
 * LuckPerms-less server can reach, the present-guard degrade to an empty list, plus the pure pieces around it: the
 * {@link GroupEntry} record, the five per-entry placeholders, the off-list resilience, the weight-descending sort,
 * and the structural guarantee that no field or method signature names the LuckPerms SDK package (so loading the
 * class on a plugin-less server pulls in zero SDK class). This mirrors how the Jobs/WorldGuard integration gates are
 * tested. A {@link ServerMock} stands in only so registration has a live {@code Server}; the mock has no LuckPerms,
 * which is exactly the absent path.
 */
class LuckPermsGroupSourceTest {

    private static final PlayerRef VIEWER = new PlayerRef(UUID.randomUUID(), "Viewer");

    private ServerMock server;
    private MenuBindings bindings;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        bindings = new MenuBindings();
        LuckPermsGroupSource.register(bindings, server, new NoopLogger());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void groupPlaceholdersReadEachRecordField() {
        GroupEntry entry = new GroupEntry("vip", "&aVIP", 50, "[VIP] ", " *");
        assertThat(resolve("lp_group_name", entry)).isEqualTo("vip");
        assertThat(resolve("lp_group_display", entry)).isEqualTo("&aVIP");
        assertThat(resolve("lp_group_weight", entry)).isEqualTo("50");
        assertThat(resolve("lp_group_prefix", entry)).isEqualTo("[VIP] ");
        assertThat(resolve("lp_group_suffix", entry)).isEqualTo(" *");
    }

    @Test
    void placeholderOffAnyListReturnsEmpty() {
        // Written on a static item with no bound entry, a group placeholder must render empty, not throw.
        MenuContext ctx = MenuContext.of(VIEWER, null, 0);
        assertThat(handler("lp_group_name").apply(ctx)).isEmpty();
        assertThat(handler("lp_group_weight").apply(ctx)).isEmpty();
    }

    @Test
    void placeholderOnAWrongTypedEntryReturnsEmpty() {
        // Bound to some other source's entry type, a group placeholder must degrade to empty rather than throw.
        assertThat(resolve("lp_group_name", new WorldEntry("world", "NORMAL", 0, true)))
                .isEmpty();
        assertThat(resolve("lp_group_display", "not-a-group-entry")).isEmpty();
    }

    @Test
    void sortOrdersByWeightDescendingThenName() {
        GroupEntry admin = new GroupEntry("admin", "Admin", 100, "", "");
        GroupEntry vip = new GroupEntry("vip", "VIP", 50, "", "");
        GroupEntry mod = new GroupEntry("mod", "Mod", 50, "", "");
        GroupEntry def = new GroupEntry("default", "Default", 0, "", "");

        List<GroupEntry> sorted = LuckPermsGroupSource.sortByRank(List.of(vip, def, admin, mod));

        // Highest weight first; the weight-50 tie breaks by name ("mod" before "vip").
        assertThat(sorted).containsExactly(admin, mod, vip, def);
    }

    @Test
    void presentGuardServesAnEmptyListWhenLuckPermsIsAbsent() {
        // The mock server has no LuckPerms, so the source must serve an empty list before any reflection runs.
        Function<MenuContext, List<?>> source =
                bindings.list("luckperms-groups").orElseThrow(() -> new AssertionError("source not registered"));
        assertThat(source.apply(MenuContext.of(VIEWER, null, 0))).isEmpty();
    }

    @Test
    void sourceDeclaresNoLuckPermsSdkTypeAnywhere() {
        // Loading the source (and its reflective nested helper) on a plugin-less server must pull in zero SDK class
        // every LuckPerms reference is a string class-name, so no field or method signature names the net.luckperms
        // package, and the present-guard, not a classload, is what gates the reflection.
        assertThat(declaresPackage(LuckPermsGroupSource.class, "net.luckperms"))
                .as("LuckPerms SDK type in a signature")
                .isFalse();
    }

    /** Resolve a registered placeholder against a context bound to {@code entry}. */
    private String resolve(String id, Object entry) {
        return handler(id).apply(MenuContext.of(VIEWER, null, 0).withEntry(entry));
    }

    /** Pull one registered placeholder handler out of the wired bindings. */
    private Function<MenuContext, String> handler(String id) {
        return bindings.placeholder(id).orElseThrow(() -> new AssertionError("placeholder not registered: " + id));
    }

    /** Whether {@code type} (or a nested class it declares) names {@code prefix} in any field or method signature. */
    private static boolean declaresPackage(Class<?> type, String prefix) {
        if (referencesPackage(type, prefix)) {
            return true;
        }
        for (Class<?> nested : type.getDeclaredClasses()) {
            if (referencesPackage(nested, prefix)) {
                return true;
            }
        }
        return false;
    }

    /** Whether {@code type} (walking up to {@code Object}) declares any field or method signature in {@code prefix}. */
    private static boolean referencesPackage(Class<?> type, String prefix) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                if (method.getReturnType().getName().startsWith(prefix)) {
                    return true;
                }
                for (Class<?> parameter : method.getParameterTypes()) {
                    if (parameter.getName().startsWith(prefix)) {
                        return true;
                    }
                }
            }
            for (Field field : c.getDeclaredFields()) {
                if (field.getType().getName().startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Discards every log line; this test asserts behaviour, not log output. */
    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
