package com.uxplima.uxmessentials.shared.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.application.command.EffectiveCommand;
import org.jspecify.annotations.NullMarked;

/**
 * Installs a command's GUI opener as its bare-input root executor when the catalog's {@code gui} flag is on.
 *
 * <p>This sits between the {@link CatalogBinding} and the {@link UsageBinding} at the registration
 * chokepoint. A command that opens a screen on bare input ({@code /kit}, {@code /warp}, {@code /uxmess})
 * exposes its opener through {@link CommandRegistration#guiRoot()}; when the resolved {@link EffectiveCommand}
 * for that id has {@code gui} on, the root literal is rebuilt with the opener as its root executor
 * replacing any executor the command shipped with. When {@code gui} is off, the node is returned untouched,
 * so a root that carries no executor falls through to the {@link UsageBinding} and answers with its usage
 * text instead. A command with no entry in the catalog map defaults to gui-on, matching the global default
 * an untouched install ships with.
 *
 * <p>Running before the {@link UsageBinding} is deliberate: the usage injection only fires on a root that
 * still lacks an executor, so a gui-on command has already gained its opener and is left alone, while a
 * gui-off command keeps its bare root open for the usage fallback.
 */
@NullMarked
public final class GuiRootBinding {

    private final Map<String, EffectiveCommand> byId;

    public GuiRootBinding(Map<String, EffectiveCommand> byId) {
        this.byId = Map.copyOf(Objects.requireNonNull(byId, "byId"));
    }

    /** Wrap {@code registration} so its bare root opens the GUI when the catalog's {@code gui} flag is on. */
    public CommandRegistration wrap(CommandRegistration registration) {
        Objects.requireNonNull(registration, "registration");
        return new BoundRegistration(registration, this);
    }

    private boolean guiOn(String commandId) {
        EffectiveCommand effective = byId.get(commandId);
        return effective == null || effective.gui();
    }

    /** A {@link CommandRegistration} whose bare root gains the GUI opener when its catalog flag is on. */
    private record BoundRegistration(CommandRegistration delegate, GuiRootBinding binding)
            implements CommandRegistration {

        @Override
        public LiteralCommandNode<CommandSourceStack> build() {
            LiteralCommandNode<CommandSourceStack> node = delegate.build();
            Optional<Command<CommandSourceStack>> opener = delegate.guiRoot();
            if (opener.isEmpty() || !binding.guiOn(delegate.commandId())) {
                return node;
            }
            return BrigadierNodes.rebindRoot(node, node.getLiteral(), opener.get());
        }

        @Override
        public String description() {
            return delegate.description();
        }

        @Override
        public List<String> aliases() {
            return delegate.aliases();
        }

        @Override
        public String commandId() {
            return delegate.commandId();
        }

        @Override
        public Optional<Command<CommandSourceStack>> guiRoot() {
            return delegate.guiRoot();
        }
    }
}
