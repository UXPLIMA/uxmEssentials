package com.uxplima.uxmessentials.shared.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class NetworkMessageCodecTest {

    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    static Stream<NetworkMessage> messages() {
        return Stream.of(
                new BalanceChanged("survival-1", OWNER, "coins"),
                new HomeChanged("survival-1", OWNER),
                new WarpChanged("lobby-2", "spawn"),
                new VaultChanged("survival-1", OWNER, 3),
                new BanChanged("survival-1", OWNER),
                new MuteChanged("survival-1", OWNER),
                new PlayerWarpChanged("survival-1", OWNER),
                new HologramChanged("survival-1", "spawn"),
                new NpcChanged("survival-1", "guide"),
                new IgnoreChanged("survival-1", OWNER),
                new ServerPing("lobby-2", 1_717_000_000_000L),
                new VotePartyFired("survival", 25),
                new VoteCounterChanged("lobby"),
                new TradeSignalFrame(
                        "survival-1",
                        UUID.fromString("00000000-0000-0000-0000-0000000000cc"),
                        "READY",
                        OWNER,
                        "Alice",
                        UUID.fromString("00000000-0000-0000-0000-0000000000bb"),
                        "Bob"),
                new VanishStateChanged("survival-1", OWNER, "Alice", true, 3),
                new VanishStateChanged("survival-2", OWNER, "Alice", false, 1));
    }

    @ParameterizedTest
    @MethodSource("messages")
    void roundTripsEveryVariant(NetworkMessage original) {
        NetworkMessage decoded = NetworkMessageCodec.decode(NetworkMessageCodec.encode(original));

        assertThat(decoded).isEqualTo(original);
        assertThat(decoded.originServer()).isEqualTo(original.originServer());
        assertThat(decoded.type()).isEqualTo(original.type());
    }

    @Test
    void everyTypeHasADistinctWireTag() {
        long distinct = Stream.of(NetworkMessage.MessageType.values())
                .map(NetworkMessage.MessageType::wireTag)
                .distinct()
                .count();

        assertThat(distinct).isEqualTo(NetworkMessage.MessageType.values().length);
    }

    @Test
    void rejectsAnEmptyFrame() {
        assertThatThrownBy(() -> NetworkMessageCodec.decode(new byte[0])).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnUnknownVersion() {
        byte[] frame = NetworkMessageCodec.encode(new HomeChanged("survival-1", OWNER));
        frame[0] = 99;

        assertThatThrownBy(() -> NetworkMessageCodec.decode(frame)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnUnknownWireTag() {
        assertThatThrownBy(() -> NetworkMessage.MessageType.fromWireTag((byte) 120))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnUnknownWireTagInAFullFrame() {
        // A frame from a newer build carrying a MessageType this version does not know: the whole decode path
        // must reject it (so the bus drops it) rather than mis-decode or crash a peer mid rolling-upgrade.
        byte[] frame = NetworkMessageCodec.encode(new HomeChanged("survival-1", OWNER));
        frame[1] = 120; // the wire-tag byte. Frame[0] is the protocol version

        assertThatThrownBy(() -> NetworkMessageCodec.decode(frame)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsATruncatedBody() {
        // A valid version and tag but the body cut short (a network truncation or a short/hostile peer frame):
        // the read failure must surface as the IllegalArgumentException the bus catches and drops, not escape
        // as a raw IOException that would kill the listener thread.
        byte[] full = NetworkMessageCodec.encode(new BalanceChanged("survival-1", OWNER, "coins"));
        byte[] truncated = Arrays.copyOf(full, 2); // keep [version][tag], drop the entire body

        assertThatThrownBy(() -> NetworkMessageCodec.decode(truncated)).isInstanceOf(IllegalArgumentException.class);
    }
}
