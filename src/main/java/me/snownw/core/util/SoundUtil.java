package me.snownw.core.util;

import me.snownw.core.SnowNWCorePlugin;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/** Sounds from config.yml section "sounds" – no separate sounds.yml. */
public final class SoundUtil {
    private final SnowNWCorePlugin plugin;

    public SoundUtil(SnowNWCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() { /* config-driven */ }

    public void play(Player player, String key) {
        if (player == null) return;
        String name = plugin.getConfig().getString("sounds." + key, "");
        if (name == null || name.isBlank()) return;
        try {
            player.playSound(player.getLocation(), Sound.valueOf(name), 1f, 1f);
        } catch (IllegalArgumentException ignored) {}
    }
}
