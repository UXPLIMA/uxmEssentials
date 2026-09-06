package com.uxplima.uxmessentials.shared.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.UUID;

/**
 * The pure-Java binary codec for every {@link NetworkMessage}. Encodes a frame to a {@code byte[]} and
 * decodes one back, with no dependency on Bukkit, Velocity, or any serialization framework, just
 * {@link DataOutputStream}/{@link DataInputStream} over a byte array. Both the backend bus client and the
 * proxy broker share this one class so the wire format cannot drift between the two sides (a single
 * pure-codec shared by both ends).
 *
 * <p>Wire layout: a one-byte protocol {@link #VERSION}, then a one-byte {@link NetworkMessage.MessageType}
 * wire tag, then the variant's fields. The version byte lets a future format change be detected rather than
 * silently mis-decoded; {@code bus-channel} and frame-codec compatibility are guaranteed across patch
 * versions only ({@code docs/09-deployment.md} "Updating"), so a mismatched version is rejected loudly.
 * UUIDs are written as two longs; strings via {@link DataOutputStream#writeUTF}.
 */
public final class NetworkMessageCodec {

    /** The protocol version written as the first wire byte; bump only on an incompatible format change. */
    public static final byte VERSION = 1;

    private NetworkMessageCodec() {}

    /** Encode {@code message} to its wire bytes. */
    public static byte[] encode(NetworkMessage message) {
        Objects.requireNonNull(message, "message");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(VERSION);
            out.writeByte(message.type().wireTag());
            writeBody(out, message);
        } catch (IOException impossible) {
            // A ByteArrayOutputStream never performs I/O that can fail; treat any failure as a defect.
            throw new UncheckedIOException("encoding a network message to a byte array failed", impossible);
        }
        return bytes.toByteArray();
    }

    /**
     * Decode {@code frame} back into a {@link NetworkMessage}.
     *
     * @throws IllegalArgumentException if the frame is empty, carries an unknown version, or names an
     *     unknown message type. A malformed or version-incompatible frame is rejected, never half-applied
     */
    public static NetworkMessage decode(byte[] frame) {
        Objects.requireNonNull(frame, "frame");
        if (frame.length == 0) {
            throw new IllegalArgumentException("cannot decode an empty network frame");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(frame))) {
            byte version = in.readByte();
            if (version != VERSION) {
                throw new IllegalArgumentException("unsupported network frame version: " + version);
            }
            NetworkMessage.MessageType type = NetworkMessage.MessageType.fromWireTag(in.readByte());
            return readBody(in, type);
        } catch (IOException truncated) {
            throw new IllegalArgumentException("network frame was truncated or malformed", truncated);
        }
    }

    private static void writeBody(DataOutputStream out, NetworkMessage message) throws IOException {
        out.writeUTF(message.originServer());
        switch (message) {
            case BalanceChanged balance -> {
                writeUuid(out, balance.owner());
                out.writeUTF(balance.currencyId());
            }
            case HomeChanged home -> writeUuid(out, home.owner());
            case WarpChanged warp -> out.writeUTF(warp.warp());
            case VaultChanged vault -> {
                writeUuid(out, vault.owner());
                out.writeInt(vault.vaultIndex());
            }
            case BanChanged ban -> writeUuid(out, ban.target());
            case MuteChanged mute -> writeUuid(out, mute.target());
            case PlayerWarpChanged playerWarp -> writeUuid(out, playerWarp.owner());
            case HologramChanged hologram -> out.writeUTF(hologram.name());
            case NpcChanged npc -> out.writeUTF(npc.name());
            case IgnoreChanged ignore -> writeUuid(out, ignore.owner());
            case ServerPing ping -> out.writeLong(ping.epochMillis());
            case VotePartyFired party -> out.writeInt(party.threshold());
            case VoteCounterChanged counter -> {
                // origin already written above; the counter frame carries no further body.
            }
            case TradeSignalFrame trade -> {
                writeUuid(out, trade.tradeId());
                out.writeUTF(trade.signal());
                writeUuid(out, trade.fromUuid());
                out.writeUTF(trade.fromName());
                writeUuid(out, trade.toUuid());
                out.writeUTF(trade.toName());
            }
            case VanishStateChanged vanish -> {
                writeUuid(out, vanish.player());
                out.writeUTF(vanish.playerName());
                out.writeBoolean(vanish.vanished());
                out.writeInt(vanish.level());
            }
        }
    }

    private static NetworkMessage readBody(DataInputStream in, NetworkMessage.MessageType type) throws IOException {
        String origin = in.readUTF();
        return switch (type) {
            case BALANCE_CHANGED -> new BalanceChanged(origin, readUuid(in), in.readUTF());
            case HOME_CHANGED -> new HomeChanged(origin, readUuid(in));
            case WARP_CHANGED -> new WarpChanged(origin, in.readUTF());
            case VAULT_CHANGED -> new VaultChanged(origin, readUuid(in), in.readInt());
            case BAN_CHANGED -> new BanChanged(origin, readUuid(in));
            case MUTE_CHANGED -> new MuteChanged(origin, readUuid(in));
            case PLAYER_WARP_CHANGED -> new PlayerWarpChanged(origin, readUuid(in));
            case HOLOGRAM_CHANGED -> new HologramChanged(origin, in.readUTF());
            case NPC_CHANGED -> new NpcChanged(origin, in.readUTF());
            case IGNORE_CHANGED -> new IgnoreChanged(origin, readUuid(in));
            case SERVER_PING -> new ServerPing(origin, in.readLong());
            case VOTE_PARTY_FIRED -> new VotePartyFired(origin, in.readInt());
            case VOTE_COUNTER_CHANGED -> new VoteCounterChanged(origin);
            case TRADE_SIGNAL ->
                new TradeSignalFrame(
                        origin, readUuid(in), in.readUTF(), readUuid(in), in.readUTF(), readUuid(in), in.readUTF());
            case VANISH_STATE_CHANGED ->
                new VanishStateChanged(origin, readUuid(in), in.readUTF(), in.readBoolean(), in.readInt());
        };
    }

    private static void writeUuid(DataOutputStream out, UUID uuid) throws IOException {
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream in) throws IOException {
        long high = in.readLong();
        long low = in.readLong();
        return new UUID(high, low);
    }
}
