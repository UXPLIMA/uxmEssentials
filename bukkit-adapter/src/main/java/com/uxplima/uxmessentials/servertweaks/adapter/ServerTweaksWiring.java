package com.uxplima.uxmessentials.servertweaks.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.servertweaks.adapter.inbound.SignedVelocityChannelListener;
import com.uxplima.uxmessentials.servertweaks.adapter.inbound.listener.ServerBrandJoinListener;
import com.uxplima.uxmessentials.servertweaks.adapter.inbound.listener.SignedVelocityChatListener;
import com.uxplima.uxmessentials.servertweaks.adapter.inbound.listener.SignedVelocityCommandListener;
import com.uxplima.uxmessentials.servertweaks.adapter.inbound.listener.SignedVelocityQuitListener;
import com.uxplima.uxmessentials.servertweaks.adapter.inbound.listener.UnsignedChatListener;
import com.uxplima.uxmessentials.servertweaks.adapter.outbound.ConsoleFilterInstaller;
import com.uxplima.uxmessentials.servertweaks.adapter.outbound.ConsoleSpamFilter;
import com.uxplima.uxmessentials.servertweaks.adapter.outbound.PluginMessageBrandSender;
import com.uxplima.uxmessentials.servertweaks.application.ServerTweaksConfig;
import com.uxplima.uxmessentials.servertweaks.application.SignedDirectiveQueue;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the server-tweaks context's Bukkit-facing effects over the module's scoped config, wiring each tweak
 * only when its own switch is on. The module ships enabled but every tweak defaults off, so a fresh install wires
 * nothing here until an operator opts a tweak in.
 *
 * <ul>
 *   <li><b>customf3brand</b>. When {@code f3-brand.enabled}, the {@code minecraft:brand} outgoing channel is
 *       registered and a {@link ServerBrandJoinListener} re-sends the configured brand to each joiner so it shows on
 *       F3; the channel is unregistered on stop.
 *   <li><b>console-spam-fix</b>. When {@code console-filter.enabled}, a {@link ConsoleSpamFilter} is attached to the
 *       root Log4j2 logger through the {@link ConsoleFilterInstaller} and detached again on stop.
 *   <li><b>nochatreports</b>. When {@code no-chat-reports.enabled}, an {@link UnsignedChatListener} re-delivers signed
 *       public chat as unsigned messages so it cannot be reported to Mojang.
 *   <li><b>signedvelocity</b>. When {@code signed-velocity.enabled}, the {@code signedvelocity:main} incoming channel
 *       is registered and the chat/command/quit listeners apply a Velocity proxy's rulings from a shared
 *       {@link SignedDirectiveQueue}; the channel is unregistered on stop. Inert without a SignedVelocity proxy.
 * </ul>
 *
 * <p>{@link Wired#stop()} unwinds every effect (unregister the channels, detach the filter) so a disable or reload
 * leaves the server exactly as it was found.
 */
@NullMarked
public final class ServerTweaksWiring {

    private ServerTweaksWiring() {}

    /** Build the enabled tweaks' listeners and their unwind hooks from {@code plugin} and {@code ctx}. */
    public static Wired wire(Plugin plugin, ModuleContext ctx) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        ServerTweaksConfig config = ServerTweaksConfig.from(ctx.config());
        Logger log = ctx.kernel().log();
        List<Listener> listeners = new ArrayList<>();
        List<Runnable> stops = new ArrayList<>();
        wireF3Brand(plugin, config.f3Brand(), ctx.kernel().scheduler(), listeners, stops);
        wireConsoleFilter(config.consoleFilter(), log, stops);
        wireNoChatReports(config.noChatReports(), listeners);
        wireSignedVelocity(plugin, config.signedVelocity(), log, listeners, stops);
        return new Wired(List.copyOf(listeners), List.copyOf(stops));
    }

    private static void wireF3Brand(
            Plugin plugin,
            ServerTweaksConfig.F3Brand f3Brand,
            Scheduler scheduler,
            List<Listener> listeners,
            List<Runnable> stops) {
        if (!f3Brand.enabled()) {
            return;
        }
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, PluginMessageBrandSender.BRAND_CHANNEL);
        PluginMessageBrandSender sender = new PluginMessageBrandSender(plugin, f3Brand.brand());
        listeners.add(new ServerBrandJoinListener(true, sender, scheduler));
        stops.add(() -> plugin.getServer()
                .getMessenger()
                .unregisterOutgoingPluginChannel(plugin, PluginMessageBrandSender.BRAND_CHANNEL));
    }

    private static void wireConsoleFilter(
            ServerTweaksConfig.ConsoleFilter consoleFilter, Logger log, List<Runnable> stops) {
        if (!consoleFilter.enabled()) {
            return;
        }
        ConsoleFilterInstaller installer =
                new ConsoleFilterInstaller(new ConsoleSpamFilter(consoleFilter.toPolicy()), log);
        installer.install();
        stops.add(installer::uninstall);
    }

    private static void wireNoChatReports(ServerTweaksConfig.NoChatReports noChatReports, List<Listener> listeners) {
        if (!noChatReports.enabled()) {
            return;
        }
        listeners.add(new UnsignedChatListener(noChatReports.toPolicy()));
    }

    private static void wireSignedVelocity(
            Plugin plugin,
            ServerTweaksConfig.SignedVelocity signedVelocity,
            Logger log,
            List<Listener> listeners,
            List<Runnable> stops) {
        if (!signedVelocity.enabled()) {
            return;
        }
        SignedDirectiveQueue queue = new SignedDirectiveQueue();
        SignedVelocityChannelListener channel = new SignedVelocityChannelListener(queue, log);
        plugin.getServer()
                .getMessenger()
                .registerIncomingPluginChannel(plugin, SignedVelocityChannelListener.CHANNEL, channel);
        listeners.add(new SignedVelocityChatListener(queue));
        listeners.add(new SignedVelocityCommandListener(queue));
        listeners.add(new SignedVelocityQuitListener(queue));
        stops.add(() -> {
            plugin.getServer()
                    .getMessenger()
                    .unregisterIncomingPluginChannel(plugin, SignedVelocityChannelListener.CHANNEL, channel);
            queue.clear();
        });
    }

    /**
     * Everything the server-tweaks module contributes once wired: the listeners the plugin registers and the unwind
     * hooks the plugin runs on stop (unregister the brand channel, detach the console filter).
     *
     * @param listeners the Bukkit listeners to register (the F3-brand join listener, when that tweak is on)
     * @param stops the per-tweak unwind hooks, run in reverse on module stop
     */
    public record Wired(List<Listener> listeners, List<Runnable> stops) {

        public Wired {
            listeners = List.copyOf(listeners);
            stops = List.copyOf(stops);
        }

        /** Unwind every enabled tweak's effect, so a disable or reload strands no channel and no log filter. */
        public void stop() {
            for (int i = stops.size() - 1; i >= 0; i--) {
                stops.get(i).run();
            }
        }
    }
}
