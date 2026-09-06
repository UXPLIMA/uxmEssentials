package com.uxplima.uxmessentials.servertweaks.application;

import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.servertweaks.domain.ChatReportPolicy;
import com.uxplima.uxmessentials.servertweaks.domain.ConsoleFilterPolicy;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;

/**
 * The typed, immutable view of {@code modules/servertweaks/config.conf}: one block per independent tweak. It is
 * resolved once from the module's scoped {@link ConfigStore} when the module starts and, per the atomic-reload rule,
 * swapped whole on reload, so an effect applied mid-reload sees one coherent snapshot.
 *
 * <p>The module itself ships enabled; every individual tweak defaults {@code false} except the F3 brand, which
 * defaults {@code true} so the debug screen shows this server's brand out of the box. An operator turns each tweak on
 * or off independently. The HOCON keys are kebab-case ({@code f3-brand}, {@code console-filter}); the record
 * components are the camelCase views the wiring reads.
 *
 * @param f3Brand the custom F3 server-brand tweak
 * @param consoleFilter the console-spam suppression tweak
 * @param noChatReports the unsigned-public-chat tweak
 * @param signedVelocity the SignedVelocity backend-handshake tweak
 */
public record ServerTweaksConfig(
        F3Brand f3Brand, ConsoleFilter consoleFilter, NoChatReports noChatReports, SignedVelocity signedVelocity) {

    public ServerTweaksConfig {
        Objects.requireNonNull(f3Brand, "f3Brand");
        Objects.requireNonNull(consoleFilter, "consoleFilter");
        Objects.requireNonNull(noChatReports, "noChatReports");
        Objects.requireNonNull(signedVelocity, "signedVelocity");
    }

    /** Resolve the server-tweaks config from the module's scoped {@link ConfigStore} ({@code modules.servertweaks}). */
    public static ServerTweaksConfig from(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        return new ServerTweaksConfig(
                F3Brand.from(config),
                ConsoleFilter.from(config),
                NoChatReports.from(config),
                SignedVelocity.from(config));
    }

    /**
     * The {@code f3-brand { … }} block: the server brand shown on the client's F3 debug screen (the
     * {@code minecraft:brand} the server reports). The client shows it as plain text, so the value is a short plain
     * string: MiniMessage or rich formatting is not interpreted.
     *
     * @param enabled whether the custom brand is sent on join ({@code f3-brand.enabled}, default {@code true})
     * @param brand the brand string sent verbatim to the client ({@code f3-brand.brand}, default {@code uxmEssentials})
     */
    public record F3Brand(boolean enabled, String brand) {

        /** The brand shown when the tweak is on but the operator has not set a custom string. */
        public static final String DEFAULT_BRAND = "uxmEssentials";

        public F3Brand {
            Objects.requireNonNull(brand, "brand");
        }

        /** Read the F3-brand switch and string from the module's scoped config, honouring the defaults. */
        public static F3Brand from(ConfigStore config) {
            Objects.requireNonNull(config, "config");
            return new F3Brand(
                    config.getBoolean("f3-brand.enabled", true), config.getString("f3-brand.brand", DEFAULT_BRAND));
        }
    }

    /**
     * The {@code console-filter { … }} block: a substring list of noisy, known-benign console lines to suppress. It
     * ships off with an empty list so nothing is suppressed until an operator both enables it and names patterns.
     *
     * @param enabled whether the filter is attached ({@code console-filter.enabled}, default {@code false})
     * @param patterns the substrings that mark a line for suppression ({@code console-filter.patterns}, default empty)
     */
    public record ConsoleFilter(boolean enabled, List<String> patterns) {

        public ConsoleFilter {
            patterns = List.copyOf(Objects.requireNonNull(patterns, "patterns"));
        }

        /** Read the console-filter switch and patterns from the module's scoped config. */
        public static ConsoleFilter from(ConfigStore config) {
            Objects.requireNonNull(config, "config");
            return new ConsoleFilter(
                    config.getBoolean("console-filter.enabled", false),
                    config.getStringList("console-filter.patterns", List.of()));
        }

        /** The pure suppression decision this block configures, ready for the adapter's Log4j2 filter to consult. */
        public ConsoleFilterPolicy toPolicy() {
            return new ConsoleFilterPolicy(enabled, patterns);
        }
    }

    /**
     * The {@code no-chat-reports { … }} block: when on, the server re-delivers public chat as unsigned messages so
     * they carry no signature and cannot be reported to Mojang. This is the whole of what a server can do without a
     * client mod: a vanilla client still signs its own messages; the server only chooses not to relay the signature.
     *
     * @param enabled whether public chat is re-delivered unsigned ({@code no-chat-reports.enabled}, default
     *     {@code false})
     */
    public record NoChatReports(boolean enabled) {

        /** Read the no-chat-reports switch from the module's scoped config. */
        public static NoChatReports from(ConfigStore config) {
            Objects.requireNonNull(config, "config");
            return new NoChatReports(config.getBoolean("no-chat-reports.enabled", false));
        }

        /** The pure re-delivery decision this block configures, for the adapter's chat listener to consult. */
        public ChatReportPolicy toPolicy() {
            return new ChatReportPolicy(enabled);
        }
    }

    /**
     * The {@code signed-velocity { … }} block: when on, the backend listens on the {@code signedvelocity:main} channel
     * for a Velocity proxy's chat/command rulings and applies them to its own events, keeping signed chat consistent
     * across the proxy hop. It pairs with a proxy-side SignedVelocity component; with no such proxy sending rulings the
     * tweak is inert (no ruling ever arrives, so no event is ever changed).
     *
     * @param enabled whether the backend handshake is wired ({@code signed-velocity.enabled}, default {@code false})
     */
    public record SignedVelocity(boolean enabled) {

        /** Read the signed-velocity switch from the module's scoped config. */
        public static SignedVelocity from(ConfigStore config) {
            Objects.requireNonNull(config, "config");
            return new SignedVelocity(config.getBoolean("signed-velocity.enabled", false));
        }
    }
}
