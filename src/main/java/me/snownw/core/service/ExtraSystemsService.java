package me.snownw.core.service;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class ExtraSystemsService {
    private final SnowNWCorePlugin plugin;
    private final File file;
    private final YamlConfiguration data;
    private final Map<UUID, Long> frozen = new HashMap<>();
    private final Map<UUID, Long> afk = new HashMap<>();

    public ExtraSystemsService(SnowNWCorePlugin plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "extras.yml");
        data = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try { data.save(file); }
        catch (IOException e) { plugin.getLogger().warning("extras.yml: " + e.getMessage()); }
    }

    public boolean isFrozen(UUID id) { return frozen.containsKey(id); }
    /** Dondurma: kar hasarı yerine hareket/hasar engeli + ağır yavaşlatma (setFreezeTicks öldürür!). */
    public void freeze(Player p) {
        frozen.put(p.getUniqueId(), System.currentTimeMillis());
        try {
            p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, 20 * 3600, 10, false, false, false));
            p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.JUMP_BOOST, 20 * 3600, 250, false, false, false));
        } catch (Throwable ignored) {}
        if (p.getFallDistance() > 0) p.setFallDistance(0);
    }
    public void unfreeze(Player p) {
        frozen.remove(p.getUniqueId());
        try {
            p.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS);
            p.removePotionEffect(org.bukkit.potion.PotionEffectType.JUMP_BOOST);
        } catch (Throwable ignored) {}
    }
    public Set<UUID> frozen() { return Collections.unmodifiableSet(frozen.keySet()); }

    public boolean isAfk(Player p) { return afk.containsKey(p.getUniqueId()); }
    public void setAfk(Player p, boolean value) {
        if (value) afk.put(p.getUniqueId(), System.currentTimeMillis());
        else afk.remove(p.getUniqueId());
    }

    public void setAfkLocation(String name, Location loc) {
        String path = "afk-locations." + name;
        data.set(path + ".world", loc.getWorld() == null ? "" : loc.getWorld().getName());
        data.set(path + ".x", loc.getX());
        data.set(path + ".y", loc.getY());
        data.set(path + ".z", loc.getZ());
        data.set(path + ".yaw", loc.getYaw());
        data.set(path + ".pitch", loc.getPitch());
        save();
    }

    public Map<String, Location> afkLocations() {
        Map<String, Location> out = new LinkedHashMap<>();
        var sec = data.getConfigurationSection("afk-locations");
        if (sec == null) return out;
        for (String name : sec.getKeys(false)) {
            String w = data.getString("afk-locations." + name + ".world");
            if (w == null || Bukkit.getWorld(w) == null) continue;
            out.put(name, new Location(Bukkit.getWorld(w),
                    data.getDouble("afk-locations."+name+".x"),
                    data.getDouble("afk-locations."+name+".y"),
                    data.getDouble("afk-locations."+name+".z"),
                    (float)data.getDouble("afk-locations."+name+".yaw"),
                    (float)data.getDouble("afk-locations."+name+".pitch")));
        }
        return out;
    }

    public String rank(UUID id) {
        return data.getString("ranks." + id, "Oyuncu");
    }

    public void setRank(UUID id, String rank) {
        data.set("ranks." + id, rank);
        save();
    }


    public ItemStack amethystPick() {
        return named(Material.NETHERITE_PICKAXE, "&dAmethyst Kazma",
                List.of("&7UltimateDonutSMP tarzı ametist eşya.", "&7Özel kazma davranışı sonraki aşamada."));
    }

    public ItemStack amethystAxe() {
        return named(Material.NETHERITE_AXE, "&dAmethyst Balta",
                List.of("&7UltimateDonutSMP tarzı ametist eşya.", "&7Özel balta davranışı sonraki aşamada."));
    }

    public ItemStack amethystShovel() {
        return named(Material.NETHERITE_SHOVEL, "&dAmethyst Kürek",
                List.of("&7UltimateDonutSMP tarzı ametist eşya.", "&7Özel kürek davranışı sonraki aşamada."));
    }

    private ItemStack named(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtil.text(name));
            meta.lore(lore.stream().map(ColorUtil::text).toList());
            item.setItemMeta(meta);
        }
        return item;
    }
}
