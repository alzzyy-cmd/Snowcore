package me.snownw.core.placeholder;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.data.SettingsStore;
import me.snownw.core.data.StatsStore;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Native PlaceholderAPI expansion – %snownwcore_*%
 * Does not depend on external Vault expansions.
 */
public final class SnowNWExpansion extends PlaceholderExpansion {

    private final SnowNWCorePlugin plugin;

    public SnowNWExpansion(SnowNWCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getIdentifier() { return "snownwcore"; }
    @Override public @NotNull String getAuthor() { return "SnowNW Team"; }
    @Override public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }
    @Override public boolean canRegister() { return true; }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";
        String p = params.toLowerCase();

        return switch (p) {
            case "name", "player" -> player.getName();
            case "uuid" -> player.getUniqueId().toString();
            case "ping" -> String.valueOf(player.getPing());

            case "kills" -> str(StatsStore.Type.KILLS, player);
            case "deaths" -> str(StatsStore.Type.DEATHS, player);
            case "blocks", "blocks_broken" -> str(StatsStore.Type.BLOCKS, player);
            case "mobs", "mob_kills" -> str(StatsStore.Type.MOBS, player);
            case "playtime" -> StatsStore.formatPlaytime(
                    plugin.stats().get(player.getUniqueId(), StatsStore.Type.PLAYTIME));
            case "playtime_raw", "playtime_seconds" -> String.valueOf(
                    plugin.stats().get(player.getUniqueId(), StatsStore.Type.PLAYTIME));

            case "shards", "shard" -> String.valueOf(plugin.shards().get(player.getUniqueId()));
            case "team" -> {
                var tm = plugin.teams().get(player);
                yield tm == null ? "None" : tm.name();
            }
            case "team_members" -> {
                var tm = plugin.teams().get(player);
                yield tm == null ? "0" : String.valueOf(tm.members().size());
            }
            case "balance", "money", "bal" -> balance(player, false);
            case "balance_formatted", "money_formatted", "bal_formatted" -> balance(player, true);

            case "homes", "home_count" -> String.valueOf(plugin.homes().count(player.getUniqueId()));
            case "home_limit" -> String.valueOf(plugin.homes().homeLimit(player));
            case "homes_max" -> String.valueOf(plugin.getConfig().getInt("homes.max-homes", 90));

            case "scoreboard" -> onOff(player, SettingsStore.Toggle.SCOREBOARD);
            case "show_money" -> onOff(player, SettingsStore.Toggle.SHOW_MONEY);
            case "show_playtime" -> onOff(player, SettingsStore.Toggle.SHOW_PLAYTIME);
            case "show_kills" -> onOff(player, SettingsStore.Toggle.SHOW_KILLS);
            case "show_deaths" -> onOff(player, SettingsStore.Toggle.SHOW_DEATHS);

            default -> {
                if (p.startsWith("setting_")) {
                    String key = p.substring(8).toUpperCase().replace("-", "_");
                    try {
                        SettingsStore.Toggle t = SettingsStore.Toggle.valueOf(key);
                        yield onOff(player, t);
                    } catch (Exception e) {
                        yield "";
                    }
                }
                yield null;
            }
        };
    }

    private String str(StatsStore.Type type, Player player) {
        return String.valueOf(plugin.stats().get(player.getUniqueId(), type));
    }

    private String onOff(Player player, SettingsStore.Toggle t) {
        return plugin.settings().get(player.getUniqueId(), t) ? "AÇIK" : "KAPALI";
    }

    private String balance(Player player, boolean formatted) {
        if (plugin.economy() == null) return "0";
        double bal = plugin.economy().get(player);
        return formatted ? plugin.economy().format(bal) : String.format(java.util.Locale.US, "%.2f", bal);
    }
}
