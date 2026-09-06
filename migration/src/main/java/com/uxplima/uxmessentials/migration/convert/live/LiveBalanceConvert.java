package com.uxplima.uxmessentials.migration.convert.live;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.migration.ImportOptions;
import com.uxplima.uxmessentials.migration.ImportPlan;
import com.uxplima.uxmessentials.migration.ImportRecord;
import com.uxplima.uxmessentials.migration.convert.Convert;
import com.uxplima.uxmessentials.migration.convert.SourceDescriptor;
import com.uxplima.uxmessentials.migration.convert.SourceId;
import org.jspecify.annotations.NullMarked;

/**
 * A live {@link Convert} that migrates a single surface, a player's economy balance, read from a
 * running provider through a {@link BalanceFeed} rather than from an on-disk data tree. One instance is
 * registered per built live source ({@code vault}, {@code playerpoints}); the id, display name, layout
 * note, and the feed that backs the read are all supplied at construction, so the class itself carries
 * no provider knowledge. The mapped records funnel through the same writer as every other source.
 */
@NullMarked
public final class LiveBalanceConvert implements Convert {

    private static final List<String> SURFACES = List.of("balance");

    private final SourceId id;
    private final String displayName;
    private final String layout;
    private final BalanceFeed feed;

    public LiveBalanceConvert(SourceId id, String displayName, String layout, BalanceFeed feed) {
        this.id = Objects.requireNonNull(id, "id");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.layout = Objects.requireNonNull(layout, "layout");
        this.feed = Objects.requireNonNull(feed, "feed");
    }

    @Override
    public SourceId id() {
        return id;
    }

    @Override
    public SourceDescriptor describe() {
        return new SourceDescriptor(id, displayName, layout, SURFACES);
    }

    @Override
    public boolean detect(Path sourcePath) {
        Objects.requireNonNull(sourcePath, "sourcePath");
        // A live source has no on-disk tree; presence is whether the backing provider can be read.
        return feed.available();
    }

    @Override
    public ImportPlan plan(ImportOptions options) {
        Objects.requireNonNull(options, "options");
        return new LiveBalancePlan(feed);
    }

    /** Streams the feed's balance-only users as {@link ImportRecord.UserRecord}s; holds no file handles. */
    private static final class LiveBalancePlan implements ImportPlan {

        private final BalanceFeed feed;

        private LiveBalancePlan(BalanceFeed feed) {
            this.feed = feed;
        }

        @Override
        public Stream<ImportRecord> records() {
            return feed.users().map(ImportRecord.UserRecord::new);
        }

        @Override
        public void close() {
            // A live read opens nothing to release.
        }
    }
}
