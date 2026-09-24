package me.snownw.core.listener;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.service.AmethystToolService;
import me.snownw.core.util.ColorUtil;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;
import java.util.*;

public final class AmethystToolListener implements Listener {
    private final SnowNWCorePlugin plugin;
    private final AmethystToolService service;
    // Re-entrancy korumasi: sentetik BlockBreakEvent'lerin bu listener'i
    // tekrar tetikleyip sonsuz donguye girmesini engeller (Watchdog kilitlenmesi)
    private boolean processingBreak = false;
    public AmethystToolListener(SnowNWCorePlugin plugin) { this.plugin = plugin; this.service = plugin.amethystTools(); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void breakBlock(BlockBreakEvent e) {
        if (processingBreak) return; // sentetik event - sonsuz donguyu engelle
        Player p = e.getPlayer(); ItemStack item = p.getInventory().getItemInMainHand();
        AmethystToolService.Type type = service.type(item);
        if (type == null) return;
        if (!service.allowedWorld(p)) { e.setCancelled(true); p.sendMessage(ColorUtil.text("&cBu dünyada Amethyst eşyaları kullanılamaz.")); return; }
        if (service.expired(item)) { e.setCancelled(true); p.getInventory().setItemInMainHand(null); p.sendMessage(ColorUtil.text("&#9B59B6[Amethyst] &fEşyanın süresi doldu.")); return; }
        if (type != AmethystToolService.Type.DRILL && type != AmethystToolService.Type.SHOVEL) return;
        Set<Material> allowed = type == AmethystToolService.Type.DRILL ? null : service.shovelBlocks();
        Set<Material> disabled = type == AmethystToolService.Type.DRILL ? service.disabledBlocks() : Set.of();
        if ((allowed != null && !allowed.contains(e.getBlock().getType())) || disabled.contains(e.getBlock().getType())) return;
        int radius = service.section(type) == null ? 1 : service.section(type).getInt("radius", 1);
        List<Block> blocks = new ArrayList<>();
        Block o = e.getBlock();
        for (int x=-radius;x<=radius;x++) for(int y=-radius;y<=radius;y++) for(int z=-radius;z<=radius;z++) {
            if (x==0&&y==0&&z==0) continue;
            Block b=o.getRelative(x,y,z);
            if (b.getType().isAir()) continue;
            if (allowed != null && !allowed.contains(b.getType())) continue;
            if (disabled.contains(b.getType())) continue;
            blocks.add(b);
        }
        processingBreak = true;
        try {
            for (Block b : blocks) {
                BlockBreakEvent simulated = new BlockBreakEvent(b,p);
                plugin.getServer().getPluginManager().callEvent(simulated);
                if (!simulated.isCancelled()) b.breakNaturally(item);
            }
        } finally {
            processingBreak = false;
        }
        service.particles(o.getLocation()); service.sound(p,"BREAK");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void interact(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p=e.getPlayer(); ItemStack item=p.getInventory().getItemInMainHand();
        AmethystToolService.Type type=service.type(item); if(type==null) return;
        if(service.expired(item)){e.setCancelled(true);p.getInventory().setItemInMainHand(null);p.sendMessage(ColorUtil.text("&#9B59B6[Amethyst] &fEşyanın süresi doldu."));return;}
        if(type==AmethystToolService.Type.BUCKET && (e.getAction()==Action.RIGHT_CLICK_BLOCK||e.getAction()==Action.RIGHT_CLICK_AIR)){
            e.setCancelled(true); drain(p,e.getClickedBlock());
        }
    }

    private void drain(Player p, Block target){
        if(target==null){p.sendMessage(ColorUtil.text("&cYakında su bulunamadı."));return;}
        var c=service.section(AmethystToolService.Type.BUCKET); int radius=c==null?1:c.getInt("drain-radius",1); int max=c==null?27:c.getInt("max-drain",27);
        Queue<Block> q=new ArrayDeque<>(); Set<Block> seen=Collections.newSetFromMap(new IdentityHashMap<>()); q.add(target); int n=0;
        while(!q.isEmpty()&&n<max){Block b=q.poll(); if(!seen.add(b))continue; boolean water=b.getType()==Material.WATER; if(b.getBlockData() instanceof Waterlogged wl) water=wl.isWaterlogged(); if(!water) { if(b.getX()==target.getX()&&b.getY()==target.getY()&&b.getZ()==target.getZ()){ for(int dx=-radius;dx<=radius;dx++)for(int dy=-radius;dy<=radius;dy++)for(int dz=-radius;dz<=radius;dz++)q.add(b.getRelative(dx,dy,dz)); } continue;} if(b.getBlockData() instanceof Waterlogged wl){wl.setWaterlogged(false);b.setBlockData(wl);} else b.setType(Material.AIR); service.particles(b.getLocation()); n++; for(Block nb: List.of(b.getRelative(1,0,0),b.getRelative(-1,0,0),b.getRelative(0,1,0),b.getRelative(0,-1,0),b.getRelative(0,0,1),b.getRelative(0,0,-1))) q.add(nb);}
        if(n==0)p.sendMessage(ColorUtil.text("&cYakında su bulunamadı.")); else p.sendMessage(ColorUtil.text("&#9B59B6[Amethyst] &f"+n+" su bloğu boşaltıldı."));
    }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void consume(PlayerItemConsumeEvent e){
        ItemStack item=e.getItem(); if(service.type(item)!=AmethystToolService.Type.SHARD_BOOSTER)return;
        e.setCancelled(true); service.activateBooster(e.getPlayer()); e.getPlayer().getInventory().setItem(e.getHand(), null);
    }
}
