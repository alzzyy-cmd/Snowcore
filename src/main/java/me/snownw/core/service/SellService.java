package me.snownw.core.service;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.util.ColorUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** /sat (eldeki) ve /sathepsi (tüm envanter) — WorthService fiyatlarını kullanır.
 *  Yetki çarpanı: snownwcore.sellmultiplier.<n> (örn. snownwcore.sellmultiplier.2 → 2x). */
public final class SellService {

    private final SnowNWCorePlugin plugin;

    public SellService(SnowNWCorePlugin plugin) {
        this.plugin = plugin;
    }

    /** Oyuncu için en yüksek satış çarpanını döner (1.0 = standart). */
    public double multiplier(Player player) {
        if (!plugin.getConfig().getBoolean("sell.sell-multiplier", true)) return 1.0;
        double best = 1.0;
        for (var pi : player.getEffectivePermissions()) {
            if (!pi.getValue()) continue;
            String p = pi.getPermission();
            if (p.startsWith("snownwcore.sellmultiplier.")) {
                try {
                    double v = Double.parseDouble(p.substring("snownwcore.sellmultiplier.".length()));
                    if (v > best) best = v;
                } catch (NumberFormatException ignored) {}
            }
        }
        return best;
    }

    /** Eldeki eşyayı satar. player el boşsa red mesajı döner. */
    public void sellHand(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("sell-hand-empty")));
            return;
        }
        if (!plugin.getConfig().getBoolean("commands.sell", true)) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("disabled")));
            return;
        }
        if (!plugin.worth().hasWorth(hand.getType())) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("sell-not-sellable")));
            return;
        }
        double mult = multiplier(player);
        double price = plugin.worth().total(hand) * mult;
        int amount = hand.getAmount();
        Material type = hand.getType();
        player.getInventory().setItemInMainHand(null);
        plugin.economy().deposit(player, price);
        player.sendMessage(ColorUtil.text(plugin.messages().get("sell-sold")
                .replace("{amount}", String.valueOf(amount))
                .replace("{item}", prettyName(type))
                .replace("{price}", plugin.economy().format(price))));
    }

    /** Envanterdeki tüm değerli eşyaları satar. */
    public void sellAll(Player player) {
        if (!plugin.getConfig().getBoolean("commands.sellall", true)) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("disabled")));
            return;
        }
        double mult = multiplier(player);
        double total = 0;
        int stacks = 0;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack it = contents[slot];
            if (it == null || it.getType().isAir()) continue;
            if (!plugin.worth().hasWorth(it.getType())) continue;
            total += plugin.worth().total(it) * mult;
            stacks++;
            player.getInventory().setItem(slot, null);
        }
        if (stacks == 0 || total <= 0) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("sell-nothing")));
            return;
        }
        plugin.economy().deposit(player, total);
        player.sendMessage(ColorUtil.text(plugin.messages().get("sell-sold-all")
                .replace("{stacks}", String.valueOf(stacks))
                .replace("{price}", plugin.economy().format(total))));
    }

    public String prettyName(Material m) {
        String raw = m.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        StringBuilder b = new StringBuilder(raw.length());
        boolean upper = true;
        for (char c : raw.toCharArray()) {
            if (upper && Character.isLetter(c)) { b.append(Character.toUpperCase(c)); upper = false; }
            else if (c == ' ') { b.append(c); upper = true; }
            else b.append(c);
        }
        return b.toString();
    }
}
