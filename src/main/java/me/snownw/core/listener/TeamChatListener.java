package me.snownw.core.listener;

import me.snownw.core.SnowNWCorePlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public final class TeamChatListener implements Listener {
    private final SnowNWCorePlugin plugin;
    public TeamChatListener(SnowNWCorePlugin plugin){this.plugin=plugin;}
    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void onChat(AsyncPlayerChatEvent e){
        if (plugin.offenses() != null && plugin.offenses().isMuted(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(me.snownw.core.util.ColorUtil.text("&cSohbet kullanımın kısıtlı."));
            return;
        }
        if(!plugin.teams().isTeamChatEnabled(e.getPlayer().getUniqueId())) return;
        e.setCancelled(true);
        plugin.getServer().getScheduler().runTask(plugin,()->plugin.teams().sendTeamChat(e.getPlayer(),e.getMessage()));
    }
}
