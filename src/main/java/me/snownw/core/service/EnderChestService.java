package me.snownw.core.service;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Genişletilmiş ender sandığı (27 veya 54). Fiziksel + komut açılışı aynı GUI'yi kullanır. */
public final class EnderChestService {

    public static final class Holder implements InventoryHolder {
        private final UUID owner;
        private Inventory inv;
        public Holder(UUID owner) { this.owner = owner; }
        public UUID owner() { return owner; }
        void bind(Inventory inv) { this.inv = inv; }
        @Override public Inventory getInventory() { return inv; }
    }

    private final SnowNWCorePlugin plugin;
    private final Map<UUID, Long> lastOpen = new ConcurrentHashMap<>();
    /** owner -> açık olan viewer (iki kişi aynı anda aynı sandığı açarsa kopyalama olur) */
    private final Map<UUID, UUID> openViewers = new ConcurrentHashMap<>();
    private final File extraFile;
    private YamlConfiguration extra;

    public EnderChestService(SnowNWCorePlugin plugin) {
        this.plugin = plugin;
        extraFile = new File(plugin.getDataFolder(), "enderchest-extra.yml");
        extra = YamlConfiguration.loadConfiguration(extraFile);
    }

    public void reload() {
        extra = YamlConfiguration.loadConfiguration(extraFile);
    }

    public boolean tryCooldown(Player player) {
        long cooldown = plugin.getConfig().getLong("enderchest.cooldown-seconds", 3) * 1000L;
        long now = System.currentTimeMillis();
        Long last = lastOpen.get(player.getUniqueId());
        if (last != null && now - last < cooldown) {
            long left = (cooldown - (now - last) + 999) / 1000;
            player.sendMessage(ColorUtil.text(plugin.messages().get("enderchest-cooldown")
                    .replace("{sec}", String.valueOf(left))));
            return false;
        }
        lastOpen.put(player.getUniqueId(), now);
        return true;
    }

    public void open(Player player) {
        openFor(player, player.getUniqueId());
    }

    /** Viewer, ownerId'nin ender sandığını açar (kendisi veya yetkili). */
    public void openFor(Player viewer, UUID ownerId) {
        if (!plugin.getConfig().getBoolean("enderchest.enabled", true)) {
            viewer.sendMessage(ColorUtil.text(plugin.messages().get("disabled")));
            return;
        }
        // Aynı sandığı iki kişi aynı anda açarsa kaydetmede çakışma/kopyalama olur.
        UUID current = openViewers.get(ownerId);
        if (current != null && !current.equals(viewer.getUniqueId())) {
            Player other = Bukkit.getPlayer(current);
            boolean stillOpen = other != null && other.isOnline()
                    && other.getOpenInventory().getTopInventory().getHolder() instanceof Holder h
                    && h.owner().equals(ownerId);
            if (stillOpen) {
                viewer.sendMessage(ColorUtil.text("&cBu ender sandığı şu anda başka biri tarafından açık. Kopyalama koruması nedeniyle açamazsın."));
                return;
            }
        }
        if (viewer.getUniqueId().equals(ownerId) && !tryCooldown(viewer)) return;

        boolean six = plugin.getConfig().getBoolean("enderchest.six-row", true);
        int size = six ? 54 : 27;
        String title = plugin.getConfig().getString("enderchest.title", "&8Ender Sandığı");
        Holder holder = new Holder(ownerId);
        Inventory inv = Bukkit.createInventory(holder, size, ColorUtil.text(title));
        holder.bind(inv);

        org.bukkit.OfflinePlayer off = Bukkit.getOfflinePlayer(ownerId);
        ItemStack[] base = null;
        if (off.isOnline() && off.getPlayer() != null) {
            base = off.getPlayer().getEnderChest().getContents();
        }
        if (base != null) {
            for (int i = 0; i < Math.min(27, size) && i < base.length; i++) {
                inv.setItem(i, base[i]);
            }
        }
        if (size > 27) {
            for (int i = 27; i < size; i++) {
                ItemStack it = extra.getItemStack(ownerId + "." + i);
                if (it != null) inv.setItem(i, it);
            }
        }
        openViewers.put(ownerId, viewer.getUniqueId());
        viewer.openInventory(inv);
        try {
            viewer.playSound(viewer.getLocation(), org.bukkit.Sound.BLOCK_ENDER_CHEST_OPEN, 1f, 1f);
        } catch (Throwable ignored) {}
        if (viewer.getUniqueId().equals(ownerId)) {
            viewer.sendMessage(ColorUtil.text(plugin.messages().get("enderchest-open")));
        }
    }

    public void saveFrom(Inventory inv, UUID ownerId) {
        org.bukkit.OfflinePlayer off = Bukkit.getOfflinePlayer(ownerId);
        if (off.isOnline() && off.getPlayer() != null) {
            var ender = off.getPlayer().getEnderChest();
            for (int i = 0; i < Math.min(27, inv.getSize()); i++) {
                ender.setItem(i, inv.getItem(i));
            }
        }
        if (inv.getSize() > 27) {
            for (int i = 27; i < inv.getSize(); i++) {
                ItemStack it = inv.getItem(i);
                if (it == null || it.getType().isAir()) extra.set(ownerId + "." + i, null);
                else extra.set(ownerId + "." + i, it);
            }
            try { extra.save(extraFile); } catch (IOException e) {
                plugin.getLogger().warning("enderchest-extra: " + e.getMessage());
            }
        }
    }

    public void releaseViewer(UUID ownerId, UUID viewerId) {
        if (openViewers.get(ownerId) != null && openViewers.get(ownerId).equals(viewerId)) {
            openViewers.remove(ownerId);
        }
    }

    public void markClose(Player player) {
        lastOpen.put(player.getUniqueId(), System.currentTimeMillis());
    }

    public void wipe(UUID uuid) {
        for (int i = 27; i < 54; i++) extra.set(uuid + "." + i, null);
        try { extra.save(extraFile); } catch (IOException ignored) {}
    }
}
