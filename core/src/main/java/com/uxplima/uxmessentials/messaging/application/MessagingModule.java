package com.uxplima.uxmessentials.messaging.application;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ListenerFactory;
import com.uxplima.uxmessentials.shared.application.module.MigrationSet;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.jspecify.annotations.NullMarked;

/**
 * The messaging bounded context as a first-class {@link FeatureModule}: it owns the persistent
 * {@code MailBox} and {@code IgnoreList}, the transient {@code /reply} targets, and the
 * {@code /msg /reply /mail /msgtoggle /ignore /unignore /ignorelist /socialspy /mailclear /helpop} command surface
 * (docs/10-feature-modules.md §15.7, NO public chat). It <em>soft-couples</em> to two other contexts: a
 * muted sender is gated by the moderation context, and a vanished target is hidden from {@code /msg}
 * resolution by the presence context; both degrade to "no mute" / "fully visible" when the other module is
 * disabled, so messaging is registered independently of either.
 *
 * <p>The mail and ignore tables ship in the persistence baseline (V4 under {@code db/migration}, always
 * applied by the persistence layer), so the module declares no extra migration location of its own; a
 * disabled module leaves the baseline tables in place but wires nothing over them. The use cases, the jOOQ
 * {@code MailRepository} / {@code IgnoreStore}, the in-memory reply store, and the self-rescheduling mail
 * expiry sweep are constructed in the adapter wiring once the module has started; the lifecycle bookkeeping
 * here keeps {@code stop()} honest.
 */
@NullMarked
public final class MessagingModule implements FeatureModule {

    private static final ModuleId ID = ModuleId.of("messaging");
    private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(5);

    private final AtomicInteger inFlight = new AtomicInteger();
    private volatile boolean running;

    @Override
    public ModuleId id() {
        return ID;
    }

    @Override
    public String configRoot() {
        return ID.configRoot();
    }

    @Override
    public List<CommandSpec> commands() {
        return MessagingCommandSurface.all();
    }

    @Override
    public List<ListenerFactory> listeners() {
        // The new-mail join notify and the reply-target capture/forget listeners are Bukkit-facing and land
        // with the inbound adapter; a disabled or not-yet-adapted module registers none here.
        return List.of();
    }

    @Override
    public List<MigrationSet> migrations() {
        // The mail and ignores tables are part of the persistence baseline (db/migration V4), always applied
        // by the persistence layer, so the module owns no additional Flyway location.
        return List.of();
    }

    @Override
    public boolean enabled(ConfigStore config) {
        return config.getBoolean(configRoot() + ".enabled", true);
    }

    @Override
    public void start(ModuleContext ctx) {
        this.running = true;
        // The use cases, the jOOQ MailRepository / IgnoreStore, the in-memory reply store, and the mail
        // expiry sweep are constructed over ctx.kernel() and the persistence DSL in the adapter wiring; the
        // lifecycle bookkeeping (running flag, in-flight counter) is armed here so stop() is already honest.
    }

    @Override
    public void stop() {
        this.running = false;
        awaitDrain();
    }

    /** True while the module is started; the async mail expiry sweep observes this and exits on stop. */
    public boolean isRunning() {
        return running;
    }

    private void awaitDrain() {
        long deadline = System.nanoTime() + DRAIN_TIMEOUT.toNanos();
        while (inFlight.get() > 0 && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
    }
}
