package me.snownw.core.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class ItemSearchListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof ItemSearchGui gui)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == null || !(e.getClickedInventory().getHolder() instanceof ItemSearchGui)) return;
        int slot = e.getRawSlot();
        if (slot == 45) gui.prev();
        else if (slot == 53) gui.next();
        else if (slot >= 0 && slot < 45) gui.select(slot);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof ItemSearchGui) e.setCancelled(true);
    }
}
