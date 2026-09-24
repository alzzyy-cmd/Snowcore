package me.snownw.core.listener;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.data.SettingsStore;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/** Applies PvP-related settings from Ayarlar menu. */
public final class PvpSettingsListener implements Listener {

    private final SnowNWCorePlugin plugin;

    public PvpSettingsListener(SnowNWCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;

        // Crystal damage – respect Fast Crystal preference as optional mitigation marker
        if (e.getDamager() instanceof EnderCrystal) {
            // no full cancel – crystals still work; client particles controlled elsewhere
            return;
        }

        Player attacker = null;
        if (e.getDamager() instanceof Player p) attacker = p;
        else if (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) attacker = p;
        if (attacker == null) return;
        var ta = plugin.teams().get(attacker);
        var tv = plugin.teams().get(victim);
        if (ta != null && tv != null && ta.id().equals(tv.id()) && !ta.friendlyFire()) {
            e.setCancelled(true);
            attacker.sendActionBar(net.kyori.adventure.text.Component.text("Takım arkadaşına hasar veremezsin."));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (!(e.getEntity() instanceof EnderCrystal crystal)) return;
        for (Player p : crystal.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(crystal.getLocation()) > 256) continue;
            if (!plugin.settings().get(p.getUniqueId(), SettingsStore.Toggle.EXPLOSION_SOUNDS)) {
                try { p.stopAllSounds(); } catch (Throwable ignored) {}
            }
        }
    }
}
