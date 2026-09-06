package com.uxplima.uxmessentials.communication.adapter.inbound.listener;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.communication.adapter.CommunicationSettings;
import com.uxplima.uxmessentials.communication.adapter.outbound.BukkitInfoSender;
import com.uxplima.uxmessentials.communication.application.InfoRegistry;
import com.uxplima.uxmessentials.communication.application.ResolveDeathMessage;
import com.uxplima.uxmessentials.communication.application.ResolvedMessage;
import com.uxplima.uxmessentials.communication.domain.DeathCause;
import com.uxplima.uxmessentials.communication.domain.InfoPage;
import com.uxplima.uxmessentials.communication.domain.PlaceholderBindings;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Replaces the vanilla death line per the configured death policy and optionally shows the dying player a
 * configured info page. The death's cause (classified by {@link DeathCauses} from the killer and last damage)
 * picks the {@code MessagePolicy} from the per-cause table, so a fall, a mob, and a PVP kill can each read
 * differently; a cause with no override falls through to the default. A {@code DISABLE} policy clears the death
 * message, {@code DEFAULT} leaves the vanilla line, and {@code CUSTOM} swaps in the operator template the
 * {@code ResolveDeathMessage} use case selected, rendered through MiniMessage with {@code {player}},
 * {@code {count}}, {@code {world}}, the vanilla {@code {message}}, the classified {@code {cause}}, and, when a
 * player dealt the blow
 * {@code {killer}} and {@code {killer_weapon}} substituted. The templates and the info-page bodies are operator
 * content, never {@code MessageKey}s.
 *
 * <p>The send-info-after-death hook is owned here: when the operator names an info page, that page's lines are
 * delivered to the dying player after the death message is settled, so a freshly killed player sees the
 * configured help. The event fires on the dying player's region thread; delivery hops to that thread inside the
 * info sender.
 */
@NullMarked
public final class DeathMessageListener implements Listener {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final ResolveDeathMessage resolveDeath;
    private final InfoRegistry pages;
    private final BukkitInfoSender infoSender;
    private final CommunicationSettings settings;

    public DeathMessageListener(
            ResolveDeathMessage resolveDeath,
            InfoRegistry pages,
            BukkitInfoSender infoSender,
            CommunicationSettings settings) {
        this.resolveDeath = Objects.requireNonNull(resolveDeath, "resolveDeath");
        this.pages = Objects.requireNonNull(pages, "pages");
        this.infoSender = Objects.requireNonNull(infoSender, "infoSender");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Player killer = player.getKiller();
        DeathCause cause = DeathCauses.of(event);
        PlaceholderBindings bindings = ConnectionPlaceholders.death(
                player, onlineCount(player), cause, vanillaMessage(event), killerName(killer), killerWeapon(killer));
        ResolvedMessage resolved = resolveDeath.resolve(cause, bindings);
        if (resolved.hasTemplate()) {
            event.deathMessage(MINI.deserialize(resolved.template().orElseThrow()));
        } else if (resolved.suppressVanilla()) {
            event.deathMessage(null);
        }
        sendInfoPage(player);
    }

    private static String killerName(@Nullable Player killer) {
        return killer == null ? "" : killer.getName();
    }

    private static String killerWeapon(@Nullable Player killer) {
        return killer == null
                ? ""
                : DeathCauses.weaponName(killer.getInventory().getItemInMainHand());
    }

    private void sendInfoPage(Player player) {
        Optional<String> pageName = settings.deathInfoPage();
        if (pageName.isEmpty()) {
            return;
        }
        Optional<InfoPage> page = pages.find(pageName.get());
        page.ifPresent(found -> infoSender.send(BukkitRefs.toRef(player), found));
    }

    private static String vanillaMessage(PlayerDeathEvent event) {
        Component message = event.deathMessage();
        return message == null ? "" : PLAIN.serialize(message);
    }

    private static int onlineCount(Player player) {
        return player.getServer().getOnlinePlayers().size();
    }
}
