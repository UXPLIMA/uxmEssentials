package com.uxplima.uxmessentials.commandcontrol.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Server;
import org.bukkit.event.Listener;

import com.uxplima.uxmessentials.commandcontrol.adapter.inbound.listener.ChannelHideConnectionListener;
import com.uxplima.uxmessentials.commandcontrol.adapter.inbound.listener.ChannelHideListener;
import com.uxplima.uxmessentials.commandcontrol.adapter.inbound.listener.CommandGateListener;
import com.uxplima.uxmessentials.commandcontrol.adapter.inbound.listener.CommandNormalizationListener;
import com.uxplima.uxmessentials.commandcontrol.adapter.inbound.listener.CommandSpamListener;
import com.uxplima.uxmessentials.commandcontrol.adapter.inbound.listener.CommandVisibilityListener;
import com.uxplima.uxmessentials.commandcontrol.adapter.outbound.CommandPermissionView;
import com.uxplima.uxmessentials.commandcontrol.adapter.outbound.PlayerGroupSource;
import com.uxplima.uxmessentials.commandcontrol.adapter.outbound.PlayerGroupSources;
import com.uxplima.uxmessentials.commandcontrol.adapter.outbound.ReflectiveChannelRegistrationRewriter;
import com.uxplima.uxmessentials.commandcontrol.application.CommandControlConfig;
import com.uxplima.uxmessentials.commandcontrol.domain.ChannelHidePolicy;
import com.uxplima.uxmessentials.commandcontrol.domain.CommandRateLimiter;
import com.uxplima.uxmessentials.commandcontrol.domain.WorldHidePolicies;
import com.uxplima.uxmessentials.commandcontrol.domain.WorldRuleSets;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmlib.pipeline.ChannelResolver;
import com.uxplima.uxmlib.pipeline.PacketListenerRegistry;
import com.uxplima.uxmlib.pipeline.PacketPipeline;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the command-control context's listeners over the injected kernel ports and the module's scoped config.
 * The world-scoped rule set and hide policy, the command-spam rate limiter, and the channel-hide policy are all derived
 * from config once here - per the atomic-reload rule a hot-reload re-runs this wiring and swaps in fresh listeners - and
 * the group source is chosen by probing the server for LuckPerms (an empty fallback otherwise). The listeners
 * contributed:
 *
 * <ul>
 *   <li>{@link CommandNormalizationListener} - lowercases the base command label first ({@code auto-lowercase-base-commands});
 *   <li>{@link CommandSpamListener} - rate-limits commands per player when {@code command-spam.enabled};
 *   <li>{@link CommandGateListener} - blocks a disallowed command's execution, per the player's current world;
 *   <li>{@link CommandVisibilityListener} - keeps disallowed / hidden commands out of the client command list, tab
 *       completion, and the scrub-help output, recomputing on a world change;
 *   <li>{@link ChannelHideConnectionListener} - when {@code plugin-channel-hide.enabled}, splices the packet
 *       interceptor that strips non-allowed plugin-messaging channels from the client's channel-registration list.
 * </ul>
 *
 * <p>Each optional listener is wired only when its feature is switched on, so a disabled feature registers nothing. The
 * context persists nothing and holds no runtime state that must drain on stop; unregistering the listeners is enough.
 */
@NullMarked
public final class CommandControlWiring {

    /** The node that exempts a holder from the command gate — always allowed through, mirroring the other bypasses. */
    public static final String BYPASS_PERMISSION = "uxmessentials.commandcontrol.bypass";

    /** The node that reveals the plugin-listing / help commands the plugin-hide otherwise removes and blocks. */
    public static final String VIEW_PERMISSION = "uxmessentials.commandcontrol.viewplugins";

    /** The node that exempts a holder from the command-spam rate limiter (never counted, never actioned). */
    public static final String SPAM_BYPASS_PERMISSION = "uxmessentials.commandcontrol.spam.bypass";

    /** The node that exempts a holder from the plugin-channel hider (their channel list is never stripped). */
    public static final String CHANNELHIDE_BYPASS_PERMISSION = "uxmessentials.commandcontrol.channelhide.bypass";

    private CommandControlWiring() {}

    /** Build the command-control listeners from {@code server} and {@code ctx}, ready to register. */
    public static Wired wire(Server server, ModuleContext ctx) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(ctx, "ctx");
        KernelPorts kernel = ctx.kernel();
        CommandControlConfig config = CommandControlConfig.from(ctx.config());
        WorldRuleSets worldRules = config.toWorldRuleSets(BYPASS_PERMISSION);
        WorldHidePolicies worldHidePolicies = config.toWorldHidePolicies(VIEW_PERMISSION);
        PlayerGroupSource groups = PlayerGroupSources.create(server);
        CommandPermissionView permissions = CommandPermissionView.backedBy(server.getCommandMap());

        List<Listener> listeners = new ArrayList<>();
        if (config.autoLowercaseBaseCommands()) {
            listeners.add(new CommandNormalizationListener());
        }
        CommandRateLimiter rateLimiter = config.toRateLimiter();
        if (rateLimiter.isEnabled()) {
            listeners.add(new CommandSpamListener(
                    rateLimiter, SPAM_BYPASS_PERMISSION, kernel.messages(), kernel.messageSink()));
        }
        listeners.add(new CommandGateListener(
                worldRules,
                config.blockNamespaceBypass(),
                groups,
                kernel.messages(),
                kernel.messageSink(),
                config.denyMessage()));
        listeners.add(new CommandVisibilityListener(
                worldRules,
                worldHidePolicies,
                groups,
                permissions,
                config.tabCompletionEnabled(),
                config.denyListCommands(),
                BYPASS_PERMISSION,
                kernel.messages(),
                kernel.messageSink()));
        ChannelHidePolicy channelHide = config.toChannelHidePolicy();
        if (channelHide.isEnabled()) {
            listeners.add(channelHideConnection(channelHide, kernel));
        }
        return new Wired(List.copyOf(listeners), worldRules, config.blockNamespaceBypass(), groups);
    }

    /**
     * Everything the command-control module contributes once wired. The listeners are what gets registered; the rest
     * is what the published check needs to answer with the rules that are actually in force, which is why the same
     * resolution is handed out here rather than rebuilt from config a second time.
     *
     * @param listeners the gate, visibility, normalisation, spam and channel-hide listeners to register
     * @param worldRules the world-scoped rules the gate consults
     * @param blockNamespaceBypass whether a {@code namespace:} prefix is folded back to the bare command
     * @param groups the primary-group source the rules read, LuckPerms-backed when it is installed
     */
    public record Wired(
            List<Listener> listeners,
            WorldRuleSets worldRules,
            boolean blockNamespaceBypass,
            PlayerGroupSource groups) {

        public Wired {
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(worldRules, "worldRules");
            Objects.requireNonNull(groups, "groups");
        }
    }

    /**
     * Build the plugin-channel hider: a namespaced packet pipeline whose single shared listener strips non-allowed
     * channels from the outbound register/unregister advertisement, and the join listener that splices it per
     * connection. Mirrors the tablist-suppression pipeline wiring so it never collides with the nametag/npc/tablist
     * packet stacks, and listener faults are logged off the I/O thread through the kernel log.
     */
    private static Listener channelHideConnection(ChannelHidePolicy policy, KernelPorts kernel) {
        PacketListenerRegistry registry = new PacketListenerRegistry();
        PacketPipeline pipeline = new PacketPipeline(
                new ChannelResolver(),
                registry,
                "uxmessentials:commandcontrol-channelhide",
                fault -> kernel.log().error("commandcontrol channel-hide listener fault", fault));
        registry.register(
                new ChannelHideListener(new ReflectiveChannelRegistrationRewriter(kernel.log()), policy, kernel.log()));
        return new ChannelHideConnectionListener(pipeline, CHANNELHIDE_BYPASS_PERMISSION);
    }
}
