package com.uxplima.uxmessentials.security.adapter;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.security.application.SecurityConfig;
import com.uxplima.uxmessentials.security.application.SecurityMessageKey;
import com.uxplima.uxmessentials.security.domain.ClientPolicy;
import com.uxplima.uxmessentials.security.domain.ClientVerdict;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The client-brand guard's join brain: it reads the {@code minecraft:brand} a joining player reports, records it
 * for {@code /clientinfo}, and applies the configured {@link ClientPolicy}. Kicking a denied client and raising a
 * staff notice for a denied or flagged one. The allow/deny decision itself is the pure domain {@link ClientPolicy};
 * this class only records, kicks, and notifies around it.
 *
 * <p>The brand can arrive slightly after the join, so {@link #onJoin} reads it once and, if the client has not sent
 * it yet, retries once after a short delay on the player's region thread. The kick hops onto the player's entity
 * thread; the staff notice fans out on its own. The whole guard is inert when {@code client-id.enabled} is false.
 */
@NullMarked
public final class ClientGuard {

    /** How long to wait before re-reading a brand a client had not reported by join. */
    private static final Duration RETRY_DELAY = Duration.ofSeconds(3);

    private final ClientBrandRegistry registry;
    private final SecurityConfig.ClientId config;
    private final ClientPolicy policy;
    private final SecurityStaffNotifier notifier;
    private final Scheduler scheduler;
    private final Messages messages;

    public ClientGuard(
            ClientBrandRegistry registry,
            SecurityConfig.ClientId config,
            SecurityStaffNotifier notifier,
            Scheduler scheduler,
            Messages messages) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.config = Objects.requireNonNull(config, "config");
        this.policy = config.policy();
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /** Read {@code player}'s reported client brand and judge it, retrying once if the client has not sent it yet. */
    public void onJoin(Player player) {
        Objects.requireNonNull(player, "player");
        if (!config.enabled()) {
            return;
        }
        PlayerRef ref = BukkitRefs.toRef(player);
        @Nullable String brand = player.getClientBrandName();
        if (brand != null && !brand.isBlank()) {
            evaluate(player, ref, brand);
            return;
        }
        scheduler.asyncAfter(RETRY_DELAY, () -> scheduler.onEntity(ref, () -> retry(ref)));
    }

    private void retry(PlayerRef ref) {
        Player live = Bukkit.getPlayer(ref.uuid());
        if (live != null && live.isOnline()) {
            evaluate(live, ref, live.getClientBrandName());
        }
    }

    /**
     * Record and judge {@code brand} for {@code player}: kick a denied client, raise a staff notice for a denied or
     * flagged one, and return the verdict. Extracted so the decision is testable without the plugin-message channel.
     */
    public ClientVerdict evaluate(Player player, PlayerRef ref, @Nullable String brand) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(ref, "ref");
        if (brand != null && !brand.isBlank()) {
            registry.record(ref.uuid(), brand);
        }
        ClientVerdict verdict = policy.judge(brand);
        if (!verdict.allowed()) {
            scheduler.onEntity(ref, () -> kick(ref));
            announce(ref, brand);
        } else if (verdict.flagged()) {
            announce(ref, brand);
        }
        return verdict;
    }

    private void announce(PlayerRef ref, @Nullable String brand) {
        String shown = brand == null ? "" : brand;
        notifier.notifyStaff(
                SecurityMessageKey.SECURITY_CLIENT_FLAGGED,
                Map.of("player", ref.name(), "brand", shown),
                "ClientID: " + ref.name() + " reported client brand '" + shown + "'");
    }

    private void kick(PlayerRef ref) {
        Player live = Bukkit.getPlayer(ref.uuid());
        if (live != null && live.isOnline()) {
            live.kick(render(ref));
        }
    }

    private Component render(PlayerRef ref) {
        return StyledText.render(messages.resolve(ref, SecurityMessageKey.SECURITY_CLIENT_BLOCKED, Map.of()));
    }
}
