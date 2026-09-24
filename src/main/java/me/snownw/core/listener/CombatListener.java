package me.snownw.core.listener;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.util.ColorUtil;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Locale;

public final class CombatListener implements Listener {
    private final SnowNWCorePlugin plugin;

    public CombatListener(SnowNWCorePlugin plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player victim = asPlayer(event.getEntity());
        Player attacker = attacker(event.getDamager());
        if (victim == null || attacker == null || attacker.equals(victim)) return;
        plugin.combat().tag(attacker, victim);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        plugin.combat().end(event.getEntity().getUniqueId(), true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.combat().isInCombat(event.getPlayer())) {
            event.getPlayer().sendMessage(ColorUtil.text(plugin.messages().get("combat-logout")));
            plugin.combat().end(event.getPlayer().getUniqueId(), true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!plugin.combat().isInCombat(player)) return;

        String raw = event.getMessage();
        if (raw == null || raw.length() < 2 || raw.charAt(0) != '/') return;
        String command = raw.substring(1).trim().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        String base = command.contains(":") ? command.substring(command.indexOf(':') + 1) : command;

        if (plugin.getConfig().getStringList("combat.blocked-commands").stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .anyMatch(base::equals)) {
            event.setCancelled(true);
            player.sendMessage(ColorUtil.text(plugin.messages().get("combat-command-blocked")));
        }
    }

    private Player asPlayer(Entity entity) {
        return entity instanceof Player p ? p : null;
    }

    private Player attacker(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player p) return p;
        return null;
    }
}
