package me.snownw.core.gui;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.service.ShardShopService;
import me.snownw.core.util.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** /shardmarket — shard-shop.yml içindeki ödülleri listeleyen sandık GUI'si. */
public final class ShardShopGui {

    public static final class Holder implements InventoryHolder {
        public final int pageIndex;
        private final Inventory inv;
        public Holder(int pageIndex, Inventory inv) { this.pageIndex = pageIndex; this.inv = inv; }
        @Override public Inventory getInventory() { return inv; }
    }

    private final SnowNWCorePlugin plugin;

    public ShardShopGui(SnowNWCorePlugin plugin) { this.plugin = plugin; }

    public void open(Player player, int pageIndex) {
        List<ShardShopService.Entry> entries = plugin.shardShop().entries();
        int perPage = 21;
        int totalPages = Math.max(1, (entries.size() + perPage - 1) / perPage);
        int idx = Math.max(0, Math.min(pageIndex, totalPages - 1));
        Component title = ColorUtil.text(plugin.shardShop().title() + (totalPages > 1 ? " &7(" + (idx + 1) + "/" + totalPages + ")" : ""));
        Inventory inv = org.bukkit.Bukkit.createInventory(null, 27, title);
        Holder h = new Holder(idx, inv);

        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        int start = idx * perPage;
        for (int i = start; i < Math.min(entries.size(), start + perPage); i++) {
            int k = i - start; if (k >= slots.length) break;
            ShardShopService.Entry e = entries.get(i);
            ItemStack s = new ItemStack(e.icon());
            ItemMeta m = s.getItemMeta();
            if (m != null) {
                m.displayName(ColorUtil.text(e.name()));
                List<Component> lore = new ArrayList<>();
                for (String l : e.lore()) lore.add(ColorUtil.text(l));
                lore.add(Component.empty());
                lore.add(ColorUtil.text("&dFiyat: &f" + e.price() + " Shard"));
                lore.add(ColorUtil.text("&7Bakiye: &f" + plugin.shards().get(player.getUniqueId()) + " Shard"));
                lore.add(ColorUtil.text("&bTıkla: satın al"));
                m.lore(lore);
                s.setItemMeta(m);
            }
            inv.setItem(slots[k], s);
        }
        if (idx > 0) inv.setItem(18, make(Material.PAPER, "&fÖnceki sayfa"));
        if (idx < totalPages - 1) inv.setItem(17, make(Material.PAPER, "&fSonraki sayfa"));
        inv.setItem(0, make(Material.BARRIER, "&cKapat"));
        player.openInventory(inv);
    }

    public void handleClick(Player player, Holder h, int rawSlot) {
        if (rawSlot == 0) { player.closeInventory(); return; }
        if (rawSlot == 18 && h.pageIndex > 0) { open(player, h.pageIndex - 1); return; }
        if (rawSlot == 17) { open(player, h.pageIndex + 1); return; }
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        int slotIndex = -1;
        for (int i = 0; i < slots.length; i++) if (slots[i] == rawSlot) { slotIndex = i; break; }
        if (slotIndex < 0) return;
        List<ShardShopService.Entry> entries = plugin.shardShop().entries();
        int perPage = 21;
        int idx = h.pageIndex * perPage + slotIndex;
        if (idx >= entries.size()) return;
        if (plugin.shardShop().purchase(player, entries.get(idx))) open(player, h.pageIndex);
    }

    private ItemStack make(Material mat, String name) {
        ItemStack s = new ItemStack(mat);
        ItemMeta m = s.getItemMeta();
        if (m != null) { m.displayName(ColorUtil.text(name)); s.setItemMeta(m); }
        return s;
    }
}
