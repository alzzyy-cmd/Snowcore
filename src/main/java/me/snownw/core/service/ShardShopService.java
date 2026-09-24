package me.snownw.core.service;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.util.ColorUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Shard pazarı: shard-shop.yml içindeki ödüller shard karşılığında satın alınır. */
public final class ShardShopService {

    public record Entry(String id, Material icon, String name, List<String> lore, long price, List<String> items, List<String> commands) {}

    private final SnowNWCorePlugin plugin;
    private final File file;
    private YamlConfiguration cfg;
    private final List<Entry> entries = new ArrayList<>();

    public ShardShopService(SnowNWCorePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "shard-shop.yml");
        reload();
    }

    public void reload() {
        if (!file.exists()) plugin.saveResource("shard-shop.yml", false);
        cfg = YamlConfiguration.loadConfiguration(file);
        entries.clear();
        ConfigurationSection root = cfg.getConfigurationSection("items");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            try {
                Material icon = Material.matchMaterial(s.getString("icon", "CHEST"));
                if (icon == null) icon = Material.CHEST;
                String name = s.getString("name", id);
                List<String> lore = s.getStringList("lore");
                long price = s.getLong("price-shards", 0);
                if (price <= 0) continue;
                entries.add(new Entry(id, icon, name, lore, price,
                        s.getStringList("items"), s.getStringList("commands")));
            } catch (Exception ignored) {}
        }
    }

    public boolean enabled() { return cfg.getBoolean("enabled", true); }
    public String title() { return cfg.getString("title", "&8Shard Pazarı"); }
    public List<Entry> entries() { return new ArrayList<>(entries); }

    public Entry byId(String id) {
        for (Entry e : entries) if (e.id().equalsIgnoreCase(id)) return e;
        return null;
    }

    /** Satın al: shard düşer, ödüller verilir. Başarılıysa true. */
    public boolean purchase(Player player, Entry e) {
        if (!enabled()) return false;
        if (!plugin.shards().take(player.getUniqueId(), e.price())) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("shardshop-insufficient")
                    .replace("{price}", String.valueOf(e.price()))
                    .replace("{have}", String.valueOf(plugin.shards().get(player.getUniqueId())))));
            return false;
        }
        for (String it : e.items()) {
            try {
                String[] parts = it.split(":");
                Material mat = Material.matchMaterial(parts[0]);
                if (mat == null) continue;
                int amt = parts.length > 1 ? Math.max(1, Integer.parseInt(parts[1])) : 1;
                player.getInventory().addItem(new ItemStack(mat, amt));
            } catch (Exception ignored) {}
        }
        for (String cmd : e.commands()) {
            String c = cmd.replace("%player%", player.getName()).replace("{player}", player.getName());
            if (c.startsWith("/")) c = c.substring(1);
            org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), c);
        }
        player.sendMessage(ColorUtil.text(plugin.messages().get("shardshop-bought")
                .replace("{item}", e.name())
                .replace("{price}", String.valueOf(e.price()))));
        return true;
    }
}
