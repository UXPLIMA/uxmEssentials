package com.uxplima.uxmessentials.bootstrap;

import io.papermc.paper.plugin.loader.PluginClasspathBuilder;
import io.papermc.paper.plugin.loader.PluginLoader;
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver;

import com.uxplima.uxmessentials.loader.LoaderDependencies;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.jspecify.annotations.NullMarked;

/**
 * Runtime classpath hook required by {@code paper-plugin.yml}'s {@code loader:} directive.
 * Resolves and downloads third-party dependencies from Maven Central dynamically at boot.
 */
@NullMarked
public final class UxmEssentialsLoader implements PluginLoader {

    @Override
    public void classloader(PluginClasspathBuilder classpath) {
        MavenLibraryResolver resolver = new MavenLibraryResolver();

        for (LoaderDependencies.Repository repo :
                LoaderDependencies.repositories(MavenLibraryResolver.MAVEN_CENTRAL_DEFAULT_MIRROR)) {
            resolver.addRepository(new RemoteRepository.Builder(repo.id(), "default", repo.url()).build());
        }

        resolver.addDependency(new Dependency(new DefaultArtifact("com.zaxxer:HikariCP:6.2.1"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("org.xerial:sqlite-jdbc:3.49.1.0"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("org.mariadb.jdbc:mariadb-java-client:3.5.3"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("org.postgresql:postgresql:42.7.4"), null));
        // H2 is the LiteBans importer's JDBC source driver (LiteBans' file default and a connectable URL
        // option). It is needed only when /uxmess import litebans runs, but the resolver pins it at boot.
        resolver.addDependency(new Dependency(new DefaultArtifact("com.h2database:h2:2.4.240"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("org.flywaydb:flyway-core:10.19.0"), null));
        resolver.addDependency(
                new Dependency(new DefaultArtifact("org.flywaydb:flyway-database-postgresql:10.19.0"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("org.flywaydb:flyway-mysql:10.19.0"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact("org.jooq:jooq:3.19.16"), null));
        resolver.addDependency(
                new Dependency(new DefaultArtifact("com.github.ben-manes.caffeine:caffeine:3.1.8"), null));
        resolver.addDependency(new Dependency(new DefaultArtifact(LoaderDependencies.configurateHocon()), null));
        resolver.addDependency(new Dependency(
                new DefaultArtifact("org.spongepowered:configurate-yaml:" + LoaderDependencies.CONFIGURATE_VERSION),
                null));
        // No Redis client is resolved here: the cross-server bus's Redis transport ships in the separate
        // uxmEssentials-redis companion jar (Lettuce, relocated there, see redis-adapter/build.gradle.kts), so
        // the main jar carries no Redis client at all. An operator who wants the Redis transport drops that
        // companion jar in plugins/; otherwise the bus degrades to local-only. gson stays loader-provided: the
        // npc skin and several row codecs compile against it.
        resolver.addDependency(new Dependency(new DefaultArtifact("com.google.code.gson:gson:2.11.0"), null));

        classpath.addLibrary(resolver);
    }
}
