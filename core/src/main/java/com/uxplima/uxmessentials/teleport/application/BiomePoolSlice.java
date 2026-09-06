package com.uxplima.uxmessentials.teleport.application;

import java.util.List;

import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.domain.BiomeName;
import com.uxplima.uxmessentials.teleport.domain.RtpColumn;

/**
 * The narrow read the {@code /rtp biome} search depends on: the persisted pool's columns for one world that
 * validated in one biome. It is satisfied by {@code RtpPoolStore::loadByBiome} when the durable pool is enabled,
 * and by {@link #EMPTY} when it is switched off, so the biome search goes straight to a live targeted search with
 * no persisted slice, exactly as the plain queue runs purely in memory when the pool is disabled.
 */
@FunctionalInterface
public interface BiomePoolSlice {

    /** Up to {@code limit} persisted columns for {@code world} that validated in {@code biome}, newest first. */
    List<RtpColumn> load(WorldRef world, BiomeName biome, int limit);

    /** A slice that has nothing persisted: wired when the durable pool is disabled. */
    BiomePoolSlice EMPTY = (world, biome, limit) -> List.of();
}
