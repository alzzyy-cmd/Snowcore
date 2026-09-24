package me.snownw.core.listener;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.data.SettingsStore;
import me.snownw.core.util.ColorUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ChatSocialListener implements Listener {

    private final SnowNWCorePlugin plugin;

    public ChatSocialListener(SnowNWCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!plugin.getConfig().getBoolean("chat.join-leave-enabled", true)) {
            e.joinMessage(null);
            return;
        }
        String msg = plugin.messages().plain("join")
                .replace("{player}", e.getPlayer().getName());
        e.joinMessage(null);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (plugin.socialAudienceAllows(e.getPlayer().getUniqueId(), viewer.getUniqueId(), SettingsStore.Toggle.JOIN_LEAVE)) {
                viewer.sendMessage(ColorUtil.text(msg));
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (!plugin.getConfig().getBoolean("chat.join-leave-enabled", true)) {
            e.quitMessage(null);
            return;
        }
        String msg = plugin.messages().plain("quit")
                .replace("{player}", e.getPlayer().getName());
        e.quitMessage(null);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (plugin.socialAudienceAllows(e.getPlayer().getUniqueId(), viewer.getUniqueId(), SettingsStore.Toggle.JOIN_LEAVE)) {
                viewer.sendMessage(ColorUtil.text(msg));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        Player dead = e.getEntity();
        // Vanilla yayınını kaldır; mesajı mesafe + ayar sınırlı yayınla
        e.deathMessage(null);
        if (!plugin.getConfig().getBoolean("chat.death-messages-enabled", true)) return;
        if (!plugin.settings().get(dead.getUniqueId(), SettingsStore.Toggle.DEATH_MESSAGES)) return;

        // Ölüm nedenine göre Türkçe mesaj (death-messages.yml); kapalıysa genel mesaj
        String text = null;
        if (plugin.deathMessages() != null) text = plugin.deathMessages().messageFor(dead);
        if (text == null) text = plugin.messages().plain("death").replace("{player}", dead.getName());
        final String finalText = text;
        int chunks = plugin.getConfig().getInt("chat.death-message-chunks", 10);
        double distSq = chunks * 16.0 * chunks * 16.0;
        for (Player p : dead.getWorld().getPlayers()) {
            if (!plugin.socialAudienceAllows(dead.getUniqueId(), p.getUniqueId(), SettingsStore.Toggle.DEATH_MESSAGES)) continue;
            if (p.getLocation().distanceSquared(dead.getLocation()) <= distSq) {
                p.sendMessage(ColorUtil.text(finalText));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player sender = e.getPlayer();
        if (plugin.offenses() != null && plugin.offenses().isMuted(sender.getUniqueId())) {
            e.setCancelled(true);
            sender.sendMessage(ColorUtil.text("&cSohbet kullanımın kısıtlı."));
            return;
        }
        if (!plugin.settings().get(sender.getUniqueId(), SettingsStore.Toggle.PUBLIC_CHAT)) {
            e.setCancelled(true);
            sender.sendMessage(ColorUtil.text(plugin.messages().get("chat-disabled-self")));
            return;
        }
        // remove viewers who disabled public chat
        e.viewers().removeIf(aud -> {
            if (aud instanceof Player p) {
                return !plugin.settings().get(p.getUniqueId(), SettingsStore.Toggle.PUBLIC_CHAT);
            }
            return false;
        });
    }
}
