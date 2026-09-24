package me.snownw.core.service;

import me.snownw.core.SnowNWCorePlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class ShopService {
    public enum Currency { MONEY, SHARDS }
    public record Entry(String id, String category, Material material, int amount, double buy, double sell, Currency currency, EntityType spawnerType) {}

    private final SnowNWCorePlugin plugin;
    private final File file;
    private YamlConfiguration data;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final NamespacedKey spawnerKey;

    public ShopService(SnowNWCorePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "shop.yml");
        this.spawnerKey = new NamespacedKey(plugin, "spawner_type");
        load();
    }

    public void load() {
        if (!file.exists()) plugin.saveResource("shop.yml", false);
        data = YamlConfiguration.loadConfiguration(file);
        entries.clear();
        ConfigurationSection root = data.getConfigurationSection("items");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            Material mat;
            try { mat = Material.valueOf(s.getString("material", "STONE").toUpperCase(Locale.ROOT)); }
            catch (Exception e) { continue; }
            EntityType spawner = null;
            String type = s.getString("spawner-type");
            if (type != null) try { spawner = EntityType.valueOf(type.toUpperCase(Locale.ROOT)); } catch (Exception ignored) {}
            Currency currency;
            try { currency = Currency.valueOf(s.getString("currency", "MONEY").toUpperCase(Locale.ROOT)); }
            catch (Exception e) { currency = Currency.MONEY; }
            entries.put(id.toLowerCase(Locale.ROOT), new Entry(id, s.getString("category", "genel"), mat,
                    Math.max(1, s.getInt("amount", 1)), Math.max(0, s.getDouble("buy", 0)),
                    Math.max(0, s.getDouble("sell", 0)), currency, spawner));
        }
    }

    public Collection<Entry> entries() { return entries.values(); }
    public Entry get(String id) { return entries.get(id.toLowerCase(Locale.ROOT)); }
    public Set<String> categories() {
        Set<String> out = new LinkedHashSet<>();
        for (Entry e : entries.values()) out.add(e.category());
        return out;
    }
    public List<Entry> category(String category) {
        return entries.values().stream().filter(e -> e.category().equalsIgnoreCase(category)).toList();
    }

    public ItemStack createItem(Entry e) {
        ItemStack item = new ItemStack(e.material(), e.amount());
        ItemMeta meta = item.getItemMeta();
        if (e.spawnerType() != null) {
            meta.getPersistentDataContainer().set(spawnerKey, PersistentDataType.STRING, e.spawnerType().name());
            meta.displayName(me.snownw.core.util.ColorUtil.text("&b" + pretty(e.spawnerType().name()) + " Spawner"));
        }
        item.setItemMeta(meta);
        return item;
    }

    public boolean isSpawnerItem(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(spawnerKey, PersistentDataType.STRING);
    }

    public EntityType spawnerType(ItemStack item) {
        if (!isSpawnerItem(item)) return null;
        String s = item.getItemMeta().getPersistentDataContainer().get(spawnerKey, PersistentDataType.STRING);
        try { return EntityType.valueOf(s); } catch (Exception e) { return null; }
    }

    public double totalBuy(Entry e, int units) { return e.buy() * Math.max(1, units) * plugin.getConfig().getDouble("rates.buy", 1.0); }
    public double totalSell(Entry e, int units) { return e.sell() * Math.max(1, units) * plugin.getConfig().getDouble("rates.sell", 1.0); }

    public boolean buy(org.bukkit.entity.Player player, Entry e, int units) {
        units = Math.max(1, units);
        double cost = totalBuy(e, units);
        if (cost <= 0) return false;
        ItemStack item = createItem(e);
        int amount = Math.max(1, e.amount() * units);
        if (!canFit(player, item, amount)) return false;

        boolean paid = e.currency() == Currency.SHARDS
                ? plugin.shards().take(player.getUniqueId(), (long) Math.ceil(cost))
                : plugin.economy().withdraw(player, cost);
        if (!paid) return false;

        item.setAmount(amount);
        Map<Integer, ItemStack> left = player.getInventory().addItem(item);
        // canFit() guarantees this is empty; never drop purchased items on the ground.
        if (!left.isEmpty()) {
            if (e.currency() == Currency.SHARDS) plugin.shards().add(player.getUniqueId(), (long) Math.ceil(cost));
            else plugin.economy().deposit(player, cost);
            return false;
        }
        return true;
    }


    /** Exact storage-capacity check for the item that will be purchased. */
    private boolean canFit(org.bukkit.entity.Player player, ItemStack prototype, int amount) {
        int remaining = amount;
        ItemStack[] storage = player.getInventory().getStorageContents();
        int max = Math.max(1, prototype.getMaxStackSize());

        for (ItemStack existing : storage) {
            if (existing == null || existing.getType().isAir()) continue;
            if (!existing.isSimilar(prototype)) continue;
            remaining -= Math.max(0, max - existing.getAmount());
            if (remaining <= 0) return true;
        }

        for (ItemStack existing : storage) {
            if (existing == null || existing.getType().isAir()) {
                remaining -= max;
                if (remaining <= 0) return true;
            }
        }
        return false;
    }
    public boolean sell(org.bukkit.entity.Player player, Entry e, int units) {
        units = Math.max(1, units);
        int wanted = e.amount() * units;
        if (e.sell() <= 0 || !hasItems(player, e, wanted)) return false;
        removeItems(player, e, wanted);
        if (e.currency() == Currency.SHARDS) plugin.shards().add(player.getUniqueId(), (long) Math.floor(totalSell(e, units)));
        else plugin.economy().deposit(player, totalSell(e, units));
        return true;
    }

    private boolean hasItems(org.bukkit.entity.Player p, Entry e, int amount) {
        int count = 0;
        for (ItemStack i : p.getInventory().getContents()) if (matches(i, e)) count += i.getAmount();
        return count >= amount;
    }
    private void removeItems(org.bukkit.entity.Player p, Entry e, int amount) {
        for (int slot = 0; slot < p.getInventory().getSize() && amount > 0; slot++) {
            ItemStack i = p.getInventory().getItem(slot);
            if (!matches(i, e)) continue;
            int take = Math.min(amount, i.getAmount());
            i.setAmount(i.getAmount() - take);
            amount -= take;
            if (i.getAmount() <= 0) p.getInventory().setItem(slot, null);
        }
    }
    private boolean matches(ItemStack item, Entry e) {
        if (item == null || item.getType() != e.material()) return false;
        if (e.spawnerType() == null) return true;
        return Objects.equals(spawnerType(item), e.spawnerType());
    }
    private String pretty(String raw) { return raw.substring(0,1) + raw.substring(1).toLowerCase(Locale.ROOT).replace('_',' '); }

    public void save() { try { data.save(file); } catch (IOException e) { plugin.getLogger().warning("shop.yml: " + e.getMessage()); } }
}
