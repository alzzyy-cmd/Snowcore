package me.snownw.core.service;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.util.ColorUtil;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Handles PvP combat tagging and the combat timer. */
public final class CombatService {
    private final SnowNWCorePlugin plugin;
    private final Map<UUID, CombatTag> tags = new ConcurrentHashMap<>();
    private BukkitTask ticker;

    public CombatService(SnowNWCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.getConfig().getBoolean("combat.enabled", true)) return;
        long period = Math.max(1L, plugin.getConfig().getLong("combat.check-period-ticks", 10L));
        ticker = new BukkitRunnable() {
            @Override public void run() { tick(); }
        }.runTaskTimer(plugin, period, period);
    }

    public void stop() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        tags.clear();
    }

    public boolean isInCombat(Player player) {
        return isInCombat(player.getUniqueId());
    }

    public boolean isInCombat(UUID id) {
        CombatTag tag = tags.get(id);
        if (tag == null) return false;
        if (tag.expiresAt <= System.currentTimeMillis()) {
            end(id, true);
            return false;
        }
        return true;
    }

    public UUID opponent(UUID id) {
        CombatTag tag = tags.get(id);
        return tag == null ? null : tag.opponent;
    }

    public void tag(Player attacker, Player victim) {
        if (!plugin.getConfig().getBoolean("combat.enabled", true)) return;
        if (attacker.getUniqueId().equals(victim.getUniqueId())) return;

        int seconds = Math.max(1, plugin.getConfig().getInt("combat.duration-seconds", 20));
        boolean attackerWasInCombat = isInCombat(attacker);
        boolean victimWasInCombat = isInCombat(victim);
        long expires = System.currentTimeMillis() + seconds * 1000L;
        put(attacker, victim, expires);
        put(victim, attacker, expires);

        playTagSound(attacker);
        playTagSound(victim);

        if (plugin.getConfig().getBoolean("combat.notify-chat-on-enter", true)) {
            if (!attackerWasInCombat) attacker.sendMessage(msg("combat-enter"));
            if (!victimWasInCombat) victim.sendMessage(msg("combat-enter"));
        }
        updateBar(attacker, seconds);
        updateBar(victim, seconds);
    }

    private void put(Player player, Player opponent, long expires) {
        tags.put(player.getUniqueId(), new CombatTag(opponent.getUniqueId(), expires));
    }

    private void tick() {
        if (tags.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, CombatTag> entry : tags.entrySet()) {
            UUID id = entry.getKey();
            CombatTag tag = entry.getValue();
            Player player = plugin.getServer().getPlayer(id);
            if (player == null || !player.isOnline()) {
                end(id, false);
                continue;
            }

            Player opponent = plugin.getServer().getPlayer(tag.opponent);
            if (opponent == null || !opponent.isOnline()) {
                end(id, true);
                continue;
            }

            if (isTooFar(player, opponent)) {
                endPair(id, tag.opponent);
                continue;
            }

            if (tag.expiresAt <= now) {
                endPair(id, tag.opponent);
                continue;
            }

            int left = (int) Math.ceil((tag.expiresAt - now) / 1000.0D);
            updateBar(player, left);
        }
    }

    private boolean isTooFar(Player a, Player b) {
        if (a.getWorld() != b.getWorld()) return true;
        double max = Math.max(1.0D, plugin.getConfig().getDouble("combat.max-distance", 200.0D));
        return a.getLocation().distanceSquared(b.getLocation()) > max * max;
    }

    public void end(UUID id, boolean notify) {
        CombatTag tag = tags.remove(id);
        Player player = plugin.getServer().getPlayer(id);
        if (player != null && player.isOnline()) {
            player.sendActionBar(ColorUtil.text(msg("combat-ended")));
        }
        if (tag != null && notify) {
            CombatTag other = tags.get(tag.opponent);
            if (other != null && other.opponent.equals(id)) tags.remove(tag.opponent);
            Player opponent = plugin.getServer().getPlayer(tag.opponent);
            if (opponent != null && opponent.isOnline()) {
                opponent.sendActionBar(ColorUtil.text(msg("combat-ended")));
            }
        }
    }

    public void endPair(UUID first, UUID second) {
        tags.remove(first);
        tags.remove(second);
        Player a = plugin.getServer().getPlayer(first);
        Player b = plugin.getServer().getPlayer(second);
        if (a != null && a.isOnline()) a.sendActionBar(ColorUtil.text(msg("combat-ended")));
        if (b != null && b.isOnline()) b.sendActionBar(ColorUtil.text(msg("combat-ended")));
    }

    private void updateBar(Player player, int seconds) {
        String text = msg("combat-actionbar").replace("{sec}", String.valueOf(Math.max(0, seconds)));
        player.sendActionBar(ColorUtil.text(text));
    }

    private void playTagSound(Player player) {
        try {
            Sound sound = Sound.valueOf(plugin.getConfig().getString("combat.enter-sound", "BLOCK_ANVIL_LAND"));
            float volume = (float) plugin.getConfig().getDouble("combat.enter-sound-volume", 0.8D);
            float pitch = (float) plugin.getConfig().getDouble("combat.enter-sound-pitch", 1.0D);
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (Exception ignored) {
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.8f, 1.0f);
        }
    }

    private String msg(String key) {
        return plugin.messages().get(key);
    }

    private record CombatTag(UUID opponent, long expiresAt) {}
}
