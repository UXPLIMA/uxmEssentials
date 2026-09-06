package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

/**
 * A parsed reference to engine behaviour, an action, condition, placeholder, or list source the registries
 * later resolve by {@link #id()}. A reference carries optional string arguments so a spec can write a single
 * compact token instead of a HOCON block.
 *
 * <p>An action ref also carries optional per-action modifiers: a {@code delayTicks} the runtime waits before it
 * fires, a {@code chance} (percent) that gates whether it fires at all, and a {@code deny} fallback ref the
 * runtime runs instead when the chance roll fails. These only mean anything on the action path; a condition,
 * placeholder, or list-source ref parses with the no-modifier defaults (no delay, always fires, no fallback) and
 * ignores them.
 *
 * <p>An {@code input:}/{@code confirm:} action ref additionally carries a {@link Continuation}: a step whose outcome
 * arrives on a later callback, so it splits the gesture's action chain rather than running inline. Every other ref
 * carries an empty {@code continuation}, so it dispatches exactly as before.
 */
public record Ref(
        String id,
        Map<String, String> args,
        int delayTicks,
        double chance,
        Optional<Ref> deny,
        Optional<Continuation> continuation) {

    public Ref {
        Objects.requireNonNull(id, "id");
        args = Map.copyOf(Objects.requireNonNull(args, "args"));
        Objects.requireNonNull(deny, "deny");
        Objects.requireNonNull(continuation, "continuation");
        // Clamp on the way in so the record can never hold a nonsensical modifier however it was built: a negative
        // delay is treated as "now", and a chance is a percent so it lives in [0, 100].
        delayTicks = Math.max(0, delayTicks);
        chance = Math.min(100.0, Math.max(0.0, chance));
    }

    /**
     * The compact two-argument form every existing call-site uses: a ref with the no-modifier defaults (fires
     * immediately, always, with no fallback). It delegates to the canonical constructor so {@link #parse},
     * {@link #of}, and every {@code new Ref(id, args)} keep compiling unchanged. Only the loader's map form
     * reaches for {@link #withModifiers}.
     */
    public Ref(String id, Map<String, String> args) {
        this(id, args, 0, 100.0, Optional.empty(), Optional.empty());
    }

    public static Ref parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        String trimmed = raw.strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("blank ref");
        }
        int colon = trimmed.indexOf(':');
        // A namespaced feature ref (e.g. warp:teleport) keeps the whole token as the id; a generic ref
        // (sound:..., command:...) splits one arg off. We treat a known generic prefix as arg-bearing.
        if (colon < 0) {
            return new Ref(trimmed, Map.of());
        }
        String head = trimmed.substring(0, colon);
        String tail = trimmed.substring(colon + 1);
        if (head.contains(":") || isGeneric(head)) {
            return new Ref(head, Map.of("value", tail));
        }
        return new Ref(trimmed, Map.of()); // namespaced feature ref, args empty
    }

    public static Ref of(String id, Map<String, String> args) {
        return new Ref(id, args);
    }

    /**
     * A copy of this ref carrying per-action modifiers: run after {@code delayTicks} ticks, fire only with
     * {@code chance} percent, and on a failed chance roll run {@code deny} instead ({@code null} for none). The
     * clamp lives in the constructor, so a negative delay becomes {@code 0} and a chance outside {@code [0, 100]}
     * is pulled back into range.
     */
    public Ref withModifiers(int delayTicks, double chance, @Nullable Ref deny) {
        return new Ref(id, args, delayTicks, chance, Optional.ofNullable(deny), continuation);
    }

    /**
     * A copy of this ref carrying {@code continuation}, the {@code input:}/{@code confirm:} step the loader attaches
     * when it parses one of those map entries. Every other builder leaves the continuation empty, so only a ref the
     * loader recognised as a continuation step ever carries one.
     */
    public Ref withContinuation(Continuation continuation) {
        return new Ref(
                id, args, delayTicks, chance, deny, Optional.of(Objects.requireNonNull(continuation, "continuation")));
    }

    /**
     * A copy of this ref re-keyed to a new {@code id} and {@code args} but carrying the SAME per-action modifiers
     * (delay, chance, deny). The runtime uses it to re-split a registry-blind {@code id:value} token the parser left
     * whole, see {@code MenuListener.resolveEffective}, without losing the delay, chance, or deny fallback a
     * map-form action attached. The canonical constructor is only reachable inside this record, so this is how the
     * runtime, in another package, rebuilds a ref. Pure: no Bukkit, just the fields.
     */
    public Ref withIdAndArgs(String id, Map<String, String> args) {
        return new Ref(id, args, delayTicks, chance, deny, continuation);
    }

    /**
     * Resolve this ref against a registry of ids, doing the split {@link #parse} is too registry-blind to do. When the
     * whole id is already registered, or it carries no colon, the ref is returned unchanged, a feature ref
     * ({@code economy:open-bank}) and an already-split generic ({@code sound:x}) both take that path, so their identity
     * is preserved byte-for-byte. Otherwise the token is split on its first colon, and when the head is a registered id
     * this returns a copy re-keyed to that head with the tail carried as {@code value} (the per-action modifiers ride
     * along through {@link #withIdAndArgs}); when neither the whole id nor the head is known, the ref is returned
     * unchanged so it misses the registry exactly as it would have.
     *
     * <p>Pure by design: the caller supplies {@code isRegistered}. An action registry's or a condition registry's
     * {@code has}, so this stays Bukkit-free and is shared by both the runtime's action path and the three condition
     * sites (startup validation, click gating, view gating). A valued condition written {@code has-money:100} therefore
     * resolves the same way {@code give-money:100} does on the action side.
     */
    public Ref resolve(Predicate<String> isRegistered) {
        Objects.requireNonNull(isRegistered, "isRegistered");
        int colon = id.indexOf(':');
        if (colon < 0 || isRegistered.test(id)) {
            return this;
        }
        String head = id.substring(0, colon);
        if (!isRegistered.test(head)) {
            return this;
        }
        // Split on the first colon only, so a value that itself carries colons ("Steve hi:there") stays whole.
        Map<String, String> merged = new HashMap<>(args);
        merged.put("value", id.substring(colon + 1));
        return withIdAndArgs(head, merged);
    }

    /**
     * Whether a chance roll of {@code roll} (expected in {@code [0, 100)}) denies this action. A ref at full
     * chance always fires, so it is never denied; otherwise a roll at or above the chance is a miss. Kept pure so
     * the runtime's deny decision can be exercised with an injected roll rather than real randomness.
     */
    public boolean deniedAt(double roll) {
        return chance < 100.0 && roll >= chance;
    }

    public String value() {
        return args.getOrDefault("value", "");
    }

    /**
     * A best-effort fast path only. {@link #parse} is registry-blind. It cannot see which action ids are actually
     * registered, so this hardcoded allowlist just lets a handful of well-known generic prefixes split their
     * {@code id:value} token at parse time. It is deliberately NOT exhaustive: the ~40 later generic actions are not
     * listed here. The authoritative split lives in {@code MenuListener.resolveEffective}, which has the action
     * registry and re-splits any {@code id:value} token whose head is a registered action. Do not "complete" this
     * list by hand: the registry, not this set, is the source of truth.
     */
    private static boolean isGeneric(String head) {
        return Set.of("sound", "command", "console", "message", "perm", "open", "expr", "refresh-slot")
                .contains(head);
    }
}
