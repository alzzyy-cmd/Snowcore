package me.snownw.core.service;

import me.snownw.core.SnowNWCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Selection wand + named cuboids (RTP zones etc.). */
public final class CuboidService {
    public record Cuboid(String name, String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        public boolean contains(Location loc) {
            if (loc.getWorld() == null || !loc.getWorld().getName().equals(world)) return false;
            int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
            return x >= Math.min(x1, x2) && x <= Math.max(x1, x2)
                    && y >= Math.min(y1, y2) && y <= Math.max(y1, y2)
                    && z >= Math.min(z1, z2) && z <= Math.max(z1, z2);
        }
    }

    private final SnowNWCorePlugin plugin;
    private final File file;
    private YamlConfiguration data;
    private final Map<String, Cuboid> cuboids = new HashMap<>();
    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();

    public CuboidService(SnowNWCorePlugin plugin) {
        this.plugin = plugin;
        File dir = new File(plugin.getDataFolder(), "data");
        if (!dir.exists()) dir.mkdirs();
        this.file = new File(dir, "cuboids.yml");
        load();
    }

    public void load() {
        data = YamlConfiguration.loadConfiguration(file);
        cuboids.clear();
        ConfigurationSection root = data.getConfigurationSection("cuboids");
        if (root == null) return;
        for (String name : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(name);
            if (s == null) continue;
            cuboids.put(name.toLowerCase(Locale.ROOT), new Cuboid(name, s.getString("world"),
                    s.getInt("x1"), s.getInt("y1"), s.getInt("z1"),
                    s.getInt("x2"), s.getInt("y2"), s.getInt("z2")));
        }
    }

    public void save() {
        data = new YamlConfiguration();
        for (Cuboid c : cuboids.values()) {
            String p = "cuboids." + c.name();
            data.set(p + ".world", c.world());
            data.set(p + ".x1", c.x1()); data.set(p + ".y1", c.y1()); data.set(p + ".z1", c.z1());
            data.set(p + ".x2", c.x2()); data.set(p + ".y2", c.y2()); data.set(p + ".z2", c.z2());
        }
        try { data.save(file); } catch (IOException e) {
            plugin.getLogger().warning("cuboids: " + e.getMessage());
        }
    }

    public ItemStack wand() {
        ItemStack item = new ItemStack(Material.WOODEN_AXE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(me.snownw.core.util.ColorUtil.text(plugin.getConfig().getString("wand.name", "&6SnowNW Wand")));
        meta.lore(List.of(me.snownw.core.util.ColorUtil.text("&7Left: pos1  Right: pos2")));
        item.setItemMeta(meta);
        return item;
    }

    public boolean isWand(ItemStack item) {
        if (item == null || item.getType() != Material.WOODEN_AXE || !item.hasItemMeta()) return false;
        String n = item.getItemMeta().getDisplayName();
        return n != null && n.contains("Wand");
    }

    public void setPos1(Player p, Location l) { pos1.put(p.getUniqueId(), l.clone()); }
    public void setPos2(Player p, Location l) { pos2.put(p.getUniqueId(), l.clone()); }
    public Location getPos1(Player p) { return pos1.get(p.getUniqueId()); }
    public Location getPos2(Player p) { return pos2.get(p.getUniqueId()); }

    public enum CreateResult { OK, NO_SELECTION, DIFFERENT_WORLD, ALREADY_EXISTS, RTPZONE_EXISTS }

    public CreateResult create(String name, Player p) {
        Location a = getPos1(p), b = getPos2(p);
        if (a == null || b == null || a.getWorld() == null || b.getWorld() == null) return CreateResult.NO_SELECTION;
        if (!a.getWorld().equals(b.getWorld())) return CreateResult.DIFFERENT_WORLD;
        String key = name.toLowerCase(Locale.ROOT);
        if (key.equals("rtpzone") && cuboids.containsKey("rtpzone")) {
            return CreateResult.RTPZONE_EXISTS;
        }
        if (cuboids.containsKey(key)) {
            return CreateResult.ALREADY_EXISTS;
        }
        Cuboid c = new Cuboid(name, a.getWorld().getName(),
                a.getBlockX(), a.getBlockY(), a.getBlockZ(),
                b.getBlockX(), b.getBlockY(), b.getBlockZ());
        cuboids.put(key, c);
        save();
        return CreateResult.OK;
    }

    /** The single RTP zone cuboid (name rtpzone), or null. */
    public Cuboid getRtpZone() {
        return cuboids.get("rtpzone");
    }

    public boolean isInRtpZone(Location loc) {
        Cuboid z = getRtpZone();
        return z != null && z.contains(loc);
    }

    public boolean delete(String name) {
        if (cuboids.remove(name.toLowerCase(Locale.ROOT)) != null) { save(); return true; }
        return false;
    }

    public Collection<Cuboid> all() { return cuboids.values(); }
    public Cuboid get(String name) { return cuboids.get(name.toLowerCase(Locale.ROOT)); }
}
