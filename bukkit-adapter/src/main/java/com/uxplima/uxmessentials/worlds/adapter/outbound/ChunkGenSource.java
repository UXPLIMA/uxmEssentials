package com.uxplima.uxmessentials.worlds.adapter.outbound;

import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.jspecify.annotations.NullMarked;

/**
 * The injectable seam over Paper's asynchronous chunk loading. The pre-generation engine asks for one
 * chunk at a time through this interface and only consumes the returned future, so the rate-limited
 * generation loop can be driven deterministically in tests by a fake that completes immediately
 * Paper's real {@code World#getChunkAtAsync} is exercised only against a live server.
 */
@NullMarked
public interface ChunkGenSource {

    /**
     * Begin generating (or loading) the chunk at {@code (chunkX, chunkZ)} of {@code world}, completing
     * the returned future when the chunk is present. A request for a world that is not loaded completes
     * immediately so the job still drains and finishes rather than stalling.
     */
    CompletableFuture<?> generate(WorldName world, int chunkX, int chunkZ);
}
