package me.snownw.core.util;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** messages.yml – prefix only when explicitly requested (e.g. reload). */
public final class Messages {

    private final JavaPlugin plugin;
    private FileConfiguration msg;

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "messages.yml");
        if (!f.exists()) plugin.saveResource("messages.yml", false);
        msg = YamlConfiguration.loadConfiguration(f);
        InputStream def = plugin.getResource("messages.yml");
        if (def != null) {
            msg.setDefaults(YamlConfiguration.loadConfiguration(
                    new InputStreamReader(def, StandardCharsets.UTF_8)));
        }
    }

    public String raw(String path) {
        return msg.getString(path, path);
    }

    /** No prefix – normal gameplay messages. */
    public String get(String path) {
        String body = msg.getString(path, path);
        return body == null ? path : body;
    }

    public String get(String path, Map<String, String> vars) {
        String s = get(path);
        if (vars != null) {
            for (Map.Entry<String, String> e : vars.entrySet()) {
                s = s.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
            }
        }
        return s;
    }

    /** With [SnowNWCore] prefix – only admin reload etc. */
    public String getPrefixed(String path) {
        String prefix = msg.getString("prefix", "&8[&fSnowNW&7Core&8] &r");
        return prefix + get(path);
    }

    public String plain(String path) {
        return msg.getString(path, path);
    }

    public String plain(String path, Map<String, String> vars) {
        return get(path, vars);
    }
}
