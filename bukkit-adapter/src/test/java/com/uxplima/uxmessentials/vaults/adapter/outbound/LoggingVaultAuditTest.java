package com.uxplima.uxmessentials.vaults.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage of {@link LoggingVaultAudit}: every staff override emits exactly one structured line on the
 * audit channel, {@code event=vault_admin_open} for an inspect and {@code event=vault_admin_delete} for a
 * deletion, carrying the actor and owner by UUID, the owner name, and the vault index. A recording
 * {@link Logger} captures the line so the format an operator's log filter matches against is pinned.
 */
class LoggingVaultAuditTest {

    private static final UUID ACTOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OWNER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void adminDeletedEmitsTheStructuredDeleteLine() {
        RecordingLogger logger = new RecordingLogger();
        LoggingVaultAudit audit = new LoggingVaultAudit(logger);

        audit.adminDeleted(new PlayerRef(ACTOR, "Staff"), new PlayerRef(OWNER, "Bob"), OWNER, 3);

        assertThat(logger.lines).hasSize(1);
        Recorded line = logger.lines.get(0);
        assertThat(line.template()).isEqualTo("event=vault_admin_delete actor={} owner={} owner_name={} idx={}");
        assertThat(line.args()).containsExactly(ACTOR, OWNER, "Bob", 3);
    }

    @Test
    void adminOpenedEmitsTheStructuredOpenLine() {
        RecordingLogger logger = new RecordingLogger();
        LoggingVaultAudit audit = new LoggingVaultAudit(logger);

        audit.adminOpened(new PlayerRef(ACTOR, "Staff"), new PlayerRef(OWNER, "Bob"), OWNER, 1);

        assertThat(logger.lines).hasSize(1);
        assertThat(logger.lines.get(0).template())
                .isEqualTo("event=vault_admin_open actor={} owner={} owner_name={} idx={}");
    }

    /** One captured log call: the SLF4J-style template and its expansion args as a list (not an array). */
    private record Recorded(String template, List<Object> args) {}

    private static final class RecordingLogger implements Logger {
        private final List<Recorded> lines = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {
            lines.add(new Recorded(message, Arrays.asList(args)));
        }

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
