package me.snownw.core.data;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Slot-fixed homes: slot 1..90 each has its own data.
 * Clicking slot 20 always uses slot 20 – never shifts to home1/home2.
 */
public final class HomeStore {

    public record Home(int slot, String name, Location location) {}

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration data;

    public HomeStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "homes.yml");
    }

    public void load() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        data = YamlConfiguration.loadConfiguration(file);
        migrateOldFormat();
    }

    /** Old format used name keys (home1, base…). Map homeN → slot N when possible. */
    private void migrateOldFormat() {
        for (String uuidKey : new ArrayList<>(data.getKeys(false))) {
            ConfigurationSection sec = data.getConfigurationSection(uuidKey);
            if (sec == null) continue;
            for (String key : new ArrayList<>(sec.getKeys(false))) {
                if (key.matches("\\d+")) continue; // already slot
                ConfigurationSection home = sec.getConfigurationSection(key);
                if (home == null) continue;
                int slot = -1;
                if (key.toLowerCase(Locale.ROOT).matches("home\\d+")) {
                    try {
                        slot = Integer.parseInt(key.substring(4));
                    } catch (NumberFormatException ignored) {}
                }
                if (slot < 1) {
                    // assign first free slot
                    slot = 1;
                    while (sec.getConfigurationSection(String.valueOf(slot)) != null) slot++;
                }
                if (sec.getConfigurationSection(String.valueOf(slot)) == null) {
                    data.set(uuidKey + "." + slot + ".name", key);
                    data.set(uuidKey + "." + slot + ".world", home.getString("world"));
                    data.set(uuidKey + "." + slot + ".x", home.getDouble("x"));
                    data.set(uuidKey + "." + slot + ".y", home.getDouble("y"));
                    data.set(uuidKey + "." + slot + ".z", home.getDouble("z"));
                    data.set(uuidKey + "." + slot + ".yaw", home.getDouble("yaw"));
                    data.set(uuidKey + "." + slot + ".pitch", home.getDouble("pitch"));
                }
                data.set(uuidKey + "." + key, null);
            }
        }
        save();
    }

    public void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("homes.yml save failed: " + e.getMessage());
        }
    }

    public int homeLimit(Player player) {
        int max = Math.max(1, plugin.getConfig().getInt("max-homes", 90));
        int best = 0;
        for (int i = max; i >= 1; i--) {
            if (player.hasPermission("snownwcore.home." + i)) {
                best = i;
                break;
            }
        }
        if (best == 0 && player.hasPermission("snownwcore.home")) best = 1;
        return best;
    }

    public Home getSlot(UUID uuid, int slot) {
        ConfigurationSection sec = data.getConfigurationSection(uuid + "." + slot);
        if (sec == null) return null;
        Location loc = readLoc(sec);
        if (loc == null) return null;
        String name = sec.getString("name", "home" + slot);
        return new Home(slot, name, loc);
    }

    public boolean hasSlot(UUID uuid, int slot) {
        return getSlot(uuid, slot) != null;
    }

    public List<Home> list(UUID uuid) {
        List<Home> list = new ArrayList<>();
        ConfigurationSection sec = data.getConfigurationSection(uuid.toString());
        if (sec == null) return list;
        for (String key : sec.getKeys(false)) {
            if (!key.matches("\\d+")) continue;
            Home h = getSlot(uuid, Integer.parseInt(key));
            if (h != null) list.add(h);
        }
        list.sort((a, b) -> Integer.compare(a.slot(), b.slot()));
        return list;
    }

    public int count(UUID uuid) {
        return list(uuid).size();
    }

    /** Set fixed slot – does not shift other slots. */
    public boolean setSlot(UUID uuid, int slot, Location loc, String displayName, int limit) {
        if (slot < 1 || slot > limit) return false;
        String path = uuid + "." + slot;
        String name = (displayName == null || displayName.isBlank())
                ? ("home" + slot)
                : displayName.trim();
        data.set(path + ".name", name);
        data.set(path + ".world", loc.getWorld().getName());
        data.set(path + ".x", loc.getX());
        data.set(path + ".y", loc.getY());
        data.set(path + ".z", loc.getZ());
        data.set(path + ".yaw", (double) loc.getYaw());
        data.set(path + ".pitch", (double) loc.getPitch());
        save();
        return true;
    }

    public boolean deleteSlot(UUID uuid, int slot) {
        if (getSlot(uuid, slot) == null) return false;
        data.set(uuid + "." + slot, null);
        save();
        return true;
    }

    public boolean renameSlot(UUID uuid, int slot, String newName) {
        Home h = getSlot(uuid, slot);
        if (h == null) return false;
        String n = newName.toLowerCase(Locale.ROOT);
        if (!n.matches("[a-z0-9_-]{1,16}")) return false;
        data.set(uuid + "." + slot + ".name", n);
        save();
        return true;
    }

    /** Legacy name lookup for /home name command */
    public Home getByName(UUID uuid, String name) {
        final String n = name.toLowerCase(Locale.ROOT);
        for (Home h : list(uuid)) {
            if (h.name().equalsIgnoreCase(name) || ("home" + h.slot()).equalsIgnoreCase(name)) return h;
        }
        return null;
    }

    private Location readLoc(ConfigurationSection sec) {
        if (sec == null) return null;
        World world = Bukkit.getWorld(sec.getString("world", "world"));
        if (world == null) return null;
        return new Location(world,
                sec.getDouble("x"), sec.getDouble("y"), sec.getDouble("z"),
                (float) sec.getDouble("yaw"), (float) sec.getDouble("pitch"));
    }
}
