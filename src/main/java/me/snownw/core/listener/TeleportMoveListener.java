package me.snownw.core.listener;

import me.snownw.core.SnowNWCorePlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public final class TeleportMoveListener implements Listener {
    private final SnowNWCorePlugin plugin;
    public TeleportMoveListener(SnowNWCorePlugin plugin) { this.plugin = plugin; }
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getWorld() != event.getTo().getWorld()) {
            if (plugin.teleports().cancel(event.getPlayer())) {
                event.getPlayer().sendMessage(me.snownw.core.util.ColorUtil.text(plugin.messages().get("home-cancelled")));
                event.getPlayer().sendActionBar(me.snownw.core.util.ColorUtil.text("&cIşınlanma iptal edildi: hareket ettin."));
            }
            plugin.rtp().cancel(event.getPlayer());
            return;
        }
        if (event.getFrom().getX() != event.getTo().getX() || event.getFrom().getZ() != event.getTo().getZ() || event.getFrom().getY() != event.getTo().getY()) {
            if (plugin.teleports().cancel(event.getPlayer())) {
                event.getPlayer().sendMessage(me.snownw.core.util.ColorUtil.text(plugin.messages().get("home-cancelled")));
                event.getPlayer().sendActionBar(me.snownw.core.util.ColorUtil.text("&cIşınlanma iptal edildi: hareket ettin."));
            }
            plugin.rtp().cancel(event.getPlayer());
        }
    }
}
