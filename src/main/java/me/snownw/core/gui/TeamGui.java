package me.snownw.core.gui;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.service.TeamService;
import me.snownw.core.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** PrimeCore-style chest GUI for /team (not dialog). */
public final class TeamGui implements InventoryHolder {
    private final SnowNWCorePlugin plugin;
    private final Player player;
    private final Inventory inv;

    public TeamGui(SnowNWCorePlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        TeamService.Team tm = plugin.teams().get(player);
        String title = tm == null ? "&8Team" : ("&8Team · " + tm.name());
        this.inv = Bukkit.createInventory(this, 27, ColorUtil.text(title));
        fill();
    }

    @Override public Inventory getInventory() { return inv; }

    public void open() {
        fill();
        player.openInventory(inv);
    }

    private void fill() {
        inv.clear();
        TeamService.Team tm = plugin.teams().get(player);
        if (tm == null) {
            inv.setItem(11, named(Material.LIME_BANNER, "&aCreate Team",
                    "&7Kullanım: &f/team create <isim>", "&eYardım için tıkla"));
            inv.setItem(13, named(Material.PAPER, "&eAccept Invite",
                    "&7Bekleyen davete katıl", "&eKabul etmek için tıkla"));
            inv.setItem(15, named(Material.STRUCTURE_VOID, "&cClose", "&7Close menu"));
            return;
        }
        boolean leader = tm.leader().equals(player.getUniqueId());
        inv.setItem(4, named(Material.SHIELD, "&f" + tm.name(),
                "&7Members: &f" + tm.members().size(),
                "&7Leader: &f" + nameOf(tm.leader())));
        inv.setItem(10, named(Material.PLAYER_HEAD, "&eInvite",
                "&7/team invite <player>"));
        if (tm.home() != null) {
            inv.setItem(12, named(Material.RED_BED, "&aTeam Home",
                    "&7Işınlanmak için tıkla"));
        } else if (leader) {
            inv.setItem(12, named(Material.GREEN_BED, "&aSet Team Home",
                    "&7Burada kurmak için tıkla"));
        } else {
            inv.setItem(12, named(Material.GRAY_BED, "&7No Team Home"));
        }
        if (leader && tm.home() != null) {
            inv.setItem(14, named(Material.TNT, "&cDelete Team Home"));
        }
        if (leader) {
            inv.setItem(16, named(Material.BARRIER, "&cDisband Team"));
        } else {
            inv.setItem(16, named(Material.OAK_DOOR, "&cLeave Team"));
        }
        // member heads
        int slot = 18;
        for (UUID id : tm.members()) {
            if (slot >= 27) break;
            inv.setItem(slot++, skull(id));
        }
    }

    private ItemStack skull(UUID id) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(id));
        meta.displayName(ColorUtil.text("&f" + nameOf(id)));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(ColorUtil.text("&7Member"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private String nameOf(UUID id) {
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n != null ? n : id.toString().substring(0, 8);
    }

    private ItemStack named(Material mat, String name, String... loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ColorUtil.text(name));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        for (String l : loreLines) lore.add(ColorUtil.text(l));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
