package me.snownw.core.service;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.data.SettingsStore;
import me.snownw.core.data.StatsStore;
import me.snownw.core.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import io.papermc.paper.scoreboard.numbers.NumberFormat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Sidebar without flicker – reuse objective/teams. */
public final class ScoreboardService {

    private final SnowNWCorePlugin plugin;
    private FileConfiguration sb;
    private final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastHash = new ConcurrentHashMap<>();

    public ScoreboardService(SnowNWCorePlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "scoreboard.yml");
        if (!f.exists()) plugin.saveResource("scoreboard.yml", false);
        sb = YamlConfiguration.loadConfiguration(f);
        lastHash.clear();
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) return;
        // every 2 seconds – less flicker
        long interval = plugin.getConfig().getLong("scoreboard.update-ticks", 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!plugin.settings().get(p.getUniqueId(), SettingsStore.Toggle.SCOREBOARD)) {
                    clear(p);
                    continue;
                }
                update(p);
            }
        }, 10L, Math.max(5L, interval));
    }

    /** Clear cache so next update redraws immediately. */
    public void forceUpdate(Player player) {
        lastHash.remove(player.getUniqueId());
        update(player);
    }

    public void update(Player player) {
        List<String> lines = buildLines(player);
        String title = plugin.settings().getScoreboardMode(player.getUniqueId()) == SettingsStore.ScoreboardMode.KLASIK
                ? player.getName() : apply(player, sb.getString("title", "SnowNW"));
        String hash = title + String.join("\n", lines);
        Scoreboard board = boards.computeIfAbsent(player.getUniqueId(),
                id -> Bukkit.getScoreboardManager().getNewScoreboard());
        if (hash.equals(lastHash.get(player.getUniqueId()))) {
            updateNameInfo(player, board);
            if (player.getScoreboard() != board) player.setScoreboard(board);
            return;
        }
        lastHash.put(player.getUniqueId(), hash);
        Objective obj = board.getObjective("snownwcore");
        if (obj == null) {
            obj = board.registerNewObjective("snownwcore", Criteria.DUMMY, ColorUtil.text(title));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            obj.numberFormat(NumberFormat.blank());
        } else {
            obj.displayName(ColorUtil.text(title));
            obj.numberFormat(NumberFormat.blank());
        }

        // Reuse stable teams/entries to avoid needless unregister/register churn.
        for (String entry : new ArrayList<>(board.getEntries())) {
            board.resetScores(entry);
        }
        for (Team team : new ArrayList<>(board.getTeams())) {
            if (team.getName().startsWith("sn_sb_")) team.removeEntries(new ArrayList<>(team.getEntries()));
        }

        int score = lines.size();
        int index = 0;
        for (String applied : lines) {
            String teamName = "sn_sb_" + index;
            Team team = board.getTeam(teamName);
            if (team == null) team = board.registerNewTeam(teamName);
            String entry = uniqueEntry(index);
            team.prefix(ColorUtil.text(applied.length() > 64 ? applied.substring(0, 64) : applied));
            team.addEntry(entry);
            obj.getScore(entry).setScore(score);
            score--;
            index++;
        }
        updateNameInfo(player, board);
        if (player.getScoreboard() != board) {
            player.setScoreboard(board);
        }
    }

    private List<String> buildLines(Player player) {
        List<String> lines = new ArrayList<>();
        if (plugin.settings().getScoreboardMode(player.getUniqueId()) == SettingsStore.ScoreboardMode.KLASIK) {
            // Klasik: oyuncu adı scoreboard başlığıdır ve daima görünür; diğer satırlar ayarlardan gizlenebilir.
            addIfVisible(lines, player, "&#00FC00&l$ " + "&f" + money(player), SettingsStore.Toggle.SHOW_MONEY);
            addIfVisible(lines, player, "&#A303F9★ &f" + shards(player), SettingsStore.Toggle.SHOW_SHARDS);
            addIfVisible(lines, player, "&#FC0000🗡 &f" + kills(player), SettingsStore.Toggle.SHOW_KILLS);
            addIfVisible(lines, player, "&#F97603☠ &f" + deaths(player), SettingsStore.Toggle.SHOW_DEATHS);
            addIfVisible(lines, player, "&#00A4FC⌛ &f" + playtime(player), SettingsStore.Toggle.SHOW_PLAYTIME);
            if (plugin.teams() != null && plugin.teams().get(player) != null) {
                lines.add("&#00A4FC⚑ &fTakım &f" + plugin.teams().get(player).name());
            }
            addIfVisible(lines, player, "&#00A4FC¤ &f" + pingText(player.getPing()) + "ms", SettingsStore.Toggle.SHOW_PING);
            lines.removeIf(String::isEmpty);
            return lines;
        }
        for (String line : sb.getStringList("lines")) {
            if (shouldHide(player, line)) continue;
            lines.add(apply(player, line));
        }
        return lines;
    }

    private void addIfVisible(List<String> lines, Player player, String line, SettingsStore.Toggle toggle) {
        if (plugin.settings().get(player.getUniqueId(), toggle)) lines.add(apply(player, line));
    }

    private String money(Player p) {
        return plugin.economy() == null ? "0" : plugin.economy().format(plugin.economy().get(p));
    }
    private String shards(Player p) {
        return plugin.shards() == null ? "0" : String.valueOf(plugin.shards().get(p.getUniqueId()));
    }
    private String kills(Player p) { return String.valueOf(plugin.stats().get(p.getUniqueId(), StatsStore.Type.KILLS)); }
    private String deaths(Player p) { return String.valueOf(plugin.stats().get(p.getUniqueId(), StatsStore.Type.DEATHS)); }
    private String playtime(Player p) { return StatsStore.formatPlaytime(plugin.stats().get(p.getUniqueId(), StatsStore.Type.PLAYTIME)); }

    private void updateNameInfo(Player viewer, Scoreboard board) {
        // Minecraft's BELOW_NAME objective can only render a raw integer score.
        // SnowNW therefore uses the scoreboard team suffix for the formatted
        // player info requested by the server design.
        for (Team team : new ArrayList<>(board.getTeams())) {
            if (team.getName().startsWith("sn_nt_")) team.unregister();
        }
        boolean enabled = plugin.settings().get(viewer.getUniqueId(), SettingsStore.Toggle.NAMETAG_INFO);
        if (!enabled) return;

        for (Player target : Bukkit.getOnlinePlayers()) {
            double money = plugin.economy() == null ? 0D : Math.max(0D, plugin.economy().get(target));
            String moneyText = plugin.economy() == null ? "0" : plugin.economy().format(money);
            String info = " &f$ &a" + moneyText + " &7| &b¤ " + target.getPing() + "ms";
            Team team = board.registerNewTeam("sn_nt_" + target.getUniqueId().toString().replace("-", "").substring(0, 12));
            team.suffix(ColorUtil.text(info));
            team.addEntry(target.getName());
        }
    }

    private boolean shouldHide(Player player, String line) {
        String l = line.toLowerCase();
        UUID id = player.getUniqueId();
        if ((l.contains("balance") || l.contains("money") || l.contains("vault_eco"))
                && !plugin.settings().get(id, SettingsStore.Toggle.SHOW_MONEY)) return true;
        if ((l.contains("playtime") || l.contains("oynama"))
                && !plugin.settings().get(id, SettingsStore.Toggle.SHOW_PLAYTIME)) return true;
        if ((l.contains("kill") || l.contains("öldür"))
                && !plugin.settings().get(id, SettingsStore.Toggle.SHOW_KILLS)) return true;
        if ((l.contains("death") || l.contains("ölüm"))
                && !plugin.settings().get(id, SettingsStore.Toggle.SHOW_DEATHS)) return true;
        if (l.contains("shard")
                && !plugin.settings().get(id, SettingsStore.Toggle.SHOW_SHARDS)) return true;
        if (l.contains("ping") && !plugin.settings().get(id, SettingsStore.Toggle.SHOW_PING)) return true;
        if (l.contains("%snownwcore_team_line%")
                && (plugin.teams() == null || plugin.teams().get(player) == null)) return true;
        return false;
    }

    public void clear(Player player) {
        lastHash.remove(player.getUniqueId());
        Scoreboard board = boards.get(player.getUniqueId());
        boolean nameInfo = plugin.settings().get(player.getUniqueId(), SettingsStore.Toggle.NAMETAG_INFO);
        if (board != null && nameInfo) {
            Objective obj = board.getObjective("snownwcore");
            if (obj != null) obj.setDisplaySlot(null);
            updateNameInfo(player, board);
            player.setScoreboard(board);
        } else {
            boards.remove(player.getUniqueId());
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    private String apply(Player player, String input) {
        if (input == null) return "";
        String s = input
                .replace("%player%", player.getName())
                .replace("%player_name%", player.getName())
                .replace("%name%", player.getName());
        s = s.replace("%snownwcore_kills%", String.valueOf(
                plugin.stats().get(player.getUniqueId(), StatsStore.Type.KILLS)));
        s = s.replace("%snownwcore_deaths%", String.valueOf(
                plugin.stats().get(player.getUniqueId(), StatsStore.Type.DEATHS)));
        s = s.replace("%snownwcore_playtime%", StatsStore.formatPlaytime(
                plugin.stats().get(player.getUniqueId(), StatsStore.Type.PLAYTIME)));
        s = s.replace("%ping%", pingText(player.getPing()))
                .replace("%snownwcore_ping%", pingText(player.getPing()));
        String shards = plugin.shards() != null
                ? String.valueOf(plugin.shards().get(player.getUniqueId())) : "0";
        s = s.replace("%snownwcore_shards%", shards).replace("%snownwcore_shard%", shards);
        var tm = plugin.teams() != null ? plugin.teams().get(player) : null;
        s = s.replace("%snownwcore_team%", tm == null ? "" : tm.name());
        s = s.replace("%snownwcore_team_line%", tm == null ? "" : "&f" + tm.name());
        if (plugin.economy() != null) {
            String bal = plugin.economy().format(plugin.economy().get(player));
            s = s.replace("%snownwcore_balance%", bal)
                    .replace("%snownwcore_balance_formatted%", bal)
                    .replace("%vault_eco_balance_formatted%", bal)
                    .replace("%vault_eco_balance%", bal);
        }
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                s = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, s);
            } catch (Throwable ignored) {}
        }
        return s;
    }


    private String pingText(int ping) {
        String color = ping <= 80 ? "&#00FC00" : (ping <= 150 ? "&#F9D503" : "&#FC0000");
        return color + ping;
    }

    private String uniqueEntry(int i) {
        String[] c = {"§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7", "§8", "§9", "§a", "§b", "§c", "§d", "§e", "§f"};
        return c[i % c.length] + "§r";
    }
}
