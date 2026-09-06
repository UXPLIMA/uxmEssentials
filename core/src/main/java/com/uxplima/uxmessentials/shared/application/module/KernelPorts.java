package com.uxplima.uxmessentials.shared.application.module;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.DomainGate;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLocator;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.port.SkinTextures;
import com.uxplima.uxmessentials.shared.application.port.Warmups;
import com.uxplima.uxmessentials.shared.application.port.WorldLookup;

/**
 * The shared cross-cutting outbound ports every command-bearing context consumes, bundled into one
 * injection envelope. A {@link FeatureModule} receives this through its {@link ModuleContext} and
 * constructor-injects the ports it needs into its own adapters. The kernel is wired once in
 * bootstrap, and no module news up a port itself.
 *
 * <p>The bundle holds only interfaces from {@code shared/application/port}, so it stays in {@code
 * :core} alongside the contracts it carries. The adapter implementations
 * ({@code FoliaScheduler}, {@code BukkitPermissions}, {@code PdcCooldowns}, …) are constructed in the
 * bukkit-adapter's {@code PluginModule} and handed in here.
 *
 * @param scheduler the Folia-aware scheduling port
 * @param permissions permission checks and the numbered/world quota reducer
 * @param cooldowns per-holder cooldown gates (tiered and generic-label)
 * @param warmups move-cancellable teleport warmups
 * @param messages MessageKey to rendered-string resolution in the viewer's locale
 * @param messageSink delivery of a rendered string to a viewer
 * @param playerLookup player identity resolution by name or uuid
 * @param worldLookup world identity resolution by name or uid
 * @param playerLocator a player's current position
 * @param events the in-process domain-event publisher
 * @param gate whether whatever is outside the plugin allows an action a use case is about to take
 * @param log operator-facing diagnostics
 */
public record KernelPorts(
        Scheduler scheduler,
        Permissions permissions,
        Cooldowns cooldowns,
        Warmups warmups,
        Messages messages,
        MessageSink messageSink,
        PlayerLookup playerLookup,
        WorldLookup worldLookup,
        PlayerLocator playerLocator,
        DomainEventPublisher events,
        Logger log,
        SkinTextures skins,
        DomainGate gate) {

    public KernelPorts {
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(permissions, "permissions");
        Objects.requireNonNull(cooldowns, "cooldowns");
        Objects.requireNonNull(warmups, "warmups");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(messageSink, "messageSink");
        Objects.requireNonNull(playerLookup, "playerLookup");
        Objects.requireNonNull(worldLookup, "worldLookup");
        Objects.requireNonNull(playerLocator, "playerLocator");
        Objects.requireNonNull(events, "events");
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(skins, "skins");
        Objects.requireNonNull(gate, "gate");
    }

    /**
     * The ports with nothing outside the plugin able to refuse an action. The form every caller uses that is not
     * bootstrap: only the plugin's own wiring has a Bukkit event bus to put the question to.
     */
    public KernelPorts(
            Scheduler scheduler,
            Permissions permissions,
            Cooldowns cooldowns,
            Warmups warmups,
            Messages messages,
            MessageSink messageSink,
            PlayerLookup playerLookup,
            WorldLookup worldLookup,
            PlayerLocator playerLocator,
            DomainEventPublisher events,
            Logger log,
            SkinTextures skins) {
        this(
                scheduler,
                permissions,
                cooldowns,
                warmups,
                messages,
                messageSink,
                playerLookup,
                worldLookup,
                playerLocator,
                events,
                log,
                skins,
                DomainGate.allowAll());
    }

    /**
     * The ports without a skin source, so nothing resolves a skin by name. The form tests use, and the form any
     * caller uses that has no business dressing something in a player's skin.
     */
    public KernelPorts(
            Scheduler scheduler,
            Permissions permissions,
            Cooldowns cooldowns,
            Warmups warmups,
            Messages messages,
            MessageSink messageSink,
            PlayerLookup playerLookup,
            WorldLookup worldLookup,
            PlayerLocator playerLocator,
            DomainEventPublisher events,
            Logger log) {
        this(
                scheduler,
                permissions,
                cooldowns,
                warmups,
                messages,
                messageSink,
                playerLookup,
                worldLookup,
                playerLocator,
                events,
                log,
                SkinTextures.none());
    }
}
