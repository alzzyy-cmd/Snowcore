
package me.snownw.core.gui;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.util.ColorUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public final class TeamGuiListener implements Listener {
    private final SnowNWCorePlugin plugin;
    public TeamGuiListener(SnowNWCorePlugin plugin) { this.plugin = plugin; }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof TeamGui)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) return;
        ItemStack cur = e.getCurrentItem();
        if (cur == null || cur.getType() == Material.AIR) return;
        Material type = cur.getType();
        var tm = plugin.teams().get(player);

        if (type == Material.LIME_BANNER) {
            player.closeInventory();
            player.sendMessage(ColorUtil.text("&7Create: &f/team create <name>"));
            return;
        }
        if (type == Material.PAPER) {
            player.closeInventory();
            if (plugin.teams().accept(player))
                player.sendMessage(ColorUtil.text(plugin.messages().get("team-joined")));
            else player.sendMessage(ColorUtil.text(plugin.messages().get("team-no-invite")));
            return;
        }
        // Close (no team)
        if (type == Material.STRUCTURE_VOID) {
            player.closeInventory();
            return;
        }
        // Disband
        if (type == Material.BARRIER) {
            if (tm != null && tm.leader().equals(player.getUniqueId())) {
                plugin.teams().disband(tm);
                player.sendMessage(ColorUtil.text(plugin.messages().get("team-disbanded")));
            }
            player.closeInventory();
            return;
        }
        // Teleport team home
        if (type == Material.RED_BED) {
            tm = plugin.teams().get(player);
            if (tm != null && tm.home() != null) {
                player.closeInventory();
                plugin.teleports().teleport(player, tm.home(), "Team Home");
            } else {
                player.sendMessage(ColorUtil.text(plugin.messages().get("team-no-home")));
                new TeamGui(plugin, player).open();
            }
            return;
        }
        // Set team home
        if (type == Material.GREEN_BED) {
            tm = plugin.teams().get(player);
            if (tm != null && tm.leader().equals(player.getUniqueId())) {
                plugin.teams().setHome(tm, player.getLocation());
                player.sendMessage(ColorUtil.text(plugin.messages().get("team-home-set")));
                new TeamGui(plugin, player).open(); // refresh – show RED_BED
            } else {
                player.sendMessage(ColorUtil.text(plugin.messages().get("team-not-leader")));
            }
            return;
        }
        if (type == Material.TNT) {
            tm = plugin.teams().get(player);
            if (tm != null && tm.leader().equals(player.getUniqueId())) {
                plugin.teams().clearHome(tm);
                player.sendMessage(ColorUtil.text(plugin.messages().get("team-home-deleted")));
                new TeamGui(plugin, player).open();
            }
            return;
        }
        if (type == Material.OAK_DOOR) {
            plugin.teams().leave(player);
            player.sendMessage(ColorUtil.text(plugin.messages().get("team-left")));
            player.closeInventory();
            return;
        }
        if (type == Material.PLAYER_HEAD) {
            player.sendMessage(ColorUtil.text("&7Invite: &f/team invite <player>"));
        }
    }
}
