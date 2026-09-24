package me.snownw.core.service;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.*;

import java.util.List;

/** Otomatik varlık temizleme (clear-lag): belirli aralıklarla hayvan/yaratık/düşen eşyaları temizler.
 *  clearlag.* yapılandırmasından beslenir; /lagtemizle ile elle de çalıştırılabilir. */
public final class ClearLagService {

    private final SnowNWCorePlugin plugin;
    private long nextRunMillis = 0;
    private boolean running = false;

    public ClearLagService(SnowNWCorePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() { return plugin.getConfig().getBoolean("clearlag.enabled", true); }

    public void start() {
        stop();
        if (!enabled()) return;
        running = true;
        int everyMin = Math.max(1, plugin.getConfig().getInt("clearlag.every-minutes", 15));
        nextRunMillis = System.currentTimeMillis() + everyMin * 60_000L;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        nextRunMillis = 0;
        running = false;
    }

    public long secondsRemaining() {
        return Math.max(0, (nextRunMillis - System.currentTimeMillis()) / 1000L);
    }

    private void tick() {
        if (!running || !enabled()) return;
        if (nextRunMillis <= 0) return;
        long left = (nextRunMillis - System.currentTimeMillis()) / 1000L;
        List<Integer> warnings = plugin.getConfig().getIntegerList("clearlag.warnings");
        if ((left == 10 || left == 3 || warnings.contains((int) left)) && left > 0) {
            Bukkit.broadcast(ColorUtil.text(plugin.messages().get("clearlag-warn")
                    .replace("{sec}", String.valueOf(left))));
        }
        if (left <= 0 && System.currentTimeMillis() >= nextRunMillis) {
            int removed = clearNow();
            Bukkit.broadcast(ColorUtil.text(plugin.messages().get("clearlag-done")
                    .replace("{count}", String.valueOf(removed))));
            int everyMin = Math.max(1, plugin.getConfig().getInt("clearlag.every-minutes", 15));
            nextRunMillis = System.currentTimeMillis() + everyMin * 60_000L;
        }
    }

    /** Tüm dünyalarda konfigürasyona uygun varlıkları temizler ve sayısını döner. */
    public int clearNow() {
        boolean animals = plugin.getConfig().getBoolean("clearlag.animals", true);
        boolean monsters = plugin.getConfig().getBoolean("clearlag.monsters", true);
        boolean drops = plugin.getConfig().getBoolean("clearlag.dropped-items", true);
        List<String> excluded = plugin.getConfig().getStringList("clearlag.excluded-worlds");
        int removed = 0;
        for (World w : Bukkit.getWorlds()) {
            if (excluded.stream().anyMatch(x -> x.equalsIgnoreCase(w.getName()))) continue;
            for (Entity e : w.getEntities()) {
                if (e instanceof Player) continue;
                if (e.isInvulnerable()) continue;
                if (e instanceof Tameable t && t.isTamed()) continue;
                if (drops && e instanceof Item && !((Item) e).isPersistent()) { e.remove(); removed++; continue; }
                if (monsters && (e instanceof Monster || e instanceof Slime) && !(e instanceof Boss)) { e.remove(); removed++; continue; }
                if (animals && (e instanceof Animals || e instanceof Ambient || e instanceof WaterMob)) { e.remove(); removed++; continue; }
            }
        }
        return removed;
    }
}
