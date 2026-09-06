package com.uxplima.uxmessentials.vote.adapter.inbound.listener;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.adapter.VoteServices;
import com.uxplima.uxmessentials.vote.domain.Vote;
import com.uxplima.uxmessentials.vote.domain.VoterNameRules;
import org.jspecify.annotations.NullMarked;

/**
 * Bridges an upstream NuVotifier/Votifier vote into the vote context's {@link com.uxplima.uxmessentials.vote.application.HandleVote}
 * use case. Votifier is a soft dependency declared {@code required: false} in {@code paper-plugin.yml} and is
 * <em>not</em> on the compile classpath, so this listener reaches the {@code com.vexsoftware.votifier.model.VotifierEvent}
 * type purely by reflection: it loads the event class by name, registers a dynamic handler for it via
 * {@link Bukkit#getPluginManager()}'s {@code registerEvent}, and reads the vote's service name and username off
 * the {@code getVote()} object reflectively. When Votifier is absent the plugin-present guard makes the whole
 * listener a no-op. The module still ships and {@code /vote} / {@code /voteparty} still work; only the
 * vote-triggered rewards are dormant until Votifier is installed.
 *
 * <p>It implements {@link Listener} so it can flow through the standard listener registration alongside the
 * join handler, but it carries no {@code @EventHandler} methods of its own. The Votifier handler is the
 * dynamically-registered one. {@link #unregister()} drops that dynamic handler on module stop.
 */
@NullMarked
public final class VotifierListener implements Listener {

    private static final String VOTIFIER_PLUGIN = "Votifier";
    private static final String EVENT_CLASS = "com.vexsoftware.votifier.model.VotifierEvent";

    private final Plugin plugin;
    private final VoteServices services;
    private final PlayerLookup players;
    private final VoterNameRules nameRules;
    private final Logger log;

    public VotifierListener(
            Plugin plugin, VoteServices services, PlayerLookup players, VoterNameRules nameRules, Logger log) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.services = Objects.requireNonNull(services, "services");
        this.players = Objects.requireNonNull(players, "players");
        this.nameRules = Objects.requireNonNull(nameRules, "nameRules");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Register the dynamic Votifier handler when Votifier is installed; a no-op (with one operator-facing log
     * line) otherwise. Called by the wiring once the module has started.
     */
    public void registerIfPresent() {
        if (Bukkit.getPluginManager().getPlugin(VOTIFIER_PLUGIN) == null) {
            log.info("event=vote_listener_dormant reason=votifier_absent");
            return;
        }
        Optional<Class<? extends Event>> eventClass = loadEventClass();
        if (eventClass.isEmpty()) {
            return;
        }
        Bukkit.getPluginManager()
                .registerEvent(eventClass.get(), this, EventPriority.NORMAL, this::onVotifierEvent, plugin);
        log.info("event=vote_listener_active");
    }

    /** Drop the dynamic Votifier handler so a module stop or reload leaves no orphaned registration. */
    public void unregister() {
        HandlerList.unregisterAll(this);
    }

    private void onVotifierEvent(Listener ignored, Event event) {
        // The reflective read off the event object is cheap; the offline-name resolution and the persistence
        // and reward dispatch all happen off the event thread, so the event thread is never blocked on a lookup.
        readRaw(event).ifPresent(raw -> services.scheduler().async(() -> resolveAndHandle(raw)));
    }

    private void resolveAndHandle(RawVote raw) {
        handleRaw(raw.service(), raw.username());
    }

    /**
     * Validate the raw username and, when it passes, resolve the voter and dispatch the vote. A junk Votifier
     * username (empty, {@code "null"}, over-long, or off the name policy) would mint a phantom leaderboard entry
     * and pay a reward into a non-existent player, so it is rejected, logged and dropped, before any vote is
     * built (no tally, no queue, no cooldown). Package-private as the test seam for the validation gate.
     */
    void handleRaw(String service, String username) {
        if (!nameRules.isValid(username)) {
            log.warn("event=vote_rejected reason=invalid_name name={} service={}", username, service);
            return;
        }
        PlayerRef voter = resolveVoter(username);
        services.handleVote().handle(new Vote(voter, service, Instant.now()));
    }

    private Optional<RawVote> readRaw(Event event) {
        try {
            Object voteObject = event.getClass().getMethod("getVote").invoke(event);
            String service = invokeString(voteObject, "getServiceName");
            String username = invokeString(voteObject, "getUsername");
            return Optional.of(new RawVote(service, username));
        } catch (ReflectiveOperationException failure) {
            log.warn("event=vote_read_failed error={}", String.valueOf(failure.getMessage()));
            return Optional.empty();
        }
    }

    private PlayerRef resolveVoter(String username) {
        Optional<PlayerRef> known = players.findByName(username);
        if (known.isPresent()) {
            return known.get();
        }
        // Nobody by that name has ever joined, so the vote is for a player still to arrive: derive the uuid the
        // way the server would and let the reward wait for them. The by-name resolution costs a Mojang round-trip
        // for an uncached name on an online-mode server, which is why this method only ever runs on the async pool
        // and never on the Votifier event thread.
        UUID id = Bukkit.getOfflinePlayer(username).getUniqueId(); // allow-blocking: async pool
        log.debug("event=vote_voter_unknown name={}", username);
        return new PlayerRef(id, username);
    }

    private Optional<Class<? extends Event>> loadEventClass() {
        try {
            Class<?> loaded = Class.forName(EVENT_CLASS);
            return Optional.of(loaded.asSubclass(Event.class));
        } catch (ClassNotFoundException | ClassCastException failure) {
            log.warn("event=vote_event_class_missing name={}", EVENT_CLASS);
            return Optional.empty();
        }
    }

    private static String invokeString(Object target, String getter) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(getter);
        return String.valueOf(method.invoke(target));
    }

    /** The raw service name and username read off the Votifier event before voter resolution. */
    private record RawVote(String service, String username) {}
}
