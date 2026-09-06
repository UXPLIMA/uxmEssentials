package com.uxplima.uxmessentials.shared.adapter.outbound.action;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

import com.google.common.base.Splitter;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRegistryKeys;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.ClickActionGates.Verdict;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.BuiltinTokens;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.HudText;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.ClickActionEconomy;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.action.ClickAction;
import com.uxplima.uxmessentials.shared.domain.action.ClickActionType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The Bukkit/Adventure {@link ClickActionRunner}: it filters an action chain by click trigger and runs the
 * matching actions as an ordered <em>sequence</em>. An effect action (console/player command, message/action-bar/
 * title, sound, connect, give) performs its effect and the sequence moves on, fail-soft, one bad effect logs a
 * one-line warning and is skipped, the chain continues. A gate action (chance, permission, condition, cost)
 * decides whether the rest of the chain runs at all through {@link ClickActionGates}: a denied gate stops the
 * remaining actions, a malformed gate spec is skipped (never aborting). A {@code DELAY} parks the rest of the
 * chain: the tail is re-scheduled after the stated tick count through the {@link Scheduler} port (ticks converted
 * at the fixed 50&nbsp;ms cadence) and then hops back onto the viewer's entity region to resume, a tiny
 * in-adapter sequencer rather than a single straight loop. A viewer who logs off during the wait aborts the rest
 * silently (the entity hop no-ops on a despawned entity). A {@code RANDOM n} marker names the next {@code n}
 * actions as a group: exactly one of them, picked uniformly at random, runs, the rest of the group is skipped,
 * and the chain continues after the whole group (see {@link #runFrom}).
 *
 * <p>Command dispatch (console/player) reuses the same {@link ClickCommandRunner} and {@code [console]}/{@code
 * [player]}/{@code {player}} convention the single click command uses; message/action-bar/title text is resolved
 * through the shared built-in tokens then the PlaceholderAPI + MiniMessage transform, so an action value may
 * embed {@code {player}}, built-in {@code {token}}s, and {@code %papi%} placeholders; a sound value is {@code
 * KEY[:vol[:pitch]]}; a connect value is a target server name. The whole interaction is cooldown-gated at the
 * listener, so a delayed continuation never re-arms the cooldown.
 */
@NullMarked
public final class BukkitClickActionRunner implements ClickActionRunner {

    private static final String CONSOLE_PREFIX = "[console]";
    private static final String PLAYER_PREFIX = "[player]";
    private static final String PLAYER_TOKEN = "{player}";
    private static final long MILLIS_PER_TICK = 50L;

    private final ClickCommandRunner commandRunner;
    private final ServerConnector connector;
    private final Scheduler scheduler;
    private final ClickActionGates gates;
    private final ClickActionGifts gifts;
    private final Logger log;

    /** Picks the chosen offset within a {@code RANDOM} group; given the group size it returns {@code [0, size)}. */
    private final IntUnaryOperator pickOffset;

    public BukkitClickActionRunner(
            ClickCommandRunner commandRunner,
            ServerConnector connector,
            Scheduler scheduler,
            Permissions permissions,
            Optional<ClickActionEconomy> economy,
            Messages messages,
            MessageKey costDeniedKey,
            Function<String, Optional<ItemStack>> serializedResolver,
            Logger log) {
        this(
                commandRunner,
                connector,
                scheduler,
                permissions,
                economy,
                messages,
                costDeniedKey,
                serializedResolver,
                log,
                ThreadLocalRandom.current()::nextInt);
    }

    /**
     * Test seam: takes the {@code RANDOM}-group offset picker explicitly so a deterministic stub can pin which
     * group member runs. Production wiring uses the public constructor, which supplies {@link ThreadLocalRandom}.
     */
    BukkitClickActionRunner(
            ClickCommandRunner commandRunner,
            ServerConnector connector,
            Scheduler scheduler,
            Permissions permissions,
            Optional<ClickActionEconomy> economy,
            Messages messages,
            MessageKey costDeniedKey,
            Function<String, Optional<ItemStack>> serializedResolver,
            Logger log,
            IntUnaryOperator pickOffset) {
        this.commandRunner = Objects.requireNonNull(commandRunner, "commandRunner");
        this.connector = Objects.requireNonNull(connector, "connector");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.pickOffset = Objects.requireNonNull(pickOffset, "pickOffset");
        this.gates = new ClickActionGates(
                Objects.requireNonNull(permissions, "permissions"),
                Objects.requireNonNull(economy, "economy"),
                new CommandFeedback(Objects.requireNonNull(messages, "messages")),
                Objects.requireNonNull(costDeniedKey, "costDeniedKey"),
                log);
        this.gifts = new ClickActionGifts(Objects.requireNonNull(serializedResolver, "serializedResolver"), log);
    }

    @Override
    public void run(Player viewer, List<ClickAction> actions, boolean attack) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(actions, "actions");
        List<ClickAction> matching = new ArrayList<>();
        for (ClickAction action : actions) {
            if (action.trigger().matches(attack)) {
                matching.add(action);
            }
        }
        runFrom(viewer, List.copyOf(matching), 0);
    }

    /**
     * Run the already-trigger-filtered {@code actions} from {@code index} onward, honouring gates and delays.
     *
     * <p>A {@code RANDOM n} marker treats the immediately-following {@code n} actions as a group: exactly one
     * member, picked uniformly at random, runs through the normal single-action handling; the rest of the group
     * is skipped and the chain continues after the whole group ({@code at + n + 1}). The count clamps to the
     * actions that remain (a marker near the end), and a non-positive count skips the marker (a no-op) so the
     * actions after it run as ordinary, un-grouped actions. The chosen member runs exactly as it would inline
     * so a chosen gate that denies still stops the chain, and a chosen {@code DELAY} still parks the tail (which
     * then resumes after the group); both are acceptable and intentional.
     */
    private void runFrom(Player viewer, List<ClickAction> actions, int index) {
        for (int at = index; at < actions.size(); at++) {
            ClickAction action = actions.get(at);
            if (action.type() == ClickActionType.RANDOM) {
                int present = Math.min(Math.max(groupCount(action.value()), 0), actions.size() - (at + 1));
                int afterGroup = (at + 1) + present; // resume past the present members (a count past the end clamps)
                if (present > 0) {
                    int chosen = (at + 1) + boundedPick(present);
                    if (step(viewer, actions, chosen, afterGroup) != Step.CONTINUE) {
                        return; // chosen STOP (a denied gate) or PARKED (a DELAY) ends this invocation, like inline
                    }
                }
                at = afterGroup - 1; // -1 so the loop's increment lands the next iteration after the group
                continue;
            }
            if (step(viewer, actions, at, at + 1) != Step.CONTINUE) {
                return; // STOP (a denied gate) or PARKED (a DELAY resumes from the scheduler) ends this invocation
            }
        }
    }

    /** The outcome of running a single action: continue the chain, stop it, or it parked the tail itself. */
    private enum Step {
        CONTINUE,
        STOP,
        PARKED
    }

    /**
     * Run the single action at {@code at}, resuming the chain at {@code continueIndex} once it completes or its
     * parked delay elapses. A {@code DELAY} parks the tail via {@link #delayThenContinue} and reports
     * {@link Step#PARKED}; a denied gate reports {@link Step#STOP}; every effect reports {@link Step#CONTINUE}.
     * The {@code continueIndex} is {@code at + 1} inline, or the index past the whole {@code RANDOM} group when the
     * action is the group's chosen member, so a chosen {@code DELAY} resumes after the group, not inside it.
     */
    private Step step(Player viewer, List<ClickAction> actions, int at, int continueIndex) {
        ClickAction action = actions.get(at);
        return switch (action.type()) {
            case DELAY -> {
                delayThenContinue(viewer, actions, continueIndex, action.value());
                yield Step.PARKED;
            }
            case CHANCE, PERMISSION, CONDITION, COST ->
                gate(viewer, action) == Verdict.DENY ? Step.STOP : Step.CONTINUE;
            case RANDOM -> Step.CONTINUE; // a RANDOM marker is sequenced in runFrom, never reached as a single step
            default -> {
                effect(viewer, action);
                yield Step.CONTINUE;
            }
        };
    }

    private Verdict gate(Player viewer, ClickAction action) {
        try {
            return switch (action.type()) {
                case CHANCE -> gates.chance(action.value());
                case PERMISSION -> gates.permission(viewer, action.value());
                case CONDITION -> gates.condition(viewer, action.value());
                case COST -> gates.cost(viewer, action.value());
                default -> Verdict.PASS;
            };
        } catch (RuntimeException failure) {
            // A gate that throws (a PAPI placeholder, the economy debit, a permission lookup) must not escape to
            // the interaction listener or the delayed resume's scheduler task. Fail closed: stop the chain rather
            // than run the actions a passed gate would have unlocked, for COST especially, a thrown withdraw
            // leaves the charge outcome unknown, so handing out the reward could give it away for free.
            log.warn(
                    "event=click_action_gate_failed type={} value={}",
                    action.type().name(),
                    action.value());
            return Verdict.DENY;
        }
    }

    /** Park the chain until {@code ticks} elapse, then resume at {@code resumeIndex} on the viewer's entity thread. */
    private void delayThenContinue(Player viewer, List<ClickAction> actions, int resumeIndex, String raw) {
        long ticks = parseTicks(raw);
        if (ticks <= 0L) {
            runFrom(viewer, actions, resumeIndex);
            return;
        }
        var ref = BukkitRefs.toRef(viewer);
        scheduler.asyncAfter(
                Duration.ofMillis(ticks * MILLIS_PER_TICK),
                () -> scheduler.onEntity(ref, () -> resume(ref, actions, resumeIndex)));
    }

    /** Resume the parked chain for the still-online viewer, or abort silently when they have logged off. */
    private void resume(PlayerRef ref, List<ClickAction> actions, int index) {
        Player viewer = org.bukkit.Bukkit.getPlayer(ref.uuid());
        if (viewer == null || !viewer.isOnline()) {
            return; // logged off during the wait, abort the rest of the chain
        }
        runFrom(viewer, actions, index);
    }

    /** Perform one effect action; never throws, so a single bad effect skips rather than aborting the chain. */
    private void effect(Player viewer, ClickAction action) {
        try {
            dispatch(viewer, action);
        } catch (RuntimeException failure) {
            // Fail-soft: a single malformed effect must not abort the rest of the chain.
            log.warn("event=click_action_failed type={} value={}", action.type().name(), action.value());
        }
    }

    private void dispatch(Player viewer, ClickAction action) {
        String value = action.value();
        switch (action.type()) {
            case RUN_CONSOLE -> runConsole(viewer, value);
            case RUN_PLAYER -> commandRunner.runAsPlayer(viewer, withPlayer(viewer, stripPrefix(value)));
            case RUN_PLAYER_AS_OP -> commandRunner.runAsPlayerOp(viewer, withPlayer(viewer, stripPrefix(value)));
            case MESSAGE -> viewer.sendMessage(component(viewer, value));
            case ACTIONBAR -> viewer.sendActionBar(component(viewer, value));
            case TITLE -> showTitle(viewer, value);
            case SOUND -> playSound(viewer, value);
            case CONNECT -> connector.connect(viewer, value.strip());
            case GIVE -> gifts.give(viewer, value);
            default -> {
                // Gate and delay types are handled by the sequencer before dispatch; nothing to do here.
            }
        }
    }

    private long parseTicks(String raw) {
        try {
            return Long.parseLong(raw.strip());
        } catch (NumberFormatException notANumber) {
            log.warn("event=click_action_bad_delay value={}", raw);
            return 0L; // a bad delay is treated as no delay. The chain continues immediately
        }
    }

    /** Parse a {@code RANDOM} group count, or {@code 0} on a non-numeric value (which skips the marker, a no-op). */
    private int groupCount(String raw) {
        try {
            return Integer.parseInt(raw.strip());
        } catch (NumberFormatException notANumber) {
            log.warn("event=click_action_bad_random value={}", raw);
            return 0;
        }
    }

    /** Pick an offset in {@code [0, bound)} through the injected picker, clamped defensively into range. */
    private int boundedPick(int bound) {
        int picked = pickOffset.applyAsInt(bound);
        return Math.floorMod(picked, bound);
    }

    private void runConsole(Player viewer, String value) {
        commandRunner.runAsConsole(withPlayer(viewer, stripPrefix(value)));
    }

    private void showTitle(Player viewer, String value) {
        TitleSpec spec = TitleSpec.parse(value);
        Component title = component(viewer, spec.title());
        Component subtitle = spec.subtitle().isEmpty() ? Component.empty() : component(viewer, spec.subtitle());
        Title.Times times = Title.Times.times(
                Duration.ofMillis(spec.fadeInTicks() * MILLIS_PER_TICK),
                Duration.ofMillis(spec.stayTicks() * MILLIS_PER_TICK),
                Duration.ofMillis(spec.fadeOutTicks() * MILLIS_PER_TICK));
        viewer.showTitle(Title.title(title, subtitle, times));
    }

    private void playSound(Player viewer, String value) {
        SoundSpec spec = SoundSpec.parse(value);
        Sound sound = BukkitRegistryKeys.resolveSound(spec.key());
        if (sound == null) {
            log.warn("event=click_action_unknown_sound value={}", value);
            return;
        }
        viewer.playSound(
                Objects.requireNonNull(viewer.getLocation(), "viewer location"), sound, spec.volume(), spec.pitch());
    }

    /** Resolve a value to a component for the viewer: built-in tokens, then the PAPI + MiniMessage transform. */
    private static Component component(Player viewer, String source) {
        String withTokens = BuiltinTokens.apply(viewer, source);
        return HudText.render(viewer.getUniqueId(), withTokens);
    }

    private static String withPlayer(Player viewer, String command) {
        return command.replace(PLAYER_TOKEN, viewer.getName());
    }

    /** Drop a leading {@code [console]}/{@code [player]} routing prefix; the type already chose the dispatcher. */
    private static String stripPrefix(String value) {
        String stripped = value.strip();
        String lower = stripped.toLowerCase(Locale.ROOT);
        if (lower.startsWith(CONSOLE_PREFIX)) {
            return stripped.substring(CONSOLE_PREFIX.length()).strip();
        }
        if (lower.startsWith(PLAYER_PREFIX)) {
            return stripped.substring(PLAYER_PREFIX.length()).strip();
        }
        return stripped;
    }

    /**
     * A parsed {@code title|subtitle|fadeIn|stay|fadeOut} title value. The title is always present; the subtitle
     * and the three tick counts are optional. The timings are taken as a set. Either all three trailing segments
     * parse as whole numbers, or the whole tail is ignored and the vanilla defaults apply (so a partial or
     * non-numeric tail never half-applies). Defaults match vanilla: 10 fade-in, 70 stay, 20 fade-out ticks. The
     * subtitle keeps any {@code |} the title text never can, because only the first {@code |} splits title from
     * the rest and the timings are peeled off the right.
     */
    record TitleSpec(String title, String subtitle, int fadeInTicks, int stayTicks, int fadeOutTicks) {

        private static final int DEFAULT_FADE_IN = 10;
        private static final int DEFAULT_STAY = 70;
        private static final int DEFAULT_FADE_OUT = 20;

        static TitleSpec parse(String value) {
            int firstBar = value.indexOf('|');
            if (firstBar < 0) {
                return new TitleSpec(value, "", DEFAULT_FADE_IN, DEFAULT_STAY, DEFAULT_FADE_OUT);
            }
            String title = value.substring(0, firstBar);
            String rest = value.substring(firstBar + 1);
            // The subtitle may itself hold '|', so peel the timings from the right: the trailing three segments are
            // fade-in|stay|fade-out and everything before them is the subtitle. Anything but a clean trailing trio
            // of whole numbers falls back to the vanilla defaults rather than half-applying.
            List<String> parts = new ArrayList<>(Splitter.on('|').splitToList(rest));
            if (parts.size() >= 4) {
                Integer fadeOut = trailingInt(parts);
                Integer stay = trailingInt(parts);
                Integer fadeIn = trailingInt(parts);
                if (fadeIn != null && stay != null && fadeOut != null) {
                    return new TitleSpec(title, String.join("|", parts), fadeIn, stay, fadeOut);
                }
            }
            return new TitleSpec(title, rest, DEFAULT_FADE_IN, DEFAULT_STAY, DEFAULT_FADE_OUT);
        }

        /** Pop the last segment as a non-negative whole number, or leave the list and return {@code null}. */
        private static @Nullable Integer trailingInt(List<String> parts) {
            if (parts.isEmpty()) {
                return null;
            }
            try {
                int parsed = Integer.parseInt(parts.get(parts.size() - 1).strip());
                if (parsed < 0) {
                    return null;
                }
                parts.remove(parts.size() - 1);
                return parsed;
            } catch (NumberFormatException notANumber) {
                return null;
            }
        }
    }

    /**
     * A parsed {@code KEY[:volume[:pitch]]} sound value. The key may itself be namespaced ({@code
     * minecraft:entity.player.levelup}), so volume and pitch are read as the trailing one or two numeric
     * {@code :}-separated segments and everything before them is the key. Splitting on the first colon would
     * eat a namespace. A missing volume/pitch defaults to {@code 1.0}.
     */
    record SoundSpec(String key, float volume, float pitch) {

        static SoundSpec parse(String value) {
            // The trailing numeric segments are, left to right, volume then pitch. Peel them off the right
            // (so a single trailing number is the volume), and whatever remains, rejoined on ':', is the key,
            // so a namespaced key keeps its own colon instead of being eaten by a split-on-first-colon.
            List<String> parts = new ArrayList<>(Splitter.on(':').trimResults().splitToList(value));
            Float last = trailingFloat(parts);
            if (last == null) {
                return new SoundSpec(String.join(":", parts), 1.0f, 1.0f);
            }
            Float secondLast = trailingFloat(parts);
            if (secondLast == null) {
                // One trailing number: it is the volume; pitch defaults.
                return new SoundSpec(String.join(":", parts), last, 1.0f);
            }
            // Two trailing numbers: volume then pitch in left-to-right order.
            return new SoundSpec(String.join(":", parts), secondLast, last);
        }

        /**
         * If the last segment is a float and is not the only segment left (the key must survive), remove and
         * return it; otherwise leave the list untouched and return {@code null}.
         */
        private static @Nullable Float trailingFloat(List<String> parts) {
            if (parts.size() <= 1) {
                return null;
            }
            String last = parts.get(parts.size() - 1);
            try {
                float parsed = Float.parseFloat(last);
                parts.remove(parts.size() - 1);
                return parsed;
            } catch (NumberFormatException notANumber) {
                return null;
            }
        }
    }
}
