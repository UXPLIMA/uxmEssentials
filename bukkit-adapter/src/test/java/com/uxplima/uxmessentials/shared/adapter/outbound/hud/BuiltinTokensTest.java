package com.uxplima.uxmessentials.shared.adapter.outbound.hud;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Covers {@link BuiltinTokens}: each built-in {@code {token}} resolves to the live value off a MockBukkit player/server,
 * a source carrying several tokens resolves them all, and PlaceholderAPI {@code %papi%} placeholders plus plain text and
 * unknown {@code {tokens}} are left untouched (so the later PlaceholderAPI/MiniMessage passes still see them).
 */
class BuiltinTokensTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void substitutesThePlayerName() {
        PlayerMock player = server.addPlayer();

        assertThat(BuiltinTokens.apply(player, "Hi {player}!")).isEqualTo("Hi " + player.getName() + "!");
    }

    @Test
    void substitutesTheWorldName() {
        PlayerMock player = server.addPlayer();

        assertThat(BuiltinTokens.apply(player, "World: {world}"))
                .isEqualTo("World: " + player.getWorld().getName());
    }

    @Test
    void substitutesTheOnlineAndMaxPlayerCounts() {
        PlayerMock player = server.addPlayer();

        // One player is online; the max comes from the server.
        assertThat(BuiltinTokens.apply(player, "{online}/{max_players}")).isEqualTo("1/" + server.getMaxPlayers());
    }

    @Test
    void substitutesThePing() {
        PlayerMock player = server.addPlayer();

        // The token is replaced with the player's integer ping (MockBukkit's default is a fixed value).
        assertThat(BuiltinTokens.apply(player, "Ping: {ping}")).isEqualTo("Ping: " + player.getPing());
    }

    @Test
    void substitutesTheDisplayName() {
        PlayerMock player = server.addPlayer();

        assertThat(BuiltinTokens.apply(player, "{displayname}")).isEqualTo(player.getName());
    }

    @Test
    void substitutesTheJvmMetricTokens() {
        PlayerMock player = server.addPlayer();

        // ram_used/ram_max read whole megabytes off the JVM, so they resolve to a non-negative integer string;
        // uptime reads the process runtime bean and renders the compact form (always non-empty).
        assertThat(BuiltinTokens.apply(player, "{ram_used}")).matches("\\d+");
        assertThat(BuiltinTokens.apply(player, "{ram_max}")).matches("\\d+");
        assertThat(BuiltinTokens.apply(player, "{uptime}")).isNotBlank();
    }

    @Test
    void resolvesEveryTokenInOnePass() {
        PlayerMock player = server.addPlayer();

        String rendered = BuiltinTokens.apply(player, "{player} {world} {online}");

        assertThat(rendered)
                .isEqualTo(player.getName() + " " + player.getWorld().getName() + " 1");
    }

    @Test
    void leavesPlaceholderApiTokensAndPlainTextAndUnknownTokensUntouched() {
        PlayerMock player = server.addPlayer();

        // A %papi% placeholder is for PlaceholderAPI; an unknown {token} is not ours, both pass through verbatim, as
        // does plain text and MiniMessage markup.
        String source = "<gold>%server_online% {player} {unknown_token}";

        assertThat(BuiltinTokens.apply(player, source))
                .isEqualTo("<gold>%server_online% " + player.getName() + " {unknown_token}");
    }

    @Test
    void returnsTheSourceUnchangedWhenItHoldsNoToken() {
        PlayerMock player = server.addPlayer();
        String source = "<gold>plain %server_online% text";

        assertThat(BuiltinTokens.apply(player, source)).isSameAs(source);
    }
}
