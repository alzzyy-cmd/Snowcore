package me.snownw.core.listener;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.service.AreaService;
import me.snownw.core.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Otomatik AFK: spawn alanında uzun süre hareketsiz kalan oyuncu AFK alanına taşınır (V2 paritesi). */
public final class AfkAutoListener implements Listener {

    private final SnowNWCorePlugin plugin;
    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();

    public AfkAutoListener(SnowNWCorePlugin plugin) { this.plugin = plugin; start(); }

    public boolean enabled() { return plugin.getConfig().getBoolean("afk-system.enabled", true); }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::check, 20L * 20, 20L * 20);
    }

    private void touch(UUID id) { lastActivity.put(id, System.currentTimeMillis()); }

    @EventHandler(ignoreCancelled = true) public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        if (e.getTo().distanceSquared(e.getFrom()) > 0.001 || e.getTo().getYaw() != e.getFrom().getYaw()) touch(e.getPlayer().getUniqueId());
    }
    @EventHandler(ignoreCancelled = true) public void onChat(io.papermc.paper.event.player.AsyncChatEvent e) { touch(e.getPlayer().getUniqueId()); }
    @EventHandler(ignoreCancelled = true) public void onInteract(PlayerInteractEvent e) { touch(e.getPlayer().getUniqueId()); }
    @EventHandler(ignoreCancelled = true) public void onCommand(PlayerCommandPreprocessEvent e) { touch(e.getPlayer().getUniqueId()); }
    @EventHandler public void onJoin(PlayerJoinEvent e) { touch(e.getPlayer().getUniqueId()); }
    @EventHandler public void onQuit(PlayerQuitEvent e) { lastActivity.remove(e.getPlayer().getUniqueId()); }

    private void check() {
        if (!enabled()) return;
        long idleMs = plugin.getConfig().getLong("afk-system.idle-seconds", 300) * 1000L;
        if (idleMs <= 0) return;
        String spawnAreaName = plugin.getConfig().getString("afk-system.spawn-area", "spawn");
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("snownwcore.admin.bypassafk")) continue;
            if (plugin.extras().isAfk(p)) continue;
            if (plugin.extras().isFrozen(p.getUniqueId())) continue;
            long last = lastActivity.getOrDefault(p.getUniqueId(), now);
            if (now - last < idleMs) continue;
            // Oluşturulmuş bir spawn alanı içinde mi duruyor? (AlanService areas; NORMAL tip "spawn")
            AreaService.Area spawnArea = plugin.areas().get(spawnAreaName);
            if (spawnArea != null && !spawnArea.contains(p.getLocation())) continue;
            // AFK alanı bul: önce "afk" adlı AFK tipi alan, yoksa setafk ile 1 konum
            org.bukkit.Location target = null;
            for (AreaService.Area a : plugin.areas().all()) {
                if (a.type() == AreaService.Type.AFK) {
                    target = new org.bukkit.Location(Bukkit.getWorld(a.world()),
                            (a.minX() + a.maxX()) / 2.0 + 0.5, a.maxY(), (a.minZ() + a.maxZ()) / 2.0 + 0.5);
                    break;
                }
            }
            if (target == null) {
                Map<String, org.bukkit.Location> locs = plugin.extras().afkLocations();
                if (locs.containsKey("afk")) target = locs.get("afk");
            }
            if (target == null || target.getWorld() == null) return;
            plugin.extras().setAfk(p, true);
            p.teleport(target);
            p.sendMessage(ColorUtil.text(plugin.messages().get("afk-moved")));
        }
    }
}
