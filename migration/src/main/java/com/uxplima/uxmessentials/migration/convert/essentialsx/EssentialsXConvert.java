package com.uxplima.uxmessentials.migration.convert.essentialsx;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.migration.ImportOptions;
import com.uxplima.uxmessentials.migration.ImportPlan;
import com.uxplima.uxmessentials.migration.convert.Convert;
import com.uxplima.uxmessentials.migration.convert.SourceDescriptor;
import com.uxplima.uxmessentials.migration.convert.SourceId;
import com.uxplima.uxmessentials.migration.convert.essentialsx.map.JailMuteMapper;
import com.uxplima.uxmessentials.migration.convert.essentialsx.map.KitMapper;
import com.uxplima.uxmessentials.migration.convert.essentialsx.map.UserdataMapper;
import com.uxplima.uxmessentials.migration.convert.essentialsx.map.WarpMapper;
import com.uxplima.uxmessentials.migration.convert.essentialsx.map.WorldNameResolver;
import org.jspecify.annotations.NullMarked;

/**
 * The EssentialsX {@link Convert}, the v1 source (docs/12-migration §1.2). It reads a
 * {@code plugins/Essentials}-shaped tree ({@code userdata/}, {@code warps/}, {@code kits.yml}) and maps
 * it into the {@code homes}/{@code economy}/{@code messaging}/{@code warps}/{@code kits}/{@code moderation}
 * aggregates through its own ACL mappers. The world resolver is the only runtime seam it needs, the
 * bukkit-side wiring backs it with the live world list. Stateless: one instance is registered for the
 * lifetime of an enabled module.
 */
@NullMarked
public final class EssentialsXConvert implements Convert {

    private static final SourceId ID = SourceId.of("essentialsx");

    private final UserdataMapper userMapper;
    private final WarpMapper warpMapper;
    private final KitMapper kitMapper;
    private final JailMuteMapper jailMuteMapper;

    public EssentialsXConvert(WorldNameResolver worlds) {
        Objects.requireNonNull(worlds, "worlds");
        this.userMapper = new UserdataMapper(worlds);
        this.warpMapper = new WarpMapper(worlds);
        this.kitMapper = new KitMapper();
        this.jailMuteMapper = new JailMuteMapper();
    }

    @Override
    public SourceId id() {
        return ID;
    }

    @Override
    public SourceDescriptor describe() {
        return new SourceDescriptor(
                ID,
                "EssentialsX",
                "plugins/Essentials with userdata/, warps/, kits.yml",
                List.of("homes", "balance", "mail", "warps", "kits", "moderation"));
    }

    @Override
    public boolean detect(Path sourcePath) {
        Objects.requireNonNull(sourcePath, "sourcePath");
        return Files.isDirectory(sourcePath.resolve("userdata"))
                || Files.isDirectory(sourcePath.resolve("warps"))
                || Files.isRegularFile(sourcePath.resolve("kits.yml"));
    }

    @Override
    public ImportPlan plan(ImportOptions options) {
        Objects.requireNonNull(options, "options");
        return new EssentialsXPlan(options.sourcePath(), userMapper, warpMapper, kitMapper, jailMuteMapper);
    }
}
