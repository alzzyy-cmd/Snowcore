package me.snownw.core.service;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Alım emri (sipariş) sistemi: oyuncu X eşyasının Y adedini birim fiyattan toplamak ister;
 *  para peşin escrow'da tutulur. Diğer oyuncular envanterlerindeki eşyayı satarak ödüllendirilir. */
public final class OrderService {

    public static final class Order {
        public final int id;
        public final String ownerName;
        public final UUID owner;
        public final Material material;
        public final int totalAmount;
        public int remaining;
        public final double priceEach;
        Order(int id, UUID owner, String ownerName, Material material, int totalAmount, int remaining, double priceEach) {
            this.id = id; this.owner = owner; this.ownerName = ownerName; this.material = material;
            this.totalAmount = totalAmount; this.remaining = remaining; this.priceEach = priceEach;
        }
        public double escrowValue() { return remaining * priceEach; }
    }

    private final SnowNWCorePlugin plugin;
    private final File file;
    private YamlConfiguration data;
    private final List<Order> orders = new ArrayList<>();
    private int nextId = 1;

    public OrderService(SnowNWCorePlugin plugin) {
        this.plugin = plugin;
        File dir = new File(plugin.getDataFolder(), "data");
        if (!dir.exists()) dir.mkdirs();
        this.file = new File(dir, "orders.yml");
        load();
    }

    public boolean enabled() { return plugin.getConfig().getBoolean("orders.enabled", true); }

    public void load() {
        data = YamlConfiguration.loadConfiguration(file);
        orders.clear();
        nextId = data.getInt("next-id", 1);
        ConfigurationSection root = data.getConfigurationSection("orders");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            try {
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s == null) continue;
                UUID owner = UUID.fromString(s.getString("owner"));
                String ownerName = s.getString("owner-name", "?");
                Material mat = Material.matchMaterial(s.getString("material", ""));
                int total = s.getInt("amount-total", 0);
                int left = s.getInt("amount-left", 0);
                double price = s.getDouble("price-each", 0);
                if (mat == null || left <= 0 || price <= 0) continue;
                orders.add(new Order(Integer.parseInt(key), owner, ownerName, mat, total, left, price));
            } catch (Exception ignored) {}
        }
        orders.sort(Comparator.comparingInt(o -> o.id));
    }

    public void save() {
        data = new YamlConfiguration();
        data.set("next-id", nextId);
        for (Order o : orders) {
            String p = "orders." + o.id;
            data.set(p + ".owner", o.owner.toString());
            data.set(p + ".owner-name", o.ownerName);
            data.set(p + ".material", o.material.name());
            data.set(p + ".amount-total", o.totalAmount);
            data.set(p + ".amount-left", o.remaining);
            data.set(p + ".price-each", o.priceEach);
        }
        try { data.save(file); } catch (IOException e) { plugin.getLogger().warning("orders.yml: " + e.getMessage()); }
    }

    public List<Order> all() { return new ArrayList<>(orders); }

    public List<Order> mine(UUID uuid) {
        List<Order> out = new ArrayList<>();
        for (Order o : orders) if (o.owner.equals(uuid)) out.add(o);
        return out;
    }

    public Order byId(int id) {
        for (Order o : orders) if (o.id == id) return o;
        return null;
    }

    /** Sipariş oluştur: tutar = adet * birim fiyat peşin çekilir. Başarılıysa null, değilse hata mesajı. */
    public String create(Player player, Material material, int amount, double priceEach) {
        if (!enabled()) return plugin.messages().get("disabled");
        if (material == null) return plugin.messages().get("order-invalid-material");
        int maxAmt = Math.max(1, plugin.getConfig().getInt("orders.max-amount", 640));
        double maxPrice = plugin.getConfig().getDouble("orders.max-price-each", 1_000_000_000.0);
        if (amount < 1 || amount > maxAmt) return ColorUtil.replace(plugin.messages().get("order-invalid-amount"), "max", String.valueOf(maxAmt));
        if (priceEach <= 0 || priceEach > maxPrice) return plugin.messages().get("order-invalid-price");
        int maxOwn = Math.max(1, plugin.getConfig().getInt("orders.max-per-player", 5));
        if (!player.hasPermission("snownwcore.admin.bypassorder") && mine(player.getUniqueId()).size() >= maxOwn)
            return ColorUtil.replace(plugin.messages().get("order-limit"), "max", String.valueOf(maxOwn));

        double cost = amount * priceEach;
        if (!plugin.economy().withdraw(player, cost))
            return ColorUtil.replace(plugin.messages().get("insufficient-funds"), "amount", plugin.economy().format(cost));

        Order o = new Order(nextId++, player.getUniqueId(), player.getName(), material, amount, amount, priceEach);
        orders.add(o);
        save();
        player.sendMessage(ColorUtil.text(plugin.messages().get("order-created")
                .replace("{id}", String.valueOf(o.id))
                .replace("{amount}", String.valueOf(amount))
                .replace("{item}", o.material.name().toLowerCase(java.util.Locale.ROOT))
                .replace("{price}", plugin.economy().format(priceEach))
                .replace("{total}", plugin.economy().format(cost))));
        return null;
    }

    /** Oyuncu kendi siparişini iptal eder; kalan escrow iade edilir. */
    public String cancel(Player player, int id) {
        Order o = byId(id);
        if (o == null) return plugin.messages().get("order-not-found");
        boolean admin = player.hasPermission("snownwcore.admin.order");
        if (!admin && !o.owner.equals(player.getUniqueId())) return plugin.messages().get("no-permission");
        double refund = o.escrowValue();
        orders.remove(o);
        save();
        if (refund > 0) plugin.economy().deposit(Bukkit.getOfflinePlayer(o.owner), refund);
        player.sendMessage(ColorUtil.text(plugin.messages().get("order-cancelled")
                .replace("{id}", String.valueOf(id))
                .replace("{refund}", plugin.economy().format(refund))));
        Player owner = Bukkit.getPlayer(o.owner);
        if (owner != null && !owner.getUniqueId().equals(player.getUniqueId()))
            owner.sendMessage(ColorUtil.text(ColorUtil.replace(plugin.messages().get("order-cancelled-owner"), "id", String.valueOf(id))));
        return null;
    }

    /** Satıcı elindeki/envanterdeki eşyayı sipariş numarasına satar. Hata mesajı veya null. */
    public String fill(Player seller, int id) {
        Order o = byId(id);
        if (o == null) return plugin.messages().get("order-not-found");
        if (o.remaining <= 0) { orders.remove(o); save(); return plugin.messages().get("order-complete"); }
        int count = 0;
        ItemStack[] storage = seller.getInventory().getStorageContents();
        for (ItemStack it : storage) if (it != null && it.getType() == o.material) count += it.getAmount();
        if (count <= 0) return plugin.messages().get("order-no-items");
        int give = Math.min(count, o.remaining);
        // eşyaları düşür
        int left = give;
        for (int slot = 0; slot < storage.length && left > 0; slot++) {
            ItemStack it = storage[slot];
            if (it == null || it.getType() != o.material) continue;
            int take = Math.min(left, it.getAmount());
            it.setAmount(it.getAmount() - take);
            left -= take;
            if (it.getAmount() <= 0) seller.getInventory().setItem(slot, null);
        }
        o.remaining -= give;
        double pay = give * o.priceEach;
        plugin.economy().deposit(seller, pay);
        seller.sendMessage(ColorUtil.text(plugin.messages().get("order-filled")
                .replace("{amount}", String.valueOf(give))
                .replace("{id}", String.valueOf(id))
                .replace("{price}", plugin.economy().format(pay))));
        Player owner = Bukkit.getPlayer(o.owner);
        if (o.remaining <= 0) {
            orders.remove(o);
            if (owner != null) owner.sendMessage(ColorUtil.text(ColorUtil.replace(plugin.messages().get("order-complete-notify"), "id", String.valueOf(o.id))));
        } else if (owner != null) {
            owner.sendMessage(ColorUtil.text(plugin.messages().get("order-partial")
                    .replace("{id}", String.valueOf(o.id))
                    .replace("{left}", String.valueOf(o.remaining))));
        }
        save();
        return null;
    }
}
