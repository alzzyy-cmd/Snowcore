package me.snownw.core.data;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SettingsStore {

    public enum Toggle {
        PUBLIC_CHAT("Chat", "Genel sohbet", true),
        PRIVATE_MESSAGES("Chat", "Özel mesajlar", true),
        DEATH_MESSAGES("Chat", "Ölüm mesajları", true),
        JOIN_LEAVE("Chat", "Giriş/çıkış mesajları", true),
        TPA_REQUESTS("Gizlilik", "TPA istekleri", true),
        TPA_HERE("Gizlilik", "Yanına TPA istekleri", true),
        ALLOW_PAYMENTS("Gizlilik", "Ödemelere izin ver", true),
        PAY_ALERTS("Bildirimler", "Ödeme bildirimleri", true),
        PAY_CONFIRMATIONS("Ekonomi", "Para gönderme onayı", true),
        FRIEND_ALERTS("Bildirimler", "Arkadaş bildirimleri", true),
        TPA_ALERTS("Bildirimler", "Işınlanma bildirimleri", true),
        SCOREBOARD("Skor tablosu", "Skor tablosu", true),
        SHOW_MONEY("Skor tablosu", "Para", true),
        SHOW_SHARDS("Skor tablosu", "Shard", true),
        SHOW_KILLS("Skor tablosu", "Öldürmeler", true),
        SHOW_DEATHS("Skor tablosu", "Ölümler", true),
        SHOW_PLAYTIME("Skor tablosu", "Oynama süresi", true),
        SHOW_PING("Skor tablosu", "Ping", true),
        SOUNDS("Görünüm", "Sesler", true),
        NIGHT_VISION("Görünüm", "Gece görüşü", false),
        WORTH_LORE("Görünüm", "Eşya fiyat bilgisi", true),
        HIDE_MOBS("Görünüm", "Yaratıkları gizle", false),
        FAST_CRYSTAL("PvP", "Hızlı kristal", false),
        TOTEM_PARTICLES("PvP", "Totem parçacıkları", true),
        EXPLOSION_PARTICLES("PvP", "Patlama parçacıkları", true),
        EXPLOSION_SOUNDS("PvP", "Patlama sesleri", true),
        NAMETAG_INFO("Görünüm", "İsim altında para ve ping", true);

        private final String category;
        private final String label;
        private final boolean def;

        Toggle(String category, String label, boolean def) {
            this.category = category;
            this.label = label;
            this.def = def;
        }

        public String category() { return category; }
        public String label() { return label; }
        public boolean def() { return def; }
    }

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration data;
    private final Map<UUID, Map<Toggle, Boolean>> cache = new HashMap<>();
    private final Map<UUID, Boolean> tpauto = new HashMap<>();

    public enum ScoreboardMode {
        MODERN,
        KLASIK
    }

    public ScoreboardMode getScoreboardMode(UUID uuid) {
        String raw = data.getString(uuid + ".scoreboard-mode",
                plugin.getConfig().getString("scoreboard.default-style", "modern"));
        try {
            String normalized = raw.trim().toUpperCase(java.util.Locale.ROOT);
            if (normalized.equals("NORMAL") || normalized.equals("MODERN")) return ScoreboardMode.MODERN;
            return ScoreboardMode.valueOf(normalized);
        } catch (Exception ignored) { return ScoreboardMode.MODERN; }
    }

    public ScoreboardMode toggleScoreboardMode(UUID uuid) {
        ScoreboardMode next = getScoreboardMode(uuid) == ScoreboardMode.MODERN
                ? ScoreboardMode.KLASIK : ScoreboardMode.MODERN;
        data.set(uuid + ".scoreboard-mode", next.name());
        save();
        return next;
    }

    public enum Audience {
        EVERYONE,
        FRIENDS,
        FOLLOWING,
        FRIENDS_AND_FOLLOWING,
        NOBODY
    }

    public enum TpaPolicy {
        EVERYONE,
        FRIENDS,
        FOLLOWING,
        FRIENDS_AND_FOLLOWING,
        NOBODY
    }


    public Audience getAudience(UUID uuid, Toggle toggle) {
        String raw = data.getString(uuid + ".audience." + toggle.name(), "EVERYONE");
        try { return Audience.valueOf(raw.toUpperCase(java.util.Locale.ROOT)); }
        catch (Exception ignored) { return Audience.EVERYONE; }
    }

    public void setAudience(UUID uuid, Toggle toggle, Audience audience) {
        data.set(uuid + ".audience." + toggle.name(), audience.name());
        save();
    }

    public Audience cycleAudience(UUID uuid, Toggle toggle) {
        Audience[] values = Audience.values();
        Audience current = getAudience(uuid, toggle);
        Audience next = values[(current.ordinal() + 1) % values.length];
        setAudience(uuid, toggle, next);
        return next;
    }

    public TpaPolicy getTpaPolicy(UUID uuid) {
        String raw = data.getString(uuid + ".tpa-policy", plugin.getConfig().getString("tpa.default-policy", "EVERYONE"));
        try {
            return TpaPolicy.valueOf(raw.toUpperCase(java.util.Locale.ROOT));
        } catch (Exception ignored) {
            return TpaPolicy.EVERYONE;
        }
    }

    public TpaPolicy cycleTpaPolicy(UUID uuid) {
        TpaPolicy[] values = TpaPolicy.values();
        TpaPolicy current = getTpaPolicy(uuid);
        TpaPolicy next = values[(current.ordinal() + 1) % values.length];
        data.set(uuid + ".tpa-policy", next.name());
        save();
        return next;
    }

    public void setTpaPolicy(UUID uuid, TpaPolicy policy) {
        data.set(uuid + ".tpa-policy", policy.name());
        save();
    }

    public SettingsStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "settings.yml");
    }

    public void load() {
        if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
        data = YamlConfiguration.loadConfiguration(file);
        cache.clear();
        tpauto.clear();
    }

    public void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("settings.yml: " + e.getMessage());
        }
    }

    public boolean get(UUID uuid, Toggle t) {
        return cache.computeIfAbsent(uuid, this::loadPlayer).getOrDefault(t, t.def());
    }

    public boolean toggle(UUID uuid, Toggle t) {
        boolean n = !get(uuid, t);
        cache.computeIfAbsent(uuid, this::loadPlayer).put(t, n);
        data.set(uuid + "." + t.name(), n);
        save();
        return n;
    }

    public boolean isTpAuto(UUID uuid) {
        if (tpauto.containsKey(uuid)) return tpauto.get(uuid);
        boolean v = data.getBoolean(uuid + ".tpauto",
                plugin.getConfig().getBoolean("tpa.tpauto-default", false));
        tpauto.put(uuid, v);
        return v;
    }

    public boolean toggleTpAuto(UUID uuid) {
        boolean n = !isTpAuto(uuid);
        tpauto.put(uuid, n);
        data.set(uuid + ".tpauto", n);
        save();
        return n;
    }

    private Map<Toggle, Boolean> loadPlayer(UUID uuid) {
        Map<Toggle, Boolean> m = new HashMap<>();
        for (Toggle t : Toggle.values()) {
            m.put(t, data.getBoolean(uuid + "." + t.name(), t.def()));
        }
        return m;
    }
}
