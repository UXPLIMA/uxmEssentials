package com.uxplima.uxmessentials.itemworld.adapter;

import java.util.Objects;

import com.uxplima.uxmessentials.itemworld.application.ItemworldConfig;
import com.uxplima.uxmessentials.itemworld.application.port.ItemworldAudit;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import org.jspecify.annotations.NullMarked;

/**
 * The constructed itemworld collaborators every inbound command holds, bundled so each Brigadier command takes
 * one argument rather than the kernel ports, the audit logger, and the config view separately. Built once in
 * the context wiring and shared read-only across the command surface.
 *
 * <p>itemworld is stateless and ACL-thin: there are no per-context use-case objects here as in the
 * persistence-backed contexts. The validating value objects live in {@code :core} and the live mutation runs
 * at the adapter boundary on the kernel {@link com.uxplima.uxmessentials.shared.application.port.Scheduler}.
 * The bundle therefore carries the {@link KernelPorts} the commands schedule and render through, the
 * {@link ItemworldAudit} the abusable verbs emit on, and the {@link ItemworldConfig} the per-group and
 * per-command disable gate plus the {@code /give}//{@code /more} caps and the enchant-level clamp read from.
 *
 * @param kernel the shared cross-cutting outbound ports (scheduler, messages, lookups, …)
 * @param audit the abusable-verb audit trail on the {@code com.uxplima.uxmessentials.audit} channel
 * @param config the live, immutable {@code itemworld.conf} view (sub-feature-group + per-command gate, caps)
 * @param disposalLayout the externalised {@code /disposal} window layout (its row count) loaded once at wire time
 */
@NullMarked
public record ItemworldServices(
        KernelPorts kernel, ItemworldAudit audit, ItemworldConfig config, GuiLayout disposalLayout) {

    public ItemworldServices {
        Objects.requireNonNull(kernel, "kernel");
        Objects.requireNonNull(audit, "audit");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(disposalLayout, "disposalLayout");
    }
}
