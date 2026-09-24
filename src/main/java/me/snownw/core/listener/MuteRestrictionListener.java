package me.snownw.core.listener;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.util.ColorUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;

/** Prevents muted players from writing to signs (including hanging signs). */
public final class MuteRestrictionListener implements Listener {
    private final SnowNWCorePlugin plugin;

    public MuteRestrictionListener(SnowNWCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        if (plugin.offenses() == null || !plugin.offenses().isMuted(player.getUniqueId())) return;
        event.setCancelled(true);
        player.sendMessage(ColorUtil.text("&cSohbet kullanımın kısıtlı. Tabelaya yazı yazamazsın."));
    }
}
