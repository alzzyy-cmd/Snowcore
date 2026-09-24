package me.snownw.core.listener;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.util.ColorUtil;
import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Çift zıplama: boşlukta bir kez daha boşluk ile öne itme. /çiftzipla ile açılır/kapanır. */
public final class DoubleJumpListener implements Listener {

    private final SnowNWCorePlugin plugin;
    private final Set<UUID> enabled = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastUse = new ConcurrentHashMap<>();
    private final Set<UUID> noFall = ConcurrentHashMap.newKeySet();

    public DoubleJumpListener(SnowNWCorePlugin plugin) { this.plugin = plugin; }

    public boolean enabled() { return plugin.getConfig().getBoolean("double-jump.enabled", true); }
    public boolean isOn(UUID id) { return enabled.contains(id); }

    /** Aç/kapa; yeni durum döner. Kapalıysa uçuş geri alınır. */
    public boolean toggle(Player p) {
        if (enabled.contains(p.getUniqueId())) {
            enabled.remove(p.getUniqueId());
            if (p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR) p.setAllowFlight(false);
            return false;
        }
        enabled.add(p.getUniqueId());
        if (p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR) p.setAllowFlight(true);
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // Dünya değişimi/girişinde uçuşu geri ver
        var p = e.getPlayer();
        if (enabled() && enabled.contains(p.getUniqueId())
                && p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR) {
            p.setAllowFlight(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        noFall.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();
        if (!enabled()) return;
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return;
        if (!enabled.contains(p.getUniqueId())) return;
        e.setCancelled(true);
        long cooldownMs = plugin.getConfig().getLong("double-jump.cooldown-ms", 3000);
        long now = System.currentTimeMillis();
        Long last = lastUse.get(p.getUniqueId());
        if (last != null && now - last < cooldownMs) {
            p.setAllowFlight(false);
            return;
        }
        lastUse.put(p.getUniqueId(), now);
        p.setAllowFlight(false);
        p.setFlying(false);
        double power = plugin.getConfig().getDouble("double-jump.power", 0.9);
        Vector dir = p.getLocation().getDirection().normalize().multiply(power);
        Vector v = new Vector(dir.getX(), 0.45, dir.getZ());
        p.setVelocity(v);
        noFall.add(p.getUniqueId());
        p.sendActionBar(ColorUtil.text(plugin.messages().get("doublejump-used")));
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (noFall.contains(p.getUniqueId()) && ((p.isOnGround()) || p.getLocation().getBlock().isLiquid())) {
            noFall.remove(p.getUniqueId());
            if (enabled.contains(p.getUniqueId())) p.setAllowFlight(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL && noFall.contains(p.getUniqueId())) {
            e.setCancelled(true);
            noFall.remove(p.getUniqueId());
            if (enabled.contains(p.getUniqueId())) p.setAllowFlight(true);
        }
    }
}
