package com.uxplima.uxmessentials.servertweaks.domain;

import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.UUID;

/**
 * One decoded SignedVelocity plugin-message frame: the proxy's {@link SignedChatDirective} for a specific player's
 * chat or command stream. The frame is the unit of coordination across the proxy hop. The proxy writes one per signed
 * message/command it has ruled on, and this backend applies it to the matching event.
 *
 * <p>The wire layout is fixed by the SignedVelocity protocol and read as a sequence of modified-UTF-8 strings (Java
 * {@link DataInput#readUTF()}, the same encoding the proxy writes with {@code DataOutput#writeUTF}):
 *
 * <ol>
 *   <li>the player UUID,
 *   <li>the source token ({@code CHAT_RESULT} / {@code COMMAND_RESULT}),
 *   <li>the result token ({@code ALLOWED} / {@code CANCEL} / {@code MODIFY}),
 *   <li>and, only for {@code MODIFY}, the replacement content.
 * </ol>
 *
 * <p>Decoding is pure Java with no Bukkit or Guava on the classpath, so it is unit-testable in isolation; a malformed
 * frame raises {@link IllegalArgumentException} (or {@link UncheckedIOException} on a truncated buffer) for the
 * adapter's channel listener to log and drop rather than mishandle.
 *
 * @param player the player whose chat/command stream the directive applies to
 * @param source whether the directive targets chat or a command
 * @param directive the outcome the proxy decided
 */
public record SignedVelocityFrame(UUID player, SignedSource source, SignedChatDirective directive) {

    private static final String RESULT_ALLOWED = "ALLOWED";
    private static final String RESULT_CANCEL = "CANCEL";
    private static final String RESULT_MODIFY = "MODIFY";

    public SignedVelocityFrame {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(directive, "directive");
    }

    /**
     * Decode one frame from the raw plugin-message bytes.
     *
     * @param bytes the frame payload as received on the {@code signedvelocity:main} channel
     * @return the decoded frame
     * @throws IllegalArgumentException if a token (UUID, source, or result) is not valid
     * @throws UncheckedIOException if the buffer is truncated
     */
    public static SignedVelocityFrame decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            UUID player = UUID.fromString(in.readUTF());
            SignedSource source = SignedSource.fromWire(in.readUTF());
            return new SignedVelocityFrame(player, source, directiveFrom(in, in.readUTF()));
        } catch (IOException e) {
            throw new UncheckedIOException("truncated SignedVelocity frame", e);
        }
    }

    private static SignedChatDirective directiveFrom(DataInput in, String result) throws IOException {
        return switch (result) {
            case RESULT_ALLOWED -> SignedChatDirective.allow();
            case RESULT_CANCEL -> SignedChatDirective.cancel();
            case RESULT_MODIFY -> SignedChatDirective.modify(in.readUTF());
            default -> throw new IllegalArgumentException("unknown SignedVelocity result: " + result);
        };
    }
}
