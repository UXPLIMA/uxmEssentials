package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.IconSpec;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.WarpEffects;
import com.uxplima.uxmessentials.playerwarps.domain.WarpTimingOverrides;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WelcomeMessage;
import org.jspecify.annotations.NullMarked;

/**
 * A uniform editable view over a server {@link Warp} and a {@link PlayerWarp} so the warp editor's click
 * handler can act on either through one code path. Both domain types carry the identical set of {@code withX}
 * copy methods and a save; the two records simply don't share a Java supertype, so this adapter bridges them.
 * Each mutator loads the current warp, applies the change and saves through the owning repository, the editor
 * never branches on "server vs player" itself.
 */
@NullMarked
interface EditableWarp {

    boolean isLocked();

    List<WelcomeMessage> welcomeMessages();

    void setIconMaterial(Optional<String> material);

    void setLocked(boolean locked);

    void setPassword(Optional<String> password);

    void clearSounds();

    void clearParticles();

    void setWarmupOverride(Optional<Double> seconds);

    void setCooldownOverride(Optional<Double> seconds);

    void setDepartureSound(Optional<String> sound);

    void setArrivalSound(Optional<String> sound);

    void setDepartureParticle(Optional<String> particle);

    void setArrivalParticle(Optional<String> particle);

    void setWelcomeMessages(List<WelcomeMessage> messages);

    /** Wrap a loaded server warp; every change is written back through {@code repository}. */
    static EditableWarp ofServer(Warp warp, WarpRepository repository) {
        return new EditableWarp() {
            private Warp current = warp;

            @Override
            public boolean isLocked() {
                return current.isLocked();
            }

            @Override
            public List<WelcomeMessage> welcomeMessages() {
                return current.welcomeMessages();
            }

            @Override
            public void setIconMaterial(Optional<String> material) {
                save(current.withIconMaterial(material));
            }

            @Override
            public void setLocked(boolean locked) {
                save(current.withLocked(locked));
            }

            @Override
            public void setPassword(Optional<String> password) {
                save(current.withPassword(password));
            }

            @Override
            public void clearSounds() {
                save(current.withDepartureSound(Optional.empty()).withArrivalSound(Optional.empty()));
            }

            @Override
            public void clearParticles() {
                save(current.withDepartureParticle(Optional.empty()).withArrivalParticle(Optional.empty()));
            }

            @Override
            public void setWarmupOverride(Optional<Double> seconds) {
                save(current.withWarmupOverride(seconds));
            }

            @Override
            public void setCooldownOverride(Optional<Double> seconds) {
                save(current.withCooldownOverride(seconds));
            }

            @Override
            public void setDepartureSound(Optional<String> sound) {
                save(current.withDepartureSound(sound));
            }

            @Override
            public void setArrivalSound(Optional<String> sound) {
                save(current.withArrivalSound(sound));
            }

            @Override
            public void setDepartureParticle(Optional<String> particle) {
                save(current.withDepartureParticle(particle));
            }

            @Override
            public void setArrivalParticle(Optional<String> particle) {
                save(current.withArrivalParticle(particle));
            }

            @Override
            public void setWelcomeMessages(List<WelcomeMessage> messages) {
                save(current.withWelcomeMessages(messages));
            }

            private void save(Warp updated) {
                current = updated;
                repository.save(updated);
            }
        };
    }

    /**
     * Wrap a loaded player warp; every change is written back through {@code repository}. The surrogate-id rebuild
     * dropped the lock, password, and welcome-message facets from the player-warp aggregate (they return in the P4
     * access gate), so the lock/password/welcome members of this shared interface are inert on the player side. The
     * shared editor does open for a player warp, {@code /pwarp edit} routes through it, but warp-editor.conf gates
     * those three controls to server warps ({@code view = warps:editor-server-warp}), so a player warp never renders
     * them and these no-op members are never reached; they exist only to satisfy the server branch. The sounds,
     * particles, warmup, and cooldown edits map onto the aggregate's {@code WarpEffects} / {@code WarpTimingOverrides}
     * facets; each edit stamps its own timestamp because this adapter, not the domain, owns the wall clock.
     */
    static EditableWarp ofPlayer(PlayerWarp warp, PlayerWarpRepository repository) {
        return new EditableWarp() {
            private PlayerWarp current = warp;

            @Override
            public boolean isLocked() {
                // Player warps no longer model a lock; the shared editor's lock control is server-warp only.
                return false;
            }

            @Override
            public List<WelcomeMessage> welcomeMessages() {
                // Welcome messages were dropped from player warps; the shared editor's welcome control is server only.
                return List.of();
            }

            @Override
            public void setIconMaterial(Optional<String> material) {
                save(current.withIcon(material.map(IconSpec::of), Instant.now()));
            }

            @Override
            public void setLocked(boolean locked) {
                // No lock facet on a player warp. Unreachable: warp-editor.conf gates the lock control to server
                // warps (view = warps:editor-server-warp), so a player warp never renders it to click.
            }

            @Override
            public void setPassword(Optional<String> password) {
                // No password facet on a player warp (it returns as the P4 access gate). Unreachable: warp-editor.conf
                // gates the password control to server warps, so a player warp never renders it to click.
            }

            @Override
            public void clearSounds() {
                WarpEffects e = current.effects();
                save(current.withEffects(
                        new WarpEffects(Optional.empty(), Optional.empty(), e.departureParticle(), e.arrivalParticle()),
                        Instant.now()));
            }

            @Override
            public void clearParticles() {
                WarpEffects e = current.effects();
                save(current.withEffects(
                        new WarpEffects(e.departureSound(), e.arrivalSound(), Optional.empty(), Optional.empty()),
                        Instant.now()));
            }

            @Override
            public void setWarmupOverride(Optional<Double> seconds) {
                save(current.withTiming(
                        new WarpTimingOverrides(seconds, current.timing().cooldownSeconds()), Instant.now()));
            }

            @Override
            public void setCooldownOverride(Optional<Double> seconds) {
                save(current.withTiming(
                        new WarpTimingOverrides(current.timing().warmupSeconds(), seconds), Instant.now()));
            }

            @Override
            public void setDepartureSound(Optional<String> sound) {
                WarpEffects e = current.effects();
                save(current.withEffects(
                        new WarpEffects(sound, e.arrivalSound(), e.departureParticle(), e.arrivalParticle()),
                        Instant.now()));
            }

            @Override
            public void setArrivalSound(Optional<String> sound) {
                WarpEffects e = current.effects();
                save(current.withEffects(
                        new WarpEffects(e.departureSound(), sound, e.departureParticle(), e.arrivalParticle()),
                        Instant.now()));
            }

            @Override
            public void setDepartureParticle(Optional<String> particle) {
                WarpEffects e = current.effects();
                save(current.withEffects(
                        new WarpEffects(e.departureSound(), e.arrivalSound(), particle, e.arrivalParticle()),
                        Instant.now()));
            }

            @Override
            public void setArrivalParticle(Optional<String> particle) {
                WarpEffects e = current.effects();
                save(current.withEffects(
                        new WarpEffects(e.departureSound(), e.arrivalSound(), e.departureParticle(), particle),
                        Instant.now()));
            }

            @Override
            public void setWelcomeMessages(List<WelcomeMessage> messages) {
                // No welcome-message facet on a player warp. Unreachable: warp-editor.conf gates the welcome control
                // to server warps, so a player warp never renders it to click.
            }

            private void save(PlayerWarp updated) {
                current = updated;
                repository.save(updated);
            }
        };
    }
}
