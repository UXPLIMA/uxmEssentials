package com.uxplima.uxmessentials.teleport.domain;

/**
 * The hard ceiling a single asynchronous safe-search may spend before it must give up: the most candidate
 * columns it probes, the most chunk loads it may trigger, and a wall-clock deadline in milliseconds. Each
 * ceiling terminates the search on its own, whichever is reached first stops it, so a search over an
 * unfriendly world (an all-ocean radius, a strict biome filter) can never spin forever or burst hundreds of
 * chunk generations at once. This is the per-world tuning the {@code teleport} config exposes, read once and
 * swapped whole on reload.
 *
 * @param maxAttempts the most candidate columns one search probes before conceding
 * @param maxChunkLoads the most chunk loads one search may trigger before conceding (counted one per attempt,
 *     a conservative upper bound: a resident chunk costs no real load, but counting per attempt keeps the
 *     ceiling honest without the probe reporting chunk residency)
 * @param maxWallClockMillis the wall-clock deadline; a search that has run this long stops at the next attempt
 */
public record SearchBudget(int maxAttempts, int maxChunkLoads, long maxWallClockMillis) {

    public SearchBudget {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1: " + maxAttempts);
        }
        if (maxChunkLoads < 1) {
            throw new IllegalArgumentException("maxChunkLoads must be >= 1: " + maxChunkLoads);
        }
        if (maxWallClockMillis < 1) {
            throw new IllegalArgumentException("maxWallClockMillis must be >= 1: " + maxWallClockMillis);
        }
    }

    /**
     * Whether a search that has already run {@code attempts} probes, triggered {@code chunkLoads} loads, and
     * been running {@code elapsedMillis} is still allowed one more attempt. It is, only while every ceiling
     * still has room: the first one reached ends the search.
     */
    public boolean allowsAnotherAttempt(int attempts, int chunkLoads, long elapsedMillis) {
        return attempts < maxAttempts && chunkLoads < maxChunkLoads && elapsedMillis < maxWallClockMillis;
    }
}
