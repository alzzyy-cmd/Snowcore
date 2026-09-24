package me.snownw.core.gui;

import me.snownw.core.SnowNWCorePlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

/** Market (ShopGui) ve Shard Pazarı (ShardShopGui) sandık GUI'lerinin tıklama dinleyicisi. */
public final class ShopGuiListener implements Listener {

    private final SnowNWCorePlugin plugin;

    public ShopGuiListener(SnowNWCorePlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory inv = e.getInventory();
        if (!(e.getWhoClicked() instanceof Player player)) return;

        if (inv.getHolder() instanceof ShopGui.Holder sh) {
            e.setCancelled(true);
            if (e.getRawSlot() >= inv.getSize()) return;
            boolean left = e.isLeftClick();
            boolean shift = e.isShiftClick();
            plugin.shopGui().handleClick(player, sh, e.getRawSlot(), left, shift);
            return;
        }
        if (inv.getHolder() instanceof ShardShopGui.Holder hh) {
            e.setCancelled(true);
            if (e.getRawSlot() >= inv.getSize()) return;
            plugin.shardShopGui().handleClick(player, hh, e.getRawSlot());
        }
    }
}
