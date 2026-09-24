package me.snownw.core.gui;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.data.HomeStore;
import me.snownw.core.data.SettingsStore;
import me.snownw.core.util.ColorUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ClassicMenusListener implements Listener {

    private final SnowNWCorePlugin plugin;

    public ClassicMenusListener(SnowNWCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof ClassicMenus.Holder holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) return;
        ItemStack cur = e.getCurrentItem();
        if (cur == null || !cur.hasItemMeta()) return;
        int slot = e.getRawSlot();

        switch (holder.kind) {
            case HOMES -> {
                if (slot == 49) { player.closeInventory(); return; }
                if (slot < 0 || slot >= 45) return;
                int homeSlot = slot + 1;
                int limit = plugin.homes().homeLimit(player);
                if (homeSlot > limit) {
                    player.sendMessage(ColorUtil.text(plugin.messages().get("home-locked")
                            .replace("{n}", String.valueOf(homeSlot))));
                    return;
                }
                HomeStore.Home home = plugin.homes().getSlot(player.getUniqueId(), homeSlot);
                if (e.isRightClick() && home != null) {
                    plugin.homes().deleteSlot(player.getUniqueId(), homeSlot);
                    player.sendMessage(ColorUtil.text(plugin.messages().get("home-deleted")
                            .replace("{name}", "#" + homeSlot)));
                    plugin.classic().openHomes(player);
                } else if (home != null) {
                    plugin.teleports().teleport(player, home.location(), "#" + homeSlot);
                    player.closeInventory();
                } else {
                    plugin.homes().setSlot(player.getUniqueId(), homeSlot, player.getLocation(), null, limit);
                    player.sendMessage(ColorUtil.text(plugin.messages().get("home-set")
                            .replace("{slot}", String.valueOf(homeSlot))));
                    plugin.classic().openHomes(player);
                }
            }
            case SETTINGS -> {
                if (slot == 22) { player.closeInventory(); return; }
                String cat = switch (slot) {
                    case 10 -> "Chat";
                    case 11 -> "PvP";
                    case 12 -> "Gizlilik";
                    case 13 -> "Notifications";
                    case 14 -> "Visuals";
                    case 15 -> "Scoreboard";
                    default -> null;
                };
                if (cat != null) plugin.classic().openAyarlarCat(player, cat);
            }
            case SETTINGS_CAT -> {
                if (slot == 31) { plugin.classic().openAyarlar(player); return; }
                ItemMeta meta = cur.getItemMeta();
                if (meta == null || meta.lore() == null) return;
                for (var line : meta.lore()) {
                    String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(line);
                    if (plain.startsWith("§8") || plain.length() > 2) {
                        String key = plain.replace("§8", "").trim();
                        try {
                            SettingsStore.Toggle t = SettingsStore.Toggle.valueOf(key);
                            boolean on = plugin.settings().toggle(player.getUniqueId(), t);
                            if (t == SettingsStore.Toggle.HIDE_MOBS) {
                                plugin.mobHide().apply(player);
                            }
                            if (t.category().equalsIgnoreCase("Scoreboard") || t == SettingsStore.Toggle.SCOREBOARD) {
                                if (plugin.scoreboard() != null) {
                                    if (t == SettingsStore.Toggle.SCOREBOARD && !on) plugin.scoreboard().clear(player);
                                    else plugin.scoreboard().forceUpdate(player);
                                }
                            }
                            player.sendMessage(ColorUtil.text(plugin.messages().get("settings-toggled")
                                    .replace("{label}", t.label())
                                    .replace("{state}", on ? "&aON" : "&cOFF")));
                            plugin.classic().openAyarlarCat(player, holder.extra);
                        } catch (Exception ignored) {}
                    }
                }
            }
            case STATS, FRIENDS -> {
                if (slot == 22 || slot == 49) player.closeInventory();
            }
        }
    }
}
