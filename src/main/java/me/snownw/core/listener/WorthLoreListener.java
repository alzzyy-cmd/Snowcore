package me.snownw.core.listener;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.data.SettingsStore;
import me.snownw.core.util.ColorUtil;
import me.snownw.core.util.MoneyFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Adds/removes Worth: $X lore based on Ayarlar WORTH_LORE. */
public final class WorthLoreListener implements Listener {

    private static final String MARKER = "Değer:";
    private final SnowNWCorePlugin plugin;

    public WorthLoreListener(SnowNWCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> refresh(e.getPlayer()), 10L);
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent e) {
        if (e.getPlayer() instanceof Player p) refresh(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p) {
            plugin.getServer().getScheduler().runTask(plugin, () -> refresh(p));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p) {
            plugin.getServer().getScheduler().runTask(plugin, () -> refresh(p));
        }
    }

    public void refresh(Player player) {
        boolean show = plugin.settings().get(player.getUniqueId(), SettingsStore.Toggle.WORTH_LORE);
        for (ItemStack item : player.getInventory().getContents()) {
            apply(item, show);
        }
        for (ItemStack item : player.getInventory().getArmorContents()) {
            apply(item, show);
        }
        apply(player.getInventory().getItemInOffHand(), show);
        player.updateInventory();
    }

    private void apply(ItemStack item, boolean show) {
        if (item == null || item.getType().isAir()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.removeIf(c -> PlainTextComponentSerializer.plainText().serialize(c).contains(MARKER));

        if (show) {
            double price = plugin.worth().price(item.getType());
            if (price > 0) {
                String line = plugin.getConfig().getString("worth.lore-format", "&7Değer: &a${amount}")
                        .replace("{amount}", MoneyFormat.nicest(price * item.getAmount()));
                lore.add(ColorUtil.text(line));
            }
        }
        meta.lore(lore.isEmpty() ? null : lore);
        item.setItemMeta(meta);
    }
}
