package me.snownw.core.service;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.util.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TeleportService {

    private final SnowNWCorePlugin plugin;
    private final Map<UUID, BukkitTask> tasks = new ConcurrentHashMap<>();
    private final Map<UUID, Location> startLoc = new ConcurrentHashMap<>();

    public TeleportService(SnowNWCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void teleportNow(Player player, Location dest, String label) {
        if (plugin.combat() != null && plugin.combat().isInCombat(player)) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("combat-command-blocked")));
            return;
        }
        cancel(player);
        player.teleport(dest);
        playXp(player);
        notifyDone(player, label);
    }

    public void teleport(Player player, Location dest, String homeName) {
        teleport(player, dest, homeName, "default");
    }

    /** type: home, team-home, spawn, afk, tpa, warp, rtp, default — teleport-delays.* altından okunur. */
    public void teleport(Player player, Location dest, String homeName, String type) {
        if (type == null || type.isBlank()) type = "default";
        if (plugin.combat() != null && plugin.combat().isInCombat(player)) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("combat-command-blocked")));
            return;
        }
        cancel(player);
        int delay = delayFor(type);
        if (delay <= 0 || player.hasPermission("snownwcore.admin.nodelay")) {
            teleportNow(player, dest, homeName);
            return;
        }

        startLoc.put(player.getUniqueId(), player.getLocation().clone());
        BukkitTask task = new BukkitRunnable() {
            int left = delay;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancelTask(player.getUniqueId());
                    return;
                }
                if (plugin.getConfig().getBoolean("teleport.cancel-on-move", true)) {
                    Location s = startLoc.get(player.getUniqueId());
                    if (s != null && (s.getWorld() != player.getWorld()
                            || s.distanceSquared(player.getLocation()) > 0.3)) {
                        player.sendActionBar(Component.empty());
                        if (plugin.getConfig().getBoolean("teleport.chat-messages", true)) {
                            player.sendMessage(ColorUtil.text(msg("home-cancelled")));
                        }
                        cancelTask(player.getUniqueId());
                        return;
                    }
                }
                if (left <= 0) {
                    player.teleport(dest);
                    playXp(player);
                    notifyDone(player, homeName);
                    cancelTask(player.getUniqueId());
                    return;
                }
                String countMsg = msg("teleport-countdown").replace("{sec}", String.valueOf(left))
                        .replace("{name}", homeName == null ? "" : homeName);
                if (plugin.getConfig().getBoolean("teleport.actionbar-messages", true)) {
                    player.sendActionBar(ColorUtil.text(countMsg));
                }
                if (plugin.getConfig().getBoolean("teleport.chat-countdown", false)) {
                    player.sendMessage(ColorUtil.text(countMsg));
                }
                // soft tick
                try {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f, 1.2f);
                } catch (Exception ignored) {}
                left--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
        tasks.put(player.getUniqueId(), task);
    }

    private void notifyDone(Player player, String label) {
        String text = msg("teleport-done").replace("{name}", label == null ? "" : label);
        if (plugin.getConfig().getBoolean("teleport.chat-messages", true)) {
            player.sendMessage(ColorUtil.text(text));
        }
        if (plugin.getConfig().getBoolean("teleport.actionbar-messages", true)) {
            player.sendActionBar(ColorUtil.text(text));
        }
    }

    private void playXp(Player player) {
        String sound = plugin.getConfig().getString("teleport.sound", "ENTITY_PLAYER_LEVELUP");
        float vol = (float) plugin.getConfig().getDouble("teleport.sound-volume", 0.7);
        float pitch = (float) plugin.getConfig().getDouble("teleport.sound-pitch", 1.2);
        try {
            player.playSound(player.getLocation(), Sound.valueOf(sound), vol, pitch);
        } catch (Exception e) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, vol, pitch);
        }
    }

    private int delayFor(String type) {
        int specific = plugin.getConfig().getInt("teleport-delays." + type, -1);
        if (specific >= 0) return specific;
        return Math.max(0, plugin.getConfig().getInt("homes.teleport-delay-seconds",
                plugin.getConfig().getInt("teleport-delay-seconds", 5)));
    }

    private String msg(String key) {
        if (plugin.messages() != null) {
            String m = plugin.messages().plain(key);
            if (m != null && !m.equals(key)) return m;
            return plugin.messages().get(key);
        }
        return key;
    }

    public boolean cancel(Player player) {
        UUID id = player.getUniqueId();
        boolean had = tasks.containsKey(id);
        cancelTask(id);
        return had;
    }

    private void cancelTask(UUID id) {
        BukkitTask t = tasks.remove(id);
        if (t != null) t.cancel();
        startLoc.remove(id);
    }
}
