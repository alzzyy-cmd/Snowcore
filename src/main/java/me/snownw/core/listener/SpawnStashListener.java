package me.snownw.core.listener;

import me.snownw.core.SnowNWCorePlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/** Lightweight proximity alert for temporary fake stashes. */
public final class SpawnStashListener implements Listener {
    private final SnowNWCorePlugin plugin;
    public SpawnStashListener(SnowNWCorePlugin plugin) { this.plugin = plugin; }

    @EventHandler(ignoreCancelled = true)
    public void move(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        plugin.spawnStash().handleApproach(event.getPlayer());
    }
}
