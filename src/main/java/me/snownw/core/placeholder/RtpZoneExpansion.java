package me.snownw.core.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.snownw.core.SnowNWCorePlugin;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** Exact placeholders requested for RTP Zone holograms: %rtpzone_time% and %rtpzone_players%. */
public final class RtpZoneExpansion extends PlaceholderExpansion {
    private final SnowNWCorePlugin plugin;
    public RtpZoneExpansion(SnowNWCorePlugin plugin){ this.plugin=plugin; }
    @Override public @NotNull String getIdentifier(){ return "rtpzone"; }
    @Override public @NotNull String getAuthor(){ return "SnowNW Team"; }
    @Override public @NotNull String getVersion(){ return plugin.getDescription().getVersion(); }
    @Override public boolean persist(){ return true; }
    @Override public boolean canRegister(){ return true; }
    @Override public String onPlaceholderRequest(Player player,@NotNull String params){
        if(params.equalsIgnoreCase("time")){
            var a=player==null?null:plugin.areas().at(player.getLocation(),me.snownw.core.service.AreaService.Type.RTPZONE);
            return a==null?"0":String.valueOf(plugin.areaListener().remainingSeconds(a.name()));
        }
        if(params.equalsIgnoreCase("players")){
            var a=player==null?null:plugin.areas().at(player.getLocation(),me.snownw.core.service.AreaService.Type.RTPZONE);
            return a==null?"0":String.valueOf(plugin.areaListener().playerCount(a.name()));
        }
        return "";
    }
}
