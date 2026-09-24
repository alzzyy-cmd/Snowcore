package me.snownw.core.service;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** SnowNW TAB list. Dünya içi isim bilgisi ScoreboardService tarafından yönetilir. */
public final class TabService {
    private final SnowNWCorePlugin plugin;
    public TabService(SnowNWCorePlugin plugin) { this.plugin = plugin; }

    public void start() {
        if (!plugin.getConfig().getBoolean("tab.enabled", true)) return;
        long interval = Math.max(10L, plugin.getConfig().getLong("tab.update-ticks", 20L));
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) update(p);
        }, 1L, interval);
    }

    public void update(Player player) {
        if (!plugin.getConfig().getBoolean("tab.enabled", true)) return;
        player.playerListName(ColorUtil.text("&f" + player.getName()));
        String header = plugin.getConfig().getString("tab.header", "&b&lSnowNW\n&7Oyuncu listesi");
        String footer = plugin.getConfig().getString("tab.footer", "&7SnowNW");
        player.sendPlayerListHeaderAndFooter(ColorUtil.text(header), ColorUtil.text(footer));
    }
}
