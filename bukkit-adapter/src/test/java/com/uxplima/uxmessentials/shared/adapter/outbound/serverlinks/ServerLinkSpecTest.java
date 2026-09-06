package com.uxplima.uxmessentials.shared.adapter.outbound.serverlinks;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Optional;

import org.bukkit.ServerLinks;

import org.junit.jupiter.api.Test;

/**
 * Coverage of {@link ServerLinkSpec#parse}: the config-entry → validated-link mapping. Asserts a built-in type
 * parses case-insensitively, a custom label parses, type wins when both are present, and every malformed shape (no
 * type/label, unknown type, blank/relative/malformed URL) is dropped to empty so the applier skips it without
 * aborting the list.
 */
class ServerLinkSpecTest {

    @Test
    void parsesBuiltInType() {
        Optional<ServerLinkSpec> spec = ServerLinkSpec.parse("WEBSITE", null, "https://example.com");
        assertThat(spec).isPresent();
        assertThat(spec.get().type()).isEqualTo(ServerLinks.Type.WEBSITE);
        assertThat(spec.get().label()).isNull();
        assertThat(spec.get().url()).isEqualTo(URI.create("https://example.com"));
    }

    @Test
    void parsesTypeCaseInsensitively() {
        assertThat(ServerLinkSpec.parse("website", null, "https://example.com")
                        .orElseThrow()
                        .type())
                .isEqualTo(ServerLinks.Type.WEBSITE);
    }

    @Test
    void parsesCustomLabel() {
        Optional<ServerLinkSpec> spec = ServerLinkSpec.parse(null, "Discord", "https://discord.gg/xxxx");
        assertThat(spec).isPresent();
        assertThat(spec.get().type()).isNull();
        assertThat(spec.get().label()).isEqualTo("Discord");
        assertThat(spec.get().url()).isEqualTo(URI.create("https://discord.gg/xxxx"));
    }

    @Test
    void typeWinsWhenBothPresent() {
        ServerLinkSpec spec = ServerLinkSpec.parse("FORUMS", "ignored", "https://forum.example.com")
                .orElseThrow();
        assertThat(spec.type()).isEqualTo(ServerLinks.Type.FORUMS);
        assertThat(spec.label()).isNull();
    }

    @Test
    void emptyWhenNeitherTypeNorLabel() {
        assertThat(ServerLinkSpec.parse(null, null, "https://example.com")).isEmpty();
        assertThat(ServerLinkSpec.parse("  ", "  ", "https://example.com")).isEmpty();
    }

    @Test
    void emptyOnUnknownTypeWithNoLabel() {
        assertThat(ServerLinkSpec.parse("NONSENSE", null, "https://example.com"))
                .isEmpty();
    }

    @Test
    void unknownTypeFallsBackToLabelWhenPresent() {
        ServerLinkSpec spec = ServerLinkSpec.parse("NONSENSE", "My Site", "https://example.com")
                .orElseThrow();
        assertThat(spec.type()).isNull();
        assertThat(spec.label()).isEqualTo("My Site");
    }

    @Test
    void emptyOnMissingOrMalformedUrl() {
        assertThat(ServerLinkSpec.parse("WEBSITE", null, null)).isEmpty();
        assertThat(ServerLinkSpec.parse("WEBSITE", null, "  ")).isEmpty();
        assertThat(ServerLinkSpec.parse("WEBSITE", null, "not a url")).isEmpty();
        assertThat(ServerLinkSpec.parse("WEBSITE", null, "/relative/path")).isEmpty();
    }

    @Test
    void describeRendersTypeOrQuotedLabel() {
        assertThat(ServerLinkSpec.parse("STATUS", null, "https://status.example.com")
                        .orElseThrow()
                        .describe())
                .isEqualTo("STATUS");
        assertThat(ServerLinkSpec.parse(null, "Store", "https://store.example.com")
                        .orElseThrow()
                        .describe())
                .isEqualTo("\"Store\"");
    }
}
