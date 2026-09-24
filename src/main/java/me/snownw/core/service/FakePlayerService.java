package me.snownw.core.service;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.util.ColorUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Görsel sahte oyuncu yardımcısı. Otomatik ceza vermez; yalnızca staff'a olay bildirir. */
public final class FakePlayerService {
    private final SnowNWCorePlugin plugin;
    private final Map<UUID, List<Entity>> active = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> removal = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> alerted = new ConcurrentHashMap<>();
    private BukkitTask watcher;

    public FakePlayerService(SnowNWCorePlugin plugin) {
        this.plugin = plugin;
        startWatcher();
    }

    public boolean spawn(Player staff) {
        clear(staff.getUniqueId());
        Location loc = staff.getLocation().clone();
        List<Entity> parts = new ArrayList<>();
        ArmorStand body = loc.getWorld().spawn(loc, ArmorStand.class, as -> {
            as.setVisible(true);
            as.setGravity(false);
            as.setInvulnerable(true);
            as.setBasePlate(false);
            as.setArms(true);
            as.setCollidable(false);
            as.setSilent(true);
            as.customName(ColorUtil.text("&bSnowNW &fSahte Oyuncu"));
            as.setCustomNameVisible(true);
            as.getEquipment().setHelmet(playerHead(staff));
            as.getEquipment().setChestplate(armor(Material.LEATHER_CHESTPLATE));
            as.getEquipment().setLeggings(armor(Material.LEATHER_LEGGINGS));
            as.getEquipment().setBoots(armor(Material.LEATHER_BOOTS));
        });
        parts.add(body);
        active.put(staff.getUniqueId(), parts);
        alerted.put(staff.getUniqueId(), ConcurrentHashMap.newKeySet());
        long seconds = Math.max(10, plugin.getConfig().getLong("fakeplayer.duration-seconds", 60));
        removal.put(staff.getUniqueId(), plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> clear(staff.getUniqueId()), seconds * 20L));
        staff.sendMessage(ColorUtil.text("&aSahte oyuncu oluşturuldu. &7" + seconds + " saniye sonra kaldırılacak."));
        return true;
    }

    private ItemStack armor(Material material) {
        ItemStack item = new ItemStack(material);
        if (item.getItemMeta() instanceof LeatherArmorMeta meta) item.setItemMeta(meta);
        return item;
    }

    private ItemStack playerHead(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(player);
            head.setItemMeta(meta);
        }
        return head;
    }

    private void startWatcher() {
        long radius = Math.max(2L, plugin.getConfig().getLong("fakeplayer.alert-radius", 8));
        watcher = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            double r2 = radius * radius;
            for (Map.Entry<UUID, List<Entity>> entry : active.entrySet()) {
                Set<UUID> seen = alerted.computeIfAbsent(entry.getKey(), k -> ConcurrentHashMap.newKeySet());
                for (Entity entity : entry.getValue()) {
                    if (entity == null || entity.isDead() || entity.getWorld() == null) continue;
                    for (Player p : entity.getWorld().getPlayers()) {
                        if (p.getUniqueId().equals(entry.getKey())) continue;
                        if (p.getLocation().distanceSquared(entity.getLocation()) <= r2 && seen.add(p.getUniqueId())) {
                            Player staff = plugin.getServer().getPlayer(entry.getKey());
                            if (staff != null && staff.hasPermission("snownwcore.fakeplayer.alert")) {
                                staff.sendMessage(ColorUtil.text("&c[FakePlayer] &f" + p.getName() + " &7sahte oyuncuya yaklaştı. &8(" + radius + " blok)"));
                            }
                            plugin.getLogger().info("[FakePlayer] " + p.getName() + " approached fake player owned by " + entry.getKey());
                        }
                    }
                }
            }
        }, 20L, 20L);
    }

    public void clear(UUID owner) {
        BukkitTask task = removal.remove(owner);
        if (task != null) task.cancel();
        List<Entity> list = active.remove(owner);
        alerted.remove(owner);
        if (list != null) for (Entity entity : list) if (entity != null && !entity.isDead()) entity.remove();
    }

    public void clearAll() {
        for (UUID id : new HashSet<>(active.keySet())) clear(id);
        if (watcher != null) watcher.cancel();
    }
}
