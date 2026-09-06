package com.uxplima.uxmessentials.communication.domain;

/**
 * How a connection message ({@code join}, {@code quit}, {@code death}) is produced for a player.
 *
 * <p>The mode is read from {@code join-quit.conf} per channel. {@link #DISABLE} suppresses the plugin's
 * message entirely. The channel renders nothing and a join/quit channel set here also asks the adapter to clear
 * the vanilla line. {@link #DEFAULT} leaves the vanilla server message untouched. The plugin contributes no
 * template of its own. {@link #CUSTOM} renders one of the operator's authored templates, chosen per the
 * {@link Ordering} on the policy.
 */
public enum PolicyMode {

    /** Suppress the message: the plugin renders nothing and the vanilla line is cleared. */
    DISABLE,

    /** Leave the vanilla server message as it is; the plugin contributes no template. */
    DEFAULT,

    /** Render one of the operator's authored templates, selected per the policy ordering. */
    CUSTOM
}
