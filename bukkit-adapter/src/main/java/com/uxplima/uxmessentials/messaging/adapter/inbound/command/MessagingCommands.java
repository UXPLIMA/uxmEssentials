package com.uxplima.uxmessentials.messaging.adapter.inbound.command;

import java.util.List;

import com.uxplima.uxmessentials.messaging.adapter.MessagingServices;
import com.uxplima.uxmessentials.messaging.adapter.inbound.gui.MessagingGuiViews;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the messaging context's Brigadier command surface (docs/10-feature-modules.md §15.7, NO public
 * chat) as {@link CommandRegistration}s over the constructed {@link MessagingServices}. Collected in one
 * greppable table so the literal/permission pairing matches the permissions reference and the kernel's
 * {@code MessagingCommandSurface}; the plugin's {@code LifecycleEvents.COMMANDS} handler registers each.
 */
@NullMarked
public final class MessagingCommands {

    private MessagingCommands() {}

    /** Every messaging command, in surface order. The GUI views back the no-arg branches and {@code /msgsettings}. */
    public static List<CommandRegistration> all(
            MessagingServices services, Messages messages, MessageSink sink, MessagingGuiViews views) {
        return List.of(
                new MsgCommand(services, messages, sink),
                new ReplyCommand(services, messages, sink),
                new MailCommand(services, messages, sink, views),
                new MsgToggleCommand(services, messages, sink),
                new RtoggleCommand(services, messages, sink),
                new IgnoreCommand(services, messages, sink, views),
                new UnignoreCommand(services, messages, sink),
                new IgnoreListCommand(services, messages, sink, views),
                new SocialSpyCommand(services, messages, sink),
                new MailClearCommand(services, messages, sink),
                new HelpOpCommand(services, messages, sink),
                new MsgSettingsCommand(services, messages, sink, views));
    }
}
