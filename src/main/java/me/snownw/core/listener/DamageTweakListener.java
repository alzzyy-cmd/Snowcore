package me.snownw.core.listener;

import me.snownw.core.SnowNWCorePlugin;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** End kristali ve diriliş çapası hasar çarpanları (V2'deki gibi nether/overworld PvP dengesi).
 *  çapa patlamaları BLOCK_EXPLOSION olarak gelir; lokasyon bazlı işaretlenir. */
public final class DamageTweakListener implements Listener {

    private final SnowNWCorePlugin plugin;
    /** patlaması muhtemel çapalar: lokasyon anahtarı -> son kullanma zamanı (ms) */
    private final Map<String, Long> anchorMarks = new ConcurrentHashMap<>();
    private static final long ANCHOR_WINDOW_MS = 5_000;

    public DamageTweakListener(SnowNWCorePlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCrystalDamage(EntityDamageByEntityEvent e) {
        if (!plugin.getConfig().getBoolean("end-crystal.enabled", true)) return;
        if (!(e.getDamager() instanceof EnderCrystal)) return;
        double mult = plugin.getConfig().getDouble("end-crystal.damage", 1.0);
        e.setDamage(e.getDamage() * mult);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnchorInteract(PlayerInteractEvent e) {
        if (!plugin.getConfig().getBoolean("respawn-anchor.enabled", true)) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b == null || b.getType() != Material.RESPAWN_ANCHOR) return;
        // Nether dışında çapaya dokunma patlamaya yol açar; konumunu işaretle
        anchorMarks.put(key(b), System.currentTimeMillis() + ANCHOR_WINDOW_MS);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplosionDamage(EntityDamageEvent e) {
        if (!plugin.getConfig().getBoolean("respawn-anchor.enabled", true)) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) return;
        if (!(e.getEntity() instanceof Player p)) return;
        long now = System.currentTimeMillis();
        for (var it = anchorMarks.entrySet().iterator(); it.hasNext();) {
            var en = it.next();
            if (en.getValue() < now) { it.remove(); continue; }
            org.bukkit.Location loc = decode(en.getKey());
            if (loc != null && loc.getWorld() == p.getWorld()
                    && loc.distanceSquared(p.getLocation()) <= 81) { // 9 blok çapı
                double mult = plugin.getConfig().getDouble("respawn-anchor.damage", 1.0);
                e.setDamage(e.getDamage() * mult);
                return;
            }
        }
    }

    private static String key(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    private static org.bukkit.Location decode(String s) {
        try {
            String[] p = s.split(";");
            org.bukkit.World w = org.bukkit.Bukkit.getWorld(p[0]);
            if (w == null) return null;
            int x = Integer.parseInt(p[1]), y = Integer.parseInt(p[2]), z = Integer.parseInt(p[3]);
            return new org.bukkit.Location(w, x + 0.5, y + 0.5, z + 0.5);
        } catch (Exception ex) { return null; }
    }
}
