package me.snownw.core.service;

import me.snownw.core.SnowNWCorePlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Runtime enable/disable for individual commands – saved under config commands.* */
public final class CommandToggleService {

    public static final List<String> ALL = List.of(
            "menu", "homes", "home", "sethome", "delhome",
            "friends", "tpa", "tpahere", "tpaccept", "tpdeny", "tpauto", "tpacancel",
            "settings", "stats", "leaderboard", "worth", "ah", "rtp", "rtpqueue", "spawn", "warp", "kit",
            "bal", "balance", "pay", "msg", "enderchest", "profilemanager", "spawnstash", "fakeplayer", "offense", "ping", "gmc", "gms", "gmsp", "team", "amethysttool"
    );

    private final SnowNWCorePlugin plugin;

    public CommandToggleService(SnowNWCorePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled(String name) {
        String n = norm(name);
        return plugin.getConfig().getBoolean("commands." + n, true);
    }

    public void setEnabled(String name, boolean enabled) {
        String n = norm(name);
        plugin.getConfig().set("commands." + n, enabled);
        // aliases
        if (n.equals("bal")) plugin.getConfig().set("commands.balance", enabled);
        if (n.equals("balance")) plugin.getConfig().set("commands.bal", enabled);
        if (n.equals("homes")) {
            plugin.getConfig().set("commands.home", enabled);
            plugin.getConfig().set("commands.sethome", enabled);
            plugin.getConfig().set("commands.delhome", enabled);
        }
        if (n.equals("leaderboard")) {
            plugin.getConfig().set("commands.lb", enabled);
            plugin.getConfig().set("commands.leaderboards", enabled);
        }
        if (n.equals("msg")) {
            plugin.getConfig().set("commands.message", enabled);
            plugin.getConfig().set("commands.tell", enabled);
            plugin.getConfig().set("commands.whisper", enabled);
        }
        plugin.saveConfig();
    }

    public List<String> enabledList() {
        List<String> out = new ArrayList<>();
        for (String c : ALL) {
            if (isEnabled(c)) out.add(c);
        }
        return out;
    }

    public List<String> disabledList() {
        List<String> out = new ArrayList<>();
        for (String c : ALL) {
            if (!isEnabled(c)) out.add(c);
        }
        return out;
    }

    private static String norm(String name) {
        return name.toLowerCase(Locale.ROOT).trim();
    }
}
