package com.uxplima.uxmessentials.playerstate.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The playerstate context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code GOD_ON} ↔ {@code playerstate.god.on}); the constant is the compile-time
 * handle, the catalog holds the text. There are no inline player-facing literals anywhere in the context
 * every message resolves through one of these.
 *
 * <p>Toggle commands carry an {@code .on}/{@code .off} pair plus an {@code .other} variant for the
 * {@code .others} target so a staff toggle confirms whose state changed; the apply-once and verb commands
 * carry a self confirmation and an {@code .other} variant where a target form exists. Per the i18n contract,
 * a disabled module still ships its keys so the catalog stays whole and the locale-parity guard sees the full
 * {@code en} key set.
 */
public enum PlayerstateMessageKey implements MessageKey {

    // /god
    GOD_ON("playerstate.god.on"),
    GOD_OFF("playerstate.god.off"),
    GOD_OTHER_ON("playerstate.god.other-on"),
    GOD_OTHER_OFF("playerstate.god.other-off"),

    // /fly
    FLY_ON("playerstate.fly.on"),
    FLY_OFF("playerstate.fly.off"),
    FLY_OTHER_ON("playerstate.fly.other-on"),
    FLY_OTHER_OFF("playerstate.fly.other-off"),
    FLY_WORLD_DISABLED("playerstate.fly.world-disabled"),

    // /heal
    HEALED("playerstate.heal.self"),
    HEALED_OTHER("playerstate.heal.other"),

    // /feed
    FED("playerstate.feed.self"),
    FED_OTHER("playerstate.feed.other"),

    // /foodlevel
    FOOD_SET("playerstate.foodlevel.set"),
    FOOD_SET_OTHER("playerstate.foodlevel.set-other"),

    // /health
    HEALTH_SET("playerstate.health.set"),
    HEALTH_SET_OTHER("playerstate.health.set-other"),

    // /gamemode
    GAMEMODE_SET("playerstate.gamemode.self"),
    GAMEMODE_SET_OTHER("playerstate.gamemode.other"),
    GAMEMODE_INVALID("playerstate.gamemode.invalid"),

    // /speed /walkspeed /flyspeed
    SPEED_WALK_SET("playerstate.speed.walk-self"),
    SPEED_FLY_SET("playerstate.speed.fly-self"),
    SPEED_WALK_SET_OTHER("playerstate.speed.walk-other"),
    SPEED_FLY_SET_OTHER("playerstate.speed.fly-other"),
    SPEED_INVALID("playerstate.speed.invalid"),

    // /ext
    EXTINGUISHED("playerstate.extinguish.self"),
    EXTINGUISHED_OTHER("playerstate.extinguish.other"),

    // /clearinventory
    INVENTORY_CLEARED("playerstate.clearinventory.self"),
    INVENTORY_CLEARED_OTHER("playerstate.clearinventory.other"),
    INVENTORY_CLEAR_CONFIRM("playerstate.clearinventory.confirm"),

    // /clearinventoryconfirmtoggle (/citoggle)
    CLEAR_CONFIRM_ON("playerstate.clearinventory.confirm-on"),
    CLEAR_CONFIRM_OFF("playerstate.clearinventory.confirm-off"),

    // /invsee
    INVSEE_OPENED("playerstate.invsee.opened"),
    INVSEE_TITLE("playerstate.invsee.title"),

    // /endersee
    ENDERSEE_OPENED("playerstate.endersee.opened"),
    ENDERSEE_TITLE("playerstate.endersee.title"),

    // /suicide
    SUICIDE("playerstate.suicide"),

    // /near
    NEAR_HEADER("playerstate.near.header"),
    NEAR_ENTRY("playerstate.near.entry"),
    NEAR_EMPTY("playerstate.near.empty"),

    // /nightvision
    NIGHTVISION_ON("playerstate.nightvision.on"),
    NIGHTVISION_OFF("playerstate.nightvision.off"),
    NIGHTVISION_ON_OTHER("playerstate.nightvision.on-other"),
    NIGHTVISION_OFF_OTHER("playerstate.nightvision.off-other"),

    // /glow
    GLOW_ON("playerstate.glow.on"),
    GLOW_OFF("playerstate.glow.off"),
    GLOW_ON_OTHER("playerstate.glow.on-other"),
    GLOW_OFF_OTHER("playerstate.glow.off-other"),
    GLOW_COLOR("playerstate.glow.color"),
    GLOW_COLOR_OTHER("playerstate.glow.color-other"),
    GLOW_COLOR_INVALID("playerstate.glow.color-invalid"),

    // /ptime
    PTIME_SET("playerstate.ptime.set"),
    PTIME_RESET("playerstate.ptime.reset"),
    PTIME_INVALID("playerstate.ptime.invalid"),

    // /pweather
    PWEATHER_SET("playerstate.pweather.set"),
    PWEATHER_RESET("playerstate.pweather.reset"),
    PWEATHER_INVALID("playerstate.pweather.invalid"),

    // /exp (/xp)
    EXP_SHOW("playerstate.exp.show"),
    EXP_SHOW_OTHER("playerstate.exp.show-other"),
    EXP_SET("playerstate.exp.set"),
    EXP_SET_OTHER("playerstate.exp.set-other"),

    // /air
    AIR_SET("playerstate.air.set"),
    AIR_SET_OTHER("playerstate.air.set-other"),

    // /burn
    BURN_SET("playerstate.burn.set"),
    BURN_SET_OTHER("playerstate.burn.set-other"),

    // /ice
    FREEZE_SET("playerstate.ice.set"),
    FREEZE_SET_OTHER("playerstate.ice.set-other"),

    // /getpos (/coords /whereami)
    GETPOS_SHOW("playerstate.getpos.show"),
    GETPOS_SHOW_OTHER("playerstate.getpos.show-other"),

    // /ping
    PING_SHOW("playerstate.ping.show"),
    PING_SHOW_OTHER("playerstate.ping.show-other"),

    // /playtime
    PLAYTIME_SHOW("playerstate.playtime.show"),
    PLAYTIME_SHOW_OTHER("playerstate.playtime.show-other"),

    // /playtime reset
    PLAYTIME_RESET("playerstate.playtime.reset"),
    PLAYTIME_RESET_OTHER("playerstate.playtime.reset-other"),

    // /playtime resetall
    PLAYTIME_RESET_ALL("playerstate.playtime.reset-all"),

    // /playtime GUI
    PLAYTIME_GUI_TITLE("playerstate.playtime.gui.title"),
    PLAYTIME_GUI_TITLE_OTHER("playerstate.playtime.gui.title-other"),
    PLAYTIME_GUI_VALUE_LORE("playerstate.playtime.gui.value-lore"),
    PLAYTIME_GUI_BACK("playerstate.playtime.gui.back"),
    PLAYTIME_GUI_TODAY("playerstate.playtime.gui.today"),
    PLAYTIME_GUI_WEEK("playerstate.playtime.gui.week"),
    PLAYTIME_GUI_MONTH("playerstate.playtime.gui.month"),
    PLAYTIME_GUI_TOTAL("playerstate.playtime.gui.total"),
    PLAYTIME_GUI_LIFETIME("playerstate.playtime.gui.lifetime"),
    PLAYTIME_GUI_ROW_VALUE("playerstate.playtime.gui.row-value"),
    PLAYTIME_GUI_LIFETIME_VALUE("playerstate.playtime.gui.lifetime-value"),

    // /rest
    REST_DONE("playerstate.rest.done"),
    REST_DONE_OTHER("playerstate.rest.done-other"),
    REST_DISABLED("playerstate.rest.disabled"),

    // /depth
    DEPTH_ABOVE("playerstate.depth.above"),
    DEPTH_AT("playerstate.depth.at"),
    DEPTH_BELOW("playerstate.depth.below"),

    // /biome
    BIOME_SHOW("playerstate.biome.show"),

    // /seed
    SEED_SHOW("playerstate.seed.show"),

    // /world
    WORLD_SHOW("playerstate.world.show"),

    // per-world command blocker
    WORLD_COMMAND_BLOCKED("playerstate.world.command-blocked"),

    // /compass. The framing line plus the eight cardinal/intercardinal direction words it embeds
    COMPASS_SHOW("playerstate.compass.show"),
    COMPASS_NORTH("playerstate.compass.north"),
    COMPASS_NORTH_EAST("playerstate.compass.north-east"),
    COMPASS_EAST("playerstate.compass.east"),
    COMPASS_SOUTH_EAST("playerstate.compass.south-east"),
    COMPASS_SOUTH("playerstate.compass.south"),
    COMPASS_SOUTH_WEST("playerstate.compass.south-west"),
    COMPASS_WEST("playerstate.compass.west"),
    COMPASS_NORTH_WEST("playerstate.compass.north-west"),

    // /dimension
    DIMENSION_SHOW("playerstate.dimension.show");

    private final String key;

    PlayerstateMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
