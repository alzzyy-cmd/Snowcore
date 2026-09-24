package me.snownw.core.data;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class StatsStore {

    public enum Type { KILLS, DEATHS, BLOCKS, PLACED, MOBS, PLAYTIME }

    public record Entry(UUID uuid, String name, long value) {}

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration data;

    public StatsStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
    }

    public void load() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        data = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("stats.yml save failed: " + e.getMessage());
        }
    }

    public long get(UUID uuid, Type type) {
        return data.getLong(uuid + "." + type.name().toLowerCase(), 0L);
    }

    public void add(UUID uuid, Type type, long amount) {
        long v = get(uuid, type) + amount;
        data.set(uuid + "." + type.name().toLowerCase(), v);
        // cache name
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        if (op.getName() != null) data.set(uuid + ".name", op.getName());
    }

    public void setPlaytimeTick(UUID uuid) {
        // accumulate 1 minute chunks from listener
        add(uuid, Type.PLAYTIME, 1);
    }

    public java.util.Set<String> knownPlayers() {
        return data.getKeys(false);
    }

    public String nameOf(UUID uuid) {
        String n = data.getString(uuid + ".name");
        if (n != null) return n;
        org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        return op.getName();
    }

    public List<Entry> top(Type type, int limit) {
        List<Entry> list = new ArrayList<>();
        for (String key : data.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                long v = get(uuid, type);
                if (v <= 0) continue;
                String name = data.getString(key + ".name");
                if (name == null) {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                    name = op.getName() == null ? "?" : op.getName();
                }
                list.add(new Entry(uuid, name, v));
            } catch (IllegalArgumentException ignored) {
            }
        }
        list.sort(Comparator.comparingLong(Entry::value).reversed());
        if (list.size() > limit) return list.subList(0, limit);
        return list;
    }

    /** Değer saniye cinsinden; gösterim Türkçe kısaltılmış (g/sa/dk). */
    public static String formatPlaytime(long seconds) {
        if (seconds < 0) seconds = 0;
        long d = seconds / 86400;
        long h = (seconds % 86400) / 3600;
        long m = (seconds % 3600) / 60;
        if (d > 0) return d + "g " + h + "sa";
        if (h > 0) return h + "sa " + m + "dk";
        return m + "dk";
    }

    public boolean hasData(UUID uuid) {
        String k = uuid.toString();
        if (data.contains(k + ".name")) return true;
        for (Type t : Type.values()) {
            if (data.getLong(k + "." + t.name().toLowerCase(), 0L) > 0) return true;
        }
        return data.isConfigurationSection(k);
    }

    public void wipe(UUID uuid) {
        data.set(uuid.toString(), null);
        save();
    }
}
