package me.snownw.core.service;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

/** Key-All: otomatik aralıklarla veya /keyall komutuyla tüm çevrim içi oyunculara ödül dağıtır.
 *  key-all.commands içindeki komutlar %player% placeholder'ıyla çalıştırılır. */
public final class KeyAllService {

    private final SnowNWCorePlugin plugin;
    private long nextRunMillis = 0;
    private boolean running = false;

    public KeyAllService(SnowNWCorePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() { return plugin.getConfig().getBoolean("key-all.enabled", true); }

    public void start() {
        stop();
        if (!enabled()) return;
        running = true;
        int everyMin = Math.max(1, plugin.getConfig().getInt("key-all.every-minutes", 60));
        nextRunMillis = System.currentTimeMillis() + everyMin * 60_000L;
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!running || nextRunMillis <= 0 || System.currentTimeMillis() < nextRunMillis) return;
            executeAll();
            nextRunMillis = System.currentTimeMillis() + Math.max(1, plugin.getConfig().getInt("key-all.every-minutes", 60)) * 60_000L;
        }, 20L * 60, 20L * 60);
    }

    public void stop() { nextRunMillis = 0; running = false; }

    public long secondsRemaining() { return Math.max(0, (nextRunMillis - System.currentTimeMillis()) / 1000L); }

    /** Tüm oyunculara konfigürasyondaki ödül komutlarını çalıştırır. */
    public void executeAll() {
        List<String> commands = plugin.getConfig().getStringList("key-all.commands");
        if (commands.isEmpty()) {
            plugin.getLogger().warning("[KeyAll] key-all.commands boş; atlandı.");
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            for (String cmd : commands) {
                String c = cmd.replace("%player%", p.getName()).replace("{player}", p.getName());
                if (c.startsWith("/")) c = c.substring(1);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c);
            }
        }
        String msg = plugin.getConfig().getString("key-all.message");
        if (msg != null && !msg.isBlank()) Bukkit.broadcast(ColorUtil.text(msg));
        else Bukkit.broadcast(ColorUtil.text(plugin.messages().get("keyall-done")));
    }
}
