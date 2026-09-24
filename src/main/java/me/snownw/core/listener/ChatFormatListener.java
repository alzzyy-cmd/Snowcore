package me.snownw.core.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.data.StatsStore;
import me.snownw.core.util.ColorUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.List;

/** V2 tarzı sohbet formatı: biçim + üzerine gelince istatistik kartı (chat-format).
 *  PUBLIC_CHAT ayarları ChatSocialListener'da kalır; bu dinleyici EN DÜŞÜK öncelikten önce gelir;
 *  ChatSocialListener (HIGH) iptal ederse burası çalışmaz. */
public final class ChatFormatListener implements Listener {

    private final SnowNWCorePlugin plugin;

    public ChatFormatListener(SnowNWCorePlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        if (!plugin.getConfig().getBoolean("chat-format.enabled", true)) return;
        String format = plugin.getConfig().getString("chat-format.chat",
                "&8[&f{level}&8] %prefix%&f{player} &8» &7{message}");
        List<String> hoverLore = plugin.getConfig().getStringList("chat-format.hover-lore");

        e.renderer((source, displayName, message, viewer) -> {
            if (!(source instanceof Player sp)) return displayName.append(Component.text(" » ")).append(message);
            String prefix = luckPrefix(sp);
            String level = String.valueOf(plugin.stats().get(sp.getUniqueId(), StatsStore.Type.KILLS));
            String msgPlain = PlainTextComponentSerializer.plainText().serialize(message);

            String text = format
                    .replace("%prefix%", prefix)
                    .replace("{level}", level)
                    .replace("{player}", sp.getName())
                    .replace("{message}", msgPlain);
            Component base = ColorUtil.text(text);

            // Hover: istatistik kartı
            Component hover;
            if (!hoverLore.isEmpty()) {
                Component hc = Component.empty();
                for (String line : hoverLore) {
                    String l = line
                            .replace("{player}", sp.getName())
                            .replace("{kills}", String.valueOf(plugin.stats().get(sp.getUniqueId(), StatsStore.Type.KILLS)))
                            .replace("{deaths}", String.valueOf(plugin.stats().get(sp.getUniqueId(), StatsStore.Type.DEATHS)))
                            .replace("{blocks}", String.valueOf(plugin.stats().get(sp.getUniqueId(), StatsStore.Type.BLOCKS)))
                            .replace("{mobs}", String.valueOf(plugin.stats().get(sp.getUniqueId(), StatsStore.Type.MOBS)))
                            .replace("{playtime}", StatsStore.formatPlaytime(plugin.stats().get(sp.getUniqueId(), StatsStore.Type.PLAYTIME)))
                            .replace("{balance}", plugin.economy().format(plugin.economy().get(sp)))
                            .replace("{shards}", String.valueOf(plugin.shards().get(sp.getUniqueId())));
                    hc = hc.append(ColorUtil.text(l)).append(Component.newline());
                }
                hover = hc;
            } else {
                hover = ColorUtil.text("&e" + sp.getName());
            }
            return base
                    .hoverEvent(HoverEvent.showText(hover))
                    .clickEvent(ClickEvent.suggestCommand("/mesaj " + sp.getName() + " "));
        });
    }

    /** LuckPerms prefix — API yansıması (bağımlılık opsiyonel). */
    private String luckPrefix(Player p) {
        try {
            Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object lp = provider.getMethod("get").invoke(null);
            Object playerAdapter = lp.getClass().getMethod("getPlayerAdapter", Class.class).invoke(lp, Player.class);
            Object user = playerAdapter.getClass().getMethod("getUser", Object.class).invoke(playerAdapter, p);
            Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
            Object meta = cachedData.getClass().getMethod("getMetaData").invoke(cachedData);
            Object prefix = meta.getClass().getMethod("getPrefix").invoke(meta);
            return prefix == null ? "" : prefix.toString() + " ";
        } catch (Throwable t) {
            return "";
        }
    }
}
