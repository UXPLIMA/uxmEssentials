package com.uxplima.uxmessentials.communication.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.communication.application.port.RandomSource;
import com.uxplima.uxmessentials.communication.application.port.SequenceCounter;
import com.uxplima.uxmessentials.communication.domain.Announcement;
import com.uxplima.uxmessentials.communication.domain.AnnouncerConfig;
import com.uxplima.uxmessentials.communication.domain.JoinGroupPolicies;
import com.uxplima.uxmessentials.communication.domain.MessagePolicy;
import com.uxplima.uxmessentials.communication.domain.Ordering;
import com.uxplima.uxmessentials.communication.domain.PlaceholderBindings;
import com.uxplima.uxmessentials.shared.display.BroadcastChannel;
import com.uxplima.uxmessentials.shared.display.DisplayCondition;
import org.junit.jupiter.api.Test;

/**
 * The communication resolution use cases through their real implementations against deterministic fakes, the
 * same wiring the connection listeners and the announcer timer drive, minus Bukkit. It pins the headline rules:
 * a join channel's {@code MessagePolicy} decides whether the plugin renders a substituted template, suppresses
 * the line, or defers to vanilla; the sequential ordering walks the templates in author order; the announcer's
 * {@code NextAnnouncement} honours the min-players gate and never repeats the previous random announcement
 * back-to-back.
 */
class CommunicationResolutionTest {

    private static final PlaceholderBindings ALICE = PlaceholderBindings.of(Map.of("player", "Alice", "count", "7"));

    @Test
    void aCustomJoinPolicySubstitutesPlaceholdersIntoTheSelectedTemplate() {
        ResolveJoinMessage resolve = joinWith(
                MessagePolicy.custom(Ordering.SEQUENTIAL, List.of("<yellow>{player}</yellow> joined, {count} online")));

        ResolvedMessage resolved = resolve.resolve(false, null, ALICE);

        assertThat(resolved.hasTemplate()).isTrue();
        assertThat(resolved.suppressVanilla()).isTrue();
        assertThat(resolved.template()).contains("<yellow>Alice</yellow> joined, 7 online");
    }

    @Test
    void aSequentialJoinPolicyWalksTheTemplatesInAuthorOrderAndWraps() {
        ResolveJoinMessage resolve =
                joinWith(MessagePolicy.custom(Ordering.SEQUENTIAL, List.of("first {player}", "second {player}")));

        // The counter advances per call; the policy wraps modulo the template count.
        assertThat(resolve.resolve(false, null, ALICE).template()).contains("first Alice");
        assertThat(resolve.resolve(false, null, ALICE).template()).contains("second Alice");
        assertThat(resolve.resolve(false, null, ALICE).template()).contains("first Alice"); // wrapped back to index 0
    }

    @Test
    void aDisabledJoinPolicySuppressesTheLineAndClearsVanilla() {
        ResolvedMessage resolved = joinWith(MessagePolicy.disabled()).resolve(false, null, ALICE);

        assertThat(resolved.hasTemplate()).isFalse();
        assertThat(resolved.suppressVanilla()).isTrue();
    }

    @Test
    void aDefaultJoinPolicyLeavesVanillaUntouched() {
        ResolvedMessage resolved = joinWith(MessagePolicy.vanilla()).resolve(false, null, ALICE);

        assertThat(resolved.hasTemplate()).isFalse();
        assertThat(resolved.suppressVanilla()).isFalse();
    }

    @Test
    void aPerGroupJoinPolicyGreetsThatGroupWithItsOwnTemplateAndEveryoneElseWithTheDefault() {
        JoinGroupPolicies groups = new JoinGroupPolicies(
                Map.of("vip", MessagePolicy.custom(Ordering.SEQUENTIAL, List.of("vip {player}"))),
                MessagePolicy.custom(Ordering.SEQUENTIAL, List.of("default {player}")));
        ResolveJoinMessage resolve = joinWith(groups, MessagePolicy.vanilla());

        assertThat(resolve.resolve(false, "vip", ALICE).template()).contains("vip Alice"); // group hit
        assertThat(resolve.resolve(false, "member", ALICE).template()).contains("default Alice"); // group miss
        assertThat(resolve.resolve(false, null, ALICE).template()).contains("default Alice"); // no group at all
    }

    @Test
    void theFirstJoinWelcomeReplacesTheOrdinaryLineOnlyOnAFirstJoin() {
        ResolveJoinMessage resolve = joinWith(
                JoinGroupPolicies.ofDefault(MessagePolicy.custom(Ordering.SEQUENTIAL, List.of("join {player}"))),
                MessagePolicy.custom(Ordering.SEQUENTIAL, List.of("welcome {player}")));

        assertThat(resolve.resolve(true, null, ALICE).template()).contains("welcome Alice"); // first join -> welcome
        assertThat(resolve.resolve(false, null, ALICE).template()).contains("join Alice"); // returning -> join line
    }

    @Test
    void aFirstJoinWithNoWelcomeAuthoredFallsThroughToTheOrdinaryJoinLine() {
        ResolveJoinMessage resolve = joinWith(
                JoinGroupPolicies.ofDefault(MessagePolicy.custom(Ordering.SEQUENTIAL, List.of("join {player}"))),
                MessagePolicy.vanilla());

        assertThat(resolve.resolve(true, null, ALICE).template()).contains("join Alice");
    }

    @Test
    void theAnnouncerSkipsTheTickBelowTheMinPlayersGate() {
        AnnouncerConfig config = new AnnouncerConfig(
                Duration.ofMinutes(5), 3, Ordering.SEQUENTIAL, List.of(line("tip one"), line("tip two")));
        NextAnnouncement announcer = new NextAnnouncement(new AtomicReference<>(config)::get, fixedRandom(0));

        assertThat(announcer.pick(2)).isEmpty(); // below the gate of 3, skipped
        assertThat(pickedId(announcer.pick(3))).contains("tip one"); // at the gate, fires
    }

    @Test
    void theAnnouncerNeverRepeatsThePreviousRandomAnnouncementBackToBack() {
        AnnouncerConfig config = new AnnouncerConfig(
                Duration.ofMinutes(5), 0, Ordering.RANDOM, List.of(line("a"), line("b"), line("c")));
        // The RNG draws 0, then 0 again: the second draw collides with the previous index and is nudged forward.
        NextAnnouncement announcer = new NextAnnouncement(new AtomicReference<>(config)::get, sequenceRandom(0, 0));

        assertThat(pickedId(announcer.pick(1))).contains("a"); // first draw -> index 0
        assertThat(pickedId(announcer.pick(1))).contains("b"); // second draw collides on 0 -> nudged to index 1
    }

    @Test
    void theAnnouncerIsSilentWithNoAnnouncements() {
        AnnouncerConfig config = AnnouncerConfig.empty();
        NextAnnouncement announcer = new NextAnnouncement(new AtomicReference<>(config)::get, fixedRandom(0));

        assertThat(announcer.pick(50)).isEmpty();
    }

    /** A single-line, chat-only, unconditional announcement whose id is the line text: convenient for assertions. */
    private static Announcement line(String text) {
        return new Announcement(
                text,
                List.of(text),
                DisplayCondition.always(),
                Optional.empty(),
                Set.of(BroadcastChannel.CHAT),
                Optional.empty(),
                false);
    }

    private static Optional<String> pickedId(Optional<Announcement> picked) {
        return picked.map(Announcement::id);
    }

    private static ResolveJoinMessage joinWith(MessagePolicy policy) {
        return joinWith(JoinGroupPolicies.ofDefault(policy), MessagePolicy.vanilla());
    }

    private static ResolveJoinMessage joinWith(JoinGroupPolicies groups, MessagePolicy firstJoin) {
        ResolveConnectionMessage engine = new ResolveConnectionMessage(new CountingSequence(), fixedRandom(0));
        return new ResolveJoinMessage(
                engine, new AtomicReference<>(groups)::get, new AtomicReference<>(firstJoin)::get);
    }

    private static RandomSource fixedRandom(int value) {
        return bound -> value % bound;
    }

    private static RandomSource sequenceRandom(int... values) {
        Deque<Integer> queue = new ArrayDeque<>();
        for (int value : values) {
            queue.add(value);
        }
        return bound -> (queue.isEmpty() ? 0 : queue.removeFirst()) % bound;
    }

    /** A {@link SequenceCounter} that hands out 0, 1, 2, … per channel, the production atomic counter, in-test. */
    private static final class CountingSequence implements SequenceCounter {
        private final java.util.Map<String, Integer> counters = new java.util.HashMap<>();

        @Override
        public int next(String channel) {
            int value = counters.getOrDefault(channel, 0);
            counters.put(channel, value + 1);
            return value;
        }
    }
}
