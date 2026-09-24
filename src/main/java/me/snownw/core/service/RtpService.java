package me.snownw.core.service;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.List;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Safe RTP: never intentionally selects Nether roof, lava, unsafe feet/head blocks or dangerous terrain. */
public final class RtpService {
    private final SnowNWCorePlugin plugin;
    private YamlConfiguration rtpCfg;
    private final Map<UUID, Long> cooldown = new ConcurrentHashMap<>();
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Location> pendingStart = new ConcurrentHashMap<>();
    /** RTP queue: each world keeps waiting players; the first two are paired. */
    private final Map<String, Deque<UUID>> queues = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public RtpService(SnowNWCorePlugin plugin) { this.plugin = plugin; reload(); }
    public YamlConfiguration config() { return rtpCfg; }
    public void reload() {
        File f = new File(plugin.getDataFolder(), "rtp.yml");
        if (!f.exists()) plugin.saveResource("rtp.yml", false);
        rtpCfg = YamlConfiguration.loadConfiguration(f);
    }

    public void rtp(Player player) { rtp(player, player.getWorld().getName()); }

    public void rtp(Player player, String worldName) {
        worldName = resolveWorldName(worldName);
        if (!rtpCfg.getBoolean("ENABLED", true)) { deny(player, msg("DISABLED", "&cRTP kapalı.")); return; }
        UUID id = player.getUniqueId();
        int maxPlayers = Math.max(1, rtpCfg.getInt("SETTINGS.PLAYERS-IN-RTP", 3));
        if (pending.size() >= maxPlayers && !pending.contains(id)) {
            deny(player, msg("MAX-PLAYERS", "&cŞu anda çok fazla oyuncu RTP kullanıyor. Lütfen biraz sonra tekrar dene."));
            return;
        }
        if (!pending.add(id)) { deny(player, plugin.messages().get("rtp-already")); return; }
        pendingStart.put(id, player.getLocation().clone());
        if (!canUseRtpHere(player)) { pending.remove(id); pendingStart.remove(id); deny(player, plugin.messages().get("rtp-zone-denied")); return; }
        List<String> denied = rtpCfg.getStringList("DENIED-WORLDS");
        if (denied.stream().anyMatch(w -> w.equalsIgnoreCase(player.getWorld().getName()))) {
            pending.remove(id); pendingStart.remove(id); deny(player, msg("DISABLED", "&cRTP bu dünyada kapalı.")); return;
        }

        ConfigurationSection ws = rtpCfg.getConfigurationSection("WORLD-SETTINGS." + worldName);
        if (ws == null) { pending.remove(id); pendingStart.remove(id); deny(player, msg("WORLD-NOT-EXIST", "&cDünya bulunamadı.")); return; }
        World world = Bukkit.getWorld(worldName);
        if (world == null) { pending.remove(id); pendingStart.remove(id); deny(player, msg("WORLD-NOT-EXIST", "&cDünya bulunamadı.")); return; }

        int cooldownSec = ws.getInt("COOLDOWN", rtpCfg.getInt("SETTINGS.COOLDOWN", 30));
        Long last = cooldown.get(id);
        if (last != null) {
            long left = cooldownSec - (System.currentTimeMillis() - last) / 1000;
            if (left > 0) { pending.remove(id); pendingStart.remove(id); deny(player, msg("COOLDOWN", "&cTekrar RTP için {remaining}s beklemelisin.").replace("{remaining}", String.valueOf(left))); return; }
        }

        int minR = Math.max(1, ws.getInt("MIN-RADIUS", 200));
        int maxR = Math.max(minR, ws.getInt("MAX-RADIUS", 5000));
        int cx = ws.getInt("CENTER-X", 0), cz = ws.getInt("CENTER-Z", 0);
        int attempts = Math.max(1, rtpCfg.getInt("SETTINGS.MAX-ATTEMPTS", 40));
        int delay = Math.max(0, plugin.getConfig().getInt("teleport-delay-seconds", plugin.getConfig().getInt("rtp.countdown-seconds", 5)));

        Location target = findSafe(world, minR, maxR, cx, cz, attempts);
        if (target == null) {
            pending.remove(id);
            pendingStart.remove(id);
            deny(player, msg("MAX-ATTEMPTS", "&cGüvenli bir RTP noktası bulunamadı."));
            return;
        }

        player.sendMessage(ColorUtil.text(plugin.messages().get("rtp-countdown-start")
                .replace("{sec}", String.valueOf(delay)).replace("{world}", displayWorldName(world))));
        for (int i = delay; i >= 1; i--) {
            final int left = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player p = Bukkit.getPlayer(id);
                if (p == null || !pending.contains(id)) return;
                p.sendActionBar(ColorUtil.text(plugin.messages().get("rtp-countdown-bar").replace("{sec}", String.valueOf(left))));
                try { p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.8f + left * 0.15f); } catch (Throwable ignored) {}
            }, (long) (delay - i) * 20L);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> finish(player, target, world, delay), (long) delay * 20L);
    }

    private void finish(Player player, Location target, World world, int delay) {
        UUID id = player.getUniqueId();
        pending.remove(id);
        pendingStart.remove(id);
        if (!player.isOnline()) return;
        // Revalidate immediately before teleport. World may have changed during countdown.
        Location safe = validate(target);
        if (safe == null || safe.getWorld() != world) {
            safe = findSafe(world,
                    rtpCfg.getInt("WORLD-SETTINGS." + world.getName() + ".MIN-RADIUS", 200),
                    rtpCfg.getInt("WORLD-SETTINGS." + world.getName() + ".MAX-RADIUS", 5000),
                    rtpCfg.getInt("WORLD-SETTINGS." + world.getName() + ".CENTER-X", 0),
                    rtpCfg.getInt("WORLD-SETTINGS." + world.getName() + ".CENTER-Z", 0),
                    rtpCfg.getInt("SETTINGS.MAX-ATTEMPTS", 16));
        }
        if (safe == null) { deny(player, "&cTeleport öncesi güvenli konum doğrulanamadı."); return; }
        cooldown.put(id, System.currentTimeMillis());
        player.teleport(safe);
        try { player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f); } catch (Throwable ignored) {}
        player.sendMessage(ColorUtil.text(plugin.messages().get("rtp-done")
                .replace("{x}", String.valueOf(safe.getBlockX())).replace("{y}", String.valueOf(safe.getBlockY()))
                .replace("{z}", String.valueOf(safe.getBlockZ())).replace("{world}", displayWorldName(world))));
    }

    private Location findSafe(World w, int min, int max, int cx, int cz, int attempts) {
        // Uniform area sampling prevents the old "mostly edge of radius" feeling.
        double min2 = (double) min * min;
        double max2 = (double) max * max;
        for (int i = 0; i < attempts; i++) {
            double radius = Math.sqrt(min2 + random.nextDouble() * Math.max(1.0, max2 - min2));
            double angle = random.nextDouble() * Math.PI * 2;
            int x = cx + (int) Math.round(Math.cos(angle) * radius);
            int z = cz + (int) Math.round(Math.sin(angle) * radius);
            Location loc = pickSpot(w, x, z);
            if (loc != null) return loc;
        }
        return null;
    }

    private Location pickSpot(World w, int x, int z) {
        int y;
        try { y = w.getHighestBlockYAt(x, z, org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES); }
        catch (Throwable e) { return null; }
        if (y <= w.getMinHeight() + 1 || y >= w.getMaxHeight() - 2) return null;

        // Nether roof protection: configurable hard ceiling, plus bedrock/roof detection.
        if (w.getEnvironment() == World.Environment.NETHER) {
            int roofY = rtpCfg.getInt("SAFETY.NETHER.MAX-SURFACE-Y", 118);
            if (y > roofY) return null;
            // Never use the top bedrock/roof area even if a custom world has an unusual heightmap.
            for (int checkY = Math.max(w.getMinHeight(), y - 3); checkY <= Math.min(w.getMaxHeight() - 1, y + 4); checkY++) {
                if (w.getBlockAt(x, checkY, z).getType() == Material.BEDROCK && checkY >= roofY - 2) return null;
            }
        }

        Block ground = w.getBlockAt(x, y, z);
        Block feet = w.getBlockAt(x, y + 1, z);
        Block head = w.getBlockAt(x, y + 2, z);
        Material g = ground.getType();
        if (!isSafeGround(g) || !isSafeAir(feet.getType()) || !isSafeAir(head.getType())) return null;
        if (ground.isLiquid() || feet.isLiquid() || head.isLiquid()) return null;
        if (w.getEnvironment() == World.Environment.NETHER && (g == Material.BEDROCK || feet.getType() == Material.BEDROCK)) return null;
        if (isDangerous(g) || isDangerous(feet.getType()) || isDangerous(head.getType())) return null;
        if (g.name().contains("LEAVES") || g.name().contains("LOG") || g.name().contains("WOOD")) return null;
        return new Location(w, x + 0.5, y + 1.0, z + 0.5);
    }

    private Location validate(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        return pickSpot(loc.getWorld(), loc.getBlockX(), loc.getBlockZ());
    }

    private boolean isSafeGround(Material m) {
        return m.isSolid() && !m.isAir() && m != Material.LAVA && m != Material.WATER && m != Material.MAGMA_BLOCK
                && m != Material.FIRE && m != Material.SOUL_FIRE && m != Material.CACTUS && m != Material.SWEET_BERRY_BUSH
                && m != Material.POWDER_SNOW;
    }
    private boolean isSafeAir(Material m) {
        return m.isAir() || m == Material.SHORT_GRASS || m == Material.TALL_GRASS
                || m == Material.FERN || m == Material.LARGE_FERN;
    }
    private boolean isDangerous(Material m) {
        String n = m.name();
        return n.contains("CAMPFIRE") || n.contains("MAGMA") || n.contains("PORTAL")
                || n.contains("WEB") || n.contains("POWDER_SNOW") || n.contains("SWEET_BERRY")
                || n.contains("POINTED_DRIPSTONE") || n.contains("END_PORTAL")
                || n.contains("LAVA") || n.contains("WATER");
    }
    public void cancel(Player player) {
        UUID id = player.getUniqueId();
        if (pending.remove(id)) {
            pendingStart.remove(id);
            player.sendActionBar(ColorUtil.text("&cRTP iptal edildi: hareket ettin."));
        }
    }

    /**
     * Adds a player to the two-player RTP queue. When a second player joins the same
     * world queue, both are teleported to exactly the same safe location.
     */
    public boolean joinQueue(Player player, String requestedWorld) {
        if (player == null || !player.isOnline()) return false;
        if (!rtpCfg.getBoolean("ENABLED", true)) {
            deny(player, msg("DISABLED", "&cRTP şu anda kapalı."));
            return false;
        }
        if (plugin.combat() != null && plugin.combat().isInCombat(player)) {
            deny(player, plugin.messages().get("combat-command-blocked"));
            return false;
        }
        String worldName = resolveWorldName(requestedWorld);
        if (!"world".equalsIgnoreCase(worldName) && !"world_nether".equalsIgnoreCase(worldName)) {
            deny(player, "&cRTP sırası sadece Dünya ve Nether için kullanılabilir.");
            return false;
        }
        World world = Bukkit.getWorld(worldName);
        ConfigurationSection ws = rtpCfg.getConfigurationSection("WORLD-SETTINGS." + worldName);
        if (world == null || ws == null) {
            deny(player, msg("WORLD-NOT-EXIST", "&cDünya bulunamadı."));
            return false;
        }
        if (!canUseRtpHere(player)) {
            deny(player, plugin.messages().get("rtp-zone-denied"));
            return false;
        }
        if (isQueued(player.getUniqueId())) {
            deny(player, "&eZaten RTP sırasında bekliyorsun.");
            return false;
        }
        if (pending.contains(player.getUniqueId())) {
            deny(player, "&eŞu anda normal RTP işlemin devam ediyor.");
            return false;
        }
        List<String> denied = rtpCfg.getStringList("DENIED-WORLDS");
        if (denied.stream().anyMatch(w -> w.equalsIgnoreCase(player.getWorld().getName()))) {
            deny(player, msg("DISABLED", "&cRTP bu dünyada kapalı."));
            return false;
        }

        int cooldownSec = ws.getInt("COOLDOWN", rtpCfg.getInt("SETTINGS.COOLDOWN", 30));
        long left = cooldownRemaining(player.getUniqueId(), cooldownSec);
        if (left > 0) {
            deny(player, msg("COOLDOWN", "&cTekrar RTP için {remaining}s beklemelisin.")
                    .replace("{remaining}", String.valueOf(left)));
            return false;
        }

        Deque<UUID> queue = queues.computeIfAbsent(worldName.toLowerCase(java.util.Locale.ROOT), k -> new ArrayDeque<>());
        synchronized (queue) {
            cleanupQueue(queue);
            queue.addLast(player.getUniqueId());
            player.sendMessage(ColorUtil.text("&aRTP sırasına girdin! &7Sıradaki oyuncu bekleniyor..."));
            player.sendActionBar(ColorUtil.text("&eRTP Sırası &8• &f" + queue.size() + " &7oyuncu"));
            if (queue.size() >= 2) {
                UUID first = queue.pollFirst();
                UUID second = queue.pollFirst();
                pairQueue(world, first, second, ws);
            }
        }
        return true;
    }

    private void pairQueue(World world, UUID firstId, UUID secondId, ConfigurationSection ws) {
        Player first = Bukkit.getPlayer(firstId);
        Player second = Bukkit.getPlayer(secondId);
        if (first == null || second == null || !first.isOnline() || !second.isOnline()) {
            if (first != null && first.isOnline()) joinQueue(first, world.getName());
            if (second != null && second.isOnline()) joinQueue(second, world.getName());
            return;
        }
        if (plugin.combat() != null && (plugin.combat().isInCombat(first) || plugin.combat().isInCombat(second))) {
            first.sendMessage(ColorUtil.text("&cEşleşme sırasında bir oyuncu savaşta olduğu için RTP eşleşmesi iptal edildi."));
            second.sendMessage(ColorUtil.text("&cEşleşme sırasında bir oyuncu savaşta olduğu için RTP eşleşmesi iptal edildi."));
            return;
        }

        int minR = Math.max(1, ws.getInt("MIN-RADIUS", 200));
        int maxR = Math.max(minR, ws.getInt("MAX-RADIUS", 5000));
        int cx = ws.getInt("CENTER-X", 0), cz = ws.getInt("CENTER-Z", 0);
        int attempts = Math.max(1, rtpCfg.getInt("SETTINGS.MAX-ATTEMPTS", 40));
        Location target = findSafe(world, minR, maxR, cx, cz, attempts * 2);
        if (target == null) {
            first.sendMessage(ColorUtil.text("&cİkiniz için güvenli bir RTP konumu bulunamadı. Sıraya tekrar girebilirsiniz."));
            second.sendMessage(ColorUtil.text("&cİkiniz için güvenli bir RTP konumu bulunamadı. Sıraya tekrar girebilirsiniz."));
            return;
        }

        long now = System.currentTimeMillis();
        cooldown.put(firstId, now);
        cooldown.put(secondId, now);
        first.teleport(target.clone());
        second.teleport(target.clone());
        try {
            first.playSound(first.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            second.playSound(second.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        } catch (Throwable ignored) {}
        String worldLabel = displayWorldName(world);
        String msg = "&aRTP eşleşmesi bulundu! &f" + worldLabel + " &7• &faynı konuma ışınlandınız. &7Ne yapacağınız size kalmış.";
        first.sendMessage(ColorUtil.text(msg));
        second.sendMessage(ColorUtil.text(msg));
        first.sendActionBar(ColorUtil.text("&aRTP eşleşmesi tamamlandı!"));
        second.sendActionBar(ColorUtil.text("&aRTP eşleşmesi tamamlandı!"));
    }

    private boolean isQueued(UUID id) {
        for (Deque<UUID> queue : queues.values()) {
            synchronized (queue) {
                if (queue.contains(id)) return true;
            }
        }
        return false;
    }

    private void cleanupQueue(Deque<UUID> queue) {
        queue.removeIf(id -> {
            Player p = Bukkit.getPlayer(id);
            return p == null || !p.isOnline();
        });
    }

    private long cooldownRemaining(UUID id, int cooldownSec) {
        Long last = cooldown.get(id);
        if (last == null) return 0;
        return Math.max(0, cooldownSec - (System.currentTimeMillis() - last) / 1000);
    }

    public int queueSize(String requestedWorld) {
        String worldName = resolveWorldName(requestedWorld);
        Deque<UUID> q = queues.get(worldName == null ? "" : worldName.toLowerCase(java.util.Locale.ROOT));
        if (q == null) return 0;
        synchronized (q) { cleanupQueue(q); return q.size(); }
    }

    public String resolveWorldName(String raw) {
        if (raw == null) return null;
        String v = raw.toLowerCase(java.util.Locale.ROOT);
        if (v.equals("dünya") || v.equals("dunya") || v.equals("overworld")) return "world";
        if (v.equals("nether")) return "world_nether";
        if (v.equals("end") || v.equals("the_end")) return "world_the_end";
        return raw;
    }

    public String displayWorldName(World world) {
        if (world == null) return "Bilinmeyen";
        return switch (world.getEnvironment()) {
            case NETHER -> "Nether";
            case THE_END -> "End";
            default -> "Dünya";
        };
    }

    private void deny(Player p, String message) { p.sendMessage(ColorUtil.text(message)); }
    private boolean canUseRtpHere(Player p) {
        if (!plugin.getConfig().getBoolean("rtp-zones.enabled", false)) return true;
        List<String> from = plugin.getConfig().getStringList("rtp-zones.use-from-cuboids");
        if (from == null || from.isEmpty()) return true;
        for (String name : from) { var c = plugin.cuboids().get(name); if (c != null && c.contains(p.getLocation())) return true; }
        return false;
    }
    private String msg(String key, String def) { String m = rtpCfg.getString("MESSAGES." + key); return m != null ? m : def; }
}
