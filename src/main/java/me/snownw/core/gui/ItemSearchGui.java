package me.snownw.core.gui;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.util.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Pick an item icon for menu (config menu-icon). */
public final class ItemSearchGui implements InventoryHolder {

    private static final int SIZE = 54;
    private static final int PER = 45;

    private final SnowNWCorePlugin plugin;
    private final Player player;
    private final Inventory inv;
    private final List<Material> all;
    private List<Material> filtered;
    private int page;
    private String query = "";

    public ItemSearchGui(SnowNWCorePlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.all = Arrays.stream(Material.values())
                .filter(Material::isItem)
                .filter(m -> !m.isAir())
                .sorted((a, b) -> a.name().compareTo(b.name()))
                .collect(Collectors.toList());
        this.filtered = new ArrayList<>(all);
        this.inv = Bukkit.createInventory(this, SIZE, ColorUtil.text("&8Item Search"));
        rebuild();
    }

    @Override
    public Inventory getInventory() { return inv; }

    public void open() {
        rebuild();
        player.openInventory(inv);
    }

    public void setQuery(String q) {
        this.query = q == null ? "" : q.toUpperCase(Locale.ROOT);
        this.page = 0;
        if (this.query.isEmpty()) {
            filtered = new ArrayList<>(all);
        } else {
            filtered = all.stream().filter(m -> m.name().contains(query)).collect(Collectors.toList());
        }
        rebuild();
    }

    public void next() {
        if (page < maxPage()) { page++; rebuild(); }
    }

    public void prev() {
        if (page > 0) { page--; rebuild(); }
    }

    public void select(int slot) {
        if (slot < 0 || slot >= PER) return;
        int idx = page * PER + slot;
        if (idx >= filtered.size()) return;
        Material mat = filtered.get(idx);
        plugin.getConfig().set("menu-icon", mat.name());
        plugin.saveConfig();
        player.sendMessage(ColorUtil.text("&aMenu icon set to &f" + mat.name()));
        player.closeInventory();
    }

    private int maxPage() {
        if (filtered.isEmpty()) return 0;
        return (filtered.size() - 1) / PER;
    }

    private void rebuild() {
        inv.clear();
        int start = page * PER;
        for (int i = 0; i < PER; i++) {
            int idx = start + i;
            if (idx >= filtered.size()) break;
            Material m = filtered.get(idx);
            ItemStack item = new ItemStack(m);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(ColorUtil.text("&f" + m.name()));
                meta.lore(List.of(ColorUtil.text("&7Tıkla to set as menu icon")));
                item.setItemMeta(meta);
            }
            inv.setItem(i, item);
        }
        inv.setItem(45, ctrl(Material.ARROW, "&aPrevious"));
        inv.setItem(49, ctrl(Material.COMPASS, "&eFiltre: &f" + (query.isEmpty() ? "*" : query)
                + "\n&7/itemsearch <name>"));
        inv.setItem(53, ctrl(Material.ARROW, "&aNext"));
    }

    private ItemStack ctrl(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String[] lines = name.split("\n");
            meta.displayName(ColorUtil.text(lines[0]));
            if (lines.length > 1) {
                List<Component> lore = new ArrayList<>();
                for (int i = 1; i < lines.length; i++) lore.add(ColorUtil.text(lines[i]));
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
