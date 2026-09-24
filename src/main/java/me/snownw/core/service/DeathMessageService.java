package me.snownw.core.service;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.util.ColorUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** Ölüm nedenine göre Türkçe ölüm mesajları (death-messages.yml). */
public final class DeathMessageService {

    private final SnowNWCorePlugin plugin;
    private final File file;
    private YamlConfiguration data;

    public DeathMessageService(SnowNWCorePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "death-messages.yml");
        reload();
    }

    public void reload() {
        if (!file.exists()) {
            plugin.saveResource("death-messages.yml", false);
        }
        data = YamlConfiguration.loadConfiguration(file);
    }

    public boolean enabled() { return data.getBoolean("enabled", true); }

    /** Ölen oyuncu için mesaj seç; ulanmışsa null döner (sistem kapalıysa vanilla). */
    public String messageFor(Player dead) {
        if (!enabled()) return null;
        EntityDamageEvent.DamageCause cause = EntityDamageEvent.DamageCause.CUSTOM;
        if (dead.getLastDamageCause() != null && dead.getLastDamageCause().getCause() != null)
            cause = dead.getLastDamageCause().getCause();

        Player killer = dead.getKiller();
        String key;
        if (killer != null) {
            key = switch (cause) {
                case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK -> "MELEE";
                case PROJECTILE -> "RANGED";
                case ENTITY_EXPLOSION -> "EXPLOSION";
                default -> "ANY";
            };
            ConfigurationSection pvp = data.getConfigurationSection("pvp." + key);
            if (pvp == null) pvp = data.getConfigurationSection("pvp.ANY");
            return pick(pvp, dead.getName(), killer.getName(), null);
        }

        String section = switch (cause) {
            case FALL -> "fall";
            case FALLING_BLOCK -> "falling-block";
            case LAVA -> "lava";
            case FIRE, FIRE_TICK -> "fire";
            case DROWNING -> "drowning";
            case SUFFOCATION -> "suffocation";
            case VOID -> "void";
            case PROJECTILE -> "mob-ranged";
            case ENTITY_EXPLOSION, BLOCK_EXPLOSION -> "explosion";
            case STARVATION -> "starvation";
            case FREEZE -> "freeze";
            case LIGHTNING -> "lightning";
            case CONTACT -> "contact";
            case MAGIC, POISON -> "magic";
            case WITHER -> "wither";
            case FLY_INTO_WALL -> "elytra";
            case ENTITY_ATTACK, ENTITY_SWEEP_ATTACK -> "mob-melee";
            case DRAGON_BREATH -> "dragon";
            case THORNS -> "thorns";
            default -> "generic";
        };
        ConfigurationSection sec = data.getConfigurationSection(section);
        if (sec == null) sec = data.getConfigurationSection("generic");
        String mob = null;
        if (dead.getLastDamageCause() instanceof org.bukkit.event.entity.EntityDamageByEntityEvent edbe) {
            mob = edbe.getDamager() instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof org.bukkit.entity.Entity m
                    ? m.getType().name() : edbe.getDamager().getType().name();
            org.bukkit.event.entity.EntityDamageByEntityEvent copy = edbe;
            if (copy.getDamager() instanceof org.bukkit.entity.Projectile p && p.getShooter() instanceof org.bukkit.entity.Entity m2)
                mob = m2.getType().name();
        }
        return pick(sec, dead.getName(), null, mob);
    }

    private String pick(ConfigurationSection sec, String victim, String killer, String mob) {
        if (sec == null) return null;
        List<String> pool = new ArrayList<>();
        List<String> all = sec.getStringList("messages");
        if (all != null) pool.addAll(all);
        if (pool.isEmpty()) return sec.getString("message", null);
        String msg = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        if (msg == null) return null;
        msg = msg.replace("{player}", victim == null ? "?" : victim);
        if (killer != null) msg = msg.replace("{killer}", killer);
        if (mob != null) msg = msg.replace("{mob}", mobName(mob));
        return ColorUtil.replace(msg, "none", "");
    }

    private static String mobName(String type) {
        if (type == null) return "?";
        String s = type.toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder b = new StringBuilder();
        boolean up = true;
        for (char c : s.toCharArray()) {
            if (up) { b.append(Character.toUpperCase(c)); up = false; }
            else if (c == ' ') { b.append(c); up = true; }
            else b.append(c);
        }
        return b.toString();
    }
}
