package com.uxplima.uxmessentials.discordlink.adapter;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import com.uxplima.uxmessentials.api.link.DiscordLinkConfirmation;
import com.uxplima.uxmessentials.discordlink.adapter.inbound.command.DiscordLinkCommands;
import com.uxplima.uxmessentials.discordlink.adapter.inbound.gui.DiscordStatusView;
import com.uxplima.uxmessentials.discordlink.adapter.outbound.ConfirmLinkService;
import com.uxplima.uxmessentials.discordlink.application.BeginLink;
import com.uxplima.uxmessentials.discordlink.application.ConfirmLink;
import com.uxplima.uxmessentials.discordlink.application.LinkStatus;
import com.uxplima.uxmessentials.discordlink.application.Unlink;
import com.uxplima.uxmessentials.discordlink.application.port.DiscordBridge;
import com.uxplima.uxmessentials.discordlink.application.port.DiscordLinkStore;
import com.uxplima.uxmessentials.persistence.discordlink.DiscordLinkStores;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the discord-link context's adapters and use cases over the injected kernel ports and the
 * persistence DSL, and produces both the Brigadier command list the plugin registers and the
 * {@link DiscordLinkConfirmation} seam implementation the plugin exposes through the {@code ServicesManager} so
 * the optional Discord bridge can redeem a {@code /link} code. This is the one place the discord-link context is
 * wired: nothing else news up its classes.
 *
 * <p>The store is the un-cached jOOQ adapter (linking is low-traffic). The one-time code's lifetime is the
 * module's {@code code-ttl-seconds} config value; codes are drawn from a {@link SecureRandom}-seeded generator
 * so a code is not guessable from a previous one.
 */
@NullMarked
public final class DiscordlinkWiring {

    private static final int DEFAULT_TTL_SECONDS = 600;

    private DiscordlinkWiring() {}

    /** Build the discord-link adapters and use cases over the kernel ports and the persistence DSL. */
    public static Wired wire(
            ModuleContext ctx, Persistence persistence, GuiLayouts guiLayouts, DiscordBridge bridge, Menus menus) {
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(persistence, "persistence");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        Objects.requireNonNull(bridge, "bridge");
        Objects.requireNonNull(menus, "menus");
        KernelPorts kernel = ctx.kernel();
        DiscordLinkStore store = DiscordLinkStores.jooq(persistence);
        Clock clock = Clock.systemUTC();
        BeginLink beginLink = new BeginLink(store, clock, rng(), ttl(ctx));
        ConfirmLink confirmLink = new ConfirmLink(store, clock);
        Unlink unlink = new Unlink(store, kernel.events());
        LinkStatus linkStatus = new LinkStatus(store);
        Notifier notifier = new Notifier(kernel.messages(), kernel.messageSink());
        DiscordLinkServices services =
                new DiscordLinkServices(beginLink, confirmLink, unlink, linkStatus, notifier, bridge);
        // The link-status panel reuses the SP0 GUI framework over the shared catalog and the data-folder layout
        // loader. It surfaces only what the use cases support: a read-only status line, a generate-code button
        // (BeginLink, told via the same chat messages /discordlink sends), and a confirm-gated unlink (Unlink).
        // /discordlink gui and the /uxmess gui hub entry both open it.
        GuiText guiText = new GuiText(kernel.messages());
        DiscordStatusView view = new DiscordStatusView(
                guiText,
                kernel.scheduler(),
                guiLayouts,
                kernel.messages(),
                beginLink,
                unlink,
                linkStatus,
                notifier,
                bridge,
                menus);
        DiscordLinkConfirmation confirmation =
                new ConfirmLinkService(confirmLink, kernel.playerLookup(), kernel.events());
        return new Wired(DiscordLinkCommands.all(services, view), confirmation, store, unlink, view);
    }

    private static Duration ttl(ModuleContext ctx) {
        return Duration.ofSeconds(Math.max(1, ctx.config().getInt("code-ttl-seconds", DEFAULT_TTL_SECONDS)));
    }

    private static RandomGenerator rng() {
        // A SecureRandom-class generator so a fresh code is not predictable from a leaked previous one.
        return RandomGeneratorFactory.of("SecureRandom").create();
    }

    /**
     * Everything the discord-link module contributes once wired: the Brigadier commands, the seam implementation
     * the plugin registers into the {@code ServicesManager}, and the link-status panel the {@code /uxmess gui}
     * hub entry opens. The context holds no repeating scheduled work and no in-memory store, so there is nothing
     * to drain on stop.
     *
     * @param commands the Brigadier command registrations to publish
     * @param confirmation the {@code /link} confirmation seam the Discord bridge consumes
     * @param store the DB-backed link store the PAPI seam and the published query read the binding from
     * @param unlink the use case behind {@code /discordunlink}, which the published unlink action runs
     * @param view the per-player link-status panel registered on the {@code /uxmess gui} hub
     */
    public record Wired(
            List<CommandRegistration> commands,
            DiscordLinkConfirmation confirmation,
            DiscordLinkStore store,
            Unlink unlink,
            DiscordStatusView view) {

        public Wired {
            commands = List.copyOf(commands);
            Objects.requireNonNull(confirmation, "confirmation");
            Objects.requireNonNull(store, "store");
            Objects.requireNonNull(unlink, "unlink");
            Objects.requireNonNull(view, "view");
        }
    }
}
