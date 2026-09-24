package me.snownw.core.service;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Named areas stored as individual YAML files under plugins/SnowNWCore/alanlar/. */
public final class AreaService {
    public enum Type { NORMAL, AFK, RTPZONE, END, DUNYA, NETHER, STASH }

    public record Area(String name, Type type, String world, int x1, int y1, int z1, int x2, int y2, int z2,
                       YamlConfiguration cfg) {
        public boolean contains(Location loc) {
            if (loc == null || loc.getWorld() == null || !loc.getWorld().getName().equals(world)) return false;
            int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
            return x >= Math.min(x1, x2) && x <= Math.max(x1, x2)
                    && y >= Math.min(y1, y2) && y <= Math.max(y1, y2)
                    && z >= Math.min(z1, z2) && z <= Math.max(z1, z2);
        }
        public int minX(){ return Math.min(x1,x2); }
        public int maxX(){ return Math.max(x1,x2); }
        public int minY(){ return Math.min(y1,y2); }
        public int maxY(){ return Math.max(y1,y2); }
        public int minZ(){ return Math.min(z1,z2); }
        public int maxZ(){ return Math.max(z1,z2); }
    }

    private final SnowNWCorePlugin plugin;
    private final File folder;
    private final Map<String, Area> areas = new LinkedHashMap<>();
    private final Map<UUID, Location> pos1 = new HashMap<>(), pos2 = new HashMap<>();
    private final Map<String, UUID> holograms = new HashMap<>();

    public AreaService(SnowNWCorePlugin plugin) {
        this.plugin = plugin;
        folder = new File(plugin.getDataFolder(), "alanlar");
        if (!folder.exists()) folder.mkdirs();
        load();
    }

    public void load() {
        areas.clear();
        if (!folder.exists()) folder.mkdirs();
        File[] files = folder.listFiles((d,n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return;
        for (File f : files) {
            YamlConfiguration c = YamlConfiguration.loadConfiguration(f);
            String name = c.getString("name", f.getName().replaceFirst("(?i)\\.yml$", ""));
            Type type;
            try { type = Type.valueOf(c.getString("type", "NORMAL").toUpperCase(Locale.ROOT)); }
            catch (Exception ex) { type = Type.NORMAL; }
            String world = c.getString("selection.world", c.getString("world", "world"));
            Area a = new Area(name, type, world,
                    c.getInt("selection.pos1.x"), c.getInt("selection.pos1.y"), c.getInt("selection.pos1.z"),
                    c.getInt("selection.pos2.x"), c.getInt("selection.pos2.y"), c.getInt("selection.pos2.z"), c);
            areas.put(name.toLowerCase(Locale.ROOT), a);
            String uuid = c.getString("hologram.uuid", "");
            if (!uuid.isBlank()) try { holograms.put(a.name().toLowerCase(Locale.ROOT), UUID.fromString(uuid)); } catch (Exception ignored) {}
        }
    }

    public ItemStack wand() {
        ItemStack item = new ItemStack(Material.WOODEN_AXE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ColorUtil.text("&bSnowNW &fAlan Baltası"));
        meta.lore(List.of(ColorUtil.text("&7Sağ tık: &f1. konum"), ColorUtil.text("&7Sol tık: &f2. konum")));
        item.setItemMeta(meta);
        return item;
    }

    public boolean isWand(ItemStack item) {
        if (item == null || item.getType() != Material.WOODEN_AXE || !item.hasItemMeta()) return false;
        ComponentName n = new ComponentName(item.getItemMeta().displayName());
        return n.raw().contains("Alan Baltası") || n.raw().contains("SnowNW");
    }

    private record ComponentName(String raw) {
        ComponentName(net.kyori.adventure.text.Component c) { this(c == null ? "" : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c)); }
    }

    public void setPos1(Player p, Location l){ pos1.put(p.getUniqueId(), l.clone()); }
    public void setPos2(Player p, Location l){ pos2.put(p.getUniqueId(), l.clone()); }
    public Location getPos1(Player p){ return pos1.get(p.getUniqueId()); }
    public Location getPos2(Player p){ return pos2.get(p.getUniqueId()); }

    public Area get(String name){ return areas.get(name.toLowerCase(Locale.ROOT)); }
    public Collection<Area> all(){ return Collections.unmodifiableCollection(areas.values()); }

    public Area create(String name, Type type, Player p) throws IllegalArgumentException {
        Location a=pos1.get(p.getUniqueId()), b=pos2.get(p.getUniqueId());
        if(a==null||b==null||a.getWorld()==null||b.getWorld()==null) throw new IllegalArgumentException("NO_SELECTION");
        if(!a.getWorld().equals(b.getWorld())) throw new IllegalArgumentException("DIFFERENT_WORLD");
        String key=name.toLowerCase(Locale.ROOT);
        if(areas.containsKey(key)) throw new IllegalArgumentException("ALREADY_EXISTS");
        YamlConfiguration c=new YamlConfiguration();
        c.set("name", name); c.set("type", type.name());
        c.set("selection.world", a.getWorld().getName());
        c.set("selection.pos1.x",a.getBlockX()); c.set("selection.pos1.y",a.getBlockY()); c.set("selection.pos1.z",a.getBlockZ());
        c.set("selection.pos2.x",b.getBlockX()); c.set("selection.pos2.y",b.getBlockY()); c.set("selection.pos2.z",b.getBlockZ());
        c.set("protection.block-break", false); c.set("protection.block-place", false); c.set("protection.interaction", true);
        c.set("protection.chest-open", true); c.set("protection.other-interactions", true);
        if(type==Type.AFK){
            c.set("afk.shards.enabled",true);
            c.set("afk.shards.interval-seconds",60);
            c.set("afk.shards.amount",1);
            c.set("afk.teleport.enabled",true);
            c.set("afk.teleport.target-area","");
        }
        if(type==Type.RTPZONE){ c.set("rtpzone.countdown-seconds",30); c.set("rtpzone.destination-area",""); c.set("rtpzone.hologram.enabled",true); }
        if(type==Type.END||type==Type.DUNYA||type==Type.NETHER){ c.set("portal.cooldown",0); }
        if(type==Type.STASH){ c.set("stash.id", name.replaceAll("[^0-9]", "")); c.set("stash.duration-seconds", plugin.getConfig().getInt("spawn-stash.duration-seconds", 30)); }
        save(name,c); Area area=new Area(name,type,a.getWorld().getName(),a.getBlockX(),a.getBlockY(),a.getBlockZ(),b.getBlockX(),b.getBlockY(),b.getBlockZ(),c); areas.put(key,area); return area;
    }

    public void save(String name,YamlConfiguration c){ try{c.save(new File(folder,name.toLowerCase(Locale.ROOT)+".yml"));}catch(IOException e){plugin.getLogger().warning("Alan kaydedilemedi: "+e.getMessage());} }

    public void setDestination(String name,String destination){
        Area a=get(name); if(a==null)return;
        if(a.type()==Type.AFK) a.cfg().set("afk.teleport.target-area",destination);
        else a.cfg().set("rtpzone.destination-area",destination);
        save(a.name(),a.cfg());
    }

    public Location areaSpawn(Area a) {
        if (a == null || a.world() == null) return null;
        World w = Bukkit.getWorld(a.world());
        if (w == null) return null;
        int x = a.x1();
        int y = a.y1();
        int z = a.z1();
        Location first = new Location(w, x + 0.5, y + 1.0, z + 0.5);
        Block ground = w.getBlockAt(x, y, z);
        Block feet = w.getBlockAt(x, y + 1, z);
        Block head = w.getBlockAt(x, y + 2, z);
        if (ground.getType().isSolid() && feet.getType().isAir() && head.getType().isAir()) return first;
        return new Location(w, a.minX() + 0.5, a.minY() + 1.0, a.minZ() + 0.5);
    }

    public boolean delete(String name){ Area a=areas.remove(name.toLowerCase(Locale.ROOT)); if(a==null)return false; removeHologram(name); return new File(folder,a.name().toLowerCase(Locale.ROOT)+".yml").delete(); }

    public boolean isProtected(Location l, String setting){
        for(Area a:areas.values()) if(a.contains(l) && !a.cfg().getBoolean("protection."+setting,true)) return true;
        return false;
    }

    public Area at(Location l, Type type){ for(Area a:areas.values()) if(a.type()==type && a.contains(l)) return a; return null; }

    public Location randomSafeInWorldType(Area source){
        World.Environment env = switch(source.type()){ case END -> World.Environment.THE_END; case NETHER -> World.Environment.NETHER; default -> World.Environment.NORMAL; };
        World target = null;
        for(World w:Bukkit.getWorlds()) if(w.getEnvironment()==env){ target=w; break; }
        if(target==null) return null;
        for(int i=0;i<32;i++){
            int x=ThreadLocalRandom.current().nextInt(-5000,5001);
            int z=ThreadLocalRandom.current().nextInt(-5000,5001);
            int y=target.getHighestBlockYAt(x,z);
            if(y<=target.getMinHeight()+1||y>=target.getMaxHeight()-2)continue;
            Block g=target.getBlockAt(x,y,z), f=target.getBlockAt(x,y+1,z), h=target.getBlockAt(x,y+2,z);
            if(!g.getType().isSolid()||!f.getType().isAir()||!h.getType().isAir()||g.isLiquid()||f.isLiquid()||h.isLiquid())continue;
            if(g.getType()==Material.LAVA||g.getType()==Material.MAGMA_BLOCK||g.getType()==Material.CACTUS||g.getType()==Material.BEDROCK)continue;
            if(env==World.Environment.NETHER && y>118)continue;
            return new Location(target,x+.5,y+1,z+.5);
        }
        return null;
    }

    public Location randomSafeIn(Area a){
        World w=Bukkit.getWorld(a.world()); if(w==null)return null;
        for(int i=0;i<24;i++){
            int x=a.minX()+java.util.concurrent.ThreadLocalRandom.current().nextInt(Math.max(1,a.maxX()-a.minX()+1));
            int z=a.minZ()+java.util.concurrent.ThreadLocalRandom.current().nextInt(Math.max(1,a.maxZ()-a.minZ()+1));
            int y=w.getHighestBlockYAt(x,z);
            if(y<a.minY()||y>a.maxY()-2) continue;
            Block g=w.getBlockAt(x,y,z), f=w.getBlockAt(x,y+1,z), h=w.getBlockAt(x,y+2,z);
            if(g.isLiquid()||f.isLiquid()||h.isLiquid()||!g.getType().isSolid()||!f.getType().isAir()||!h.getType().isAir()) continue;
            if(g.getType()==Material.LAVA||g.getType()==Material.MAGMA_BLOCK||g.getType()==Material.CACTUS) continue;
            return new Location(w,x+.5,y+1,z+.5);
        }
        return null;
    }

    public void setHologram(String name, Location loc){ Area a=get(name); if(a==null)return; removeHologram(name); TextDisplay td=loc.getWorld().spawn(loc,TextDisplay.class,e->{ e.setBillboard(Display.Billboard.CENTER); e.setSeeThrough(false); e.setShadowed(false); e.text(ColorUtil.text("&cRTP Zone\n&fKalan süre: &c%rtpzone_time%\n&fKaç oyuncu var: &c%rtpzone_players%")); }); holograms.put(name.toLowerCase(Locale.ROOT),td.getUniqueId()); a.cfg().set("hologram.uuid",td.getUniqueId().toString()); save(a.name(),a.cfg()); }
    public void updateHologram(String name,String text){ UUID id=holograms.get(name.toLowerCase(Locale.ROOT)); if(id==null)return; Entity e=Bukkit.getEntity(id); if(e instanceof TextDisplay td) td.text(ColorUtil.text(text)); }
    public void removeHologram(String name){ UUID id=holograms.remove(name.toLowerCase(Locale.ROOT)); if(id==null)return; Entity e=Bukkit.getEntity(id); if(e!=null)e.remove(); }
}
