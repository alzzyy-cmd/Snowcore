package me.snownw.core.service;

import me.snownw.core.SnowNWCorePlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Permission based kit service. No kit is usable by a normal player without its explicit permission. */
public final class KitService {
    public record Kit(String id, String displayName, String permission, long cooldownSeconds, List<ItemStack> items) {}

    private final SnowNWCorePlugin plugin;
    private final Map<String, Kit> kits = new LinkedHashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    public KitService(SnowNWCorePlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        kits.clear();
        if (!plugin.getConfig().getBoolean("kits.enabled", true)) return;
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("kits");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            if (id.equalsIgnoreCase("enabled")) continue;
            ConfigurationSection k = section.getConfigurationSection(id);
            if (k == null) continue;
            String permission = k.getString("permission", "snownwcore.kit." + id.toLowerCase());
            String display = k.getString("display-name", "&f" + id);
            long cooldown = Math.max(0L, k.getLong("cooldown-seconds", 86400L));
            List<ItemStack> items = new ArrayList<>();
            for (String raw : k.getStringList("items")) {
                ItemStack item = parseItem(raw);
                if (item != null) items.add(item);
            }
            kits.put(id.toLowerCase(), new Kit(id.toLowerCase(), display, permission, cooldown, Collections.unmodifiableList(items)));
        }
    }

    public List<Kit> all() { return List.copyOf(kits.values()); }

    public List<Kit> available(Player player) {
        List<Kit> out = new ArrayList<>();
        for (Kit kit : kits.values()) {
            if (player.hasPermission(kit.permission())) out.add(kit);
        }
        return out;
    }

    public Kit get(String id) { return id == null ? null : kits.get(id.toLowerCase()); }

    public long remaining(Player player, Kit kit) {
        Long until = cooldowns.getOrDefault(player.getUniqueId(), Map.of()).get(kit.id());
        if (until == null) return 0L;
        long left = until - System.currentTimeMillis();
        if (left <= 0) {
            Map<String, Long> map = cooldowns.get(player.getUniqueId());
            if (map != null) map.remove(kit.id());
            return 0L;
        }
        return (long) Math.ceil(left / 1000.0D);
    }

    public boolean claim(Player player, String id) {
        Kit kit = get(id);
        if (kit == null || !player.hasPermission(kit.permission())) return false;
        if (remaining(player, kit) > 0) return false;
        if (kit.items().isEmpty()) return false;

        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(
                kit.items().stream().map(ItemStack::clone).toArray(ItemStack[]::new));
        if (!leftovers.isEmpty()) {
            // Do not consume the cooldown when the inventory cannot fit the full kit.
            leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            // The kit is still granted, but overflow is dropped visibly rather than silently lost.
        }
        if (kit.cooldownSeconds() > 0) {
            cooldowns.computeIfAbsent(player.getUniqueId(), u -> new ConcurrentHashMap<>())
                    .put(kit.id(), System.currentTimeMillis() + kit.cooldownSeconds() * 1000L);
        }
        return true;
    }

    private ItemStack parseItem(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String[] split = raw.trim().split(":", 2);
        Material material = Material.matchMaterial(split[0].trim());
        if (material == null || !material.isItem()) return null;
        int amount = 1;
        if (split.length == 2) {
            try { amount = Math.max(1, Math.min(material.getMaxStackSize(), Integer.parseInt(split[1].trim()))); }
            catch (NumberFormatException ignored) {}
        }
        return new ItemStack(material, amount);
    }
}
