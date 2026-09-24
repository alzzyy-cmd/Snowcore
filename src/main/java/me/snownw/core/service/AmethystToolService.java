package me.snownw.core.service;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.util.ColorUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.inventory.meta.PotionMeta;

import java.util.*;

/** Safe, non-combat Amethyst utility items ported from the Ultimate configuration. */
public final class AmethystToolService {
    public enum Type { DRILL, SHOVEL, BUCKET, SHARD_BOOSTER }

    private final SnowNWCorePlugin plugin;
    private final NamespacedKey typeKey;
    private final NamespacedKey expiryKey;
    private final NamespacedKey idKey;
    private final Map<UUID, Long> boosters = new HashMap<>();

    public AmethystToolService(SnowNWCorePlugin plugin) {
        this.plugin = plugin;
        typeKey = new NamespacedKey(plugin, "amethyst_tool_type");
        expiryKey = new NamespacedKey(plugin, "amethyst_tool_expiry");
        idKey = new NamespacedKey(plugin, "amethyst_tool_id");
    }

    public ItemStack create(Type type, UUID owner, long durationSeconds) {
        ConfigurationSection c = section(type);
        if (c == null) return null;
        Material mat = material(c.getString("material"), Material.IRON_PICKAXE);
        long duration = durationSeconds > 0 ? durationSeconds : c.getLong("duration", 86400L);
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        String name = c.getString("name", "&#9B59B6&lAmethyst Tool");
        meta.displayName(ColorUtil.text(name));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        for (String line : c.getStringList("lore")) {
            lore.add(ColorUtil.text(line.replace("{time}", format(duration))));
        }
        meta.lore(lore);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(typeKey, PersistentDataType.STRING, type.name());
        pdc.set(expiryKey, PersistentDataType.LONG, System.currentTimeMillis() / 1000L + duration);
        pdc.set(idKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        if (type == Type.SHARD_BOOSTER && meta instanceof PotionMeta pm) {
            item.setItemMeta(pm);
        } else item.setItemMeta(meta);
        return item;
    }

    public Type type(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String raw = item.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        if (raw == null) return null;
        try { return Type.valueOf(raw); } catch (IllegalArgumentException ex) { return null; }
    }

    public boolean isAmethyst(ItemStack item) { return type(item) != null; }
    public boolean expired(ItemStack item) {
        if (!isAmethyst(item)) return false;
        Long e = item.getItemMeta().getPersistentDataContainer().get(expiryKey, PersistentDataType.LONG);
        return e == null || e <= System.currentTimeMillis() / 1000L;
    }
    public long remaining(ItemStack item) {
        if (!isAmethyst(item)) return 0;
        Long e = item.getItemMeta().getPersistentDataContainer().get(expiryKey, PersistentDataType.LONG);
        return e == null ? 0 : Math.max(0, e - System.currentTimeMillis() / 1000L);
    }
    public boolean allowedWorld(Player p) {
        return !plugin.getConfig().getStringList("amethyst-tools.excluded-worlds").contains(p.getWorld().getName());
    }
    public boolean ownerOk(ItemStack item, Player p) { return true; }

    public ConfigurationSection section(Type type) {
        return plugin.getConfig().getConfigurationSection("amethyst-tools." + type.name().toLowerCase(Locale.ROOT));
    }

    public void expire(Player p, int slot) {
        ItemStack i = p.getInventory().getItem(slot);
        if (isAmethyst(i) && expired(i)) {
            p.getInventory().setItem(slot, null);
            p.sendMessage(ColorUtil.text("&#9B59B6[Amethyst] &fAmethyst eşyanın süresi doldu ve yok edildi."));
        }
    }

    public void particles(org.bukkit.Location loc) {
        if (!plugin.getConfig().getBoolean("amethyst-tools.particles.enabled", true)) return;
        try { loc.getWorld().spawnParticle(Particle.BLOCK, loc.clone().add(.5,.5,.5), 8, .35,.35,.35, Material.PURPLE_CONCRETE_POWDER.createBlockData()); } catch (Throwable ignored) {}
    }

    public void sound(Player p, String key) {
        try { p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 1f, 1.15f); } catch (Throwable ignored) {}
    }

    public String format(long seconds) {
        long d = seconds / 86400; seconds %= 86400;
        long h = seconds / 3600; seconds %= 3600;
        long m = seconds / 60;
        if (d > 0) return d + "g " + h + "s";
        if (h > 0) return h + "s " + m + "d";
        return Math.max(1, m) + "d";
    }

    public boolean boosterActive(UUID id) { return boosters.getOrDefault(id, 0L) > System.currentTimeMillis(); }
    public long boosterRemaining(UUID id) { return Math.max(0, boosters.getOrDefault(id, 0L) - System.currentTimeMillis()); }
    public boolean activateBooster(Player p) {
        if (boosterActive(p.getUniqueId())) return false;
        ConfigurationSection c = section(Type.SHARD_BOOSTER);
        long sec = c == null ? 3600 : c.getLong("booster-duration", 3600);
        int multiplier = c == null ? 2 : Math.max(2, c.getInt("multiplier", 2));
        boosters.put(p.getUniqueId(), System.currentTimeMillis() + sec * 1000L);
        int every = plugin.getConfig().getInt("shards.every-seconds", 600);
        long base = plugin.getConfig().getLong("shards.amount", 1);
        long reward = base * multiplier;
        p.sendMessage(ColorUtil.text("&#9B59B6[Amethyst] &fShard Booster aktif! &d" + multiplier + "x &fShard &7• " + (sec / 60) + " dakika &7• sonraki ödül: &d" + reward + " Shard &7(" + formatInterval(every) + ")"));
        p.sendActionBar(ColorUtil.text("&#9B59B6✦ &fShard Booster: &d" + multiplier + "x &7• Sonraki ödül: &d" + reward + " Shard"));
        sound(p, "ACTIVATE"); particles(p.getLocation());
        return true;
    }

    public int multiplier(UUID id) {
        if (!boosterActive(id)) return 1;
        ConfigurationSection c = section(Type.SHARD_BOOSTER);
        return c == null ? 2 : Math.max(2, c.getInt("multiplier", 2));
    }

    private String formatInterval(int seconds) {
        if (seconds >= 3600) return (seconds / 3600) + " saat";
        if (seconds >= 60) return (seconds / 60) + " dakika";
        return seconds + " saniye";
    }

    public Set<Material> disabledBlocks() {
        Set<Material> out = EnumSet.noneOf(Material.class);
        ConfigurationSection c = section(Type.DRILL);
        if (c != null) for (String s : c.getStringList("disabled-blocks")) { try { out.add(Material.valueOf(s)); } catch (Exception ignored) {} }
        return out;
    }
    public Set<Material> shovelBlocks() {
        Set<Material> out = EnumSet.noneOf(Material.class);
        ConfigurationSection c = section(Type.SHOVEL);
        if (c != null) for (String s : c.getStringList("allowed-blocks")) { try { out.add(Material.valueOf(s)); } catch (Exception ignored) {} }
        return out;
    }
    private Material material(String s, Material fallback) { try { return s == null ? fallback : Material.valueOf(s); } catch (Exception e) { return fallback; } }
}
