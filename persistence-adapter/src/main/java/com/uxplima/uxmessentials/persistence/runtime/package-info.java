/**
 * The persistence runtime: the backend-agnostic infrastructure every bounded context's repositories
 * build on. {@link com.uxplima.uxmessentials.persistence.runtime.Persistence} is the lifecycle handle
 * bootstrap opens on enable. It resolves the backend from config, builds the HikariCP data source
 * (single-writer WAL for the embedded SQLite default, a pooled set for the network backends), runs the
 * Flyway migrations, and exposes the shared jOOQ {@code DSLContext}. {@code JooqRepository} is the base
 * a context's outbound persistence adapter extends; {@code Transactions} and {@code ReadThroughCache} are
 * the shared transaction and read-cache seams. The generated jOOQ classes live in the sibling
 * {@code com.uxplima.uxmessentials.persistence.jooq} package the build's code generator produces.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.persistence.runtime;
