package me.snownw.core.data;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public final class FriendsStore {

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration data;

    public FriendsStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "friends.yml");
    }

    public void load() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        data = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("friends.yml: " + e.getMessage());
        }
    }

    public List<UUID> following(UUID uuid) {
        return data.getStringList(uuid + ".following").stream().map(UUID::fromString).collect(Collectors.toList());
    }

    public List<UUID> followers(UUID uuid) {
        List<UUID> out = new ArrayList<>();
        for (String key : data.getKeys(false)) {
            try {
                UUID other = UUID.fromString(key);
                if (following(other).contains(uuid)) out.add(other);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return out;
    }

    /** Mutual = friends */
    public List<UUID> friends(UUID uuid) {
        List<UUID> out = new ArrayList<>();
        for (UUID f : following(uuid)) {
            if (following(f).contains(uuid)) out.add(f);
        }
        return out;
    }

    public boolean isFollowing(UUID from, UUID to) {
        return following(from).contains(to);
    }

    public void follow(UUID from, UUID to) {
        if (from.equals(to)) return;
        List<String> list = new ArrayList<>(data.getStringList(from + ".following"));
        String id = to.toString();
        if (!list.contains(id)) {
            list.add(id);
            data.set(from + ".following", list);
            save();
        }
    }

    public void unfollow(UUID from, UUID to) {
        List<String> list = new ArrayList<>(data.getStringList(from + ".following"));
        list.remove(to.toString());
        data.set(from + ".following", list);
        save();
    }

    public enum Filter { ALL, FRIENDS, FOLLOWING, FOLLOWERS }

    public List<UUID> filtered(UUID uuid, Filter filter) {
        return switch (filter) {
            case FRIENDS -> friends(uuid);
            case FOLLOWING -> following(uuid);
            case FOLLOWERS -> followers(uuid);
            case ALL -> {
                List<UUID> all = new ArrayList<>(following(uuid));
                for (UUID f : followers(uuid)) {
                    if (!all.contains(f)) all.add(f);
                }
                yield all;
            }
        };
    }
}
