package com.uxplima.uxmessentials.servertweaks.adapter.outbound;

import java.util.Objects;

import com.uxplima.uxmessentials.servertweaks.domain.ConsoleFilterPolicy;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.jspecify.annotations.NullMarked;

/**
 * The Log4j2 filter that puts the pure {@link ConsoleFilterPolicy} on the server's logging pipeline. Attached to the
 * root logger config by {@link ConsoleFilterInstaller}, it is consulted for every log event reaching the console and
 * denies exactly the events whose rendered message the policy marks for suppression; everything else is
 * {@link Result#NEUTRAL neutral}, so an event this filter does not deny is decided by the rest of the pipeline
 * exactly as before: the filter can only remove configured spam, never add or reroute a line.
 *
 * <p>Only the {@link #filter(LogEvent)} hook is overridden: that is the one the root logger config consults for an
 * actual event, and leaving the pre-log message overloads at their neutral defaults keeps the surface (and the
 * matching) to a single, well-understood path.
 */
@NullMarked
public final class ConsoleSpamFilter extends AbstractFilter {

    private final ConsoleFilterPolicy policy;

    public ConsoleSpamFilter(ConsoleFilterPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    @Override
    public Result filter(LogEvent event) {
        String line = event.getMessage().getFormattedMessage();
        return line != null && policy.shouldSuppress(line) ? Result.DENY : Result.NEUTRAL;
    }
}
