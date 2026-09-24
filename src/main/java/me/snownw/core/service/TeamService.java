package me.snownw.core.service;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** SnowNW takım sistemi: takım, davet, takım sohbeti, takım evi ve dost ateşi. */
public final class TeamService {
    public static final class Team {
        private final String id;
        private final String name;
        private final UUID leader;
        private final Set<UUID> members;
        private Location home;
        private boolean friendlyFire;
        Team(String id, String name, UUID leader, Set<UUID> members, Location home, boolean friendlyFire) {
            this.id=id; this.name=name; this.leader=leader; this.members=members; this.home=home; this.friendlyFire=friendlyFire;
        }
        public String id(){return id;} public String name(){return name;} public UUID leader(){return leader;}
        public Set<UUID> members(){return members;} public Location home(){return home;} public boolean friendlyFire(){return friendlyFire;}
        void home(Location l){home=l;} void friendlyFire(boolean v){friendlyFire=v;}
    }

    public enum CreateResult { OK, INVALID_NAME, RESTRICTED_NAME, ALREADY_IN_TEAM, NAME_TAKEN }

    private final SnowNWCorePlugin plugin;
    private final File file;
    private YamlConfiguration data;
    private final Map<String, Team> teams = new HashMap<>();
    private final Map<UUID, String> byPlayer = new HashMap<>();
    private final Map<UUID, String> invites = new HashMap<>();
    private final Map<UUID, Boolean> teamChat = new HashMap<>();

    public TeamService(SnowNWCorePlugin plugin) {
        this.plugin=plugin; File dir=new File(plugin.getDataFolder(),"data"); if(!dir.exists())dir.mkdirs(); file=new File(dir,"teams.yml"); load();
    }
    public void load(){
        data=YamlConfiguration.loadConfiguration(file); teams.clear(); byPlayer.clear();
        ConfigurationSection root=data.getConfigurationSection("teams"); if(root==null)return;
        for(String id:root.getKeys(false)){
            ConfigurationSection s=root.getConfigurationSection(id); if(s==null)continue;
            try {
                String name=s.getString("name",id); UUID leader=UUID.fromString(s.getString("leader")); Set<UUID> mem=new HashSet<>();
                for(String m:s.getStringList("members")) mem.add(UUID.fromString(m)); mem.add(leader);
                Location home=null; String world=s.getString("home.world");
                if(world!=null&&Bukkit.getWorld(world)!=null) home=new Location(Bukkit.getWorld(world),s.getDouble("home.x"),s.getDouble("home.y"),s.getDouble("home.z"),(float)s.getDouble("home.yaw"),(float)s.getDouble("home.pitch"));
                Team t=new Team(id,name,leader,mem,home,s.getBoolean("friendly-fire",false)); teams.put(id,t); for(UUID m:mem)byPlayer.put(m,id);
            } catch(Exception ignored) {}
        }
    }
    public void save(){
        data=new YamlConfiguration();
        for(Team t:teams.values()){String p="teams."+t.id(); data.set(p+".name",t.name());data.set(p+".leader",t.leader().toString());List<String> mem=t.members().stream().map(UUID::toString).toList();data.set(p+".members",mem);data.set(p+".friendly-fire",t.friendlyFire());
            if(t.home()!=null&&t.home().getWorld()!=null){data.set(p+".home.world",t.home().getWorld().getName());data.set(p+".home.x",t.home().getX());data.set(p+".home.y",t.home().getY());data.set(p+".home.z",t.home().getZ());data.set(p+".home.yaw",t.home().getYaw());data.set(p+".home.pitch",t.home().getPitch());}
        } try{data.save(file);}catch(IOException e){plugin.getLogger().warning("teams.yml: "+e.getMessage());}
    }
    public Team get(Player p){return p==null?null:get(p.getUniqueId());}
    public Team get(UUID uuid){return uuid==null?null:teams.get(byPlayer.get(uuid));}
    public Team getByName(String name){if(name==null)return null;for(Team t:teams.values())if(t.name().equalsIgnoreCase(name))return t;return null;}

    public int minNameLength(){ return Math.max(1, plugin.getConfig().getInt("team.name-min-length", 3)); }
    public int maxNameLength(){ return Math.max(minNameLength(), plugin.getConfig().getInt("team.name-max-length", 12)); }
    public int maxMembers(){ return Math.max(1, plugin.getConfig().getInt("team.max-members", 8)); }

    public boolean isValidName(String name){
        return name!=null && name.length()>=minNameLength() && name.length()<=maxNameLength() && name.matches("[a-zA-Z0-9_]+");
    }
    public boolean isRestrictedName(String name){
        if(name==null) return false;
        for(String bad : plugin.getConfig().getStringList("team.restricted-names")){
            if(name.toLowerCase(Locale.ROOT).contains(bad.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    public CreateResult create(Player leader,String name){
        if(leader==null) return CreateResult.INVALID_NAME;
        if(!isValidName(name)) return CreateResult.INVALID_NAME;
        if(isRestrictedName(name)) return CreateResult.RESTRICTED_NAME;
        if(get(leader)!=null) return CreateResult.ALREADY_IN_TEAM;
        if(getByName(name)!=null) return CreateResult.NAME_TAKEN;
        String id=UUID.randomUUID().toString().substring(0,8);
        Set<UUID> m=new HashSet<>(); m.add(leader.getUniqueId());
        Team t=new Team(id,name,leader.getUniqueId(),m,null,false);
        teams.put(id,t); byPlayer.put(leader.getUniqueId(),id); save();
        return CreateResult.OK;
    }
    public void disband(Team t){if(t==null)return;for(UUID m:t.members()) {byPlayer.remove(m);teamChat.remove(m);Player p=Bukkit.getPlayer(m);if(p!=null)p.sendMessage(ColorUtil.text("&cTakımınız dağıtıldı."));}teams.remove(t.id());save();}
    public boolean invite(Player from,Player to){
        Team t=get(from);
        if(t==null||to==null||get(to)!=null||t.members().size()>=maxMembers())return false;
        invites.put(to.getUniqueId(),t.id());
        Bukkit.getScheduler().runTaskLater(plugin,()->{String id=invites.get(to.getUniqueId());if(t.id().equals(id))invites.remove(to.getUniqueId());},20L*60);
        return true;
    }
    public boolean accept(Player p){String id=invites.remove(p.getUniqueId());if(id==null||get(p)!=null)return false;Team t=teams.get(id);if(t==null||t.members().size()>=maxMembers())return false;t.members().add(p.getUniqueId());byPlayer.put(p.getUniqueId(),id);save();return true;}
    public void leave(Player p){if(p!=null)leave(p.getUniqueId(),get(p));}
    public void leave(UUID uuid,Team t){if(t==null)return;if(t.leader().equals(uuid)){disband(t);return;}t.members().remove(uuid);byPlayer.remove(uuid);teamChat.remove(uuid);save();}
    public void setHome(Team t,Location loc){if(t==null)return;t.home(loc==null?null:loc.clone());save();}
    public void clearHome(Team t){setHome(t,null);}
    public boolean toggleFriendlyFire(Team t){if(t==null)return false;t.friendlyFire(!t.friendlyFire());save();return t.friendlyFire();}
    public boolean areTeammates(UUID a,UUID b){if(a==null||b==null)return false;String x=byPlayer.get(a),y=byPlayer.get(b);return x!=null&&x.equals(y);}
    public boolean toggleTeamChat(UUID uuid){boolean n=!teamChat.getOrDefault(uuid,false);teamChat.put(uuid,n);return n;}
    public boolean isTeamChatEnabled(UUID uuid){return teamChat.getOrDefault(uuid,false);}
    public void sendTeamChat(Player sender,String message){
        Team t=get(sender);if(t==null)return;
        for(UUID u:t.members()){Player p=Bukkit.getPlayer(u);if(p!=null)p.sendMessage(ColorUtil.text("&b[Takım] &f"+sender.getName()+" &8» &f"+message));}
    }
}
