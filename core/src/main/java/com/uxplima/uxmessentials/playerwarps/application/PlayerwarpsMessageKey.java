package com.uxplima.uxmessentials.playerwarps.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The player-warps context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key
 * in {@code messages_<lang>.conf} ({@code PWARP_SET} ↔ {@code pwarp.set}); the constant is the compile-time
 * handle, the catalog holds the text. There are no inline player-facing literals anywhere in the context
 * every message resolves through one of these.
 *
 * <p>Per the i18n contract, a disabled module still ships its keys so the catalog stays whole and the
 * locale-parity guard sees the full {@code en} key set.
 */
public enum PlayerwarpsMessageKey implements MessageKey {

    // set / move / delete feedback
    PWARP_SET("pwarp.set"),
    PWARP_MOVED("pwarp.moved"),
    PWARP_DELETED("pwarp.deleted"),

    // visibility toggles
    PWARP_PUBLIC("pwarp.public"),
    PWARP_PRIVATE("pwarp.private"),

    // teleport
    PWARP_TELEPORTING("pwarp.teleporting"),

    // one-warp summary (/pwarp info)
    PWARP_INFO("pwarp.info"),

    // listing your own warps
    PWARP_LIST_HEADER("pwarp.list.header"),
    PWARP_LIST_ENTRY("pwarp.list.entry"),
    PWARP_LIST_EMPTY("pwarp.list.empty"),

    // listing another player's public warps
    PWARP_LIST_OTHER_HEADER("pwarp.list.other-header"),
    PWARP_LIST_OTHER_ENTRY("pwarp.list.other-entry"),
    PWARP_LIST_OTHER_EMPTY("pwarp.list.other-empty"),

    // failures
    PWARP_NOT_FOUND("pwarp.not-found"),
    PWARP_INVALID_NAME("pwarp.invalid-name"),
    PWARP_NAME_TAKEN("pwarp.name-taken"),
    PWARP_LIMIT_REACHED("pwarp.limit-reached"),
    PWARP_NOT_PUBLIC("pwarp.not-public"),
    PWARP_SUSPENDED("pwarp.suspended"),
    PWARP_BANNED("pwarp.banned"),
    PWARP_NOT_WHITELISTED("pwarp.not-whitelisted"),
    PWARP_RATE_LIMITED("pwarp.rate-limited"),
    PWARP_CANNOT_AFFORD("pwarp.cannot-afford"),
    PWARP_NONE("pwarp.none"),
    PWARP_UNSAFE("pwarp.unsafe"),
    PWARP_LOCKED("pwarp.locked"),
    PWARP_WRONG_PASSWORD("pwarp.wrong-password"),
    PWARP_PASSWORD_SET("pwarp.password-set"),
    PWARP_PASSWORD_CLEARED("pwarp.password-cleared"),
    PWARP_LOCK_TOGGLED("pwarp.lock-toggled"),
    PWARP_WORLD_BLACKLISTED("pwarp.world-blacklisted"),
    PWARP_RATED("pwarp.rated"),
    PWARP_RATING("pwarp.rating"),
    PWARP_RATING_INVALID("pwarp.rating-invalid"),
    PWARP_CANNOT_RATE_OWN("pwarp.cannot-rate-own"),

    // rating rewards. The reward a rater earns for rating a warp, and the reward its owner earns per unique rater
    PWARP_RATE_REWARDED("pwarp.rate-rewarded"),
    PWARP_RATE_REWARD_OWNER("pwarp.rate-reward-owner"),

    // favouriting, any-viewer star toggle kept consistent with favourite_count
    PWARP_FAVOURITED("pwarp.favourited"),
    PWARP_UNFAVOURITED("pwarp.unfavourited"),
    PWARP_ALREADY_FAVOURITED("pwarp.already-favourited"),
    PWARP_NOT_FAVOURITED("pwarp.not-favourited"),

    // management edits: role-gated single-warp verbs
    PWARP_NO_PERMISSION("pwarp.no-permission"),
    PWARP_CURRENCY_LOCKED("pwarp.currency-locked"),
    PWARP_DISPLAY_NAME_SET("pwarp.display-name-set"),
    PWARP_DESCRIPTION_SET("pwarp.description-set"),
    PWARP_ICON_SET("pwarp.icon-set"),
    PWARP_CATEGORY_SET("pwarp.category-set"),
    PWARP_ACCESS_SET("pwarp.access-set"),
    PWARP_PRICE_SET("pwarp.price-set"),
    PWARP_RENAMED("pwarp.renamed"),
    PWARP_TRANSFERRED("pwarp.transferred"),
    PWARP_ARCHIVED("pwarp.archived"),
    PWARP_RESTORED("pwarp.restored"),

    // role-gated people management + the earnings bank
    PWARP_MEMBER_ADDED("pwarp.member-added"),
    PWARP_MEMBER_REMOVED("pwarp.member-removed"),
    PWARP_WHITELIST_ADDED("pwarp.whitelist-added"),
    PWARP_WHITELIST_REMOVED("pwarp.whitelist-removed"),
    PWARP_BAN_SET("pwarp.ban-set"),
    PWARP_BAN_LIFTED("pwarp.ban-lifted"),
    PWARP_WITHDRAWN("pwarp.withdrawn"),
    PWARP_WITHDRAW_FAILED("pwarp.withdraw-failed"),
    PWARP_NOTHING_TO_WITHDRAW("pwarp.nothing-to-withdraw"),
    PWARP_INVALID_ROLE("pwarp.invalid-role"),
    PWARP_CANNOT_TARGET_OWNER("pwarp.cannot-target-owner"),
    PWARP_RESERVED_NAME("pwarp.reserved-name"),

    // sponsorship. Paid, time-limited pinned browse placement bought by the owner
    PWARP_SPONSORED("pwarp.sponsored"),
    PWARP_SPONSORED_LOCKED("pwarp.sponsored-locked"),
    PWARP_SPONSOR_COOLDOWN("pwarp.sponsor-cooldown"),
    PWARP_SPONSOR_LIMIT("pwarp.sponsor-limit"),
    PWARP_SPONSOR_FULL("pwarp.sponsor-full"),

    // rent lifecycle, the offline reminder mail and the system sender it is left under
    PWARP_RENT_REMINDER("pwarp.rent.reminder"),
    PWARP_RENT_MAIL_SENDER("pwarp.rent.mail-sender"),

    // cross-server teleport. Routing a warp that lives on another backend of the network
    PWARP_CROSS_SERVER_SENDING("pwarp.cross-server.sending"),
    PWARP_CROSS_SERVER_UNAVAILABLE("pwarp.cross-server.unavailable"),
    PWARP_CROSS_SERVER_ARRIVED("pwarp.cross-server.arrived"),
    PWARP_CROSS_SERVER_FAILED("pwarp.cross-server.failed"),

    // admin group. Operator verbs that act on any warp by its surrogate id, gated by the admin node
    PWARP_ADMIN_RESTORED("pwarp.admin.restored"),
    PWARP_ADMIN_PURGE_CONFIRM("pwarp.admin.purge-confirm"),
    PWARP_ADMIN_PURGED("pwarp.admin.purged"),
    PWARP_ADMIN_SETOWNER("pwarp.admin.setowner"),
    PWARP_ADMIN_NOT_FOUND("pwarp.admin.not-found"),
    PWARP_ADMIN_RELOAD_HINT("pwarp.admin.reload-hint"),

    // management GUI, list
    PWARP_GUI_LIST_TITLE("pwarp.gui.list.title"),
    PWARP_GUI_LIST_ENTRY_NAME("pwarp.gui.list.entry-name"),
    PWARP_GUI_LIST_ENTRY_LORE("pwarp.gui.list.entry-lore"),
    PWARP_GUI_LIST_PREV("pwarp.gui.list.prev"),
    PWARP_GUI_LIST_NEXT("pwarp.gui.list.next"),
    PWARP_GUI_LIST_CREATE("pwarp.gui.list.create"),
    PWARP_GUI_LIST_CREATE_PROMPT("pwarp.gui.list.create-prompt"),

    // browse menu. The paged public browse (opened by bare /pwarp), one card per public warp read a page at a time
    PWARP_GUI_BROWSE_TITLE("pwarp.gui.browse.title"),
    PWARP_GUI_BROWSE_ENTRY_NAME("pwarp.gui.browse.entry-name"),
    PWARP_GUI_BROWSE_LORE_OWNER("pwarp.gui.browse.lore-owner"),
    PWARP_GUI_BROWSE_LORE_WORLD("pwarp.gui.browse.lore-world"),
    PWARP_GUI_BROWSE_LORE_SERVER("pwarp.gui.browse.lore-server"),
    PWARP_GUI_BROWSE_LORE_VISITS("pwarp.gui.browse.lore-visits"),
    PWARP_GUI_BROWSE_LORE_RATING("pwarp.gui.browse.lore-rating"),
    PWARP_GUI_BROWSE_LORE_FAVOURITES("pwarp.gui.browse.lore-favourites"),
    PWARP_GUI_BROWSE_LORE_PRICE("pwarp.gui.browse.lore-price"),
    PWARP_GUI_BROWSE_LORE_ACCESS("pwarp.gui.browse.lore-access"),
    PWARP_GUI_BROWSE_LORE_SPONSORED("pwarp.gui.browse.lore-sponsored"),
    PWARP_GUI_BROWSE_LORE_CLICK("pwarp.gui.browse.lore-click"),
    PWARP_GUI_BROWSE_PREV("pwarp.gui.browse.prev"),
    PWARP_GUI_BROWSE_NEXT("pwarp.gui.browse.next"),
    PWARP_GUI_BROWSE_SORT("pwarp.gui.browse.sort"),
    PWARP_GUI_BROWSE_SEARCH("pwarp.gui.browse.search"),
    PWARP_GUI_BROWSE_MINE("pwarp.gui.browse.mine"),
    PWARP_GUI_BROWSE_FAVOURITES("pwarp.gui.browse.favourites"),
    PWARP_GUI_BROWSE_ALL("pwarp.gui.browse.all"),
    PWARP_GUI_BROWSE_SORT_LORE("pwarp.gui.browse.sort-lore"),
    PWARP_GUI_BROWSE_SEARCH_LORE("pwarp.gui.browse.search-lore"),
    PWARP_GUI_BROWSE_MINE_LORE("pwarp.gui.browse.mine-lore"),
    PWARP_GUI_BROWSE_FAVOURITES_LORE("pwarp.gui.browse.favourites-lore"),
    PWARP_GUI_BROWSE_ALL_LORE("pwarp.gui.browse.all-lore"),
    PWARP_GUI_BROWSE_CREATE("pwarp.gui.browse.create"),
    PWARP_GUI_BROWSE_CREATE_LORE("pwarp.gui.browse.create-lore"),
    PWARP_GUI_BROWSE_CATEGORIES("pwarp.gui.browse.categories"),
    PWARP_GUI_BROWSE_CATEGORIES_LORE("pwarp.gui.browse.categories-lore"),
    PWARP_GUI_BROWSE_EMPTY("pwarp.gui.browse.empty"),
    PWARP_GUI_BROWSE_EMPTY_LORE("pwarp.gui.browse.empty-lore"),

    // per-warp detail panel (pwarp-view). The browse tile opens this; teleport / favourite / rate / info
    PWARP_GUI_VIEW_TITLE("pwarp.gui.view.title"),
    PWARP_GUI_VIEW_ENTRY_NAME("pwarp.gui.view.entry-name"),
    PWARP_GUI_VIEW_LORE_OWNER("pwarp.gui.view.lore-owner"),
    PWARP_GUI_VIEW_LORE_WORLD("pwarp.gui.view.lore-world"),
    PWARP_GUI_VIEW_LORE_SERVER("pwarp.gui.view.lore-server"),
    PWARP_GUI_VIEW_LORE_VISITS("pwarp.gui.view.lore-visits"),
    PWARP_GUI_VIEW_LORE_UNIQUE("pwarp.gui.view.lore-unique"),
    PWARP_GUI_VIEW_LORE_RATING("pwarp.gui.view.lore-rating"),
    PWARP_GUI_VIEW_LORE_FAVOURITES("pwarp.gui.view.lore-favourites"),
    PWARP_GUI_VIEW_LORE_PRICE("pwarp.gui.view.lore-price"),
    PWARP_GUI_VIEW_LORE_ACCESS("pwarp.gui.view.lore-access"),
    PWARP_GUI_VIEW_TELEPORT("pwarp.gui.view.teleport"),
    PWARP_GUI_VIEW_TELEPORT_LOCKED("pwarp.gui.view.teleport-locked"),
    PWARP_GUI_VIEW_PASSWORD_PROMPT("pwarp.gui.view.password-prompt"),
    PWARP_GUI_VIEW_FAVOURITE("pwarp.gui.view.favourite"),
    PWARP_GUI_VIEW_UNFAVOURITE("pwarp.gui.view.unfavourite"),
    PWARP_GUI_VIEW_RATE("pwarp.gui.view.rate"),
    PWARP_GUI_VIEW_MANAGE("pwarp.gui.view.manage"),
    PWARP_GUI_VIEW_BACK("pwarp.gui.view.back"),
    PWARP_GUI_VIEW_TELEPORT_LORE("pwarp.gui.view.teleport-lore"),
    PWARP_GUI_VIEW_TELEPORT_LOCKED_LORE("pwarp.gui.view.teleport-locked-lore"),
    PWARP_GUI_VIEW_FAVOURITE_LORE("pwarp.gui.view.favourite-lore"),
    PWARP_GUI_VIEW_UNFAVOURITE_LORE("pwarp.gui.view.unfavourite-lore"),
    PWARP_GUI_VIEW_RATE_LORE("pwarp.gui.view.rate-lore"),
    PWARP_GUI_VIEW_MANAGE_LORE("pwarp.gui.view.manage-lore"),
    PWARP_GUI_VIEW_BACK_LORE("pwarp.gui.view.back-lore"),

    // five-star rating menu (pwarp-rate). Reached from the detail panel's rate button
    PWARP_GUI_RATE_TITLE("pwarp.gui.rate.title"),
    PWARP_GUI_RATE_STAR_1("pwarp.gui.rate.star-1"),
    PWARP_GUI_RATE_STAR_2("pwarp.gui.rate.star-2"),
    PWARP_GUI_RATE_STAR_3("pwarp.gui.rate.star-3"),
    PWARP_GUI_RATE_STAR_4("pwarp.gui.rate.star-4"),
    PWARP_GUI_RATE_STAR_5("pwarp.gui.rate.star-5"),
    PWARP_GUI_RATE_BACK("pwarp.gui.rate.back"),
    PWARP_GUI_RATE_STAR_1_LORE("pwarp.gui.rate.star-1-lore"),
    PWARP_GUI_RATE_STAR_2_LORE("pwarp.gui.rate.star-2-lore"),
    PWARP_GUI_RATE_STAR_3_LORE("pwarp.gui.rate.star-3-lore"),
    PWARP_GUI_RATE_STAR_4_LORE("pwarp.gui.rate.star-4-lore"),
    PWARP_GUI_RATE_STAR_5_LORE("pwarp.gui.rate.star-5-lore"),
    PWARP_GUI_RATE_BACK_LORE("pwarp.gui.rate.back-lore"),

    // capability-gated single-warp management panel (pwarp-manage), opened from the detail panel's manage button. Each
    // button is view-gated by the viewer's WarpRole capability and drives one P4 use case; the info tile reflects
    // state.
    PWARP_GUI_MANAGE_TITLE("pwarp.gui.manage.title"),
    PWARP_GUI_MANAGE_ENTRY_NAME("pwarp.gui.manage.entry-name"),
    PWARP_GUI_MANAGE_LORE_OWNER("pwarp.gui.manage.lore-owner"),
    PWARP_GUI_MANAGE_LORE_ACCESS("pwarp.gui.manage.lore-access"),
    PWARP_GUI_MANAGE_LORE_PRICE("pwarp.gui.manage.lore-price"),
    PWARP_GUI_MANAGE_LORE_BANK("pwarp.gui.manage.lore-bank"),
    PWARP_GUI_MANAGE_LORE_CATEGORY("pwarp.gui.manage.lore-category"),
    PWARP_GUI_MANAGE_RENAME("pwarp.gui.manage.rename"),
    PWARP_GUI_MANAGE_RENAME_PROMPT("pwarp.gui.manage.rename-prompt"),
    PWARP_GUI_MANAGE_DISPLAY_NAME("pwarp.gui.manage.display-name"),
    PWARP_GUI_MANAGE_DISPLAY_NAME_PROMPT("pwarp.gui.manage.display-name-prompt"),
    PWARP_GUI_MANAGE_DESCRIPTION("pwarp.gui.manage.description"),
    PWARP_GUI_MANAGE_DESCRIPTION_PROMPT("pwarp.gui.manage.description-prompt"),
    PWARP_GUI_MANAGE_ICON("pwarp.gui.manage.icon"),
    PWARP_GUI_MANAGE_ICON_PROMPT("pwarp.gui.manage.icon-prompt"),
    PWARP_GUI_MANAGE_CATEGORY("pwarp.gui.manage.category"),
    PWARP_GUI_MANAGE_CATEGORY_PROMPT("pwarp.gui.manage.category-prompt"),
    PWARP_GUI_MANAGE_ACCESS("pwarp.gui.manage.access"),
    PWARP_GUI_MANAGE_PASSWORD("pwarp.gui.manage.password"),
    PWARP_GUI_MANAGE_PASSWORD_PROMPT("pwarp.gui.manage.password-prompt"),
    PWARP_GUI_MANAGE_PASSWORD_CLEAR_CONFIRM("pwarp.gui.manage.password-clear-confirm"),
    PWARP_GUI_MANAGE_PRICE("pwarp.gui.manage.price"),
    PWARP_GUI_MANAGE_PRICE_PROMPT("pwarp.gui.manage.price-prompt"),
    PWARP_GUI_MANAGE_MOVE("pwarp.gui.manage.move"),
    PWARP_GUI_MANAGE_BANK("pwarp.gui.manage.bank"),
    PWARP_GUI_MANAGE_TRANSFER("pwarp.gui.manage.transfer"),
    PWARP_GUI_MANAGE_TRANSFER_PROMPT("pwarp.gui.manage.transfer-prompt"),
    PWARP_GUI_MANAGE_TRANSFER_CONFIRM("pwarp.gui.manage.transfer-confirm"),
    PWARP_GUI_MANAGE_SPONSOR("pwarp.gui.manage.sponsor"),
    PWARP_GUI_MANAGE_SPONSOR_PROMPT("pwarp.gui.manage.sponsor-prompt"),
    PWARP_GUI_MANAGE_DELETE("pwarp.gui.manage.delete"),
    PWARP_GUI_MANAGE_DELETE_CONFIRM("pwarp.gui.manage.delete-confirm"),
    PWARP_GUI_MANAGE_BACK("pwarp.gui.manage.back"),
    PWARP_GUI_MANAGE_MEMBERS("pwarp.gui.manage.members"),
    PWARP_GUI_MANAGE_WHITELIST("pwarp.gui.manage.whitelist"),
    PWARP_GUI_MANAGE_BANS("pwarp.gui.manage.bans"),

    // landing. The pwarp-categories menu bare /pwarp opens (quick browse entries + the defined categories)
    PWARP_GUI_CATEGORIES_TITLE("pwarp.gui.categories.title"),
    PWARP_GUI_CATEGORIES_BROWSE_ALL("pwarp.gui.categories.browse-all"),
    PWARP_GUI_CATEGORIES_MINE("pwarp.gui.categories.mine"),
    PWARP_GUI_CATEGORIES_FAVOURITES("pwarp.gui.categories.favourites"),
    PWARP_GUI_CATEGORIES_TOP("pwarp.gui.categories.top"),
    PWARP_GUI_CATEGORIES_ENTRY_NAME("pwarp.gui.categories.entry-name"),
    PWARP_GUI_CATEGORIES_ENTRY_LORE("pwarp.gui.categories.entry-lore"),
    PWARP_GUI_CATEGORIES_SPONSOR("pwarp.gui.categories.sponsor"),
    PWARP_GUI_CATEGORIES_SPONSOR_NAME("pwarp.gui.categories.sponsor-name"),
    PWARP_GUI_CATEGORIES_SPONSOR_LORE("pwarp.gui.categories.sponsor-lore"),
    PWARP_GUI_CATEGORIES_PREV("pwarp.gui.categories.prev"),
    PWARP_GUI_CATEGORIES_NEXT("pwarp.gui.categories.next"),
    PWARP_GUI_CATEGORIES_CLOSE("pwarp.gui.categories.close"),
    PWARP_GUI_CATEGORIES_CLOSE_LORE("pwarp.gui.categories.close-lore"),
    PWARP_GUI_CATEGORIES_CAT_BUILDING("pwarp.gui.categories.cat-building"),
    PWARP_GUI_CATEGORIES_CAT_SHOP("pwarp.gui.categories.cat-shop"),
    PWARP_GUI_CATEGORIES_CAT_PVP("pwarp.gui.categories.cat-pvp"),
    PWARP_GUI_CATEGORIES_CAT_FARM("pwarp.gui.categories.cat-farm"),
    PWARP_GUI_CATEGORIES_CAT_REDSTONE("pwarp.gui.categories.cat-redstone"),
    PWARP_GUI_CATEGORIES_CAT_MISC("pwarp.gui.categories.cat-misc"),
    PWARP_GUI_CATEGORIES_HEADER("pwarp.gui.categories.header"),
    PWARP_GUI_CATEGORIES_HEADER_LORE("pwarp.gui.categories.header-lore"),
    PWARP_GUI_CATEGORIES_BROWSE_ALL_LORE("pwarp.gui.categories.browse-all-lore"),
    PWARP_GUI_CATEGORIES_MINE_LORE("pwarp.gui.categories.mine-lore"),
    PWARP_GUI_CATEGORIES_FAVOURITES_LORE("pwarp.gui.categories.favourites-lore"),
    PWARP_GUI_CATEGORIES_TOP_LORE("pwarp.gui.categories.top-lore"),

    // the pwarp-icon selector the manage panel's icon button opens
    PWARP_GUI_ICON_TITLE("pwarp.gui.icon.title"),
    PWARP_GUI_ICON_ENTRY_NAME("pwarp.gui.icon.entry-name"),
    PWARP_GUI_ICON_RESET("pwarp.gui.icon.reset"),
    PWARP_GUI_ICON_RESET_LORE("pwarp.gui.icon.reset-lore"),
    PWARP_GUI_ICON_PREV("pwarp.gui.icon.prev"),
    PWARP_GUI_ICON_NEXT("pwarp.gui.icon.next"),
    PWARP_GUI_ICON_BACK("pwarp.gui.icon.back"),

    // people sub-menus: members (co-owners / managers)
    PWARP_GUI_MEMBERS_TITLE("pwarp.gui.members.title"),
    PWARP_GUI_MEMBERS_ENTRY_NAME("pwarp.gui.members.entry-name"),
    PWARP_GUI_MEMBERS_ENTRY_LORE("pwarp.gui.members.entry-lore"),
    PWARP_GUI_MEMBERS_ADD_COOWNER("pwarp.gui.members.add-coowner"),
    PWARP_GUI_MEMBERS_ADD_MANAGER("pwarp.gui.members.add-manager"),
    PWARP_GUI_MEMBERS_ADD_PROMPT("pwarp.gui.members.add-prompt"),
    PWARP_GUI_MEMBERS_PREV("pwarp.gui.members.prev"),
    PWARP_GUI_MEMBERS_NEXT("pwarp.gui.members.next"),
    PWARP_GUI_MEMBERS_BACK("pwarp.gui.members.back"),

    // people sub-menus, whitelist
    PWARP_GUI_WHITELIST_TITLE("pwarp.gui.whitelist.title"),
    PWARP_GUI_WHITELIST_ENTRY_NAME("pwarp.gui.whitelist.entry-name"),
    PWARP_GUI_WHITELIST_ENTRY_LORE("pwarp.gui.whitelist.entry-lore"),
    PWARP_GUI_WHITELIST_ADD("pwarp.gui.whitelist.add"),
    PWARP_GUI_WHITELIST_ADD_PROMPT("pwarp.gui.whitelist.add-prompt"),
    PWARP_GUI_WHITELIST_PREV("pwarp.gui.whitelist.prev"),
    PWARP_GUI_WHITELIST_NEXT("pwarp.gui.whitelist.next"),
    PWARP_GUI_WHITELIST_BACK("pwarp.gui.whitelist.back"),

    // people sub-menus, bans
    PWARP_GUI_BANS_TITLE("pwarp.gui.bans.title"),
    PWARP_GUI_BANS_ENTRY_NAME("pwarp.gui.bans.entry-name"),
    PWARP_GUI_BANS_ENTRY_LORE("pwarp.gui.bans.entry-lore"),
    PWARP_GUI_BANS_ADD("pwarp.gui.bans.add"),
    PWARP_GUI_BANS_ADD_PROMPT("pwarp.gui.bans.add-prompt"),
    PWARP_GUI_BANS_NO_REASON("pwarp.gui.bans.no-reason"),
    PWARP_GUI_BANS_PERMANENT("pwarp.gui.bans.permanent"),
    PWARP_GUI_BANS_PREV("pwarp.gui.bans.prev"),
    PWARP_GUI_BANS_NEXT("pwarp.gui.bans.next"),
    PWARP_GUI_BANS_BACK("pwarp.gui.bans.back"),

    // management GUI, editor frame
    PWARP_GUI_EDITOR_TITLE("pwarp.gui.editor.title"),
    PWARP_GUI_EDITOR_VALUE_LORE("pwarp.gui.editor.value-lore"),
    PWARP_GUI_EDITOR_BACK("pwarp.gui.editor.back"),
    PWARP_GUI_EDITOR_DELETE("pwarp.gui.editor.delete"),
    PWARP_GUI_EDITOR_DELETE_CONFIRM("pwarp.gui.editor.delete-confirm"),

    // management GUI, properties
    PWARP_GUI_PROP_NAME("pwarp.gui.prop.name"),
    PWARP_GUI_PROP_NAME_PROMPT("pwarp.gui.prop.name-prompt"),
    PWARP_GUI_PROP_MOVE("pwarp.gui.prop.move"),
    PWARP_GUI_PROP_ICON("pwarp.gui.prop.icon"),
    PWARP_GUI_PROP_ICON_PROMPT("pwarp.gui.prop.icon-prompt"),
    PWARP_GUI_PROP_VISIBILITY("pwarp.gui.prop.visibility"),
    PWARP_GUI_PROP_LOCK("pwarp.gui.prop.lock"),
    PWARP_GUI_PROP_PASSWORD("pwarp.gui.prop.password"),
    PWARP_GUI_PROP_PASSWORD_PROMPT("pwarp.gui.prop.password-prompt"),
    PWARP_GUI_PROP_DEPARTURE_SOUND("pwarp.gui.prop.departure-sound"),
    PWARP_GUI_PROP_DEPARTURE_SOUND_PROMPT("pwarp.gui.prop.departure-sound-prompt"),
    PWARP_GUI_PROP_ARRIVAL_SOUND("pwarp.gui.prop.arrival-sound"),
    PWARP_GUI_PROP_ARRIVAL_SOUND_PROMPT("pwarp.gui.prop.arrival-sound-prompt"),
    PWARP_GUI_PROP_DEPARTURE_PARTICLE("pwarp.gui.prop.departure-particle"),
    PWARP_GUI_PROP_DEPARTURE_PARTICLE_PROMPT("pwarp.gui.prop.departure-particle-prompt"),
    PWARP_GUI_PROP_ARRIVAL_PARTICLE("pwarp.gui.prop.arrival-particle"),
    PWARP_GUI_PROP_ARRIVAL_PARTICLE_PROMPT("pwarp.gui.prop.arrival-particle-prompt"),
    PWARP_GUI_PROP_WARMUP("pwarp.gui.prop.warmup"),
    PWARP_GUI_PROP_COOLDOWN("pwarp.gui.prop.cooldown"),

    // management GUI. Shared value words and selectors
    PWARP_GUI_VALUE_NONE("pwarp.gui.value.none"),
    PWARP_GUI_VALUE_PUBLIC("pwarp.gui.value.public"),
    PWARP_GUI_VALUE_PRIVATE("pwarp.gui.value.private"),
    PWARP_GUI_VALUE_LOCKED("pwarp.gui.value.locked"),
    PWARP_GUI_VALUE_UNLOCKED("pwarp.gui.value.unlocked"),
    PWARP_GUI_VALUE_SET("pwarp.gui.value.set"),
    PWARP_GUI_SELECT_VISIBILITY("pwarp.gui.select.visibility");

    private final String key;

    PlayerwarpsMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
