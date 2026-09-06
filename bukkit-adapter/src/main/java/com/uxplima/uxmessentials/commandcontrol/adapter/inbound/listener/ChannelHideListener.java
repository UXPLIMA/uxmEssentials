package com.uxplima.uxmessentials.commandcontrol.adapter.inbound.listener;

import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.commandcontrol.adapter.outbound.ChannelRegistrationRewriter;
import com.uxplima.uxmessentials.commandcontrol.domain.ChannelHidePolicy;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmlib.pipeline.PacketAction;
import com.uxplima.uxmlib.pipeline.PacketListener;
import com.uxplima.uxmlib.pipeline.PacketVerdict;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The uxmLib {@link PacketListener} behind the plugin-channel hider: for every outbound packet it asks the
 * {@link ChannelRegistrationRewriter} whether the packet is a {@code minecraft:register} / {@code minecraft:unregister}
 * channel-list advertisement and, if so, folds the {@link ChannelHidePolicy} over its channel names - passing an
 * all-allowed list unchanged, cancelling an all-disallowed one, or rewriting a mixed one down to the allowed channels.
 * A client-side mod fingerprints installed plugins by the channels the server registers, so stripping the non-allowed
 * channels denies it that signal.
 *
 * <p><strong>Netty-thread safety.</strong> The callback runs on a Netty I/O thread. It touches no Bukkit API and never
 * blocks - the rewriter is pure reflection over the packet object and the policy is an immutable snapshot. Any failure
 * is swallowed by the rewriter (which returns pass) and, as a second belt, by this listener's own guard, so the hider
 * can never throw on the I/O thread and can never break a player's connection: the worst case is a channel list left
 * unfiltered.
 *
 * <p>The {@code channelhide.bypass} escape is handled upstream: the connection listener injects this interceptor only
 * into a non-bypass player's pipeline, so a bypass holder's channels are never touched and the listener needs no
 * per-packet permission check on the I/O thread.
 */
@NullMarked
public final class ChannelHideListener implements PacketListener {

    private final ChannelRegistrationRewriter rewriter;
    private final ChannelHidePolicy policy;
    private final Logger log;

    public ChannelHideListener(ChannelRegistrationRewriter rewriter, ChannelHidePolicy policy, Logger log) {
        this.rewriter = Objects.requireNonNull(rewriter, "rewriter");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public PacketAction onSend(@Nullable UUID player, Object packet) {
        // The rewrite path is in onSendVerdict; a plain pass keeps the pass/cancel-only contract correct.
        return PacketAction.PASS;
    }

    @Override
    public PacketVerdict onSendVerdict(@Nullable UUID player, Object packet) {
        try {
            return rewriter.rewrite(packet, policy);
        } catch (RuntimeException failure) {
            // Never throw on the Netty thread and never drop a connection: log and forward the original.
            log.error("plugin-channel hider verdict failed; forwarding original packet", failure);
            return PacketVerdict.pass();
        }
    }
}
