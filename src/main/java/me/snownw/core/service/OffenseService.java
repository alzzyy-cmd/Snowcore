package me.snownw.core.service;

import me.snownw.core.SnowNWCorePlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class OffenseService {
    public record Rule(String key, String name, String type, List<String> durations) {
        public String duration(int tier) {
            if (durations == null || durations.isEmpty()) return "perm";
            return durations.get(Math.min(Math.max(0, tier - 1), durations.size() - 1));
        }
    }

    private static final Pattern DURATION = Pattern.compile("(\\d+)\\s*(mo|s|m|h|d|w|y)", Pattern.CASE_INSENSITIVE);
    private final SnowNWCorePlugin plugin;
    private final File file;
    private final YamlConfiguration data;
    private final Map<String, Rule> rules = new LinkedHashMap<>();
    private final Map<UUID, Long> mutedUntil = new java.util.concurrent.ConcurrentHashMap<>();

    public OffenseService(SnowNWCorePlugin plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "offenses.yml");
        if (!file.exists()) {
            try { plugin.saveResource("offenses.yml", false); } catch (Throwable ignored) {}
        }
        data = YamlConfiguration.loadConfiguration(file);
        loadRules();
    }

    private void loadRules() {
        ConfigurationSection section = data.getConfigurationSection("offenses");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(key);
            if (s == null) continue;
            rules.put(key.toLowerCase(Locale.ROOT),
                    new Rule(key.toLowerCase(Locale.ROOT),
                            s.getString("name", key),
                            s.getString("type", "BAN").toUpperCase(Locale.ROOT),
                            s.getStringList("durations")));
        }
    }

    public Set<String> keys() { return Collections.unmodifiableSet(rules.keySet()); }
    public Rule rule(String key) { return rules.get(key == null ? "" : key.toLowerCase(Locale.ROOT)); }

    public int add(UUID player, String offenseKey, UUID staff) {
        String path = "history." + player + "." + offenseKey.toLowerCase(Locale.ROOT) + ".count";
        int count = data.getInt(path, 0) + 1;
        data.set(path, count);
        data.set("history." + player + "." + offenseKey.toLowerCase(Locale.ROOT) + ".last-staff", String.valueOf(staff));
        data.set("history." + player + "." + offenseKey.toLowerCase(Locale.ROOT) + ".last-time", System.currentTimeMillis());
        save();
        return count;
    }


    /** Applies the configured moderation action after the staff explicitly creates an offense. */
    public void apply(Player target, Player staff, Rule rule, String durationRaw) {
        Long duration = parseDuration(durationRaw);
        long expiresAt = duration == null ? Long.MAX_VALUE : System.currentTimeMillis() + Math.max(0L, duration);
        if (duration != null && duration <= 0L) {
            target.sendMessage(me.snownw.core.util.ColorUtil.text("&eUyarı: &f" + rule.name()));
            return;
        }
        switch (rule.type()) {
            case "BAN" -> {
                java.util.Date expiry = duration == null ? null : new java.util.Date(expiresAt);
                Bukkit.getBanList(BanList.Type.NAME).addBan(target.getName(), rule.name(), expiry, staff.getName());
                target.kickPlayer("SnowNW\n\nCeza: " + rule.name() + "\nSüre: " + durationLabel(durationRaw));
            }
            case "MUTE" -> {
                mutedUntil.put(target.getUniqueId(), expiresAt);
                data.set("active-mutes." + target.getUniqueId(), expiresAt);
                save();
                target.sendMessage(me.snownw.core.util.ColorUtil.text("&cSohbet kullanımın kısıtlandı. &7Süre: &f" + durationLabel(durationRaw)));
            }
            case "KICK" -> target.kickPlayer("SnowNW\n\nCeza: " + rule.name());
            default -> target.sendMessage(me.snownw.core.util.ColorUtil.text("&eUyarı: &f" + rule.name()));
        }
    }

    public boolean isMuted(UUID uuid) {
        Long until = mutedUntil.get(uuid);
        if (until == null) {
            until = data.getLong("active-mutes." + uuid, 0L);
            if (until > 0L) mutedUntil.put(uuid, until);
        }
        if (until == null || until <= 0L) return false;
        if (until != Long.MAX_VALUE && until <= System.currentTimeMillis()) {
            mutedUntil.remove(uuid);
            data.set("active-mutes." + uuid, null);
            save();
            return false;
        }
        return true;
    }

    private String durationLabel(String raw) {
        if (raw == null || raw.equalsIgnoreCase("perm")) return "Süresiz";
        return raw.replace("mo", " ay").replace("d", " gün").replace("h", " saat").replace("m", " dakika").replace("s", " saniye").replace("w", " hafta").replace("y", " yıl");
    }

    public int count(UUID player, String key) {
        return data.getInt("history." + player + "." + key.toLowerCase(Locale.ROOT) + ".count", 0);
    }

    public void save() {
        try { data.save(file); } catch (IOException e) { plugin.getLogger().warning("offenses.yml: " + e.getMessage()); }
    }

    public static Long parseDuration(String raw) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("perm")) return null;
        Matcher m = DURATION.matcher(raw.replace(" ", ""));
        long total = 0;
        boolean found = false;
        while (m.find()) {
            found = true;
            long n = Long.parseLong(m.group(1));
            total += switch (m.group(2).toLowerCase(Locale.ROOT)) {
                case "s" -> n * 1000L;
                case "m" -> n * 60_000L;
                case "h" -> n * 3_600_000L;
                case "d" -> n * 86_400_000L;
                case "w" -> n * 604_800_000L;
                case "mo" -> n * 2_592_000_000L;
                case "y" -> n * 31_536_000_000L;
                default -> 0L;
            };
        }
        return found ? total : null;
    }
}
