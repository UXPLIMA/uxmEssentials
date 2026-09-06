package com.uxplima.uxmessentials.kits.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.ItemDisplay;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.kits.domain.KitItem;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit coverage of the directory-backed {@link ConfigurateKitRepository}: it reads every {@code <id>.conf}
 * in the kits folder, writes one file per kit on save, drops the file on delete, splits a legacy monolith
 * {@code kits.conf} into per-kit files on first load, and skips a malformed file without failing the load.
 * No MockBukkit server is needed: the repository only parses HOCON and carries opaque Base64 item strings.
 */
class ConfigurateKitRepositoryTest {

    private static final Logger NOOP = new Logger() {
        @Override
        public void info(String m, Object... a) {}

        @Override
        public void warn(String m, Object... a) {}

        @Override
        public void error(String m, Throwable t) {}

        @Override
        public void debug(String m, Object... a) {}
    };

    @Test
    void loadsMultiplePerKitFiles(@TempDir Path root) throws Exception {
        Path dir = kitsDir(root);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("starter.conf"), """
                cooldown = 30
                one-time = true
                cost = "0"
                items = []
                """);
        Files.writeString(dir.resolve("vip.conf"), """
                cooldown = 0
                permission = true
                cost = "100"
                items = []
                """);

        KitRepository repository = ConfigurateKitRepository.load(dir, legacy(root), NOOP);

        assertThat(repository.all()).extracting(d -> d.id().value()).containsExactlyInAnyOrder("starter", "vip");
        KitDefinition starter = repository.find(KitId.of("starter")).orElseThrow();
        assertThat(starter.cooldown()).isEqualTo(Duration.ofSeconds(30));
        assertThat(starter.oneTime()).isTrue();
        KitDefinition vip = repository.find(KitId.of("vip")).orElseThrow();
        assertThat(vip.requiresPermission()).isTrue();
        assertThat(vip.hasCost()).isTrue();
    }

    @Test
    void saveWritesASingleFile(@TempDir Path root) {
        Path dir = kitsDir(root);
        KitRepository repository = ConfigurateKitRepository.load(dir, legacy(root), NOOP);

        KitDefinition miner =
                KitDefinition.repeatable(KitId.of("miner"), List.of(KitItem.of("AAAA", 1)), Duration.ofSeconds(45));
        repository.save(miner);

        assertThat(Files.exists(dir.resolve("miner.conf"))).isTrue();
        assertThat(confFiles(dir)).isEqualTo(1L);
        assertThat(repository.find(KitId.of("miner"))).isPresent();

        KitRepository reloaded = ConfigurateKitRepository.load(dir, legacy(root), NOOP);
        assertThat(reloaded.find(KitId.of("miner")).orElseThrow().cooldown()).isEqualTo(Duration.ofSeconds(45));
    }

    @Test
    void deleteRemovesTheFile(@TempDir Path root) throws Exception {
        Path dir = kitsDir(root);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("gone.conf"), "cooldown = 0\nitems = []\n");
        KitRepository repository = ConfigurateKitRepository.load(dir, legacy(root), NOOP);
        assertThat(repository.exists(KitId.of("gone"))).isTrue();

        repository.delete(KitId.of("gone"));

        assertThat(Files.exists(dir.resolve("gone.conf"))).isFalse();
        assertThat(repository.find(KitId.of("gone"))).isEmpty();
        assertThat(repository.all()).isEmpty();
    }

    @Test
    void legacyMonolithStillLoadsAndIsSplit(@TempDir Path root) throws Exception {
        Path dir = kitsDir(root);
        Path legacy = legacy(root);
        Files.createDirectories(legacy.getParent());
        Files.writeString(legacy, """
                kits {
                  old {
                    cooldown = 5
                    items = []
                  }
                }
                """);

        KitRepository repository = ConfigurateKitRepository.load(dir, legacy, NOOP);

        assertThat(repository.find(KitId.of("old"))).isPresent();
        assertThat(Files.exists(dir.resolve("old.conf"))).isTrue();
        assertThat(Files.exists(legacy)).isFalse();
        assertThat(Files.exists(legacy.resolveSibling("kits.conf.migrated"))).isTrue();
    }

    @Test
    void malformedFileIsSkippedNotFatal(@TempDir Path root) throws Exception {
        Path dir = kitsDir(root);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("good.conf"), "cooldown = 10\nitems = []\n");
        Files.writeString(dir.resolve("broken.conf"), "cooldown = = = oops {{{\n");

        KitRepository repository = assertLoads(dir, legacy(root));

        assertThat(repository.find(KitId.of("good"))).isPresent();
    }

    @Test
    void atomicSaveOverwritesExisting(@TempDir Path root) {
        Path dir = kitsDir(root);
        KitRepository repository = ConfigurateKitRepository.load(dir, legacy(root), NOOP);

        KitId id = KitId.of("daily");
        repository.save(KitDefinition.repeatable(id, List.of(KitItem.of("AAAA", 1)), Duration.ofSeconds(10)));
        repository.save(KitDefinition.repeatable(id, List.of(KitItem.of("BBBB", 2)), Duration.ofSeconds(20)));

        assertThat(confFiles(dir)).isEqualTo(1L);
        KitDefinition latest = repository.find(id).orElseThrow();
        assertThat(latest.cooldown()).isEqualTo(Duration.ofSeconds(20));
        assertThat(latest.items()).containsExactly(KitItem.of("BBBB", 2));
    }

    private static KitRepository assertLoads(Path dir, Path legacy) {
        assertThatCode(() -> ConfigurateKitRepository.load(dir, legacy, NOOP)).doesNotThrowAnyException();
        return ConfigurateKitRepository.load(dir, legacy, NOOP);
    }

    private static Path kitsDir(Path root) {
        return root.resolve("modules").resolve("kits").resolve("kits");
    }

    private static Path legacy(Path root) {
        return root.resolve("kits.conf");
    }

    private static long confFiles(Path dir) {
        try (var stream = Files.newDirectoryStream(dir, "*.conf")) {
            long count = 0;
            for (Path ignored : stream) {
                count++;
            }
            return count;
        } catch (Exception failure) {
            throw new RuntimeException(failure);
        }
    }

    @Test
    void readsVariantsCustomPermissionPreviewAndOffhand(@TempDir Path root) throws Exception {
        Path dir = kitsDir(root);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("daily.conf"), """
                cooldown = 3600
                permission = true
                permission-node = "myserver.shared.kit"
                preview = false
                close-on-claim = true
                items = [ { data = "AAAA", offhand = true } ]
                variants {
                  vip { permission = "uxmessentials.kit.tier.vip", cooldown = 1800, items = [ "VVVV" ] }
                  mvp { permission = "uxmessentials.kit.tier.mvp", cooldown = 900, items = [ "MMMM" ] }
                }
                """);

        KitRepository repository = ConfigurateKitRepository.load(dir, legacy(root), NOOP);
        KitDefinition daily = repository.find(KitId.of("daily")).orElseThrow();

        assertThat(daily.permissionNode()).isEqualTo("myserver.shared.kit");
        assertThat(daily.preview()).isFalse();
        assertThat(daily.closeOnClaim()).isTrue();
        assertThat(daily.items()).hasSize(1);
        assertThat(daily.items().get(0).slot()).contains(40); // offhand alias
        assertThat(daily.variants())
                .extracting(v -> v.permission())
                .containsExactly("uxmessentials.kit.tier.vip", "uxmessentials.kit.tier.mvp");
        assertThat(daily.variants().get(0).cooldown()).contains(Duration.ofMinutes(30));
    }

    @Test
    void roundTripsVariantsAndOffhand(@TempDir Path root) {
        Path dir = kitsDir(root);
        KitRepository repository = ConfigurateKitRepository.load(dir, legacy(root), NOOP);

        KitDefinition daily = KitDefinition.repeatable(
                        KitId.of("daily"),
                        List.of(KitItem.of("AAAA", 1, java.util.Optional.of(40))),
                        Duration.ofSeconds(60))
                .withCustomPermission(java.util.Optional.of("myserver.shared.kit"))
                .withPreview(false)
                .withCloseOnClaim(true)
                .withVariants(List.of(com.uxplima.uxmessentials.kits.domain.KitVariant.of(
                        "uxmessentials.kit.tier.vip", List.of(KitItem.of("VVVV", 2)))));
        repository.save(daily);

        KitDefinition loaded = ConfigurateKitRepository.load(dir, legacy(root), NOOP)
                .find(KitId.of("daily"))
                .orElseThrow();

        assertThat(loaded.permissionNode()).isEqualTo("myserver.shared.kit");
        assertThat(loaded.preview()).isFalse();
        assertThat(loaded.closeOnClaim()).isTrue();
        assertThat(loaded.items().get(0).slot()).contains(40);
        assertThat(loaded.variants()).hasSize(1);
        assertThat(loaded.variants().get(0).items()).containsExactly(KitItem.of("VVVV", 2));
    }

    @Test
    void readsRequirementsAndSkipsMalformedEntries(@TempDir Path root) throws Exception {
        Path dir = kitsDir(root);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("ranked.conf"), """
                cooldown = 0
                items = []
                requirements = [
                  "%player_level% >= 10",
                  "%essentials_playtime_seconds% >= 3600",
                  "no operator here"
                ]
                requirements-material = "BARRIER"
                requirements-name = "<red>Locked"
                requirements-lore = [ "Level up to unlock" ]
                """);

        KitRepository repository = ConfigurateKitRepository.load(dir, legacy(root), NOOP);
        KitDefinition ranked = repository.find(KitId.of("ranked")).orElseThrow();

        assertThat(ranked.requirements()).hasSize(2); // the malformed third entry is skipped, not fatal
        assertThat(ranked.requirements().get(0).left()).isEqualTo("%player_level%");
        assertThat(ranked.requirements().get(0).operator())
                .isEqualTo(com.uxplima.uxmessentials.kits.domain.RequirementOperator.GTE);
        assertThat(ranked.requirements().get(0).right()).isEqualTo("10");
        assertThat(ranked.requirementsDisplay().material()).contains("BARRIER");
        assertThat(ranked.requirementsDisplay().name()).contains("<red>Locked");
        assertThat(ranked.requirementsDisplay().lore()).containsExactly("Level up to unlock");
    }

    @Test
    void roundTripsRequirementsAndTheirDisplayState(@TempDir Path root) {
        Path dir = kitsDir(root);
        KitRepository repository = ConfigurateKitRepository.load(dir, legacy(root), NOOP);

        KitDefinition ranked = KitDefinition.repeatable(
                        KitId.of("ranked"), List.of(KitItem.of("AAAA", 1)), Duration.ofSeconds(60))
                .withRequirements(List.of(
                        new com.uxplima.uxmessentials.kits.domain.KitRequirement(
                                "%player_level%", com.uxplima.uxmessentials.kits.domain.RequirementOperator.GTE, "10"),
                        new com.uxplima.uxmessentials.kits.domain.KitRequirement(
                                "%rank%", com.uxplima.uxmessentials.kits.domain.RequirementOperator.EQ, "vip")));
        repository.save(ranked);

        KitDefinition loaded = ConfigurateKitRepository.load(dir, legacy(root), NOOP)
                .find(KitId.of("ranked"))
                .orElseThrow();

        assertThat(loaded.requirements()).hasSize(2);
        assertThat(loaded.requirements().get(0).asText()).isEqualTo("%player_level% >= 10");
        assertThat(loaded.requirements().get(1).asText()).isEqualTo("%rank% == vip");
    }

    @Test
    void parsePlaceholdersDefaultsOffAndRoundTripsWhenOn(@TempDir Path root) {
        Path dir = kitsDir(root);
        KitRepository repository = ConfigurateKitRepository.load(dir, legacy(root), NOOP);

        KitDefinition plain =
                KitDefinition.repeatable(KitId.of("plain"), List.of(KitItem.of("AAAA", 1)), Duration.ZERO);
        repository.save(plain);
        repository.save(plain.withParsePlaceholders(true).withItems(List.of(KitItem.of("BBBB", 1))));

        KitRepository reloaded = ConfigurateKitRepository.load(dir, legacy(root), NOOP);
        assertThat(reloaded.find(KitId.of("plain")).orElseThrow().parsePlaceholders())
                .isTrue();

        // A second kit saved without opting in keeps the default off, so existing kits are unaffected.
        repository.save(KitDefinition.repeatable(KitId.of("legacy"), List.of(KitItem.of("CCCC", 1)), Duration.ZERO));
        assertThat(ConfigurateKitRepository.load(dir, legacy(root), NOOP)
                        .find(KitId.of("legacy"))
                        .orElseThrow()
                        .parsePlaceholders())
                .isFalse();
    }

    @Test
    void unlockOnceDefaultsOffAndRoundTripsWhenOn(@TempDir Path root) {
        Path dir = kitsDir(root);
        KitRepository repository = ConfigurateKitRepository.load(dir, legacy(root), NOOP);

        KitDefinition plain =
                KitDefinition.repeatable(KitId.of("forge"), List.of(KitItem.of("AAAA", 1)), Duration.ZERO);
        repository.save(plain);
        repository.save(plain.withUnlockOnce(true).withItems(List.of(KitItem.of("BBBB", 1))));

        KitRepository reloaded = ConfigurateKitRepository.load(dir, legacy(root), NOOP);
        assertThat(reloaded.find(KitId.of("forge")).orElseThrow().unlockOnce()).isTrue();

        // A kit saved without opting in keeps the default off, so existing kits are unaffected.
        repository.save(KitDefinition.repeatable(KitId.of("legacy"), List.of(KitItem.of("CCCC", 1)), Duration.ZERO));
        assertThat(ConfigurateKitRepository.load(dir, legacy(root), NOOP)
                        .find(KitId.of("legacy"))
                        .orElseThrow()
                        .unlockOnce())
                .isFalse();
    }

    @Test
    void onFullDefaultsToDropAndRoundTripsDeny(@TempDir Path root) {
        Path dir = kitsDir(root);
        KitRepository repository = ConfigurateKitRepository.load(dir, legacy(root), NOOP);

        KitDefinition dropping =
                KitDefinition.repeatable(KitId.of("dropping"), List.of(KitItem.of("AAAA", 1)), Duration.ZERO);
        repository.save(dropping);
        repository.save(dropping.withOnFull(com.uxplima.uxmessentials.kits.domain.KitFullPolicy.DENY)
                .withItems(List.of(KitItem.of("BBBB", 1))));

        KitRepository reloaded = ConfigurateKitRepository.load(dir, legacy(root), NOOP);
        assertThat(reloaded.find(KitId.of("dropping")).orElseThrow().onFull())
                .isEqualTo(com.uxplima.uxmessentials.kits.domain.KitFullPolicy.DENY);

        // A kit saved without naming a policy keeps the default DROP, so existing kits are unaffected.
        repository.save(KitDefinition.repeatable(KitId.of("legacy"), List.of(KitItem.of("CCCC", 1)), Duration.ZERO));
        assertThat(ConfigurateKitRepository.load(dir, legacy(root), NOOP)
                        .find(KitId.of("legacy"))
                        .orElseThrow()
                        .onFull())
                .isEqualTo(com.uxplima.uxmessentials.kits.domain.KitFullPolicy.DROP);
    }

    @Test
    void readsTypedClaimAndDenyActionsInOrder(@TempDir Path root) throws Exception {
        Path dir = kitsDir(root);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("vote.conf"), """
                cooldown = 0
                items = []
                claim-actions = [
                  { type = broadcast, value = "<gold>{player} claimed the kit!", before-items = true }
                  { type = sound, value = "ENTITY_PLAYER_LEVELUP;1;1" }
                  { type = title, value = "0;40;10;<green>Kit claimed;<gray>Enjoy!" }
                  { type = console-command, value = "crate give {player} vote 1", count-as-item = true }
                  { type = wait-ticks, value = "20" }
                  { type = firework, value = "colors:RED,BLUE type:BALL_LARGE power:1" }
                ]
                deny-actions = [ { type = sound, value = "ENTITY_VILLAGER_NO;1;1" } ]
                """);

        KitRepository repository = ConfigurateKitRepository.load(dir, legacy(root), NOOP);
        KitDefinition vote = repository.find(KitId.of("vote")).orElseThrow();

        assertThat(vote.claimActions())
                .extracting(a -> a.type())
                .containsExactly(
                        com.uxplima.uxmessentials.kits.domain.KitActionType.BROADCAST,
                        com.uxplima.uxmessentials.kits.domain.KitActionType.SOUND,
                        com.uxplima.uxmessentials.kits.domain.KitActionType.TITLE,
                        com.uxplima.uxmessentials.kits.domain.KitActionType.CONSOLE_COMMAND,
                        com.uxplima.uxmessentials.kits.domain.KitActionType.WAIT_TICKS,
                        com.uxplima.uxmessentials.kits.domain.KitActionType.FIREWORK);
        assertThat(vote.claimActions().get(0).beforeItems()).isTrue();
        assertThat(vote.claimActions().get(3).countAsItem()).isTrue();
        assertThat(vote.denyActions())
                .singleElement()
                .satisfies(
                        a -> assertThat(a.type()).isEqualTo(com.uxplima.uxmessentials.kits.domain.KitActionType.SOUND));
    }

    @Test
    void legacyCommandsSoundParticlesMapIntoClaimActions(@TempDir Path root) throws Exception {
        Path dir = kitsDir(root);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("old.conf"), """
                cooldown = 0
                items = []
                commands = [ "say hi {player}", "give {player} dirt 1" ]
                sound = "ENTITY_PLAYER_LEVELUP"
                particles = "FLAME"
                """);

        KitRepository repository = ConfigurateKitRepository.load(dir, legacy(root), NOOP);
        KitDefinition old = repository.find(KitId.of("old")).orElseThrow();

        // An unmodified legacy kit's effects now run through the mapped claim actions, in command-then-sound-then-
        // particle order, so the player sees exactly the same behaviour as before the action engine landed.
        assertThat(old.claimActions())
                .extracting(a -> a.type())
                .containsExactly(
                        com.uxplima.uxmessentials.kits.domain.KitActionType.CONSOLE_COMMAND,
                        com.uxplima.uxmessentials.kits.domain.KitActionType.CONSOLE_COMMAND,
                        com.uxplima.uxmessentials.kits.domain.KitActionType.SOUND,
                        com.uxplima.uxmessentials.kits.domain.KitActionType.PARTICLE);
        assertThat(old.claimActions().get(0).value()).isEqualTo("say hi {player}");
        assertThat(old.claimActions().get(2).value()).isEqualTo("ENTITY_PLAYER_LEVELUP");
        assertThat(old.claimActions().get(3).value()).isEqualTo("FLAME");
        // The legacy fields are still carried so the GUI command editor keeps working.
        assertThat(old.commands()).containsExactly("say hi {player}", "give {player} dirt 1");
        assertThat(old.sound()).contains("ENTITY_PLAYER_LEVELUP");
        assertThat(old.particles()).contains("FLAME");
    }

    @Test
    void aLegacyKitRoundTripsUnchanged(@TempDir Path root) throws Exception {
        Path dir = kitsDir(root);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("old.conf"), """
                cooldown = 0
                items = []
                commands = [ "say hi {player}" ]
                sound = "ENTITY_PLAYER_LEVELUP"
                """);
        KitRepository repository = ConfigurateKitRepository.load(dir, legacy(root), NOOP);
        repository.save(repository.find(KitId.of("old")).orElseThrow()); // rewrite through the codec

        KitDefinition reloaded = ConfigurateKitRepository.load(dir, legacy(root), NOOP)
                .find(KitId.of("old"))
                .orElseThrow();

        // A kit only ever in the legacy shape is written back as legacy keys, so re-reading yields the same kit.
        assertThat(reloaded.commands()).containsExactly("say hi {player}");
        assertThat(reloaded.sound()).contains("ENTITY_PLAYER_LEVELUP");
        assertThat(reloaded.claimActions())
                .extracting(a -> a.type())
                .containsExactly(
                        com.uxplima.uxmessentials.kits.domain.KitActionType.CONSOLE_COMMAND,
                        com.uxplima.uxmessentials.kits.domain.KitActionType.SOUND);
    }

    @Test
    void theNewActionBlockWinsOverLegacyKeysWhenBothPresent(@TempDir Path root) throws Exception {
        Path dir = kitsDir(root);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("both.conf"), """
                cooldown = 0
                items = []
                commands = [ "legacy command" ]
                claim-actions = [ { type = message, value = "new action" } ]
                """);

        KitRepository repository = ConfigurateKitRepository.load(dir, legacy(root), NOOP);
        KitDefinition both = repository.find(KitId.of("both")).orElseThrow();

        assertThat(both.claimActions()).singleElement().satisfies(a -> {
            assertThat(a.type()).isEqualTo(com.uxplima.uxmessentials.kits.domain.KitActionType.MESSAGE);
            assertThat(a.value()).isEqualTo("new action");
        });
    }

    @Test
    void roundTripsTypedClaimAndDenyActions(@TempDir Path root) {
        Path dir = kitsDir(root);
        KitRepository repository = ConfigurateKitRepository.load(dir, legacy(root), NOOP);

        KitDefinition vote = KitDefinition.repeatable(
                        KitId.of("vote"), List.of(KitItem.of("AAAA", 1)), Duration.ofSeconds(60))
                .withClaimActions(List.of(
                        new com.uxplima.uxmessentials.kits.domain.KitAction(
                                com.uxplima.uxmessentials.kits.domain.KitActionType.BROADCAST,
                                "<gold>{player} claimed!",
                                true,
                                false),
                        new com.uxplima.uxmessentials.kits.domain.KitAction(
                                com.uxplima.uxmessentials.kits.domain.KitActionType.CONSOLE_COMMAND,
                                "crate give {player} vote 1",
                                false,
                                true)))
                .withDenyActions(List.of(new com.uxplima.uxmessentials.kits.domain.KitAction(
                        com.uxplima.uxmessentials.kits.domain.KitActionType.SOUND,
                        "ENTITY_VILLAGER_NO;1;1",
                        false,
                        false)));
        repository.save(vote);

        KitDefinition loaded = ConfigurateKitRepository.load(dir, legacy(root), NOOP)
                .find(KitId.of("vote"))
                .orElseThrow();

        assertThat(loaded.claimActions()).isEqualTo(vote.claimActions());
        assertThat(loaded.denyActions()).isEqualTo(vote.denyActions());
        assertThat(loaded.claimActions().get(0).beforeItems()).isTrue();
        assertThat(loaded.claimActions().get(1).countAsItem()).isTrue();
    }

    @Test
    void savesAndLoadsStateBasedOverrides(@TempDir Path root) {
        Path dir = kitsDir(root);
        KitRepository repository = ConfigurateKitRepository.load(dir, legacy(root), NOOP);

        KitDefinition custom = KitDefinition.builder()
                .id(KitId.of("custom"))
                .items(List.of(KitItem.of("AAAA", 1)))
                .cooldown(Duration.ofSeconds(10))
                .noPermission(new ItemDisplay(
                        java.util.Optional.of("BARRIER"),
                        java.util.Optional.of("<red>No Perm"),
                        List.of("Lore line 1")))
                .cooldownDisplay(new ItemDisplay(
                        java.util.Optional.of("CLOCK"),
                        java.util.Optional.of("<yellow>Cooldown"),
                        List.of("Cooldown lore")))
                .claimed(new ItemDisplay(
                        java.util.Optional.of("MINECART"),
                        java.util.Optional.of("<red>Claimed"),
                        List.of("Claimed lore")))
                .unaffordable(new ItemDisplay(
                        java.util.Optional.of("GOLD_NUGGET"),
                        java.util.Optional.of("<red>Cannot Afford"),
                        List.of("Price is %cost%")))
                .build();

        repository.save(custom);

        KitRepository reloaded = ConfigurateKitRepository.load(dir, legacy(root), NOOP);
        KitDefinition loaded = reloaded.find(KitId.of("custom")).orElseThrow();

        assertThat(loaded.noPermission().material()).contains("BARRIER");
        assertThat(loaded.noPermission().name()).contains("<red>No Perm");
        assertThat(loaded.noPermission().lore()).containsExactly("Lore line 1");

        assertThat(loaded.cooldownDisplay().material()).contains("CLOCK");
        assertThat(loaded.cooldownDisplay().name()).contains("<yellow>Cooldown");
        assertThat(loaded.cooldownDisplay().lore()).containsExactly("Cooldown lore");

        assertThat(loaded.claimed().material()).contains("MINECART");
        assertThat(loaded.claimed().name()).contains("<red>Claimed");
        assertThat(loaded.claimed().lore()).containsExactly("Claimed lore");

        assertThat(loaded.unaffordable().material()).contains("GOLD_NUGGET");
        assertThat(loaded.unaffordable().name()).contains("<red>Cannot Afford");
        assertThat(loaded.unaffordable().lore()).containsExactly("Price is %cost%");
    }
}
