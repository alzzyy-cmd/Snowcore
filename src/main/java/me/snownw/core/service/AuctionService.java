package me.snownw.core.service;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.util.ColorUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Persistent, single-server auction house used by /ah. */
public final class AuctionService {
    public record Listing(long id, UUID seller, String sellerName, double price, ItemStack item, long createdAt) {}
    private final SnowNWCorePlugin plugin;
    private final File file;
    private YamlConfiguration data;
    private final Map<Long, Listing> listings = new LinkedHashMap<>();
    private final AtomicLong ids = new AtomicLong(1);

    public AuctionService(SnowNWCorePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "auction.yml");
        load();
    }

    public synchronized void load() {
        data = YamlConfiguration.loadConfiguration(file);
        listings.clear();
        long max = 0;
        ConfigurationSection root = data.getConfigurationSection("listings");
        if (root != null) for (String key : root.getKeys(false)) {
            try {
                long id = Long.parseLong(key);
                UUID seller = UUID.fromString(root.getString(key + ".seller"));
                String name = root.getString(key + ".seller-name", "Bilinmeyen");
                double price = root.getDouble(key + ".price");
                ItemStack item = root.getItemStack(key + ".item");
                long created = root.getLong(key + ".created-at", System.currentTimeMillis());
                if (item != null && !item.getType().isAir() && price > 0) {
                    listings.put(id, new Listing(id, seller, name, price, item, created));
                    max = Math.max(max, id);
                }
            } catch (Exception ignored) {}
        }
        ids.set(max + 1);
    }

    public synchronized List<Listing> all() { return List.copyOf(listings.values()); }
    public synchronized Listing get(long id) { return listings.get(id); }
    public synchronized List<Listing> mine(UUID uuid) { return listings.values().stream().filter(l -> l.seller().equals(uuid)).toList(); }

    public synchronized boolean create(Player seller, double price) {
        if (price <= 0) return false;
        int max = Math.max(1, plugin.getConfig().getInt("auction.max-listings-per-player", 10));
        if (mine(seller.getUniqueId()).size() >= max) return false;
        ItemStack hand = seller.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) return false;
        ItemStack copy = hand.clone();
        long id = ids.getAndIncrement();
        seller.getInventory().setItemInMainHand(null);
        listings.put(id, new Listing(id, seller.getUniqueId(), seller.getName(), price, copy, System.currentTimeMillis()));
        save();
        return true;
    }

    public synchronized boolean buy(Player buyer, long id) {
        Listing l = listings.get(id);
        if (l == null || l.seller().equals(buyer.getUniqueId())) return false;
        if (!canFit(buyer, l.item())) return false;
        if (!plugin.economy().withdraw(buyer, l.price())) return false;
        Map<Integer, ItemStack> left = buyer.getInventory().addItem(l.item().clone());
        if (!left.isEmpty()) {
            plugin.economy().deposit(buyer, l.price());
            return false;
        }
        listings.remove(id);
        plugin.economy().deposit(org.bukkit.Bukkit.getOfflinePlayer(l.seller()), l.price());
        save();
        Player seller = org.bukkit.Bukkit.getPlayer(l.seller());
        if (seller != null) seller.sendMessage(ColorUtil.text("&aİlanın satıldı: &f" + l.item().getType().name() + " &7(" + plugin.economy().format(l.price()) + ")"));
        return true;
    }

    private boolean canFit(Player player, ItemStack item) {
        int remaining = item.getAmount();
        int max = Math.max(1, item.getMaxStackSize());
        for (ItemStack existing : player.getInventory().getStorageContents()) {
            if (existing != null && existing.isSimilar(item)) {
                remaining -= Math.max(0, max - existing.getAmount());
                if (remaining <= 0) return true;
            }
        }
        for (ItemStack existing : player.getInventory().getStorageContents()) {
            if (existing == null || existing.getType().isAir()) {
                remaining -= max;
                if (remaining <= 0) return true;
            }
        }
        return false;
    }

    public synchronized boolean cancel(Player seller, long id) {
        Listing l = listings.get(id);
        if (l == null || !l.seller().equals(seller.getUniqueId())) return false;
        if (!canFit(seller, l.item())) return false;
        if (!seller.getInventory().addItem(l.item().clone()).isEmpty()) return false;
        listings.remove(id);
        save();
        return true;
    }

    public synchronized void save() {
        data = new YamlConfiguration();
        for (Listing l : listings.values()) {
            String p = "listings." + l.id();
            data.set(p + ".seller", l.seller().toString());
            data.set(p + ".seller-name", l.sellerName());
            data.set(p + ".price", l.price());
            data.set(p + ".created-at", l.createdAt());
            data.set(p + ".item", l.item());
        }
        try { data.save(file); } catch (IOException e) { plugin.getLogger().warning("auction.yml: " + e.getMessage()); }
    }
}
