package com.uxplima.uxmessentials.economy.adapter.inbound.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Resolves the target set for the bulk eco-admin verbs ({@code /eco giveall|giverandom|resetall}). The online
 * roster is read once on the tick thread by the command (the Bukkit roster is not safe to read off-tick) and
 * handed to the off-tick worker as an immutable list, so the DB writes never run on the tick thread.
 *
 * <p>Scope note ({@code docs/11-economy-integration.md} §9.5): the canonical {@code giveall}/{@code resetall}
 * operate over <em>every known wallet</em>, online and offline, materialising offline rows via
 * {@code ensureUserExists} first. Enumerating the full offline owner set needs a repository
 * {@code allOwners()} read-model not yet on the {@code WalletRepository} port; until that lands, the bulk
 * verbs operate over the currently-online set (each target still passes through {@code ensureOwner} in the
 * use case, so a never-credited online player is materialised). The offline-wide sweep is a documented
 * follow-up, not a behavioural change to the per-target verbs.
 */
@NullMarked
final class EcoTargets {

    private EcoTargets() {}

    /**
     * A snapshot of the online players as {@link PlayerRef}s. Called only from the {@code /eco} and {@code /payall}
     * Brigadier handlers, which Paper dispatches on the global region thread, the one thread where
     * {@code Bukkit.getOnlinePlayers()} is consistently readable on Folia, so the enumeration is already on the
     * correct thread and needs no {@code onGlobal} hop. The refs are handed to the off-tick worker as an immutable
     * list so the DB writes never touch the roster off-thread.
     */
    static List<PlayerRef> online() {
        List<PlayerRef> refs = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            refs.add(BukkitRefs.toRef(player));
        }
        return List.copyOf(refs);
    }

    /** One randomly chosen ref from {@code online}, or empty when nobody is connected. */
    static Optional<PlayerRef> randomOnline(List<PlayerRef> online) {
        if (online.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(online.get(ThreadLocalRandom.current().nextInt(online.size())));
    }
}
