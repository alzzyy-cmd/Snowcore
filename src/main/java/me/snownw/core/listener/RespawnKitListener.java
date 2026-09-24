package me.snownw.core.listener;

import me.snownw.core.SnowNWCorePlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Yeniden doğuş şefkat ekipmanı: ölünce doğunca config'deki eşyalar verilir (respawn-kit). */
public final class RespawnKitListener implements Listener {

    private final SnowNWCorePlugin plugin;

    public RespawnKitListener(SnowNWCorePlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (!plugin.getConfig().getBoolean("respawn-kit.enabled", true)) return;
        Player p = e.getPlayer();
        List<String> items = plugin.getConfig().getStringList("respawn-kit.items");
        if (items.isEmpty()) return;
        org.bukkit.inventory.Inventory inv = p.getInventory();
        for (String line : items) {
            try {
                String[] parts = line.split(":");
                Material mat = Material.matchMaterial(parts[0].trim());
                if (mat == null) continue;
                int amt = parts.length > 1 ? Math.max(1, Integer.parseInt(parts[1].trim())) : 1;
                inv.addItem(new ItemStack(mat, amt));
            } catch (Exception ignored) {}
        }
    }
}
