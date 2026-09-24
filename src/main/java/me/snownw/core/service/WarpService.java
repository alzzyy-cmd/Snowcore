package me.snownw.core.service;

import me.snownw.core.SnowNWCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

/** Simple SnowNW warp/spawn service backed by config.yml. */
public final class WarpService {
    private final SnowNWCorePlugin plugin;
    public WarpService(SnowNWCorePlugin plugin) { this.plugin = plugin; }

    public Location spawn() {
        String worldName = plugin.getConfig().getString("spawn.world", "world");
        World w = Bukkit.getWorld(worldName);
        if (w == null) w = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
        if (w == null) return null;
        if (plugin.getConfig().contains("spawn.x")) {
            return new Location(w, plugin.getConfig().getDouble("spawn.x"), plugin.getConfig().getDouble("spawn.y"), plugin.getConfig().getDouble("spawn.z"), (float) plugin.getConfig().getDouble("spawn.yaw", 0), (float) plugin.getConfig().getDouble("spawn.pitch", 0));
        }
        return w.getSpawnLocation();
    }

    public Location warp(String name) {
        String path = "warps." + name.toLowerCase();
        if (!plugin.getConfig().contains(path + ".world")) return null;
        World w = Bukkit.getWorld(plugin.getConfig().getString(path + ".world"));
        if (w == null) return null;
        return new Location(w, plugin.getConfig().getDouble(path + ".x"), plugin.getConfig().getDouble(path + ".y"), plugin.getConfig().getDouble(path + ".z"), (float) plugin.getConfig().getDouble(path + ".yaw", 0), (float) plugin.getConfig().getDouble(path + ".pitch", 0));
    }

    public List<String> names() {
        var sec = plugin.getConfig().getConfigurationSection("warps");
        return sec == null ? List.of() : new ArrayList<>(sec.getKeys(false));
    }

    public boolean setSpawn(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        plugin.getConfig().set("spawn.world", loc.getWorld().getName());
        plugin.getConfig().set("spawn.x", loc.getX());
        plugin.getConfig().set("spawn.y", loc.getY());
        plugin.getConfig().set("spawn.z", loc.getZ());
        plugin.getConfig().set("spawn.yaw", loc.getYaw());
        plugin.getConfig().set("spawn.pitch", loc.getPitch());
        plugin.saveConfig();
        return true;
    }

    public boolean setWarp(String name, Location loc) {
        if (name == null || !name.matches("[A-Za-z0-9_-]{1,24}")) return false;
        String p = "warps." + name.toLowerCase();
        plugin.getConfig().set(p + ".world", loc.getWorld().getName());
        plugin.getConfig().set(p + ".x", loc.getX()); plugin.getConfig().set(p + ".y", loc.getY()); plugin.getConfig().set(p + ".z", loc.getZ());
        plugin.getConfig().set(p + ".yaw", loc.getYaw()); plugin.getConfig().set(p + ".pitch", loc.getPitch());
        plugin.saveConfig(); return true;
    }

    public boolean deleteWarp(String name) {
        if (warp(name) == null) return false; plugin.getConfig().set("warps." + name.toLowerCase(), null); plugin.saveConfig(); return true;
    }
}
