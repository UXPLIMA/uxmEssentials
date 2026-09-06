/**
 * The communication context's outbound ports. {@code BroadcastOptOutStore} holds the per-player announcer
 * subscription set (a player who ran {@code /broadcasttoggle} off); {@code RandomSource} is the bounded RNG the
 * random orderings draw through so the use cases stay pure and deterministic under test; {@code SequenceCounter}
 * holds the monotonically-advancing per-channel rotation index a sequential connection policy reads. The adapters
 * (in-memory/PDC for the opt-out set, a seeded {@code java.util.random} source, an in-memory counter) live in the
 * bukkit module. None of these ports carries operator template content. That flows through the use cases as raw
 * strings and is rendered in the adapter.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.communication.application.port;
