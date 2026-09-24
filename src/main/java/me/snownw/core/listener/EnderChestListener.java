package me.snownw.core.listener;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.service.EnderChestService;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;

public final class EnderChestListener implements Listener {

    private final SnowNWCorePlugin plugin;

    public EnderChestListener(SnowNWCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b == null || b.getType() != Material.ENDER_CHEST) return;
        if (!plugin.getConfig().getBoolean("enderchest.enabled", true)) return;
        // Always use expanded GUI for physical EC
        e.setCancelled(true);
        plugin.enderChest().open(e.getPlayer());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;
        Inventory inv = e.getInventory();
        if (!(inv.getHolder() instanceof EnderChestService.Holder holder)) return;
        plugin.enderChest().saveFrom(inv, holder.owner());
        plugin.enderChest().markClose(player);
        try {
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ENDER_CHEST_CLOSE, 1f, 1f);
        } catch (Throwable ignored) {}
    }
}
