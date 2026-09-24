package me.snownw.core.service;

import me.snownw.core.SnowNWCorePlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class WorthService {

    private final SnowNWCorePlugin plugin;
    private final Map<Material, Double> prices = new HashMap<>();

    public WorthService(SnowNWCorePlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        prices.clear();
        File f = new File(plugin.getDataFolder(), "worth.yml");
        if (!f.exists()) plugin.saveResource("worth.yml", false);
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection type = cfg.getConfigurationSection("TYPE");
        if (type == null) return;
        for (String cat : type.getKeys(false)) {
            ConfigurationSection sec = type.getConfigurationSection(cat);
            if (sec == null) continue;
            for (String key : sec.getKeys(false)) {
                try {
                    Material mat = Material.valueOf(key.toUpperCase(Locale.ROOT));
                    prices.put(mat, sec.getDouble(key));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        plugin.getLogger().info("Worth loaded: " + prices.size() + " items");
    }

    public double price(Material material) {
        return prices.getOrDefault(material, plugin.getConfig().getDouble("worth.default-value", 0.0));
    }

    public boolean hasWorth(Material material) {
        return prices.containsKey(material) && prices.get(material) > 0;
    }

    public java.util.Map<Material, Double> allPrices() {
        return java.util.Collections.unmodifiableMap(prices);
    }

    public double total(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return 0;
        return price(stack.getType()) * stack.getAmount();
    }
}
