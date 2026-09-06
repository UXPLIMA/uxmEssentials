package com.uxplima.uxmessentials.communication.domain;

import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * The join channel's per-group policy table: a {@link MessagePolicy} for each permission group an operator has
 * authored a join template for, over a default policy that applies to everyone else. It is pure operator content
 * and answers one question, "which {@link MessagePolicy} greets a player of this group?", through
 * {@link #policyFor(String)}, which returns the group's own policy when one is configured and the default
 * otherwise.
 *
 * <p>This keeps the pre-per-group behaviour intact: a file that authors only the flat {@code join} block yields a
 * table with an empty per-group map, so every player, whatever their group, is greeted through the default
 * policy exactly as before. The group is matched case-insensitively, since a permission plugin reports its groups
 * lowercased while an operator may author the key in any case (mirrors {@code ChatFormatPolicy.formatFor}).
 *
 * @param byGroup the per-group overrides, keyed by the joiner's primary group; a group absent here takes the default
 * @param defaultPolicy the policy applied to any group with no override (and to the whole channel pre-migration)
 */
public record JoinGroupPolicies(Map<String, MessagePolicy> byGroup, MessagePolicy defaultPolicy) {

    public JoinGroupPolicies {
        byGroup = Map.copyOf(Objects.requireNonNull(byGroup, "byGroup"));
        Objects.requireNonNull(defaultPolicy, "defaultPolicy");
    }

    /** A table with no per-group overrides: every player takes {@code defaultPolicy}, the single-policy behaviour. */
    public static JoinGroupPolicies ofDefault(MessagePolicy defaultPolicy) {
        return new JoinGroupPolicies(Map.of(), defaultPolicy);
    }

    /**
     * The policy that greets a player whose primary group is {@code group}: the group's override when configured
     * (matched case-insensitively), else the default. A {@code null} or blank group takes the default.
     */
    public MessagePolicy policyFor(@Nullable String group) {
        if (group == null || group.isBlank()) {
            return defaultPolicy;
        }
        MessagePolicy direct = byGroup.get(group);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, MessagePolicy> override : byGroup.entrySet()) {
            if (override.getKey().equalsIgnoreCase(group)) {
                return override.getValue();
            }
        }
        return defaultPolicy;
    }
}
