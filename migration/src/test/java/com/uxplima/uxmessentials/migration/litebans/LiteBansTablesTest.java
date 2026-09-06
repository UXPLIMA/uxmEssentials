package com.uxplima.uxmessentials.migration.litebans;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uxplima.uxmessentials.migration.convert.litebans.parse.LiteBansTables;
import org.junit.jupiter.api.Test;

/**
 * The prefix-sanitisation guard for the LiteBans table-name builder. A table name cannot be a JDBC bind
 * parameter, so the configured prefix is interpolated into the SQL, which makes strict whitelisting the
 * single line of defence against injection. Anything outside {@code [a-z0-9_]+} is rejected at construction.
 */
class LiteBansTablesTest {

    @Test
    void theDefaultPrefixBuildsTheThreeTableSelects() {
        LiteBansTables tables = new LiteBansTables("litebans_");

        assertThat(tables.selectBans()).contains("FROM litebans_bans");
        assertThat(tables.selectMutes()).contains("FROM litebans_mutes");
        assertThat(tables.selectWarnings()).contains("FROM litebans_warnings");
        assertThat(tables.selectWarnings()).contains("warned");
    }

    @Test
    void aPrefixWithASqlMetacharacterIsRejected() {
        assertThatThrownBy(() -> new LiteBansTables("litebans_; DROP TABLE players;--"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[a-z0-9_]");
    }

    @Test
    void anUppercaseOrSpacedPrefixIsRejected() {
        assertThatThrownBy(() -> new LiteBansTables("LiteBans_")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LiteBansTables("lite bans_")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LiteBansTables("")).isInstanceOf(IllegalArgumentException.class);
    }
}
