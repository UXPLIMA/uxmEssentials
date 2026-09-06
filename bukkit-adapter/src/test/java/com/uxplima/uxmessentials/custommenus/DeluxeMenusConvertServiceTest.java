package com.uxplima.uxmessentials.custommenus;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import com.uxplima.uxmessentials.custommenus.adapter.convert.AbstractMenuConvertService.ConvertReport;
import com.uxplima.uxmessentials.custommenus.adapter.convert.DeluxeMenusConvertService;
import com.uxplima.uxmessentials.custommenus.adapter.convert.DeluxeMenusConverter;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pure coverage of the file-facing convert service: it turns a DeluxeMenus {@code .yml} (or a directory of them) under
 * the menus directory into {@code menus/<name>.conf}, tallies the outcome, and never crashes on a bad input. Uses a
 * temp directory rather than MockBukkit: the service is plain file I/O over the converter.
 */
class DeluxeMenusConvertServiceTest {

    private static final String MENU = "menu_title: 'Shop'\nsize: 9\nitems:\n  a:\n    material: STONE\n    slot: 0\n";

    private final Logger log = new NoopLogger();

    @Test
    void convertsASingleFileAndWritesTheConf(@TempDir Path menus) throws Exception {
        Files.writeString(menus.resolve("shop.yml"), MENU);
        DeluxeMenusConvertService service = new DeluxeMenusConvertService(menus, new DeluxeMenusConverter(), log);

        ConvertReport report = service.convert("shop.yml");

        assertThat(report.found()).isTrue();
        assertThat(report.converted()).isEqualTo(1);
        assertThat(report.skipped()).isZero();
        assertThat(Files.exists(menus.resolve("shop.conf"))).isTrue();
        assertThat(Files.readString(menus.resolve("shop.conf"))).contains("title=", "rows=1");
    }

    @Test
    void convertsEveryYmlInADirectory(@TempDir Path menus) throws Exception {
        Path source = Files.createDirectory(menus.resolve("dm"));
        Files.writeString(source.resolve("one.yml"), MENU);
        Files.writeString(source.resolve("two.yaml"), MENU);
        DeluxeMenusConvertService service = new DeluxeMenusConvertService(menus, new DeluxeMenusConverter(), log);

        ConvertReport report = service.convert("dm");

        assertThat(report.converted()).isEqualTo(2);
        assertThat(Files.exists(menus.resolve("one.conf"))).isTrue();
        assertThat(Files.exists(menus.resolve("two.conf"))).isTrue();
    }

    @Test
    void reportsNotFoundForAPathThatMatchesNoYaml(@TempDir Path menus) {
        DeluxeMenusConvertService service = new DeluxeMenusConvertService(menus, new DeluxeMenusConverter(), log);

        ConvertReport report = service.convert("does-not-exist.yml");

        assertThat(report.found()).isFalse();
    }

    @Test
    void countsAnUnparsableFileAsSkippedWithoutCrashing(@TempDir Path menus) throws Exception {
        Files.writeString(menus.resolve("good.yml"), MENU);
        // A tab-indented YAML mapping is a hard parse error, not a recoverable construct.
        Files.writeString(menus.resolve("bad.yml"), "menu_title: 'x'\n\tbroken: [oops\n");
        DeluxeMenusConvertService service = new DeluxeMenusConvertService(menus, new DeluxeMenusConverter(), log);

        ConvertReport report = service.convert(menus.toString());

        assertThat(report.found()).isTrue();
        assertThat(report.converted()).isEqualTo(1);
        assertThat(report.skipped()).isEqualTo(1);
    }

    /** A no-op logger; the service's warnings are covered through the converter's own warning tests. */
    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
