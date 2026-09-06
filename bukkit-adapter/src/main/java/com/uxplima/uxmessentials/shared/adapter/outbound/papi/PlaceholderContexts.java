package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Optional;

import com.uxplima.uxmessentials.messaging.application.port.VanishVisibility;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import org.jspecify.annotations.Nullable;

/**
 * The bundle of read seams the {@link PlaceholderResolver} consults, one per feature context that
 * contributes placeholders. Every seam is optional: a disabled (or not yet landed) context contributes no
 * seam, and the resolver degrades that context's placeholders to their empty/"-" default rather than
 * failing.
 *
 * <p>The bundle is assembled once in bootstrap through {@link Builder} as each context's adapters are
 * wired, then handed to the resolver. It holds only adapter-side read seams, no PlaceholderAPI type and
 * no live {@code Player}, so it is a plain value that the resolver test can populate with fakes.
 */
public final class PlaceholderContexts {

    private final @Nullable HomesPlaceholders homes;
    private final @Nullable EconomyPlaceholders economy;
    private final @Nullable PresencePlaceholders presence;
    private final @Nullable PlayerstatePlaceholders playerstate;
    private final @Nullable KitsPlaceholders kits;
    private final @Nullable VaultsPlaceholders vaults;
    private final @Nullable WarpsPlaceholders warps;
    private final @Nullable PlayerwarpsPlaceholders playerwarps;
    private final @Nullable ModerationPlaceholders moderation;
    private final @Nullable TeleportPlaceholders teleport;
    private final @Nullable VotePlaceholders vote;
    private final @Nullable MessagingPlaceholders messaging;
    private final @Nullable StaffPlaceholders staff;
    private final @Nullable DiscordlinkPlaceholders discordlink;
    private final @Nullable HologramsPlaceholders holograms;
    private final @Nullable CommunicationPlaceholders communication;
    private final @Nullable ScoreboardPlaceholders scoreboard;
    private final @Nullable ServerMetricsPlaceholders serverMetrics;
    private final @Nullable WorldsPlaceholders worldsPlaceholders;
    private final @Nullable MenuPlaceholders menu;
    private final @Nullable PosesPlaceholders poses;
    private final @Nullable SurvivalPlaceholders survival;
    private final @Nullable TablistPlaceholders tablist;
    private final @Nullable NametagsPlaceholders nametags;
    private final @Nullable VillagersPlaceholders villagers;
    private final @Nullable ServerTweaksPlaceholders serverTweaks;
    private final @Nullable CommandControlPlaceholders commandControl;
    private final @Nullable InvrollbackPlaceholders invrollback;
    private final @Nullable ItemworldPlaceholders itemworld;
    private final @Nullable NpcPlaceholders npc;
    private final @Nullable RegionsPlaceholders regions;
    private final @Nullable SkinPlaceholders skin;
    private final @Nullable SecurityPlaceholders security;
    private final @Nullable ModulesPlaceholders modules;
    private final @Nullable RanksPlaceholders ranks;
    private final @Nullable PlayerLookup players;
    private final @Nullable PlayerFactsPlaceholders playerFacts;
    private final @Nullable Cooldowns cooldowns;
    private final @Nullable VanishVisibility visibility;
    private final @Nullable TradePlaceholders trade;

    private PlaceholderContexts(Builder builder) {
        this.homes = builder.homes;
        this.economy = builder.economy;
        this.presence = builder.presence;
        this.playerstate = builder.playerstate;
        this.kits = builder.kits;
        this.vaults = builder.vaults;
        this.warps = builder.warps;
        this.playerwarps = builder.playerwarps;
        this.moderation = builder.moderation;
        this.teleport = builder.teleport;
        this.vote = builder.vote;
        this.messaging = builder.messaging;
        this.staff = builder.staff;
        this.discordlink = builder.discordlink;
        this.holograms = builder.holograms;
        this.communication = builder.communication;
        this.scoreboard = builder.scoreboard;
        this.serverMetrics = builder.serverMetrics;
        this.worldsPlaceholders = builder.worldsPlaceholders;
        this.menu = builder.menu;
        this.poses = builder.poses;
        this.survival = builder.survival;
        this.tablist = builder.tablist;
        this.nametags = builder.nametags;
        this.villagers = builder.villagers;
        this.serverTweaks = builder.serverTweaks;
        this.commandControl = builder.commandControl;
        this.invrollback = builder.invrollback;
        this.itemworld = builder.itemworld;
        this.npc = builder.npc;
        this.regions = builder.regions;
        this.skin = builder.skin;
        this.security = builder.security;
        this.modules = builder.modules;
        this.ranks = builder.ranks;
        this.players = builder.players;
        this.playerFacts = builder.playerFacts;
        this.cooldowns = builder.cooldowns;
        this.visibility = builder.visibility;
        this.trade = builder.trade;
    }

    /** A fresh, empty builder: every seam starts absent until a wired context registers it. */
    public static Builder builder() {
        return new Builder();
    }

    /** How a name becomes an account, for the {@code p_<name>_<key>} form that reads another player. */
    public Optional<PlayerLookup> players() {
        return Optional.ofNullable(players);
    }

    /** What the server itself holds about an account, for the {@code player_*} and item-in-hand keys. */
    public Optional<PlayerFactsPlaceholders> playerFacts() {
        return Optional.ofNullable(playerFacts);
    }

    /** The shared cooldown gate, for the generic {@code cooldown_<label>} family. */
    public Optional<Cooldowns> cooldowns() {
        return Optional.ofNullable(cooldowns);
    }

    /** Whether vanish hides one player from another, for the relational {@code cansee} key. */
    public Optional<VanishVisibility> visibility() {
        return Optional.ofNullable(visibility);
    }

    /** The live trade registry, for the {@code trading} keys. */
    public Optional<TradePlaceholders> trade() {
        return Optional.ofNullable(trade);
    }

    public Optional<HomesPlaceholders> homes() {
        return Optional.ofNullable(homes);
    }

    public Optional<EconomyPlaceholders> economy() {
        return Optional.ofNullable(economy);
    }

    public Optional<PresencePlaceholders> presence() {
        return Optional.ofNullable(presence);
    }

    public Optional<PlayerstatePlaceholders> playerstate() {
        return Optional.ofNullable(playerstate);
    }

    public Optional<KitsPlaceholders> kits() {
        return Optional.ofNullable(kits);
    }

    public Optional<VaultsPlaceholders> vaults() {
        return Optional.ofNullable(vaults);
    }

    public Optional<WarpsPlaceholders> warps() {
        return Optional.ofNullable(warps);
    }

    public Optional<PlayerwarpsPlaceholders> playerwarps() {
        return Optional.ofNullable(playerwarps);
    }

    public Optional<ModerationPlaceholders> moderation() {
        return Optional.ofNullable(moderation);
    }

    public Optional<TeleportPlaceholders> teleport() {
        return Optional.ofNullable(teleport);
    }

    public Optional<VotePlaceholders> vote() {
        return Optional.ofNullable(vote);
    }

    public Optional<MessagingPlaceholders> messaging() {
        return Optional.ofNullable(messaging);
    }

    public Optional<StaffPlaceholders> staff() {
        return Optional.ofNullable(staff);
    }

    public Optional<DiscordlinkPlaceholders> discordlink() {
        return Optional.ofNullable(discordlink);
    }

    public Optional<HologramsPlaceholders> holograms() {
        return Optional.ofNullable(holograms);
    }

    public Optional<CommunicationPlaceholders> communication() {
        return Optional.ofNullable(communication);
    }

    public Optional<ScoreboardPlaceholders> scoreboard() {
        return Optional.ofNullable(scoreboard);
    }

    public Optional<ServerMetricsPlaceholders> serverMetrics() {
        return Optional.ofNullable(serverMetrics);
    }

    public Optional<WorldsPlaceholders> worlds() {
        return Optional.ofNullable(worldsPlaceholders);
    }

    public Optional<MenuPlaceholders> menu() {
        return Optional.ofNullable(menu);
    }

    public Optional<PosesPlaceholders> poses() {
        return Optional.ofNullable(poses);
    }

    public Optional<SurvivalPlaceholders> survival() {
        return Optional.ofNullable(survival);
    }

    public Optional<TablistPlaceholders> tablist() {
        return Optional.ofNullable(tablist);
    }

    public Optional<NametagsPlaceholders> nametags() {
        return Optional.ofNullable(nametags);
    }

    public Optional<VillagersPlaceholders> villagers() {
        return Optional.ofNullable(villagers);
    }

    public Optional<ServerTweaksPlaceholders> serverTweaks() {
        return Optional.ofNullable(serverTweaks);
    }

    public Optional<CommandControlPlaceholders> commandControl() {
        return Optional.ofNullable(commandControl);
    }

    public Optional<InvrollbackPlaceholders> invrollback() {
        return Optional.ofNullable(invrollback);
    }

    public Optional<SkinPlaceholders> skin() {
        return Optional.ofNullable(skin);
    }

    public Optional<ItemworldPlaceholders> itemworld() {
        return Optional.ofNullable(itemworld);
    }

    public Optional<NpcPlaceholders> npc() {
        return Optional.ofNullable(npc);
    }

    public Optional<RegionsPlaceholders> regions() {
        return Optional.ofNullable(regions);
    }

    public Optional<SecurityPlaceholders> security() {
        return Optional.ofNullable(security);
    }

    public Optional<ModulesPlaceholders> modules() {
        return Optional.ofNullable(modules);
    }

    public Optional<RanksPlaceholders> ranks() {
        return Optional.ofNullable(ranks);
    }

    /** True when no context registered a seam: registering the expansion would surface nothing. */
    public boolean isEmpty() {
        return homes == null
                && economy == null
                && presence == null
                && playerstate == null
                && kits == null
                && vaults == null
                && warps == null
                && playerwarps == null
                && moderation == null
                && teleport == null
                && vote == null
                && messaging == null
                && staff == null
                && discordlink == null
                && holograms == null
                && communication == null
                && scoreboard == null
                && serverMetrics == null
                && worldsPlaceholders == null
                && poses == null
                && ranks == null;
    }

    /** Mutable collector for the seams, filled as each context's adapters are wired in bootstrap. */
    public static final class Builder {

        private @Nullable HomesPlaceholders homes;
        private @Nullable EconomyPlaceholders economy;
        private @Nullable PresencePlaceholders presence;
        private @Nullable PlayerstatePlaceholders playerstate;
        private @Nullable KitsPlaceholders kits;
        private @Nullable VaultsPlaceholders vaults;
        private @Nullable WarpsPlaceholders warps;
        private @Nullable PlayerwarpsPlaceholders playerwarps;
        private @Nullable ModerationPlaceholders moderation;
        private @Nullable TeleportPlaceholders teleport;
        private @Nullable VotePlaceholders vote;
        private @Nullable MessagingPlaceholders messaging;
        private @Nullable StaffPlaceholders staff;
        private @Nullable DiscordlinkPlaceholders discordlink;
        private @Nullable HologramsPlaceholders holograms;
        private @Nullable CommunicationPlaceholders communication;
        private @Nullable ScoreboardPlaceholders scoreboard;
        private @Nullable ServerMetricsPlaceholders serverMetrics;
        private @Nullable WorldsPlaceholders worldsPlaceholders;
        private @Nullable MenuPlaceholders menu;
        private @Nullable PosesPlaceholders poses;
        private @Nullable SurvivalPlaceholders survival;
        private @Nullable TablistPlaceholders tablist;
        private @Nullable NametagsPlaceholders nametags;
        private @Nullable VillagersPlaceholders villagers;
        private @Nullable ServerTweaksPlaceholders serverTweaks;
        private @Nullable CommandControlPlaceholders commandControl;
        private @Nullable InvrollbackPlaceholders invrollback;
        private @Nullable ItemworldPlaceholders itemworld;
        private @Nullable NpcPlaceholders npc;
        private @Nullable RegionsPlaceholders regions;
        private @Nullable SkinPlaceholders skin;
        private @Nullable SecurityPlaceholders security;
        private @Nullable ModulesPlaceholders modules;
        private @Nullable RanksPlaceholders ranks;
        private @Nullable PlayerLookup players;
        private @Nullable PlayerFactsPlaceholders playerFacts;
        private @Nullable Cooldowns cooldowns;
        private @Nullable VanishVisibility visibility;
        private @Nullable TradePlaceholders trade;

        private Builder() {}

        public Builder players(PlayerLookup lookup) {
            this.players = lookup;
            return this;
        }

        public Builder playerFacts(PlayerFactsPlaceholders seam) {
            this.playerFacts = seam;
            return this;
        }

        public Builder cooldowns(Cooldowns gate) {
            this.cooldowns = gate;
            return this;
        }

        public Builder visibility(VanishVisibility gate) {
            this.visibility = gate;
            return this;
        }

        public Builder trade(TradePlaceholders seam) {
            this.trade = seam;
            return this;
        }

        public Builder homes(HomesPlaceholders seam) {
            this.homes = seam;
            return this;
        }

        public Builder economy(EconomyPlaceholders seam) {
            this.economy = seam;
            return this;
        }

        public Builder presence(PresencePlaceholders seam) {
            this.presence = seam;
            return this;
        }

        public Builder playerstate(PlayerstatePlaceholders seam) {
            this.playerstate = seam;
            return this;
        }

        public Builder kits(KitsPlaceholders seam) {
            this.kits = seam;
            return this;
        }

        public Builder vaults(VaultsPlaceholders seam) {
            this.vaults = seam;
            return this;
        }

        public Builder warps(WarpsPlaceholders seam) {
            this.warps = seam;
            return this;
        }

        public Builder playerwarps(PlayerwarpsPlaceholders seam) {
            this.playerwarps = seam;
            return this;
        }

        public Builder moderation(ModerationPlaceholders seam) {
            this.moderation = seam;
            return this;
        }

        public Builder teleport(TeleportPlaceholders seam) {
            this.teleport = seam;
            return this;
        }

        public Builder vote(VotePlaceholders seam) {
            this.vote = seam;
            return this;
        }

        public Builder messaging(MessagingPlaceholders seam) {
            this.messaging = seam;
            return this;
        }

        public Builder staff(StaffPlaceholders seam) {
            this.staff = seam;
            return this;
        }

        public Builder discordlink(DiscordlinkPlaceholders seam) {
            this.discordlink = seam;
            return this;
        }

        public Builder holograms(HologramsPlaceholders seam) {
            this.holograms = seam;
            return this;
        }

        public Builder communication(CommunicationPlaceholders seam) {
            this.communication = seam;
            return this;
        }

        public Builder scoreboard(ScoreboardPlaceholders seam) {
            this.scoreboard = seam;
            return this;
        }

        public Builder serverMetrics(ServerMetricsPlaceholders seam) {
            this.serverMetrics = seam;
            return this;
        }

        public Builder worlds(WorldsPlaceholders seam) {
            this.worldsPlaceholders = java.util.Objects.requireNonNull(seam, "seam");
            return this;
        }

        public Builder menu(MenuPlaceholders seam) {
            this.menu = java.util.Objects.requireNonNull(seam, "seam");
            return this;
        }

        public Builder poses(PosesPlaceholders seam) {
            this.poses = java.util.Objects.requireNonNull(seam, "seam");
            return this;
        }

        public Builder survival(SurvivalPlaceholders seam) {
            this.survival = java.util.Objects.requireNonNull(seam, "seam");
            return this;
        }

        public Builder tablist(TablistPlaceholders seam) {
            this.tablist = java.util.Objects.requireNonNull(seam, "seam");
            return this;
        }

        public Builder nametags(NametagsPlaceholders seam) {
            this.nametags = java.util.Objects.requireNonNull(seam, "seam");
            return this;
        }

        public Builder villagers(VillagersPlaceholders seam) {
            this.villagers = java.util.Objects.requireNonNull(seam, "seam");
            return this;
        }

        public Builder serverTweaks(ServerTweaksPlaceholders seam) {
            this.serverTweaks = java.util.Objects.requireNonNull(seam, "seam");
            return this;
        }

        public Builder commandControl(CommandControlPlaceholders seam) {
            this.commandControl = java.util.Objects.requireNonNull(seam, "seam");
            return this;
        }

        public Builder invrollback(InvrollbackPlaceholders seam) {
            this.invrollback = java.util.Objects.requireNonNull(seam, "seam");
            return this;
        }

        public Builder skin(SkinPlaceholders seam) {
            this.skin = java.util.Objects.requireNonNull(seam, "seam");
            return this;
        }

        public Builder itemworld(ItemworldPlaceholders seam) {
            this.itemworld = java.util.Objects.requireNonNull(seam, "seam");
            return this;
        }

        public Builder npc(NpcPlaceholders seam) {
            this.npc = java.util.Objects.requireNonNull(seam, "seam");
            return this;
        }

        public Builder regions(RegionsPlaceholders seam) {
            this.regions = java.util.Objects.requireNonNull(seam, "seam");
            return this;
        }

        public Builder security(SecurityPlaceholders seam) {
            this.security = java.util.Objects.requireNonNull(seam, "seam");
            return this;
        }

        public Builder modules(ModulesPlaceholders seam) {
            this.modules = java.util.Objects.requireNonNull(seam, "seam");
            return this;
        }

        public Builder ranks(RanksPlaceholders seam) {
            this.ranks = java.util.Objects.requireNonNull(seam, "seam");
            return this;
        }

        public PlaceholderContexts build() {
            return new PlaceholderContexts(this);
        }
    }
}
