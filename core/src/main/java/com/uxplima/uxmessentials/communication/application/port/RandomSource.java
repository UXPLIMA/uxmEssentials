package com.uxplima.uxmessentials.communication.application.port;

/**
 * The bounded random source the random orderings draw through. Keeping randomness behind a port leaves the domain
 * and the use cases pure and deterministic: a test injects a fixed sequence to assert the no-immediate-repeat
 * rule, while the adapter binds a {@code java.util.random.RandomGenerator}. The single method matches the shape
 * the domain's cursor expects, a value in {@code [0, bound)} for a positive {@code bound}.
 */
@FunctionalInterface
public interface RandomSource {

    /** A pseudo-random {@code int} in {@code [0, bound)}; {@code bound} must be positive. */
    int nextBounded(int bound);
}
