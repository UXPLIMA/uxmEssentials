package com.uxplima.uxmessentials.discord;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.uxplima.uxmessentials.api.link.DiscordLinkConfirmation;
import com.uxplima.uxmessentials.api.link.DiscordLinkConfirmation.Outcome;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

/**
 * The inbound side of account linking inside Discord: a JDA listener for the {@code /link <code>} slash command
 * that redeems a code through the host's {@link DiscordLinkConfirmation} seam and replies ephemerally with the
 * outcome. The reply is always private to the invoking user (a link code and the result are not channel chatter)
 * and the redemption runs on JDA's own event thread, off any server tick, so the host use case behind the
 * seam is called off-tick exactly as it expects.
 *
 * <p>The reply text here is short Discord-side English, not a Minecraft player-locale catalog key: it is sent to
 * a Discord user with no in-game locale, so it does not route through the host's {@code MessageKey} catalog.
 */
final class LinkSlashListener extends ListenerAdapter {

    static final String COMMAND_NAME = "link";
    static final String CODE_OPTION = "code";

    private static final Logger LOG = Logger.getLogger(LinkSlashListener.class.getName());

    private final DiscordLinkConfirmation confirmation;

    LinkSlashListener(DiscordLinkConfirmation confirmation) {
        this.confirmation = Objects.requireNonNull(confirmation, "confirmation");
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!COMMAND_NAME.equals(event.getName())) {
            return;
        }
        OptionMapping codeOption = event.getOption(CODE_OPTION);
        if (codeOption == null) {
            event.reply("Usage: /link <code>").setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue();
        event.getHook()
                .sendMessage(redeem(codeOption.getAsString(), event.getUser().getId()))
                .queue();
    }

    /**
     * Redeem the code through the seam and pick the reply. The seam is contracted never to throw for a
     * modelled outcome, but an unexpected runtime fault (a transient store error) must still resolve the
     * deferred interaction rather than leave it hung, so it is logged with context and a generic ephemeral
     * reply is returned instead of being rethrown back onto JDA's edge.
     */
    private String redeem(String code, String discordId) {
        try {
            return reply(confirmation.confirm(code, discordId));
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "failed to redeem /link code for discord user " + discordId, failure);
            return "Something went wrong redeeming that code. Please try again in a moment.";
        }
    }

    private static String reply(Outcome outcome) {
        return switch (outcome.result()) {
            case LINKED -> "Linked to **" + outcome.playerName() + "**. You can close this.";
            case NO_CODE -> "That code is not valid. Run /discordlink in game to get a fresh one.";
            case EXPIRED -> "That code has expired. Run /discordlink in game to get a new one.";
            case ALREADY_LINKED -> "This Discord account is already linked to a different player.";
            case INVALID -> "That does not look like a valid link code.";
        };
    }
}
