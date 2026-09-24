package me.snownw.core.gui;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.service.ShopService;
import me.snownw.core.util.ColorUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** /market — kategori + eşya sandık GUI'si (tüm istemcilerde çalışır). Sol tık: satın al, Sağ tık: sat. */
public final class ShopGui {

    public enum Page { CATEGORIES, ITEMS }

    public static final class Holder implements InventoryHolder {
        public final Page page;
        public final String category;
        public final int pageIndex;
        private final Inventory inv;
        public Holder(Page page, String category, int pageIndex, Inventory inv) {
            this.page = page; this.category = category; this.pageIndex = pageIndex; this.inv = inv;
        }
        @Override public Inventory getInventory() { return inv; }
    }

    private final SnowNWCorePlugin plugin;
    private ShopService shop() { return plugin.shop(); }

    public ShopGui(SnowNWCorePlugin plugin) { this.plugin = plugin; }

    public void openCategories(Player player) {
        net.kyori.adventure.text.Component title = ColorUtil.text("&8Market · Kategoriler");
        Inventory inv = org.bukkit.Bukkit.createInventory(null, 27, title);
        Holder h = new Holder(Page.CATEGORIES, null, 0, inv);
        int slot = 10;
        for (String cat : shop().categories()) {
            Material icon = switch (cat.toLowerCase(java.util.Locale.ROOT)) {
                case "bloklar" -> Material.BRICKS;
                case "tarım", "tarim" -> Material.WHEAT;
                case "maden" -> Material.DIAMOND_PICKAXE;
                case "gıda", "yemek" -> Material.COOKED_BEEF;
                case "kırmızıtaş" -> Material.REDSTONE;
                default -> Material.CHEST;
            };
            inv.setItem(slot++, item(icon, "&e" + cat, List.of("&7Bu kategorideki eşyaları gör")));
        }
        inv.setItem(22, item(Material.BARRIER, "&cKapat", List.of()));
        player.openInventory(inv);
        player.sendMessage(ColorUtil.text(plugin.messages().get("shop-open")));
    }

    public void openItems(Player player, String category, int pageIndex) {
        List<ShopService.Entry> entries = shop().category(category);
        int rows = 7;
        int perPage = rows * 4;
        int totalPages = Math.max(1, (entries.size() + perPage - 1) / perPage);
        int idx = Math.max(0, Math.min(pageIndex, totalPages - 1));
        net.kyori.adventure.text.Component title = ColorUtil.text("&8Market · " + category + (totalPages > 1 ? " &7(" + (idx + 1) + "/" + totalPages + ")" : ""));
        Inventory inv = org.bukkit.Bukkit.createInventory(null, 54, title);
        Holder h = new Holder(Page.ITEMS, category, idx, inv);
        int start = idx * perPage;
        int slot = 10;
        for (int i = start; i < Math.min(entries.size(), start + perPage); i++) {
            if (slot >= 44) break;
            ShopService.Entry e = entries.get(i);
            List<String> lore = new ArrayList<>();
            lore.add("&7Miktar: &f" + e.amount());
            if (e.buy() > 0) lore.add("&aSatın Al: &f" + plugin.economy().format(shop().totalBuy(e, 1)));
            if (e.sell() > 0) lore.add("&eSat: &f" + plugin.economy().format(shop().totalSell(e, 1)));
            lore.add("");
            lore.add("&7Sol tık: &a1 al · Shift+Sol: &a8 al");
            lore.add("&7Sağ tık: &e1 sat · Shift+Sağ: &e8 sat");
            ItemStack display = shop().createItem(e);
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                meta.lore(lore.stream().map(ColorUtil::text).toList());
                display.setItemMeta(meta);
            }
            inv.setItem(slot, display);
            slot++;
            if ((slot + 1) % 9 == 0) slot += 2;
        }
        inv.setItem(45, item(Material.ARROW, "&7Kategoriler", List.of()));
        if (idx > 0) inv.setItem(48, item(Material.PAPER, "&fÖnceki sayfa", List.of()));
        if (idx < totalPages - 1) inv.setItem(50, item(Material.PAPER, "&fSonraki sayfa", List.of()));
        inv.setItem(49, item(Material.BARRIER, "&cKapat", List.of()));
        player.openInventory(inv);
    }

    public ItemStack item(Material mat, String name, List<String> lore) {
        ItemStack s = new ItemStack(mat);
        ItemMeta m = s.getItemMeta();
        if (m != null) {
            m.displayName(ColorUtil.text(name));
            m.lore(lore.stream().map(ColorUtil::text).toList());
            s.setItemMeta(m);
        }
        return s;
    }

    /** Tıklama işleyici: listener'dan çağrılır. */
    public void handleClick(Player player, Holder h, int rawSlot, boolean left, boolean shift) {
        if (h.page == Page.CATEGORIES) {
            if (rawSlot == 22) { player.closeInventory(); return; }
            int slotIndex = rawSlot - 10;
            List<String> cats = new ArrayList<>(shop().categories());
            if (slotIndex < 0 || slotIndex >= cats.size()) return;
            openItems(player, cats.get(slotIndex), 0);
            return;
        }
        if (rawSlot == 45) { openCategories(player); return; }
        if (rawSlot == 49) { player.closeInventory(); return; }
        if (rawSlot == 48 && h.pageIndex > 0) { openItems(player, h.category, h.pageIndex - 1); return; }
        if (rawSlot == 50) { openItems(player, h.category, h.pageIndex + 1); return; }
        if (rawSlot < 10 || rawSlot > 43) return;
        int col = rawSlot % 9;
        if (col == 0 || col == 8) return;
        int itemIndex = ((rawSlot / 9) - 1) * 7 + (col - 1);
        int perPage = 28;
        int entryIndex = h.pageIndex * perPage + itemIndex;
        List<ShopService.Entry> entries = shop().category(h.category);
        if (entryIndex >= entries.size() || itemIndex < 0) return;
        ShopService.Entry e = entries.get(entryIndex);
        int units = shift ? 8 : 1;
        if (left) {
            if (shop().buy(player, e, units)) {
                player.sendMessage(ColorUtil.text(plugin.messages().get("shop-bought")
                        .replace("{amount}", String.valueOf(e.amount() * units))
                        .replace("{item}", e.material().name().toLowerCase(java.util.Locale.ROOT))
                        .replace("{price}", plugin.economy().format(shop().totalBuy(e, units)))));
                openItems(player, h.category, h.pageIndex);
            } else {
                player.sendMessage(ColorUtil.text(plugin.messages().get("shop-cant-buy")));
            }
        } else {
            if (shop().sell(player, e, units)) {
                player.sendMessage(ColorUtil.text(plugin.messages().get("shop-sold")
                        .replace("{amount}", String.valueOf(e.amount() * units))
                        .replace("{item}", e.material().name().toLowerCase(java.util.Locale.ROOT))
                        .replace("{price}", plugin.economy().format(shop().totalSell(e, units)))));
                openItems(player, h.category, h.pageIndex);
            } else {
                player.sendMessage(ColorUtil.text(plugin.messages().get("shop-nothing-to-sell")));
            }
        }
    }
}
