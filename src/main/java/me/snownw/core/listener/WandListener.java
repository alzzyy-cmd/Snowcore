package me.snownw.core.listener;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.util.ColorUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public final class WandListener implements Listener {
    private final SnowNWCorePlugin plugin;
    public WandListener(SnowNWCorePlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void on(PlayerInteractEvent e) {
        if (e.getItem() == null || (!plugin.areas().isWand(e.getItem()) && !plugin.cuboids().isWand(e.getItem()))) return;
        if (e.getClickedBlock() == null) return;
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            e.setCancelled(true);
            plugin.areas().setPos1(e.getPlayer(), e.getClickedBlock().getLocation());
            plugin.cuboids().setPos1(e.getPlayer(), e.getClickedBlock().getLocation());
            e.getPlayer().sendMessage(ColorUtil.text("&bAlan &f1. konum &7seçildi."));
        } else if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            e.setCancelled(true);
            plugin.areas().setPos2(e.getPlayer(), e.getClickedBlock().getLocation());
            plugin.cuboids().setPos2(e.getPlayer(), e.getClickedBlock().getLocation());
            e.getPlayer().sendMessage(ColorUtil.text("&bAlan &f2. konum &7seçildi."));
        }
    }
}
