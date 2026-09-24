package me.snownw.core.listener;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.data.SettingsStore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Hide blacklist mobs for player: client hide, no target, no damage, remove nearby, silence.
 */
public final class MobHideListener implements Listener {

    private final SnowNWCorePlugin plugin;
    private final Set<org.bukkit.entity.EntityType> blacklist = new HashSet<>();

    public MobHideListener(SnowNWCorePlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        blacklist.clear();
        for (String s : plugin.getConfig().getStringList("mob-hide.blacklist")) {
            try {
                blacklist.add(org.bukkit.entity.EntityType.valueOf(s.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public boolean isHiddenFor(Player player, Entity entity) {
        if (!plugin.getConfig().getBoolean("mob-hide.enabled", true)) return false;
        if (entity == null || entity instanceof Player) return false;
        if (!blacklist.contains(entity.getType())) return false;
        return plugin.settings().get(player.getUniqueId(), SettingsStore.Toggle.HIDE_MOBS);
    }

    public void apply(Player player) {
        if (!plugin.getConfig().getBoolean("mob-hide.enabled", true)) return;
        boolean hide = plugin.settings().get(player.getUniqueId(), SettingsStore.Toggle.HIDE_MOBS);
        double radius = plugin.getConfig().getDouble("mob-hide.remove-radius", 48);
        boolean removeNearby = plugin.getConfig().getBoolean("mob-hide.remove-nearby", true);

        for (Entity e : player.getWorld().getEntities()) {
            if (!(e instanceof LivingEntity) || e instanceof Player) continue;
            if (!blacklist.contains(e.getType())) continue;

            if (hide) {
                player.hideEntity(plugin, e);
                if (e instanceof Mob mob && mob.getTarget() instanceof Player t
                        && t.getUniqueId().equals(player.getUniqueId())) {
                    mob.setTarget(null);
                }
                // silence entity globally when remove-nearby is used we remove instead
                try {
                    e.setSilent(true);
                } catch (Throwable ignored) {}

                if (removeNearby && e.getLocation().distanceSquared(player.getLocation()) <= radius * radius) {
                    e.remove();
                }
            } else {
                player.showEntity(plugin, e);
                try {
                    e.setSilent(false);
                } catch (Throwable ignored) {}
            }
        }

        // Not: stopAllSounds() tüm sesleri (müzik, portallar dahil) keser; yaratık sesleri
        // gizlemek için varlık gizlenirken setSilent(true) uygulanır — yeterlidir.
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> apply(e.getPlayer()), 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!e.getPlayer().isOnline()) {
                task.cancel();
                return;
            }
            if (plugin.settings().get(e.getPlayer().getUniqueId(), SettingsStore.Toggle.HIDE_MOBS)) {
                apply(e.getPlayer());
            }
        }, 40L, 40L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (!blacklist.contains(e.getEntityType())) return;
        // hide for all players with setting after spawn
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player p : e.getEntity().getWorld().getPlayers()) {
                if (plugin.settings().get(p.getUniqueId(), SettingsStore.Toggle.HIDE_MOBS)) {
                    p.hideEntity(plugin, e.getEntity());
                    try { e.getEntity().setSilent(true); } catch (Throwable ignored) {}
                    double radius = plugin.getConfig().getDouble("mob-hide.remove-radius", 48);
                    if (plugin.getConfig().getBoolean("mob-hide.remove-nearby", true)
                            && e.getEntity().getLocation().distanceSquared(p.getLocation()) <= radius * radius) {
                        e.getEntity().remove();
                        return;
                    }
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (!(e.getTarget() instanceof Player player)) return;
        if (isHiddenFor(player, e.getEntity())) {
            e.setCancelled(true);
            e.setTarget(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget2(EntityTargetEvent e) {
        if (!(e.getTarget() instanceof Player player)) return;
        if (isHiddenFor(player, e.getEntity())) {
            e.setCancelled(true);
            e.setTarget(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player player) {
            Entity damager = e.getDamager();
            if (damager instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof Entity shooter) {
                damager = shooter;
            }
            if (isHiddenFor(player, damager)) {
                e.setCancelled(true);
            }
        }
    }
}
