package me.snownw.core.service;

import me.snownw.core.SnowNWCorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** PrimeCore-style shards currency (YAML). */
public final class ShardService {
    private final SnowNWCorePlugin plugin;
    private final File file;
    private YamlConfiguration data;

    public ShardService(SnowNWCorePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data");
        if (!file.exists()) file.mkdirs();
        File f = new File(file, "shards.yml");
        data = YamlConfiguration.loadConfiguration(f);
        startPassive();
    }

    private File shardsFile() { return new File(file, "shards.yml"); }

    public void reload() {
        data = YamlConfiguration.loadConfiguration(shardsFile());
    }

    public void save() {
        try { data.save(shardsFile()); } catch (IOException e) {
            plugin.getLogger().warning("shards.yml: " + e.getMessage());
        }
    }

    public long get(UUID id) { return data.getLong(id.toString(), 0L); }
    public long get(OfflinePlayer p) { return get(p.getUniqueId()); }

    public void set(UUID id, long amount) {
        data.set(id.toString(), Math.max(0, amount));
        save();
    }

    public void add(UUID id, long amount) { set(id, get(id) + amount); }

    public boolean take(UUID id, long amount) {
        if (get(id) < amount) return false;
        set(id, get(id) - amount);
        return true;
    }

    public boolean pay(Player from, OfflinePlayer to, long amount) {
        if (amount <= 0 || !take(from.getUniqueId(), amount)) return false;
        add(to.getUniqueId(), amount);
        return true;
    }

    public List<Map.Entry<UUID, Long>> top(int n) {
        List<Map.Entry<UUID, Long>> list = new ArrayList<>();
        for (String k : data.getKeys(false)) {
            try {
                list.add(Map.entry(UUID.fromString(k), data.getLong(k)));
            } catch (Exception ignored) {}
        }
        list.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        if (list.size() > n) return list.subList(0, n);
        return list;
    }

    private void startPassive() {
        if (!plugin.getConfig().getBoolean("shards.enabled", true)) return;
        int every = plugin.getConfig().getInt("shards.every-seconds", 600);
        long amount = plugin.getConfig().getLong("shards.amount", 1);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                long reward = amount * (plugin.amethystTools() != null ? plugin.amethystTools().multiplier(p.getUniqueId()) : 1L);
                add(p.getUniqueId(), reward);
                if (plugin.getConfig().getBoolean("shards.chat-messages", true)) {
                    String msg = plugin.messages().get("shard-received").replace("{amount}", String.valueOf(reward));
                    if (plugin.amethystTools() != null && plugin.amethystTools().boosterActive(p.getUniqueId())) {
                        msg += " &7(&d" + plugin.amethystTools().multiplier(p.getUniqueId()) + "x &7booster)";
                    }
                    p.sendMessage(me.snownw.core.util.ColorUtil.text(msg));
                }
            }
        }, every * 20L, every * 20L);
    }
}
