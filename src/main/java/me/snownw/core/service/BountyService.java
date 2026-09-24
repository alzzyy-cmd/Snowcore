package me.snownw.core.service;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Kafa ödülü sistemi: oyuncu hedef üzerine para koyar; hedefi öldüren ödülü alır (escrow'dan).
 *  Kampçılık koruması: 1 saat içinde aynı oyuncu ikinci kez ödül koyamaz (opsiyonel). */
public final class BountyService {

    private final SnowNWCorePlugin plugin;
    private final File file;
    private YamlConfiguration data;
    /** hedef UUID -> ödül tutarı */
    private final Map<UUID, Double> bounties = new LinkedHashMap<>();

    public BountyService(SnowNWCorePlugin plugin) {
        this.plugin = plugin;
        File dir = new File(plugin.getDataFolder(), "data");
        if (!dir.exists()) dir.mkdirs();
        this.file = new File(dir, "bounties.yml");
        load();
    }

    public boolean enabled() { return plugin.getConfig().getBoolean("bounty.enabled", true); }

    public void load() {
        data = YamlConfiguration.loadConfiguration(file);
        bounties.clear();
        ConfigurationSection root = data.getConfigurationSection("bounties");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            try {
                double v = root.getDouble(key, 0);
                if (v > 0) bounties.put(UUID.fromString(key), v);
            } catch (Exception ignored) {}
        }
    }

    public void save() {
        data = new YamlConfiguration();
        for (var e : bounties.entrySet()) data.set("bounties." + e.getKey(), e.getValue());
        try { data.save(file); } catch (IOException e) { plugin.getLogger().warning("bounties.yml: " + e.getMessage()); }
    }

    public double bountyOf(UUID target) { return bounties.getOrDefault(target, 0.0); }
    public Map<UUID, Double> all() { return new LinkedHashMap<>(bounties); }

    /** Ödül koy: tutar peşin çekilir. Hata mesajı veya null. */
    public String place(Player from, Player target, double amount) {
        if (!enabled()) return plugin.messages().get("disabled");
        if (target == null || !target.isOnline()) return plugin.messages().get("player-offline");
        if (target.getUniqueId().equals(from.getUniqueId())) return plugin.messages().get("order-self");
        double min = plugin.getConfig().getDouble("bounty.min-amount", 1000.0);
        if (amount < min) return ColorUtil.replace(plugin.messages().get("bounty-too-low"), "min", plugin.economy().format(min));
        if (!plugin.economy().withdraw(from, amount))
            return ColorUtil.replace(plugin.messages().get("insufficient-funds"), "amount", plugin.economy().format(amount));
        bounties.merge(target.getUniqueId(), amount, Double::sum);
        save();
        if (plugin.getConfig().getBoolean("bounty.announce-place", true)) {
            Bukkit.broadcast(ColorUtil.text(plugin.messages().get("bounty-placed")
                    .replace("{player}", target.getName())
                    .replace("{price}", plugin.economy().format(bountyOf(target.getUniqueId())))));
        } else {
            from.sendMessage(ColorUtil.text(plugin.messages().get("bounty-placed")
                    .replace("{player}", target.getName())
                    .replace("{price}", plugin.economy().format(bountyOf(target.getUniqueId())))));
        }
        if (plugin.getConfig().getBoolean("bounty.notify-target", true))
            target.sendMessage(ColorUtil.text(ColorUtil.replace(plugin.messages().get("bounty-on-you"), "price", plugin.economy().format(bountyOf(target.getUniqueId())))));
        return null;
    }

    /** Killer, hedefi öldürdüğünde çağrılır; varsa ödülü verir. */
    public void claim(Player killer, Player victim) {
        if (!enabled()) return;
        Double amount = bounties.remove(victim.getUniqueId());
        if (amount == null || amount <= 0) return;
        if (killer != null && !killer.equals(victim)) {
            plugin.economy().deposit(killer, amount);
            killer.sendMessage(ColorUtil.text(plugin.messages().get("bounty-claimed")
                    .replace("{player}", victim.getName())
                    .replace("{price}", plugin.economy().format(amount))));
            Bukkit.broadcast(ColorUtil.text(plugin.messages().get("bounty-claimed-broadcast")
                    .replace("{killer}", killer.getName())
                    .replace("{player}", victim.getName())
                    .replace("{price}", plugin.economy().format(amount))));
        }
        // Doğal ölümde ödül iade: hedefin ödülü düşer (killer yoksa kaybolur)
        save();
    }

    /** Admin: ödülü kaldır ve koyan yoksa escrow olarak yut (karmaşıklığı önlemek için). */
    public String remove(Player admin, UUID target) {
        Double amount = bounties.remove(target);
        if (amount == null) return plugin.messages().get("bounty-not-found");
        save();
        admin.sendMessage(ColorUtil.text(ColorUtil.replace(plugin.messages().get("bounty-removed"),
                "player", Bukkit.getOfflinePlayer(target).getName() == null ? target.toString() : Bukkit.getOfflinePlayer(target).getName())));
        return null;
    }
}
