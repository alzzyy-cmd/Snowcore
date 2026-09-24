package me.snownw.core.listener;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.service.AreaService;
import me.snownw.core.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime behavior for /alan-created areas. */
public final class AreaListener implements Listener {
    private final SnowNWCorePlugin plugin;
    private final Map<String, Long> countdownEnd = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> occupants = new ConcurrentHashMap<>();
    private final Map<UUID, Long> afkEnteredAt = new ConcurrentHashMap<>();
    private final Map<UUID, Long> afkLastReward = new ConcurrentHashMap<>();
    private final Map<UUID, String> currentAfkArea = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastSpecialArea = new ConcurrentHashMap<>();

    public AreaListener(SnowNWCorePlugin plugin){ this.plugin=plugin; startTicker(); }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void breakBlock(BlockBreakEvent e){
        if(plugin.areas().isProtected(e.getBlock().getLocation(),"block-break")){ e.setCancelled(true); }
    }
    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void placeBlock(BlockPlaceEvent e){
        if(plugin.areas().isProtected(e.getBlock().getLocation(),"block-place")){ e.setCancelled(true); }
    }
    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void interact(PlayerInteractEvent e){
        if(e.getClickedBlock()==null)return;
        var a=plugin.areas().at(e.getClickedBlock().getLocation(), AreaService.Type.NORMAL);
        if(a!=null && !a.cfg().getBoolean("protection.interaction",true)) e.setCancelled(true);
        String type=e.getClickedBlock().getType().name();
        if(a!=null && (type.contains("CHEST")||type.contains("BARREL")||type.contains("SHULKER")) && !a.cfg().getBoolean("protection.chest-open",true)) e.setCancelled(true);
        if(a!=null && !a.cfg().getBoolean("protection.other-interactions",true) && !(type.contains("CHEST")||type.contains("BARREL")||type.contains("SHULKER"))) e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void move(PlayerMoveEvent e){
        if(e.getTo()==null)return;
        if(e.getFrom().getBlockX()==e.getTo().getBlockX()&&e.getFrom().getBlockY()==e.getTo().getBlockY()&&e.getFrom().getBlockZ()==e.getTo().getBlockZ())return;
        var p=e.getPlayer();
        for(var a:plugin.areas().all()){
            boolean now=a.contains(e.getTo());
            boolean was=a.contains(e.getFrom());
            if(now && !was) enter(p,a);
            if(!now && was) leave(p,a);
        }
    }
    @EventHandler public void quit(PlayerQuitEvent e){ for(var a:plugin.areas().all()) occupants.getOrDefault(a.name().toLowerCase(Locale.ROOT),Set.of()).remove(e.getPlayer().getUniqueId()); }

    private void enter(org.bukkit.entity.Player p, AreaService.Area a){
        String key=a.name().toLowerCase(Locale.ROOT);
        switch(a.type()){
            case RTPZONE -> { occupants.computeIfAbsent(key,k->ConcurrentHashMap.newKeySet()).add(p.getUniqueId()); startCountdown(a); }
            case AFK -> {
                UUID id = p.getUniqueId();
                afkEnteredAt.put(id, System.currentTimeMillis());
                afkLastReward.remove(id);
                currentAfkArea.put(id, key);
                p.sendMessage(ColorUtil.text("&bSnowNW &7» &fAFK alanına giriş yaptınız."));
                if(a.cfg().getBoolean("afk.teleport.enabled",true)) teleportAfk(p,a);
            }
            case END,DUNYA,NETHER -> { Location dest=plugin.areas().randomSafeInWorldType(a); if(dest!=null) p.teleport(dest); }
            case STASH -> { }
            default -> {}
        }
    }
    private void leave(org.bukkit.entity.Player p, AreaService.Area a){
        String key=a.name().toLowerCase(Locale.ROOT);
        if(a.type()==AreaService.Type.RTPZONE) occupants.getOrDefault(key,Set.of()).remove(p.getUniqueId());
        if(a.type()==AreaService.Type.AFK) {
            UUID id = p.getUniqueId();
            String current = currentAfkArea.get(id);
            if (key.equals(current)) {
                afkEnteredAt.remove(id);
                afkLastReward.remove(id);
                currentAfkArea.remove(id);
            }
        }
    }
    private void teleportAfk(org.bukkit.entity.Player p, AreaService.Area a){
        String targetName=a.cfg().getString("afk.teleport.target-area","");
        if(targetName == null || targetName.isBlank() || targetName.equalsIgnoreCase(a.name())) return;
        AreaService.Area target=plugin.areas().get(targetName);
        if(target==null){
            p.sendMessage(ColorUtil.text("&cAFK alanının ışınlanma hedefi ayarlanmamış."));
            return;
        }
        Location l=plugin.areas().areaSpawn(target);
        if(l!=null && l.getWorld()!=null) p.teleport(l);
    }
    private void startCountdown(AreaService.Area a){
        String key=a.name().toLowerCase(Locale.ROOT);
        if(countdownEnd.containsKey(key))return;
        long seconds=Math.max(1,a.cfg().getLong("rtpzone.countdown-seconds",30));
        countdownEnd.put(key,System.currentTimeMillis()+seconds*1000L);
        updateHolo(a,seconds);
        Bukkit.getScheduler().runTaskTimer(plugin,task->{
            long left=Math.max(0,(countdownEnd.getOrDefault(key,0L)-System.currentTimeMillis()+999)/1000);
            Set<UUID> set=occupants.getOrDefault(key,Set.of()); set.removeIf(id->{var p=Bukkit.getPlayer(id); return p==null||!p.isOnline()||!a.contains(p.getLocation());});
            updateHolo(a,left);
            if(set.isEmpty()){ countdownEnd.remove(key); task.cancel(); return; }
            if(left<=0){ countdownEnd.remove(key); teleportGroup(a,set); task.cancel(); }
        },0L,20L);
    }
    private void teleportGroup(AreaService.Area source,Set<UUID> ids){
        String target=source.cfg().getString("rtpzone.destination-area","");
        AreaService.Area dest=plugin.areas().get(target);
        if(dest==null){ for(UUID id:ids){var p=Bukkit.getPlayer(id);if(p!=null)p.sendMessage(ColorUtil.text("&cRTP Zone hedef alanı ayarlanmamış."));} return; }
        for(UUID id:new HashSet<>(ids)){var p=Bukkit.getPlayer(id);if(p==null)continue; Location l=plugin.areas().randomSafeIn(dest); if(l!=null)p.teleport(l);}
    }
    private void updateHolo(AreaService.Area a,long left){
        String key=a.name().toLowerCase(Locale.ROOT); int count=occupants.getOrDefault(key,Set.of()).size();
        plugin.areas().updateHologram(a.name(),"&cRTP Zone\n&fKalan süre: &c"+left+"\n&fKaç oyuncu var: &c"+count);
    }
    public long remainingSeconds(String areaName){
        long end=countdownEnd.getOrDefault(areaName.toLowerCase(Locale.ROOT),0L);
        return end<=0?0:Math.max(0,(end-System.currentTimeMillis()+999)/1000);
    }
    public int playerCount(String areaName){ return occupants.getOrDefault(areaName.toLowerCase(Locale.ROOT),Set.of()).size(); }

    private void startTicker(){
        Bukkit.getScheduler().runTaskTimer(plugin,()->{
            long now=System.currentTimeMillis();
            for(var p:Bukkit.getOnlinePlayers()) {
                UUID id = p.getUniqueId();
                String key = currentAfkArea.get(id);
                if (key == null) continue;
                var a = plugin.areas().get(key);
                if (a == null || a.type() != AreaService.Type.AFK || !a.contains(p.getLocation())) {
                    currentAfkArea.remove(id);
                    afkEnteredAt.remove(id);
                    afkLastReward.remove(id);
                    continue;
                }
                if (!a.cfg().getBoolean("afk.shards.enabled", true)) continue;
                long interval=Math.max(1,a.cfg().getLong("afk.shards.interval-seconds",60))*1000L;
                long amount=Math.max(1,a.cfg().getLong("afk.shards.amount",1));
                long entered=afkEnteredAt.getOrDefault(id, now);
                long last=afkLastReward.getOrDefault(id, entered);
                if(now-entered >= interval && (last==entered || now-last >= interval)){
                    plugin.shards().add(id, amount);
                    afkLastReward.put(id, now);
                    p.sendActionBar(ColorUtil.text("&b+"+amount+" Shard"));
                }
            }
        },20L,20L);
    }
}
