package me.snownw.core.service;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.Sign;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Temporary anti-cheat bait stashes. Locations are selected with /alan oluştur stash <1-5>. */
public final class SpawnStashService {
    private final SnowNWCorePlugin plugin;
    private final List<PlacedBlock> active = new ArrayList<>();
    private BukkitTask removalTask;
    private final YamlConfiguration config;
    private Location activeOrigin;
    private int activeType = -1;
    private final Set<UUID> alertedPlayers = new HashSet<>();

    public SpawnStashService(SnowNWCorePlugin plugin) {
        this.plugin = plugin;
        File f = new File(plugin.getDataFolder(), "spawn-stash.yml");
        if (!f.exists()) {
            try { plugin.saveResource("spawn-stash.yml", false); } catch (Throwable ignored) {}
        }
        config = YamlConfiguration.loadConfiguration(f);
    }

    public boolean spawn(Player staff, Integer requestedType) {
        if (!plugin.getConfig().getBoolean("spawn-stash.enabled", true)) return false;
        clear();
        int type = requestedType == null ? randomType() : requestedType;
        ConfigurationSection def = config.getConfigurationSection("types." + type);
        if (def == null || !def.getBoolean("enabled", true)) return false;

        AreaService.Area area = plugin.areas().get("stash" + type);
        if (area == null || area.type() != AreaService.Type.STASH) {
            staff.sendMessage(ColorUtil.text("&cStash alanı bulunamadı: &f/alan oluştur stash " + type));
            return false;
        }
        Location base = anchor(area);
        if (base == null || base.getWorld() == null) return false;
        activeOrigin = base.clone();
        activeType = type;
        alertedPlayers.clear();

        ConfigurationSection blocks = def.getConfigurationSection("blocks");
        if (blocks == null) return false;
        List<String> keys = new ArrayList<>(blocks.getKeys(false));
        keys.sort(Comparator.comparingInt(Integer::parseInt));

        for (String key : keys) {
            ConfigurationSection b = blocks.getConfigurationSection(key);
            if (b == null) continue;
            List<Integer> off = b.getIntegerList("offset");
            if (off.size() < 3) continue;
            Location l = base.clone().add(off.get(0), off.get(1), off.get(2));
            if (!area.contains(l)) {
                staff.sendMessage(ColorUtil.text("&cStash #" + type + " alanı yapının tamamını kapsamıyor. Alanı biraz büyüt."));
                clear();
                return false;
            }
            Material mat = material(b.getString("material"));
            if (mat == null) continue;
            place(l, mat, b);
        }

        int duration = Math.max(1, def.getInt("duration-seconds", plugin.getConfig().getInt("spawn-stash.duration-seconds", 30)));
        removalTask = Bukkit.getScheduler().runTaskLater(plugin, this::clear, duration * 20L);
        staff.sendMessage(ColorUtil.text("&aFake Stash &f#" + type + " &aoluşturuldu. &7" + duration + " saniye sonra kaldırılacak."));
        return true;
    }


    /** Called by the movement listener. This is an alert signal for staff, not an automatic ban. */
    public void handleApproach(Player player) {
        if (player == null || activeOrigin == null || activeOrigin.getWorld() == null || !activeOrigin.getWorld().equals(player.getWorld())) return;
        if (player.hasPermission("snownwcore.spawnstash.bypass") || player.hasPermission("snownwcore.admin") || player.hasPermission("snownwcore.spawnstash")) return;
        double radius = plugin.getConfig().getDouble("spawn-stash.alert-radius", 8.0);
        if (player.getLocation().distanceSquared(activeOrigin) > radius * radius) return;
        if (!alertedPlayers.add(player.getUniqueId())) return;
        String msg = "&c[Fake Stash] &f" + player.getName() + " &7#" + activeType + " stash'e girdi! &8(" + String.format(java.util.Locale.US, "%.1f", player.getLocation().distance(activeOrigin)) + " blok)";
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("snownwcore.spawnstash.alert") || staff.hasPermission("snownwcore.admin")) staff.sendMessage(ColorUtil.text(msg));
        }
        plugin.getLogger().info("Fake Stash alert: " + player.getName() + " -> #" + activeType);
    }
    private int randomType() {
        List<Integer> enabled = new ArrayList<>();
        ConfigurationSection sec = config.getConfigurationSection("types");
        if (sec == null) return 1;
        for (String k : sec.getKeys(false)) {
            try {
                int id = Integer.parseInt(k);
                if (sec.getConfigurationSection(k).getBoolean("enabled", true)
                        && plugin.areas().get("stash" + id) != null
                        && plugin.areas().get("stash" + id).type() == AreaService.Type.STASH) enabled.add(id);
            } catch (Exception ignored) {}
        }
        return enabled.isEmpty() ? 1 : enabled.get(ThreadLocalRandom.current().nextInt(enabled.size()));
    }

    private Location anchor(AreaService.Area a) {
        org.bukkit.World w = Bukkit.getWorld(a.world());
        if (w == null) return null;
        int x = (a.minX() + a.maxX()) / 2;
        int z = (a.minZ() + a.maxZ()) / 2;
        int y = a.minY();
        return new Location(w, x, y, z);
    }

    private void place(Location location, Material material, ConfigurationSection def) {
        Block block = location.getBlock();
        active.add(new PlacedBlock(block, block.getState()));
        block.setType(material, false);

        if (block.getState() instanceof Container container) {
            container.getInventory().clear();
            ConfigurationSection items = def.getConfigurationSection("container-items");
            if (items != null) {
                for (String k : items.getKeys(false)) {
                    ConfigurationSection it = items.getConfigurationSection(k);
                    if (it == null) continue;
                    Material m = material(it.getString("material"));
                    int slot = it.getInt("slot", 0);
                    int amount = Math.max(1, it.getInt("amount", 1));
                    if (m != null && slot >= 0 && slot < container.getInventory().getSize())
                        container.getInventory().setItem(slot, new ItemStack(m, amount));
                }
            }
            container.update(true, false);
        }
        if (block.getState() instanceof CreatureSpawner spawner) {
            try {
                EntityType entity = EntityType.valueOf(def.getString("spawner-type", "ZOMBIE").toUpperCase(Locale.ROOT));
                spawner.setSpawnedType(entity);
                spawner.update(true, false);
            } catch (Exception ignored) {}
        }
        if (block.getState() instanceof Sign sign) {
            List<String> lines = def.getStringList("sign-lines");
            for (int i = 0; i < 4; i++) sign.setLine(i, ColorUtil.text(i < lines.size() ? lines.get(i) : "").toString());
            sign.update(true, false);
        }
    }

    private Material material(String raw) {
        if (raw == null) return null;
        try { return Material.valueOf(raw.toUpperCase(Locale.ROOT)); } catch (Exception e) { return null; }
    }

    public void clear() {
        if (removalTask != null) { removalTask.cancel(); removalTask = null; }
        for (int i = active.size() - 1; i >= 0; i--) {
            PlacedBlock p = active.get(i);
            try { p.state().update(true, false); } catch (Throwable ignored) { p.block().setType(Material.AIR, false); }
        }
        active.clear();
        activeOrigin = null;
        activeType = -1;
        alertedPlayers.clear();
    }

    private record PlacedBlock(Block block, BlockState state) {}
}
