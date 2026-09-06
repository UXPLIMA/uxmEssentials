package com.uxplima.uxmessentials.commandcontrol.adapter.outbound;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.commandcontrol.domain.ChannelHidePolicy;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmlib.pipeline.PacketVerdict;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The best-effort, reflection-only {@link ChannelRegistrationRewriter} for the plugin-channel hider. It recognises the
 * outbound {@code ClientboundCustomPayloadPacket} carrying a {@code minecraft:register} / {@code minecraft:unregister}
 * channel list, reads the null-separated channel names out of its payload, and folds the pure
 * {@link ChannelHidePolicy} over them:
 *
 * <ul>
 *   <li>no channel removed &rarr; {@link PacketVerdict#pass()} (the packet is left exactly as it was);
 *   <li>every channel removed &rarr; {@link PacketVerdict#cancel()} (advertise nothing);
 *   <li>some removed &rarr; {@link PacketVerdict#rewrite(Object)} with a payload rebuilt from only the allowed channels.
 * </ul>
 *
 * <p><strong>Deliberately reflective, deliberately fail-open.</strong> Per the project's packet policy the plugin adds
 * no {@code net.minecraft} import of its own - the one sanctioned NMS seam is the offline-player storage - so this
 * reaches the vanilla custom-payload packet only by reflection, the same way it would through a uxmLib helper if one
 * existed. Every step is guarded: a packet that is not a channel registration, a payload shape this build does not
 * expose, or any reflective failure yields {@code pass()} and (once) a log line, so the hider can only ever strip
 * channel names it understands and can never drop an unrelated packet or break a connection. It is not
 * {@code @NullMarked}-analysed against the NMS surface because that surface is unannotated; the
 * {@link ChannelRegistrationRewriter} seam it implements is null-marked, so the rest of the context sees a fully
 * checked contract.
 *
 * <p>The durable path is a first-class {@code NmsChannelPackets} helper in uxmLib (mirroring the tablist / nametag NMS
 * ports); until then this reflective reader is the achievable implementation.
 */
@NullMarked
public final class ReflectiveChannelRegistrationRewriter implements ChannelRegistrationRewriter {

    private static final String PACKET_SIMPLE_NAME = "ClientboundCustomPayloadPacket";
    private static final String REGISTER_ID = "minecraft:register";
    private static final String UNREGISTER_ID = "minecraft:unregister";
    private static final int CHANNEL_SEPARATOR = 0;

    private final Logger log;
    private volatile boolean warned;

    public ReflectiveChannelRegistrationRewriter(Logger log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public PacketVerdict rewrite(Object outboundPacket, ChannelHidePolicy policy) {
        Objects.requireNonNull(outboundPacket, "outboundPacket");
        Objects.requireNonNull(policy, "policy");
        if (!policy.isEnabled() || !outboundPacket.getClass().getSimpleName().equals(PACKET_SIMPLE_NAME)) {
            return PacketVerdict.pass();
        }
        try {
            return rewriteRegistration(outboundPacket, policy);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            warnOnce(failure);
            return PacketVerdict.pass();
        }
    }

    /** The reflective body: read the channel list, filter it, and fold the outcome into a verdict. Fail-open on any gap. */
    private PacketVerdict rewriteRegistration(Object packet, ChannelHidePolicy policy)
            throws ReflectiveOperationException {
        Object payload = invokeAccessor(packet, "payload");
        if (payload == null) {
            return PacketVerdict.pass();
        }
        String channelId = payloadChannelId(payload);
        if (channelId == null || !isRegistration(channelId)) {
            return PacketVerdict.pass();
        }
        byte[] data = payloadData(payload);
        if (data == null) {
            return PacketVerdict.pass();
        }
        List<String> channels = splitChannels(data);
        List<String> kept = policy.filter(channels);
        if (kept.size() == channels.size()) {
            return PacketVerdict.pass();
        }
        if (kept.isEmpty()) {
            return PacketVerdict.cancel();
        }
        Object rebuilt = rebuildPacket(packet, payload, joinChannels(kept));
        return rebuilt == null ? PacketVerdict.pass() : PacketVerdict.rewrite(rebuilt);
    }

    /** True when the payload channel id is the plugin-channel register/unregister message the client fingerprints on. */
    private static boolean isRegistration(String channelId) {
        return channelId.equalsIgnoreCase(REGISTER_ID) || channelId.equalsIgnoreCase(UNREGISTER_ID);
    }

    /** The payload's own channel identifier ("minecraft:register"), via {@code id()} or {@code type().id()}. */
    private static @Nullable String payloadChannelId(Object payload) throws ReflectiveOperationException {
        Object id = invokeAccessor(payload, "id");
        if (id != null) {
            return id.toString();
        }
        Object type = invokeAccessor(payload, "type");
        if (type == null) {
            return null;
        }
        Object typeId = invokeAccessor(type, "id");
        return typeId == null ? null : typeId.toString();
    }

    /** The raw payload bytes (the null-separated channel list), read non-destructively from a ByteBuf or a byte[]. */
    private static byte @Nullable [] payloadData(Object payload) {
        Object raw = readDataMember(payload);
        if (raw instanceof ByteBuf buffer) {
            ByteBuf copy = buffer.duplicate();
            byte[] bytes = new byte[copy.readableBytes()];
            copy.readBytes(bytes);
            return bytes;
        }
        return raw instanceof byte[] bytes ? bytes.clone() : null;
    }

    /** Locate the payload's data member: a {@code data()} accessor, or a ByteBuf / byte[] field, whichever this build has. */
    private static @Nullable Object readDataMember(Object payload) {
        try {
            Object viaAccessor = invokeAccessor(payload, "data");
            if (viaAccessor != null) {
                return viaAccessor;
            }
        } catch (ReflectiveOperationException ignored) {
            // No data() accessor on this build; fall through to a field scan.
        }
        for (Field field : payload.getClass().getDeclaredFields()) {
            if (ByteBuf.class.isAssignableFrom(field.getType()) || field.getType() == byte[].class) {
                try {
                    field.setAccessible(true);
                    return field.get(payload);
                } catch (ReflectiveOperationException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    /** Rebuild the payload (same id, new channel bytes) and wrap it in a fresh packet, or {@code null} if not possible. */
    private @Nullable Object rebuildPacket(Object packet, Object payload, byte[] channelBytes)
            throws ReflectiveOperationException {
        Object id = invokeAccessor(payload, "id");
        if (id == null) {
            return null;
        }
        Object newPayload = rebuildPayload(payload, id, channelBytes);
        if (newPayload == null) {
            return null;
        }
        Constructor<?> packetCtor = singleArgConstructor(packet.getClass());
        return packetCtor == null ? null : packetCtor.newInstance(newPayload);
    }

    /** Build a new payload of the original's class from its id and the filtered channel bytes (ByteBuf or byte[] form). */
    private static @Nullable Object rebuildPayload(Object payload, Object id, byte[] channelBytes)
            throws ReflectiveOperationException {
        for (Constructor<?> ctor : payload.getClass().getDeclaredConstructors()) {
            Class<?>[] params = ctor.getParameterTypes();
            if (params.length != 2 || !params[0].isInstance(id)) {
                continue;
            }
            ctor.setAccessible(true);
            if (ByteBuf.class.isAssignableFrom(params[1])) {
                return ctor.newInstance(id, Unpooled.wrappedBuffer(channelBytes));
            }
            if (params[1] == byte[].class) {
                return ctor.newInstance(id, channelBytes);
            }
        }
        return null;
    }

    /** The single-argument constructor of {@code type}, made accessible, or {@code null} when there is none. */
    private static @Nullable Constructor<?> singleArgConstructor(Class<?> type) {
        for (Constructor<?> ctor : type.getDeclaredConstructors()) {
            if (ctor.getParameterCount() == 1) {
                ctor.setAccessible(true);
                return ctor;
            }
        }
        return null;
    }

    /** Split the null-separated channel list, dropping empties. */
    private static List<String> splitChannels(byte[] data) {
        List<String> channels = new ArrayList<>();
        int start = 0;
        for (int i = 0; i <= data.length; i++) {
            if (i == data.length || data[i] == CHANNEL_SEPARATOR) {
                if (i > start) {
                    channels.add(new String(data, start, i - start, StandardCharsets.UTF_8));
                }
                start = i + 1;
            }
        }
        return channels;
    }

    /** Re-encode the kept channels as the null-separated list the register/unregister payload carries. */
    private static byte[] joinChannels(List<String> channels) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < channels.size(); i++) {
            if (i > 0) {
                out.write(CHANNEL_SEPARATOR);
            }
            byte[] name = channels.get(i).getBytes(StandardCharsets.UTF_8);
            out.write(name, 0, name.length);
        }
        return out.toByteArray();
    }

    /** Invoke a zero-arg accessor {@code name} on {@code target}, or return {@code null} when the method is absent. */
    private static @Nullable Object invokeAccessor(Object target, String name) throws ReflectiveOperationException {
        Method method;
        try {
            method = target.getClass().getMethod(name);
        } catch (NoSuchMethodException absent) {
            return null;
        }
        method.setAccessible(true);
        return method.invoke(target);
    }

    /** Log the first reflective miss so an operator sees the hider degraded to pass-through, then stay quiet. */
    private void warnOnce(Throwable failure) {
        if (!warned) {
            warned = true;
            log.warn("plugin-channel hider could not read/rewrite a channel-registration packet on this server "
                    + "build; leaving channel lists unfiltered (pass-through). Cause: " + failure);
        }
    }
}
