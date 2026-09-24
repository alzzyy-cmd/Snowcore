package me.snownw.core.listener;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.util.ColorUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * When player enters the cuboid named "rtpzone", start RTP (SnowNW RTP-style zone trigger).
 */
public final class RtpZoneListener implements Listener {
    private final SnowNWCorePlugin plugin;
    private final Map<UUID, Boolean> wasInside = new ConcurrentHashMap<>();

    public RtpZoneListener(SnowNWCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (!plugin.getConfig().getBoolean("rtp-zone.enabled", true)) return;
        if (e.getTo() == null) return;
        // only block changes
        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
                && e.getFrom().getBlockY() == e.getTo().getBlockY()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;

        Player player = e.getPlayer();
        boolean inside = plugin.cuboids().isInRtpZone(e.getTo());
        boolean was = wasInside.getOrDefault(player.getUniqueId(), false);
        wasInside.put(player.getUniqueId(), inside);

        if (inside && !was) {
            // entered rtpzone
            if (!plugin.getConfig().getBoolean("rtp-zone.trigger-on-enter", true)) return;
            if (!player.hasPermission("snownwcore.command.rtp") && !player.hasPermission("snownwcore.rtp.zone")) return;
            String dest = plugin.getConfig().getString("rtp-zone.destination-world", "world");
            player.sendMessage(ColorUtil.text(plugin.messages().get("rtpzone-enter")));
            plugin.rtp().rtp(player, dest);
        }
    }
}
