package com.uxplima.uxmessentials.shared.adapter.outbound.hooks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The optional HeadDatabase hook in its absent state, the only state reachable in a test, since HeadDatabase
 * is not on the classpath (it is reached purely by reflection, like the NBT-API hook and the currency
 * reflection providers). The contract proved here is the load-safe one: a HeadDatabase-less server resolves the
 * hook to the no-op {@link HeadQuery} whose every call is safe, and its interface and absent default declare no
 * {@code me.arcaniax} type (so loading them pulls in zero SDK class). The present SDK happy-path stays untested
 * by design, there is no HeadDatabase to delegate to, so the absent path, the null/blank-id contract, and the
 * registration are the tested contract.
 */
class HeadDatabaseHookTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void absent_resolvesToNoOpAndEveryCallIsSafe() {
        HeadDatabaseHook hook = new HeadDatabaseHook(HookHarness.SILENT);

        HeadQuery query = HookHarness.absent(hook);

        assertThat(query).isSameAs(HeadQuery.ABSENT);
        assertThatCode(() -> {
                    assertThat(query.available()).isFalse();
                    assertThat(query.head("any_head_id")).isEmpty();
                })
                .doesNotThrowAnyException();
    }

    @Test
    void absent_blankOrNullId_isEmpty() {
        // The null/blank-id contract is verifiable on the absent default (a present query can't be exercised
        // without the SDK on the classpath); the real HeadDatabaseService applies the same id guard before any
        // reflective read.
        assertThatCode(() -> {
                    assertThat(HeadQuery.ABSENT.head(null)).isEmpty();
                    assertThat(HeadQuery.ABSENT.head("")).isEmpty();
                    assertThat(HeadQuery.ABSENT.head("   ")).isEmpty();
                })
                .doesNotThrowAnyException();
    }

    @Test
    void absentDefaultAndInterfaceDeclareNoHeadDatabaseType() {
        // The structural confirmation that the no-op default carries no SDK type, so loading the interface (and
        // its ABSENT field initializer) on a HeadDatabase-less server cannot pull in me.arcaniax. Only
        // HeadDatabaseService, reached past Hooks' present-guard, references HeadDatabase, and even then only
        // by reflective string name.
        assertThat(referencesHeadDatabaseSdk(HeadQuery.class)).isFalse();
        assertThat(referencesHeadDatabaseSdk(HeadQuery.ABSENT.getClass())).isFalse();
    }

    @Test
    void hookIsRegisteredAndResolvesToNonNullCapability() {
        Hooks hooks = Hooks.resolve(server, HookHarness.SILENT, List.of(new HeadDatabaseHook(HookHarness.SILENT)));

        assertThat(hooks.provides(HeadQuery.class)).isTrue();
        assertThat(hooks.capability(HeadQuery.class)).isSameAs(HeadQuery.ABSENT);
    }

    private static boolean referencesHeadDatabaseSdk(Class<?> type) {
        for (Method method : type.getDeclaredMethods()) {
            if (mentionsHeadDatabase(method.getReturnType())) {
                return true;
            }
            for (Class<?> parameter : method.getParameterTypes()) {
                if (mentionsHeadDatabase(parameter)) {
                    return true;
                }
            }
        }
        for (Field field : type.getDeclaredFields()) {
            if (mentionsHeadDatabase(field.getType())) {
                return true;
            }
        }
        return false;
    }

    private static boolean mentionsHeadDatabase(Class<?> type) {
        return type.getName().startsWith("me.arcaniax");
    }
}
