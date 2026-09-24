package me.snownw.core.gui;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.data.HomeStore;
import me.snownw.core.data.SettingsStore;
import me.snownw.core.data.StatsStore;
import me.snownw.core.util.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Diyalog desteksiz istemciler için (1.21.6 ve altı) sandık menüleri. */
public final class ClassicMenus {

    public enum Kind { HOMES, SETTINGS, SETTINGS_CAT, STATS, FRIENDS }

    public static final class Holder implements InventoryHolder {
        public final Kind kind;
        public final String extra;
        private final Inventory inv;
        public Holder(Kind kind, String extra, int size, Component title) {
            this.kind = kind;
            this.extra = extra;
            this.inv = Bukkit.createInventory(this, size, title);
        }
        @Override public Inventory getInventory() { return inv; }
    }

    private final SnowNWCorePlugin plugin;

    public ClassicMenus(SnowNWCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void openEvler(Player player) { openHomes(player); }

    public void openİstatistikler(Player player, UUID target) { openStats(player, target); }

    public void openHomes(Player player) {
        int max = plugin.getConfig().getInt("homes.max-homes",
                plugin.getConfig().getInt("max-homes", 90));
        int limit = plugin.homes().homeLimit(player);
        Holder h = new Holder(Kind.HOMES, null, 54, ColorUtil.text("&8Evler"));
        Inventory inv = h.getInventory();
        for (int slot = 1; slot <= Math.min(45, max); slot++) {
            if (slot > limit) {
                inv.setItem(slot - 1, item(Material.RED_BED, "&c#" + slot + " Kilitli",
                        List.of("&7Açmak için: snownwcore.home." + slot + " yetkisi")));
            } else {
                HomeStore.Home home = plugin.homes().getSlot(player.getUniqueId(), slot);
                if (home != null) {
                    inv.setItem(slot - 1, item(Material.WHITE_BED, "&f#" + slot + " &7" + home.name(),
                            List.of("&7Sol tık: &fışınlan", "&7Sağ tık: &fsil")));
                } else {
                    inv.setItem(slot - 1, item(Material.LIGHT_GRAY_BED, "&7#" + slot + " Boş",
                            List.of("&7Ev kurmak için tıkla")));
                }
            }
        }
        inv.setItem(49, item(Material.BARRIER, "&cKapat", List.of()));
        player.openInventory(inv);
    }

    public void openAyarlar(Player player) {
        if (!plugin.getConfig().getBoolean("menus.classic-enabled", true)) {
            DialogMenus.openAyarlar(plugin, player);
            return;
        }
        Holder h = new Holder(Kind.SETTINGS, null, 27, ColorUtil.text("&8Ayarlar"));
        Inventory inv = h.getInventory();
        inv.setItem(10, item(Material.PAPER, "&fSohbet", List.of("&7Mesaj ayarlarını aç")));
        inv.setItem(11, item(Material.IRON_SWORD, "&fPvP", List.of("&7PvP ayarlarını aç")));
        inv.setItem(12, item(Material.ENDER_EYE, "&fGizlilik", List.of("&7Gizlilik ayarlarını aç")));
        inv.setItem(13, item(Material.BELL, "&fBildirimler", List.of("&7Bildirim ayarlarını aç")));
        inv.setItem(14, item(Material.SPYGLASS, "&fGörünüm", List.of("&7Görünüm ayarlarını aç")));
        inv.setItem(15, item(Material.PAINTING, "&fSkor tablosu", List.of("&7Skor tablosu ayarlarını aç")));
        inv.setItem(16, item(Material.GOLD_INGOT, "&fEkonomi", List.of("&7Ekonomi ayarlarını aç")));
        inv.setItem(22, item(Material.BARRIER, "&cKapat", List.of()));
        player.openInventory(inv);
    }

    public void openAyarlarCat(Player player, String cat) {
        Holder h = new Holder(Kind.SETTINGS_CAT, cat, 36, ColorUtil.text("&8Ayarlar · " + cat));
        Inventory inv = h.getInventory();
        int i = 10;
        for (SettingsStore.Toggle t : SettingsStore.Toggle.values()) {
            if (!t.category().equalsIgnoreCase(cat)) continue;
            boolean on = plugin.settings().get(player.getUniqueId(), t);
            inv.setItem(i++, item(on ? Material.LIME_DYE : Material.GRAY_DYE,
                    "&f" + t.label() + " " + (on ? "&aAÇIK" : "&cKAPALI"),
                    List.of("&7Değiştirmek için tıkla", "&8" + t.name())));
        }
        inv.setItem(31, item(Material.ARROW, "&7Geri", List.of()));
        player.openInventory(inv);
    }

    public void openStats(Player player, UUID target) {
        String name = Bukkit.getOfflinePlayer(target).getName();
        if (name == null) name = "?";
        Holder h = new Holder(Kind.STATS, target.toString(), 27, ColorUtil.text("&8" + name + " · İstatistikler"));
        Inventory inv = h.getInventory();
        inv.setItem(10, item(Material.DIAMOND_SWORD, "&fÖldürme",
                List.of("&7" + plugin.stats().get(target, StatsStore.Type.KILLS))));
        inv.setItem(11, item(Material.SKELETON_SKULL, "&fÖlüm",
                List.of("&7" + plugin.stats().get(target, StatsStore.Type.DEATHS))));
        inv.setItem(12, item(Material.IRON_PICKAXE, "&fKırılan blok",
                List.of("&7" + plugin.stats().get(target, StatsStore.Type.BLOCKS))));
        inv.setItem(13, item(Material.ZOMBIE_HEAD, "&fKesilen yaratık",
                List.of("&7" + plugin.stats().get(target, StatsStore.Type.MOBS))));
        inv.setItem(14, item(Material.CLOCK, "&fOynama süresi",
                List.of("&7" + StatsStore.formatPlaytime(
                        plugin.stats().get(target, StatsStore.Type.PLAYTIME)))));
        inv.setItem(22, item(Material.BARRIER, "&cKapat", List.of()));
        player.openInventory(inv);
    }

    public void openFriends(Player player) {
        Holder h = new Holder(Kind.FRIENDS, null, 54, ColorUtil.text("&8Arkadaşlar"));
        Inventory inv = h.getInventory();
        int i = 0;
        for (UUID id : plugin.friends().following(player.getUniqueId())) {
            if (i >= 45) break;
            String n = Bukkit.getOfflinePlayer(id).getName();
            inv.setItem(i++, item(Material.PLAYER_HEAD, "&f" + (n != null ? n : "?"),
                    List.of("&7Takip ettiğiniz oyuncu", "&7Detaylar için: &f/arkadas")));
        }
        inv.setItem(49, item(Material.BARRIER, "&cKapat", List.of()));
        player.openInventory(inv);
    }

    private ItemStack item(Material mat, String name, List<String> lore) {
        ItemStack s = new ItemStack(mat);
        ItemMeta m = s.getItemMeta();
        if (m != null) {
            m.displayName(ColorUtil.text(name));
            List<Component> l = new ArrayList<>();
            for (String line : lore) l.add(ColorUtil.text(line));
            m.lore(l);
            s.setItemMeta(m);
        }
        return s;
    }
}
