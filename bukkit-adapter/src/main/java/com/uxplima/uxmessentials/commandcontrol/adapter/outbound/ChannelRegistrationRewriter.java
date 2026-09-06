package com.uxplima.uxmessentials.commandcontrol.adapter.outbound;

import com.uxplima.uxmessentials.commandcontrol.domain.ChannelHidePolicy;
import com.uxplima.uxmlib.pipeline.PacketVerdict;
import org.jspecify.annotations.NullMarked;

/**
 * The packet seam the plugin-channel hider uses to act on an outbound {@code minecraft:register} /
 * {@code minecraft:unregister} custom-payload packet: it reads the advertised channel names, applies the pure
 * {@link ChannelHidePolicy}, and returns the {@link PacketVerdict} to fold into the interception pipeline - pass the
 * packet unchanged, cancel it (when the policy strips every channel), or rewrite it with only the allowed channels. It
 * exists so the fragile, version-specific packet plumbing lives behind one interface and the listener stays a thin,
 * fail-open dispatcher; a unit test can also supply a fake verdict without a live connection.
 *
 * <p>Any packet that is not a channel-registration payload, or one the implementation cannot read, yields
 * {@link PacketVerdict#pass()} - the hider never touches a packet it does not understand, so it can only ever remove
 * channel names, never break an unrelated packet.
 */
@NullMarked
public interface ChannelRegistrationRewriter {

    /** The verdict for {@code outboundPacket} under {@code policy}: pass, cancel, or rewrite with the allowed channels. */
    PacketVerdict rewrite(Object outboundPacket, ChannelHidePolicy policy);
}
