/**
 * The nametags context's domain: the immutable value objects describing an above-head nametag, the
 * {@link com.uxplima.uxmessentials.nametags.domain.NametagFormat} (lines, condition, priority, appearance,
 * visibility), the {@link com.uxplima.uxmessentials.nametags.domain.NametagConfig} that selects one per wearer,
 * and the {@link com.uxplima.uxmessentials.nametags.domain.NametagAppearance} /
 * {@link com.uxplima.uxmessentials.nametags.domain.NametagVisibility} settings. The line content is raw MiniMessage
 * source and the appearance is raw primitives the adapter renders later. The domain only enforces the structural
 * and range invariants. Pure Java: no Bukkit, Paper, Kyori, or SLF4J.
 */
@NullMarked
package com.uxplima.uxmessentials.nametags.domain;

import org.jspecify.annotations.NullMarked;
