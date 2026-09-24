package me.snownw.core.listener;

import me.snownw.core.SnowNWCorePlugin;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

public final class SpawnerListener implements Listener {
    private final SnowNWCorePlugin plugin;
    public SpawnerListener(SnowNWCorePlugin plugin){this.plugin=plugin;}
    @EventHandler public void place(BlockPlaceEvent e){
        ItemStack item=e.getItemInHand(); EntityType type=plugin.shop().spawnerType(item); if(type==null)return;
        Block b=e.getBlockPlaced(); if(!(b.getState() instanceof CreatureSpawner sp))return;
        sp.setSpawnedType(type); sp.update(true,false);
    }
    @EventHandler public void breakBlock(BlockBreakEvent e){
        if(!(e.getBlock().getState() instanceof CreatureSpawner sp))return;
        if(!plugin.getConfig().getBoolean("spawners.drop-on-break", true)) return;
        if(!e.getPlayer().hasPermission("snownwcore.spawner.break")) return;
        e.setExpToDrop(0);
        e.setDropItems(false);
        var type=sp.getSpawnedType();
        var entry=plugin.shop().entries().stream().filter(x->x.spawnerType()==type).findFirst().orElse(null);
        if(entry!=null) e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(),plugin.shop().createItem(entry));
    }
}
