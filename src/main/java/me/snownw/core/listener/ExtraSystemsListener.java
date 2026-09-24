package me.snownw.core.listener;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.service.ExtraSystemsService;
import me.snownw.core.util.ColorUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.*;
import org.bukkit.entity.Player;

public final class ExtraSystemsListener implements Listener {
    private final SnowNWCorePlugin plugin; private final ExtraSystemsService extra;
    public ExtraSystemsListener(SnowNWCorePlugin plugin) { this.plugin=plugin; this.extra=plugin.extras(); }

    @EventHandler public void move(PlayerMoveEvent e) {
        Player p=e.getPlayer();
        // Donmuş oyuncu dönebilir ama yer değiştiremez (yaw/pitch korunur)
        if (extra.isFrozen(p.getUniqueId())) {
            if (e.getTo()!=null && (e.getTo().getX()!=e.getFrom().getX() || e.getTo().getY()!=e.getFrom().getY() || e.getTo().getZ()!=e.getFrom().getZ())
                    && !p.hasPermission("snownwcore.admin.bypassfreeze")) e.setCancelled(true);
            return;
        }
        if (extra.isAfk(p) && e.getTo()!=null && e.getFrom().distanceSquared(e.getTo())>0.01) { extra.setAfk(p,false); p.sendActionBar(ColorUtil.text("&eAFK modu kapandı.")); }
    }
    @EventHandler public void interact(PlayerInteractEvent e) { if(extra.isFrozen(e.getPlayer().getUniqueId())) e.setCancelled(true); }
    @EventHandler public void drop(PlayerDropItemEvent e) { if(extra.isFrozen(e.getPlayer().getUniqueId())) e.setCancelled(true); }
    @EventHandler public void command(PlayerCommandPreprocessEvent e) {
        if(!extra.isFrozen(e.getPlayer().getUniqueId())) return;
        // Yetkili, donmuş oyuncunun cezasını kaldırabilmeli: temel komutlar serbest kalmasın ama yetkili bypass yapar
        if (e.getPlayer().hasPermission("snownwcore.admin.bypassfreeze")) return;
        e.setCancelled(true);
        e.getPlayer().sendMessage(ColorUtil.text("&cDonduruldun! Bu sırada komut kullanamazsın. Yetkili birini bekle."));
    }
    @EventHandler public void quit(PlayerQuitEvent e) { extra.setAfk(e.getPlayer(), false); extra.unfreeze(e.getPlayer()); }
    @EventHandler public void join(PlayerJoinEvent e) {
        // Her girişte rütbe spamı yok; sadece ilk girişte gösterilir
        if (!e.getPlayer().hasPlayedBefore())
            e.getPlayer().sendMessage(ColorUtil.text("&7Rütben: &f"+extra.rank(e.getPlayer().getUniqueId())));
    }
    @EventHandler public void damage(org.bukkit.event.entity.EntityDamageEvent e) {
        // Donmuş oyuncu hasar almaz (kar hasarı ve düşme dahil)
        if (e.getEntity() instanceof Player p && extra.isFrozen(p.getUniqueId())) e.setCancelled(true);
    }
    @EventHandler public void breakBlock(BlockBreakEvent e) {
        if(extra.isFrozen(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }
}
