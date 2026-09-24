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
        }
    }

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
