package com.uxplima.uxmessentials.custommenus;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import com.uxplima.uxmessentials.custommenus.adapter.convert.AbstractMenuConvertService.ConvertReport;
import com.uxplima.uxmessentials.custommenus.adapter.convert.ZMenuConvertService;
import com.uxplima.uxmessentials.custommenus.adapter.convert.ZMenuConverter;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pure coverage of the file-facing zMenu convert service: it turns a zMenu {@code .yml} (or a directory of them) under
 * the menus directory into {@code menus/<name>.conf}, tallies the outcome, and never crashes on a bad input. Uses a
 * temp directory rather than MockBukkit: the service is plain file I/O over the converter.
 */
class ZMenuConvertServiceTest {

    private static final String MENU =
            "name: 'Cookies'\nsize: 36\nitems:\n  c:\n    slot: 13\n    item:\n      material: COOKIE\n";

    private final Logger log = new NoopLogger();

    @Test
    void convertsASingleFileAndWritesTheConf(@TempDir Path menus) throws Exception {
        Files.writeString(menus.resolve("cookies.yml"), MENU);
        ZMenuConvertService service = new ZMenuConvertService(menus, new ZMenuConverter(), log);

        ConvertReport report = service.convert("cookies.yml");

        assertThat(report.found()).isTrue();
        assertThat(report.converted()).isEqualTo(1);
        assertThat(report.skipped()).isZero();
        assertThat(Files.exists(menus.resolve("cookies.conf"))).isTrue();
        assertThat(Files.readString(menus.resolve("cookies.conf"))).contains("title=", "rows=4");
    }

    @Test
    void convertsEveryYmlInADirectory(@TempDir Path menus) throws Exception {
        Path source = Files.createDirectory(menus.resolve("zm"));
        Files.writeString(source.resolve("one.yml"), MENU);
        Files.writeString(source.resolve("two.yaml"), MENU);
        ZMenuConvertService service = new ZMenuConvertService(menus, new ZMenuConverter(), log);

        ConvertReport report = service.convert("zm");

        assertThat(report.converted()).isEqualTo(2);
        assertThat(Files.exists(menus.resolve("one.conf"))).isTrue();
        assertThat(Files.exists(menus.resolve("two.conf"))).isTrue();
    }

    @Test
    void reportsNotFoundForAPathThatMatchesNoYaml(@TempDir Path menus) {
        ZMenuConvertService service = new ZMenuConvertService(menus, new ZMenuConverter(), log);

        ConvertReport report = service.convert("does-not-exist.yml");

        assertThat(report.found()).isFalse();
    }

    @Test
    void countsAnUnparsableFileAsSkippedWithoutCrashing(@TempDir Path menus) throws Exception {
        Files.writeString(menus.resolve("good.yml"), MENU);
        // A tab-indented YAML mapping is a hard parse error, not a recoverable construct.
        Files.writeString(menus.resolve("bad.yml"), "name: 'x'\n\tbroken: [oops\n");
        ZMenuConvertService service = new ZMenuConvertService(menus, new ZMenuConverter(), log);

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
