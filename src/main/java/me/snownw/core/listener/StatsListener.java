package me.snownw.core.listener;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.data.StatsStore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StatsListener implements Listener {

    private final SnowNWCorePlugin plugin;
    private final Map<UUID, BukkitTask> playTasks = new ConcurrentHashMap<>();

    public StatsListener(SnowNWCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (e.getPlayer().isOnline()) {
                plugin.stats().add(id, StatsStore.Type.PLAYTIME, 1);
            }
        }, 20L, 20L); // every second
        playTasks.put(id, task);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        BukkitTask t = playTasks.remove(e.getPlayer().getUniqueId());
        if (t != null) t.cancel();
        plugin.stats().save();
        plugin.homes().save();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        plugin.stats().add(victim.getUniqueId(), StatsStore.Type.DEATHS, 1);
        Player killer = victim.getKiller();
        if (killer != null) {
            plugin.stats().add(killer.getUniqueId(), StatsStore.Type.KILLS, 1);
            // Kafa ödülü: hedefin başına ödül varsa katil alır
            if (plugin.bounty() != null) {
                try { plugin.bounty().claim(killer, victim); } catch (Throwable ignored) {}
            }
            // Öldürme başına shard (cooldown ile spam korumalı)
            if (plugin.getConfig().getBoolean("shards.cells-on-kill", true) && plugin.shards() != null) {
                long perKill = plugin.getConfig().getLong("shards.per-kill", 10);
                long cooldownSec = plugin.getConfig().getLong("shards.kill-cooldown-seconds", 120);
                String key = killer.getUniqueId() + ":" + victim.getUniqueId();
                long now = System.currentTimeMillis();
                Long last = SHARD_COOLDOWNS.get(key);
                if (last == null || now - last >= cooldownSec * 1000L) {
                    SHARD_COOLDOWNS.put(key, now);
                    plugin.shards().add(killer.getUniqueId(), perKill);
                    killer.sendActionBar(me.snownw.core.util.ColorUtil.text(plugin.messages().get("shard-kill-gain")
                            .replace("{amount}", String.valueOf(perKill))));
                }
            }
        }
    }

    private static final java.util.Map<String, Long> SHARD_COOLDOWNS = new java.util.concurrent.ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        plugin.stats().add(e.getPlayer().getUniqueId(), StatsStore.Type.BLOCKS, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (e.getPlayer() == null) return;
        plugin.stats().add(e.getPlayer().getUniqueId(), StatsStore.Type.PLACED, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent e) {
        if (e.getEntity() instanceof Player) return;
        Player killer = e.getEntity().getKiller();
        if (killer != null) {
            plugin.stats().add(killer.getUniqueId(), StatsStore.Type.MOBS, 1);
        }
    }
}
