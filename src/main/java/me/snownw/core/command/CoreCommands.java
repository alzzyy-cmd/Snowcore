package me.snownw.core.command;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.data.HomeStore;
import me.snownw.core.data.StatsStore;
import me.snownw.core.util.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class CoreCommands implements CommandExecutor, TabCompleter {

    private final SnowNWCorePlugin plugin;

    public CoreCommands(SnowNWCorePlugin plugin) {
        this.plugin = plugin;
    }

    /** Paper BasicCommand entry. */
    public void dispatch(CommandSender sender, String cmdName, String[] args) {
        handle(sender, cmdName.toLowerCase(Locale.ROOT), args == null ? new String[0] : args);
    }

    public java.util.Collection<String> suggest(CommandSender sender, String cmdName, String[] args) {
        List<String> r = tab(sender, cmdName.toLowerCase(Locale.ROOT), args == null ? new String[0] : args);
        return r == null ? List.of() : r;
    }



    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return handle(sender, command.getName().toLowerCase(Locale.ROOT), args);
    }

    private boolean handle(CommandSender sender, String name, String[] args) {

        if (name.equals("snownwcore")) {
            return admin(sender, args);
        }

        if (!plugin.commandsEnabled() && !name.equals("snownwcore") && !name.equals("vc")) {
            if (sender instanceof Player p) p.sendMessage(ColorUtil.text(plugin.messages().get("disabled")));
            return true;
        }
        if (!name.equals("snownwcore") && !name.equals("vc") && !plugin.cmdEnabled(name)) {
            sender.sendMessage(ColorUtil.text(plugin.messages() != null ? plugin.messages().get("disabled") : "&cDisabled"));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Bu komut yalnızca oyuncular içindir.");
            return true;
        }

        return switch (name) {
            case "menu", "snownwcore" -> {
                if (!plugin.cmdEnabled("menu")) { player.sendMessage(ColorUtil.text(plugin.messages().get("disabled"))); yield true; }
                if (plugin.menus().supports(player)) plugin.menus().openMain(player);
                else player.sendMessage(ColorUtil.text(plugin.messages().get("unsupported")));
                yield true;
            }
            case "homes" -> {
                if (!plugin.cmdEnabled("homes")) { player.sendMessage(ColorUtil.text(plugin.messages().get("disabled"))); yield true; }
                if (plugin.menus().supports(player)) plugin.menus().openHomes(player, 0);
                else plugin.classic().openHomes(player);
                yield true;
            }
            case "home" -> home(player, args);
            case "sethome" -> setHome(player, args);
            case "delhome" -> delHome(player, args);
            case "leaderboard", "lb", "leaderboards" -> leaderboard(player, args);
            case "itemsearch" -> {
                if (args.length > 0) plugin.menus().openItemSearch(player, String.join(" ", args), "menu");
                else plugin.menus().openItemSearchInput(player, "menu");
                yield true;
            }
            case "friends" -> {
                if (!plugin.cmdEnabled("friends")) { player.sendMessage(ColorUtil.text(plugin.messages().get("disabled"))); yield true; }
                if (plugin.menus().supports(player)) plugin.menus().openFriends(player);
                else plugin.classic().openFriends(player);
                yield true;
            }
            case "settings" -> {
                if (!plugin.cmdEnabled("settings")) { player.sendMessage(ColorUtil.text(plugin.messages().get("disabled"))); yield true; }
                if (plugin.menus().supports(player)) plugin.menus().openAyarlar(player);
                else plugin.classic().openAyarlar(player);
                yield true;
            }
            case "stats" -> {
                if (!plugin.cmdEnabled("stats")) { player.sendMessage(ColorUtil.text(plugin.messages().get("disabled"))); yield true; }
                org.bukkit.entity.Player target = player;
                if (args.length >= 1) {
                    org.bukkit.entity.Player tplayer = org.bukkit.Bukkit.getPlayerExact(args[0]);
                    if (tplayer != null) target = tplayer;
                }
                plugin.menus().openStatsView(player, target.getUniqueId());
                yield true;
            }
            case "tpa" -> {
                if (args.length >= 1) {
                    org.bukkit.entity.Player target = org.bukkit.Bukkit.getPlayerExact(args[0]);
                    if (target == null) player.sendMessage(ColorUtil.text(plugin.messages().get("player-offline")));
                    else plugin.tpa().send(player, target, false);
                } else {
                    if (plugin.menus().supports(player)) plugin.menus().openTpaMenu(player);
                    else player.sendMessage(ColorUtil.text(plugin.messages().get("usage-tpa")));
                }
                yield true;
            }
            case "tpahere" -> {
                if (args.length >= 1) {
                    org.bukkit.entity.Player target = org.bukkit.Bukkit.getPlayerExact(args[0]);
                    if (target == null) player.sendMessage(ColorUtil.text(plugin.messages().get("player-offline")));
                    else plugin.tpa().send(player, target, true);
                } else plugin.menus().openTpaInput(player, true);
                yield true;
            }
            case "tpaccept", "tpyes" -> {
                plugin.tpa().accept(player);
                yield true;
            }
            case "tpdeny", "tpno" -> {
                plugin.tpa().deny(player);
                yield true;
            }
            case "tpauto" -> {
                plugin.tpa().toggleAuto(player);
                yield true;
            }
            case "tpacancel" -> {
                plugin.tpa().cancel(player);
                yield true;
            }
            case "worth" -> {
                plugin.menus().openWorth(player);
                yield true;
            }
            case "ah" -> {
                if (!plugin.cmdEnabled("ah")) {
                    player.sendMessage(ColorUtil.text(plugin.messages().get("disabled")));
                } else if (!plugin.getConfig().getBoolean("auction.enabled", true)) {
                    player.sendMessage(ColorUtil.text("&cAçık artırma sistemi kapalı."));
                } else {
                    plugin.menus().openAuction(player);
                }
                yield true;
            }
            case "profilemanager", "pm" -> {
                plugin.menus().openProfiles(player);
                yield true;
            }
            case "enderchest", "ec", "echest" -> {
                plugin.enderChest().open(player);
                yield true;
            }
            case "shard" -> {
                if (args.length >= 1) {
                    org.bukkit.OfflinePlayer off = org.bukkit.Bukkit.getOfflinePlayer(args[0]);
                    long s = plugin.shards().get(off);
                    player.sendMessage(ColorUtil.text(plugin.messages().get("shard-other")
                            .replace("{player}", args[0]).replace("{amount}", String.valueOf(s))));
                } else {
                    player.sendMessage(ColorUtil.text(plugin.messages().get("shard-self")
                            .replace("{amount}", String.valueOf(plugin.shards().get(player.getUniqueId())))));
                }
                yield true;
            }
            case "rtp" -> {
                if (args.length >= 1) plugin.rtp().rtp(player, args[0]);
                else plugin.menus().openRtpMenu(player);
                yield true;
            }
            case "rtpqueue" -> {
                if (!plugin.cmdEnabled("rtp")) { player.sendMessage(ColorUtil.text(plugin.messages().get("disabled"))); yield true; }
                if (plugin.menus().supports(player)) plugin.menus().openRtpQueueMenu(player);
                else player.sendMessage(ColorUtil.text("&cRTP sırası için güncel istemci sürümü gerekiyor."));
                yield true;
            }
            case "spawn" -> {
                // /spawn is a Dialog entry point; the actual teleport is performed by the Dialog action.
                plugin.menus().openTeleportMenu(player);
                yield true;
            }
            case "setspawn" -> {
                if (!player.hasPermission("snownwcore.setspawn") && !player.hasPermission("snownwcore.admin")) {
                    player.sendMessage(ColorUtil.text(plugin.messages().get("no-permission")));
                    yield true;
                }
                if (plugin.warps().setSpawn(player.getLocation()))
                    player.sendMessage(ColorUtil.text("&aSpawn başarıyla ayarlandı."));
                else player.sendMessage(ColorUtil.text("&cSpawn ayarlanamadı."));
                yield true;
            }
            case "kit", "kits" -> {
                if (!plugin.getConfig().getBoolean("kits.enabled", true)) {
                    player.sendMessage(ColorUtil.text(plugin.messages().get("disabled")));
                    yield true;
                }
                if (args.length == 0) {
                    plugin.menus().openKits(player);
                    yield true;
                }
                var kit = plugin.kits().get(args[0]);
                if (kit == null || !player.hasPermission(kit.permission())) {
                    player.sendMessage(ColorUtil.text(plugin.messages().get("kit-no-permission")));
                    yield true;
                }
                long left = plugin.kits().remaining(player, kit);
                if (left > 0) {
                    player.sendMessage(ColorUtil.text(plugin.messages().get("kit-cooldown").replace("{sec}", String.valueOf(left))));
                    yield true;
                }
                if (!plugin.kits().claim(player, kit.id())) {
                    player.sendMessage(ColorUtil.text("&cKit alınamadı."));
                    yield true;
                }
                player.sendMessage(ColorUtil.text(plugin.messages().get("kit-claimed").replace("{kit}", kit.id())));
                yield true;
            }
            case "warp", "warps" -> {
                if (args.length == 0) plugin.menus().openWarpMenu(player);
                else {
                    var loc = plugin.warps().warp(args[0]);
                    if (loc == null) player.sendMessage(ColorUtil.text("&cBu warp bulunamadı."));
                    else plugin.teleports().teleport(player, loc, args[0]);
                }
                yield true;
            }
            case "afk" -> {
                boolean now = !plugin.extras().isAfk(player);
                plugin.extras().setAfk(player, now);
                player.sendMessage(ColorUtil.text(now ? "&eAFK moduna girdin." : "&aAFK modundan çıktın."));
                if (now) {
                    var locs = plugin.extras().afkLocations();
                    if (args.length > 0) { var loc=locs.get(args[0]); if(loc!=null) plugin.teleports().teleport(player, loc, "AFK"); }
                }
                yield true;
            }
            case "setafk" -> {
                if (!player.hasPermission("snownwcore.setafk") && !player.hasPermission("snownwcore.admin")) { player.sendMessage(ColorUtil.text(plugin.messages().get("no-permission"))); yield true; }
                if (args.length < 1) { player.sendMessage(ColorUtil.text("&7/setafk <isim>")); yield true; }
                plugin.extras().setAfkLocation(args[0], player.getLocation()); player.sendMessage(ColorUtil.text("&aAFK noktası kaydedildi: &f"+args[0])); yield true;
            }
            case "freeze" -> {
                if(!player.hasPermission("snownwcore.freeze")&&!player.hasPermission("snownwcore.admin")){player.sendMessage(ColorUtil.text(plugin.messages().get("no-permission")));yield true;}
                if(args.length<1){player.sendMessage(ColorUtil.text("&7/freeze <oyuncu>"));yield true;} Player t=org.bukkit.Bukkit.getPlayerExact(args[0]); if(t==null){player.sendMessage(ColorUtil.text(plugin.messages().get("player-offline")));yield true;} plugin.extras().freeze(t); t.sendMessage(ColorUtil.text("&cBir yetkili tarafından donduruldun.")); player.sendMessage(ColorUtil.text("&aOyuncu donduruldu.")); yield true;
            }
            case "unfreeze" -> {
                if(!player.hasPermission("snownwcore.freeze")&&!player.hasPermission("snownwcore.admin")){player.sendMessage(ColorUtil.text(plugin.messages().get("no-permission")));yield true;}
                if(args.length<1){player.sendMessage(ColorUtil.text("&7/unfreeze <oyuncu>"));yield true;} Player t=org.bukkit.Bukkit.getPlayerExact(args[0]); if(t==null){player.sendMessage(ColorUtil.text(plugin.messages().get("player-offline")));yield true;} plugin.extras().unfreeze(t); t.sendMessage(ColorUtil.text("&aDondurulman kaldırıldı.")); player.sendMessage(ColorUtil.text("&aOyuncunun dondurması kaldırıldı.")); yield true;
            }
            case "rank", "rütbe" -> {
                if(args.length==0){player.sendMessage(ColorUtil.text("&6Rütben: &f"+plugin.extras().rank(player.getUniqueId())));yield true;}
                if(!player.hasPermission("snownwcore.rank.set")&&!player.hasPermission("snownwcore.admin")){player.sendMessage(ColorUtil.text(plugin.messages().get("no-permission")));yield true;} Player t=org.bukkit.Bukkit.getPlayerExact(args[0]); if(t==null){player.sendMessage(ColorUtil.text(plugin.messages().get("player-offline")));yield true;} if(args.length<2){player.sendMessage(ColorUtil.text("&7/rank <oyuncu> <rütbe>"));yield true;} plugin.extras().setRank(t.getUniqueId(),String.join(" ",java.util.Arrays.copyOfRange(args,1,args.length))); player.sendMessage(ColorUtil.text("&aRütbe güncellendi.")); yield true;
            }
            case "report" -> {
                if(args.length<2){player.sendMessage(ColorUtil.text("&7/report <oyuncu> <sebep>"));yield true;} net.kyori.adventure.text.Component msg=ColorUtil.text("&c[Şikayet] &f"+player.getName()+" &7→ &f"+args[0]+" &8» &7"+String.join(" ",java.util.Arrays.copyOfRange(args,1,args.length))); for(Player s:org.bukkit.Bukkit.getOnlinePlayers()) if(s.hasPermission("snownwcore.staff")||s.hasPermission("snownwcore.admin")) s.sendMessage(msg); player.sendMessage(ColorUtil.text("&aŞikayetin yetkililere iletildi.")); yield true;
            }
            case "helpop" -> {
                if(args.length<1){player.sendMessage(ColorUtil.text("&7/helpop <mesaj>"));yield true;} net.kyori.adventure.text.Component msg=ColorUtil.text("&e[Helpop] &f"+player.getName()+" &8» &7"+String.join(" ",args)); for(Player s:org.bukkit.Bukkit.getOnlinePlayers()) if(s.hasPermission("snownwcore.staff")||s.hasPermission("snownwcore.admin")) s.sendMessage(msg); player.sendMessage(ColorUtil.text("&aMesajın yetkililere gönderildi.")); yield true;
            }
            case "rules" -> { player.sendMessage(ColorUtil.text("&6&lSnowNW Kuralları")); player.sendMessage(ColorUtil.text("&f1. &7Hile ve açık kullanımı yasaktır.")); player.sendMessage(ColorUtil.text("&f2. &7Oyuncuları rahatsız etmeyin ve hakaret etmeyin.")); player.sendMessage(ColorUtil.text("&f3. &7Sunucu ekonomisini ve açıklarını kötüye kullanmayın.")); player.sendMessage(ColorUtil.text("&f4. &7Yetkililerin kararlarına saygı gösterin.")); yield true; }
            case "serverinfo" -> { player.sendMessage(ColorUtil.text("&6&lSnowNW")); player.sendMessage(ColorUtil.text("&7Sürüm: &f26.1.2")); player.sendMessage(ColorUtil.text("&7Çevrim içi: &f"+org.bukkit.Bukkit.getOnlinePlayers().size())); player.sendMessage(ColorUtil.text("&7Bu çekirdek; sosyal, ekonomi, RTP, kit ve yönetim sistemlerini birleştirir.")); yield true; }
            case "hide", "gizle" -> {
                boolean hidden = player.getScoreboardTags().contains("snownw_hide"); if(hidden){player.removeScoreboardTag("snownw_hide"); for(Player p:org.bukkit.Bukkit.getOnlinePlayers()) p.showPlayer(plugin,player); player.sendMessage(ColorUtil.text("&aOyuncu gizleme kapatıldı."));} else {player.addScoreboardTag("snownw_hide"); for(Player p:org.bukkit.Bukkit.getOnlinePlayers()) if(!p.equals(player)) p.hidePlayer(plugin,player); player.sendMessage(ColorUtil.text("&aDiğer oyunculara görünmüyorsun."));} yield true;
            }
            case "spawnstash" -> {
                if (!player.hasPermission("snownwcore.spawnstash") && !player.hasPermission("snownwcore.admin")) {
                    player.sendMessage(ColorUtil.text(plugin.messages().get("no-permission")));
                    yield true;
                }
                Integer type = null;
                if (args.length >= 1) {
                    try { type = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) {
                        player.sendMessage(ColorUtil.text("&cKullanım: &f/spawnstash [1-5]"));
                        yield true;
                    }
                }
                boolean ok = plugin.spawnStash().spawn(player, type);
                if (!ok) player.sendMessage(ColorUtil.text("&cUygun bir stash alanı bulunamadı veya stash oluşturulamadı."));
                yield true;
            }
            case "fakeplayer" -> {
                if (!player.hasPermission("snownwcore.fakeplayer") && !player.hasPermission("snownwcore.admin")) {
                    player.sendMessage(ColorUtil.text(plugin.messages().get("no-permission"))); yield true;
                }
                plugin.fakePlayers().spawn(player);
                yield true;
            }
            case "offense" -> {
                if (!player.hasPermission("snownwcore.offense") && !player.hasPermission("snownwcore.admin")) {
                    player.sendMessage(ColorUtil.text(plugin.messages().get("no-permission"))); yield true;
                }
                if (args.length < 2) {
                    player.sendMessage(ColorUtil.text("&7/offense <oyuncu> <sebep>"));
                    yield true;
                }
                Player target = org.bukkit.Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    player.sendMessage(ColorUtil.text(plugin.messages().get("player-offline"))); yield true;
                }
                var rule = plugin.offenses().rule(args[1]);
                if (rule == null) {
                    player.sendMessage(ColorUtil.text("&cBilinmeyen ceza türü. &7Tab ile mevcut sebepleri görebilirsin."));
                    yield true;
                }
                int tier = plugin.offenses().add(target.getUniqueId(), rule.key(), player.getUniqueId());
                String duration = rule.duration(tier);
                plugin.offenses().apply(target, player, rule, duration);
                player.sendMessage(ColorUtil.text("&aCeza uygulandı: &f" + target.getName()
                        + " &7· &f" + rule.name() + " &7· Kademe &f" + tier + " &7· Süre &f" + duration));
                yield true;
            }
            case "ping" -> {
                Player target = player;
                if (args.length >= 1) {
                    Player t = org.bukkit.Bukkit.getPlayerExact(args[0]);
                    if (t == null) { player.sendMessage(ColorUtil.text(plugin.messages().get("player-offline"))); yield true; }
                    target = t;
                }
                int ping = target.getPing();
                String pingColor = ping <= 80 ? "&#00FC00" : (ping <= 150 ? "&#F9D503" : "&#FC0000");
                player.sendMessage(ColorUtil.text("&b¤ &fPing: " + pingColor + ping + "ms"));
                yield true;
            }
            case "amethysttool" -> {
                if (!player.hasPermission("snownwcore.amethysttool") && !player.hasPermission("snownwcore.admin")) {
                    player.sendMessage(ColorUtil.text(plugin.messages().get("no-permission"))); yield true;
                }
                if (args.length < 3 || !args[0].equalsIgnoreCase("give")) {
                    player.sendMessage(ColorUtil.text("&7/amethysttool give <oyuncu> <DRILL|SHOVEL|BUCKET|SHARD_BOOSTER> [süre]")); yield true;
                }
                Player target = org.bukkit.Bukkit.getPlayerExact(args[1]);
                if (target == null) { player.sendMessage(ColorUtil.text(plugin.messages().get("player-offline"))); yield true; }
                me.snownw.core.service.AmethystToolService.Type type;
                try { type = me.snownw.core.service.AmethystToolService.Type.valueOf(args[2].toUpperCase(java.util.Locale.ROOT).replace('-', '_')); }
                catch (IllegalArgumentException ex) { player.sendMessage(ColorUtil.text("&cGeçersiz Amethyst eşya türü.")); yield true; }
                long duration = 0;
                if (args.length >= 4) { try { duration = Long.parseLong(args[3]); } catch (NumberFormatException ex) { player.sendMessage(ColorUtil.text("&cSüre saniye cinsinden sayı olmalı.")); yield true; } }
                var item = plugin.amethystTools().create(type, target.getUniqueId(), duration);
                if (item == null) { player.sendMessage(ColorUtil.text("&cAmethyst eşya oluşturulamadı.")); yield true; }
                java.util.Map<Integer, ItemStack> left = target.getInventory().addItem(item);
                if (!left.isEmpty()) target.getWorld().dropItemNaturally(target.getLocation(), left.values().iterator().next());
                player.sendMessage(ColorUtil.text("&aAmethyst eşya verildi: &f" + type.name()));
                if (!target.equals(player)) target.sendMessage(ColorUtil.text("&#9B59B6[Amethyst] &fSana bir Amethyst eşya verildi."));
                yield true;
            }
            case "gmc", "gms", "gmsp" -> {
                if (!player.hasPermission("snownwcore.gamemode") && !player.hasPermission("snownwcore.admin")) {
                    player.sendMessage(ColorUtil.text(plugin.messages().get("no-permission"))); yield true;
                }
                org.bukkit.GameMode mode = switch (name) {
                    case "gmc" -> org.bukkit.GameMode.CREATIVE;
                    case "gms" -> org.bukkit.GameMode.SURVIVAL;
                    default -> org.bukkit.GameMode.SPECTATOR;
                };
                player.setGameMode(mode);
                String label = switch (mode) { case CREATIVE -> "Yaratıcı"; case SURVIVAL -> "Hayatta Kalma"; default -> "İzleyici"; };
                player.sendMessage(ColorUtil.text("&aOyun modu: &f" + label));
                yield true;
            }
            case "alan" -> { areaCmd(player, args); yield true; }
            case "alanbaltasi", "alanbaltası" -> {
                if(!player.hasPermission("snownwcore.area") && !player.hasPermission("snownwcore.admin")){ player.sendMessage(ColorUtil.text(plugin.messages().get("no-permission"))); yield true; }
                player.getInventory().addItem(plugin.areas().wand());
                player.sendMessage(ColorUtil.text("&bSnowNW &fAlan Baltası verildi. &7Sağ tık = 1. konum, sol tık = 2. konum."));
                yield true;
            }
            case "bal", "balance", "money" -> {
                balanceCmd(player, args);
                yield true;
            }
            case "pay" -> {
                payCmd(player, args);
                yield true;
            }
            case "msg", "message", "tell", "whisper" -> {
                msgCmd(player, args);
                yield true;
            }
            case "team", "takim", "takım" -> {
                teamCmd(player, args);
                yield true;
            }
            case "cuboid", "kup", "küp" -> { cuboidCmd(player, args); yield true; }
            case "baltop", "zenginler", "enler" -> { baltopCmd(player); yield true; }
            case "shardmanager", "shardyonetim", "shardyönetim" -> { shardManager(player, args); yield true; }
            case "sell", "sat" -> { plugin.sell().sellHand(player); yield true; }
            case "sellall", "sathepsi", "satall" -> { plugin.sell().sellAll(player); yield true; }
            case "order", "orders", "siparis", "sipariş" -> {
                if (args.length == 0) {
                    if (plugin.menus().supports(player)) plugin.menus().openOrders(player);
                    else player.sendMessage(ColorUtil.text(plugin.messages().get("order-usage")));
                } else {
                    orderCmd(player, args);
                }
                yield true;
            }
            case "bounty", "bounties", "odul", "ödül" -> {
                if (args.length == 0) {
                    if (plugin.menus().supports(player)) plugin.menus().openBounties(player);
                    else bountyListCmd(player);
                } else {
                    bountyCmd(player, args);
                }
                yield true;
            }
            case "market", "shop", "magaza", "mağaza" -> { plugin.shopGui().openCategories(player); yield true; }
            case "shardshop", "shardmarket", "shardpazari", "shardpazarı" -> { plugin.shardShopGui().open(player, 0); yield true; }
            case "clearlag", "lagtemizle", "lagtemizlik" -> {
                if (!player.hasPermission("snownwcore.admin.clearlag")) {
                    player.sendMessage(ColorUtil.text(plugin.messages().get("no-permission")));
                    yield true;
                }
                int removed = plugin.clearLag().clearNow();
                player.sendMessage(ColorUtil.text(plugin.messages().get("clearlag-done").replace("{count}", String.valueOf(removed))));
                yield true;
            }
            case "keyall", "anahtarver" -> {
                if (!player.hasPermission("snownwcore.admin.keyall")) {
                    player.sendMessage(ColorUtil.text(plugin.messages().get("no-permission")));
                    yield true;
                }
                plugin.keyAll().executeAll();
                yield true;
            }
            case "doublejump", "ciftzipla", "çiftzıpla", "dj" -> {
                if (!player.hasPermission("snownwcore.doublejump")) {
                    player.sendMessage(ColorUtil.text(plugin.messages().get("no-permission")));
                    yield true;
                }
                if (!plugin.getConfig().getBoolean("double-jump.enabled", true)) {
                    player.sendMessage(ColorUtil.text(plugin.messages().get("disabled")));
                    yield true;
                }
                boolean djOn = plugin.doubleJump().toggle(player);
                player.sendMessage(ColorUtil.text(plugin.messages().get(djOn ? "doublejump-on" : "doublejump-off")));
                yield true;
            }
            default -> false;
        };
    }



    /** /siparis olustur <esya> <adet> <birimfiyat> | iptal <id> | sat <id> | benim */
    private void orderCmd(Player player, String[] args) {
        if (!plugin.orders().enabled()) { player.sendMessage(ColorUtil.text(plugin.messages().get("disabled"))); return; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "olustur", "oluştur", "yeni", "create" -> {
                if (args.length < 4) { player.sendMessage(ColorUtil.text(plugin.messages().get("order-usage"))); return; }
                org.bukkit.Material mat = org.bukkit.Material.matchMaterial(args[1].toUpperCase(Locale.ROOT));
                if (mat == null || !mat.isItem()) { player.sendMessage(ColorUtil.text(plugin.messages().get("order-invalid-material"))); return; }
                try {
                    int amount = Integer.parseInt(args[2]);
                    double price = Double.parseDouble(args[3].replace(',', '.'));
                    String err = plugin.orders().create(player, mat, amount, price);
                    if (err != null) player.sendMessage(ColorUtil.text(err));
                } catch (NumberFormatException e) { player.sendMessage(ColorUtil.text(plugin.messages().get("order-invalid-amount"))); }
            }
            case "iptal", "cancel" -> {
                if (args.length < 2) { player.sendMessage(ColorUtil.text("&7/siparis iptal <id>")); return; }
                try {
                    String err = plugin.orders().cancel(player, Integer.parseInt(args[1]));
                    if (err != null) player.sendMessage(ColorUtil.text(err));
                } catch (NumberFormatException e) { player.sendMessage(ColorUtil.text("&cGeçerli bir id gir: /siparis iptal <id>")); }
            }
            case "sat", "fill", "doldur" -> {
                if (args.length < 2) { player.sendMessage(ColorUtil.text("&7/siparis sat <id>")); return; }
                try {
                    String err = plugin.orders().fill(player, Integer.parseInt(args[1]));
                    if (err != null) player.sendMessage(ColorUtil.text(err));
                } catch (NumberFormatException e) { player.sendMessage(ColorUtil.text("&cGeçerli bir id gir: /siparis sat <id>")); }
            }
            case "benim", "mine", "listem" -> {
                var mine = plugin.orders().mine(player.getUniqueId());
                if (mine.isEmpty()) { player.sendMessage(ColorUtil.text("&7Açık siparişin yok. Oluştur: &f/siparis olustur <esya> <adet> <birimfiyat>")); return; }
                player.sendMessage(ColorUtil.text("&6&lSiparişlerim:"));
                for (var o : mine)
                    player.sendMessage(ColorUtil.text("&7#" + o.id + " &f" + o.material.name().toLowerCase(Locale.ROOT)
                            + " &8· &f" + o.remaining + "/" + o.totalAmount + " kalan &8· &a" + plugin.economy().format(o.priceEach) + "/adet"));
            }
            default -> player.sendMessage(ColorUtil.text(plugin.messages().get("order-usage")));
        }
    }

    /** /odul <oyuncu> <miktar> | liste | kaldir <oyuncu> */
    private void bountyCmd(Player player, String[] args) {
        if (!plugin.bounty().enabled()) { player.sendMessage(ColorUtil.text(plugin.messages().get("disabled"))); return; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("liste") || sub.equals("list")) { bountyListCmd(player); return; }
        if (sub.equals("kaldir") || sub.equals("kaldır") || sub.equals("remove")) {
            if (!player.hasPermission("snownwcore.admin.bounty")) { player.sendMessage(ColorUtil.text(plugin.messages().get("no-permission"))); return; }
            if (args.length < 2) { player.sendMessage(ColorUtil.text("&7/odul kaldir <oyuncu>")); return; }
            org.bukkit.OfflinePlayer t = org.bukkit.Bukkit.getOfflinePlayer(args[1]);
            plugin.bounty().remove(player, t.getUniqueId());
            return;
        }
        if (args.length < 2) { player.sendMessage(ColorUtil.text("&7/odul <oyuncu> <miktar>  &8·  /odul liste")); return; }
        Player target = org.bukkit.Bukkit.getPlayerExact(args[0]);
        if (target == null) { player.sendMessage(ColorUtil.text(plugin.messages().get("player-offline"))); return; }
        try {
            double price = Double.parseDouble(args[1].replace(',', '.'));
            String err = plugin.bounty().place(player, target, price);
            if (err != null) player.sendMessage(ColorUtil.text(err));
        } catch (NumberFormatException e) { player.sendMessage(ColorUtil.text(plugin.messages().get("pay-money-invalid"))); }
    }

    private void bountyListCmd(Player player) {
        var all = new java.util.ArrayList<>(plugin.bounty().all().entrySet());
        if (all.isEmpty()) { player.sendMessage(ColorUtil.text(plugin.messages().get("bounty-none"))); return; }
        all.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        player.sendMessage(ColorUtil.text("&c&lKafa Ödülleri:"));
        int shown = 0;
        for (var e : all) {
            if (shown++ >= 10) break;
            String n = org.bukkit.Bukkit.getOfflinePlayer(e.getKey()).getName();
            player.sendMessage(ColorUtil.text("&8» &f" + (n == null ? "?" : n) + " &8· &c" + plugin.economy().format(e.getValue())));
        }
    }

    private void teamCmd(Player player, String[] args) {
        if (args.length == 0) {
            var tm = plugin.teams().get(player);
            if (tm == null) player.sendMessage(ColorUtil.text(plugin.messages().get("team-none")));
            else player.sendMessage(ColorUtil.text(plugin.messages().get("team-info")
                    .replace("{team}", tm.name()).replace("{members}", String.valueOf(tm.members().size()))));
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create", "oluştur", "olustur" -> {
                if (args.length < 2) { player.sendMessage(ColorUtil.text("&7/team create <isim>")); return; }
                switch (plugin.teams().create(player, args[1])) {
                    case OK -> player.sendMessage(ColorUtil.text(plugin.messages().get("team-created").replace("{team}", args[1])));
                    case INVALID_NAME -> player.sendMessage(ColorUtil.text(plugin.messages().get("team-invalid-name")
                            .replace("{min}", String.valueOf(plugin.teams().minNameLength()))
                            .replace("{max}", String.valueOf(plugin.teams().maxNameLength()))));
                    case RESTRICTED_NAME -> player.sendMessage(ColorUtil.text(plugin.messages().get("team-restricted-name")));
                    case ALREADY_IN_TEAM -> player.sendMessage(ColorUtil.text(plugin.messages().get("team-already-in")));
                    case NAME_TAKEN -> player.sendMessage(ColorUtil.text(plugin.messages().get("team-name-taken")));
                }
            }
            case "disband" -> {
                var tm = plugin.teams().get(player);
                if (tm == null) { player.sendMessage(ColorUtil.text(plugin.messages().get("team-none"))); return; }
                if (!tm.leader().equals(player.getUniqueId())) { player.sendMessage(ColorUtil.text(plugin.messages().get("team-not-leader"))); return; }
                plugin.teams().disband(tm);
                player.sendMessage(ColorUtil.text(plugin.messages().get("team-disbanded")));
            }
            case "leave" -> {
                plugin.teams().leave(player);
                player.sendMessage(ColorUtil.text(plugin.messages().get("team-left")));
            }
            case "invite" -> {
                if (args.length < 2) { player.sendMessage(ColorUtil.text("&7/team invite <player>")); return; }
                Player target = org.bukkit.Bukkit.getPlayerExact(args[1]);
                if (target == null) { player.sendMessage(ColorUtil.text(plugin.messages().get("player-offline"))); return; }
                plugin.teams().invite(player, target);
                player.sendMessage(ColorUtil.text(plugin.messages().get("team-invite-sent").replace("{player}", target.getName())));
                var tm = plugin.teams().get(player);
                if (tm != null) target.sendMessage(ColorUtil.text(plugin.messages().get("team-invited").replace("{team}", tm.name())));
            }
            case "accept", "join" -> {
                if (plugin.teams().accept(player)) player.sendMessage(ColorUtil.text(plugin.messages().get("team-joined")));
                else player.sendMessage(ColorUtil.text(plugin.messages().get("team-no-invite")));
            }
            case "kick" -> {
                if (args.length < 2) { player.sendMessage(ColorUtil.text("&7/team kick <player>")); return; }
                var tm = plugin.teams().get(player);
                if (tm == null) { player.sendMessage(ColorUtil.text(plugin.messages().get("team-none"))); return; }
                if (!tm.leader().equals(player.getUniqueId())) { player.sendMessage(ColorUtil.text(plugin.messages().get("team-not-leader"))); return; }
                org.bukkit.OfflinePlayer off = org.bukkit.Bukkit.getOfflinePlayer(args[1]);
                if (!tm.members().contains(off.getUniqueId()) || off.getUniqueId().equals(tm.leader())) {
                    player.sendMessage(ColorUtil.text(plugin.messages().get("team-kick-fail")));
                    return;
                }
                plugin.teams().leave(off.getUniqueId(), tm);
                player.sendMessage(ColorUtil.text(plugin.messages().get("team-kicked").replace("{player}", args[1])));
                if (off.isOnline() && off.getPlayer() != null)
                    off.getPlayer().sendMessage(ColorUtil.text(plugin.messages().get("team-kicked-you")));
            }
            case "info" -> {
                var tm = plugin.teams().get(player);
                if (tm == null) { player.sendMessage(ColorUtil.text(plugin.messages().get("team-none"))); return; }
                player.sendMessage(ColorUtil.text(plugin.messages().get("team-info")
                        .replace("{team}", tm.name()).replace("{members}", String.valueOf(tm.members().size()))));
            }
            case "chat", "sohbet" -> {
                var tm = plugin.teams().get(player);
                if (tm == null) { player.sendMessage(ColorUtil.text(plugin.messages().get("team-none"))); return; }
                // Mesaj varsa takım mesajı gönder: /team sohbet <mesaj...>
                if (args.length >= 2) {
                    plugin.teams().sendTeamChat(player, String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
                    return;
                }
                boolean tcOn = plugin.teams().toggleTeamChat(player.getUniqueId());
                player.sendMessage(ColorUtil.text(plugin.messages().get(tcOn ? "team-chat-on" : "team-chat-off")));
            }
            case "ff", "dostatesi", "dostatesi" -> {
                var tm = plugin.teams().get(player);
                if (tm == null) { player.sendMessage(ColorUtil.text(plugin.messages().get("team-none"))); return; }
                if (!tm.leader().equals(player.getUniqueId())) { player.sendMessage(ColorUtil.text(plugin.messages().get("team-not-leader"))); return; }
                boolean ff = plugin.teams().toggleFriendlyFire(tm);
                player.sendMessage(ColorUtil.text(plugin.messages().get(ff ? "team-ff-on" : "team-ff-off")));
            }
            case "home" -> {
                var tm = plugin.teams().get(player);
                if (tm == null) { player.sendMessage(ColorUtil.text(plugin.messages().get("team-none"))); return; }
                if (tm.home() == null) { player.sendMessage(ColorUtil.text(plugin.messages().get("team-no-home"))); return; }
                plugin.teleports().teleport(player, tm.home(), "Takım Evi", "team-home");
            }
            case "sethome" -> {
                var tm = plugin.teams().get(player);
                if (tm == null) { player.sendMessage(ColorUtil.text(plugin.messages().get("team-none"))); return; }
                if (!tm.leader().equals(player.getUniqueId())) { player.sendMessage(ColorUtil.text(plugin.messages().get("team-not-leader"))); return; }
                plugin.teams().setHome(tm, player.getLocation());
                player.sendMessage(ColorUtil.text(plugin.messages().get("team-home-set")));
            }
            case "delhome" -> {
                var tm = plugin.teams().get(player);
                if (tm == null) { player.sendMessage(ColorUtil.text(plugin.messages().get("team-none"))); return; }
                if (!tm.leader().equals(player.getUniqueId())) { player.sendMessage(ColorUtil.text(plugin.messages().get("team-not-leader"))); return; }
                plugin.teams().clearHome(tm);
                player.sendMessage(ColorUtil.text(plugin.messages().get("team-home-deleted")));
            }
            default -> player.sendMessage(ColorUtil.text("&7/team <oluştur|davet|katil|ayril|dagit|sohbet|ff|home|sethome|delhome>"));
        }
    }

    private void areaCmd(Player player, String[] args) {
        if(!player.hasPermission("snownwcore.area") && !player.hasPermission("snownwcore.admin")){
            player.sendMessage(ColorUtil.text(plugin.messages().get("no-permission"))); return;
        }
        if(args.length==0){ player.sendMessage(ColorUtil.text("&7/alan oluştur <isim> [tip] &8| &7/alan liste &8| &7/alan sil <isim>")); return; }
        String sub=args[0].toLowerCase(Locale.ROOT);
        if(sub.equals("oluştur")||sub.equals("olustur")||sub.equals("create")){
            if(args.length<2){ player.sendMessage(ColorUtil.text("&7/alan oluştur <isim> [tip]")); return; }
            String raw=args[1], name=raw; me.snownw.core.service.AreaService.Type type=me.snownw.core.service.AreaService.Type.NORMAL;
            String typeArg=args.length>=3?args[2]:raw;
            if (raw.equalsIgnoreCase("stash")) {
                if (args.length < 3) { player.sendMessage(ColorUtil.text("&7/alan oluştur stash <1-5>")); return; }
                name = "stash" + args[2];
                type = me.snownw.core.service.AreaService.Type.STASH;
            } else {
                switch(typeArg.toLowerCase(Locale.ROOT)){
                    case "afk" -> type=me.snownw.core.service.AreaService.Type.AFK;
                    case "rtpzone" -> type=me.snownw.core.service.AreaService.Type.RTPZONE;
                    case "end" -> type=me.snownw.core.service.AreaService.Type.END;
                    case "dünya","dunya" -> type=me.snownw.core.service.AreaService.Type.DUNYA;
                    case "nether" -> type=me.snownw.core.service.AreaService.Type.NETHER;
                    default -> type=me.snownw.core.service.AreaService.Type.NORMAL;
                }
            }
            try{ plugin.areas().create(name,type,player); player.sendMessage(ColorUtil.text("&bAlan &f"+name+" &7oluşturuldu. Tip: &f"+type.name())); }
            catch(IllegalArgumentException ex){
                String msg=switch(ex.getMessage()){case "NO_SELECTION"->"&cÖnce alan baltasıyla iki konumu seç.";case "DIFFERENT_WORLD"->"&cİki konum aynı dünyada olmalı.";default->"&cBu isimde bir alan zaten var.";};
                player.sendMessage(ColorUtil.text(msg));
            }
            return;
        }
        if(sub.equals("hedef")||sub.equals("target")){
            if(args.length<3){ player.sendMessage(ColorUtil.text("&7/alan hedef <rtpzone|afk> <hedef-alan>")); return; }
            var a=plugin.areas().get(args[1]); var d=plugin.areas().get(args[2]);
            if(a==null || (a.type()!=me.snownw.core.service.AreaService.Type.RTPZONE && a.type()!=me.snownw.core.service.AreaService.Type.AFK)){
                player.sendMessage(ColorUtil.text("&cRTP Zone veya AFK alanı bulunamadı.")); return;
            }
            if(d==null){ player.sendMessage(ColorUtil.text("&cHedef alan bulunamadı.")); return; }
            plugin.areas().setDestination(a.name(),d.name()); player.sendMessage(ColorUtil.text("&bAlan hedefi &f"+a.name()+" &7→ &f"+d.name()+" &7olarak ayarlandı.")); return;
        }
        if(sub.equals("hologram")||sub.equals("holo")){
            if(args.length<2){player.sendMessage(ColorUtil.text("&7/alan hologram <rtpzone>"));return;}
            var a=plugin.areas().get(args[1]); if(a==null||a.type()!=me.snownw.core.service.AreaService.Type.RTPZONE){player.sendMessage(ColorUtil.text("&cRTP Zone bulunamadı."));return;}
            plugin.areas().setHologram(a.name(),player.getLocation()); player.sendMessage(ColorUtil.text("&aRTP Zone hologramı oluşturuldu.")); return;
        }
        if(sub.equals("liste")||sub.equals("list")){
            player.sendMessage(ColorUtil.text("&bSnowNW &fAlanlar:")); for(var a:plugin.areas().all()) player.sendMessage(ColorUtil.text("&7- &f"+a.name()+" &8[&b"+a.type().name()+"&8]")); return;
        }
        if(sub.equals("sil")||sub.equals("delete")){
            if(args.length<2){player.sendMessage(ColorUtil.text("&7/alan sil <isim>"));return;}
            player.sendMessage(ColorUtil.text(plugin.areas().delete(args[1])?"&aAlan silindi.":"&cAlan bulunamadı.")); return;
        }
        player.sendMessage(ColorUtil.text("&7/alan oluştur <isim> [tip] | /alan oluştur stash <1-5> | /alan hedef <rtpzone> <hedef> | /alan hologram <rtpzone> | /alan liste | /alan sil <isim>"));
    }

    private void cuboidCmd(Player player, String[] args) {
        if (!player.hasPermission("snownwcore.admin") && !player.hasPermission("snownwcore.command.cuboid")) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("no-permission")));
            return;
        }
        if (args.length == 0) {
            player.sendMessage(ColorUtil.text("&7/cuboid <create|list|delete>"));
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> {
                if (args.length < 2) { player.sendMessage(ColorUtil.text("&7/cuboid create <name>")); return; }
                var result = plugin.cuboids().create(args[1], player);
                switch (result) {
                    case OK -> {
                        String msgKey = args[1].equalsIgnoreCase("rtpzone") ? "rtpzone-created" : "cuboid-created";
                        player.sendMessage(ColorUtil.text(plugin.messages().get(msgKey).replace("{name}", args[1])));
                    }
                    case NO_SELECTION -> player.sendMessage(ColorUtil.text(plugin.messages().get("cuboid-need-selection")));
                    case DIFFERENT_WORLD -> player.sendMessage(ColorUtil.text(plugin.messages().get("cuboid-diff-world")));
                    case RTPZONE_EXISTS -> player.sendMessage(ColorUtil.text(plugin.messages().get("rtpzone-exists")));
                    case ALREADY_EXISTS -> player.sendMessage(ColorUtil.text(plugin.messages().get("cuboid-exists").replace("{name}", args[1])));
                }
            }
            case "list" -> {
                player.sendMessage(ColorUtil.text(plugin.messages().get("cuboid-list-header")));
                for (var c : plugin.cuboids().all()) {
                    org.bukkit.World cw = org.bukkit.Bukkit.getWorld(c.world());
                    String wn = cw == null ? "Bilinmeyen" : switch (cw.getEnvironment()) {
                        case NETHER -> "Nether";
                        case THE_END -> "End";
                        default -> "Dünya";
                    };
                    player.sendMessage(ColorUtil.text("&7- &f" + c.name() + " &8(" + wn + ")"));
                }
            }
            case "delete" -> {
                if (args.length < 2) { player.sendMessage(ColorUtil.text("&7/cuboid delete <name>")); return; }
                if (plugin.cuboids().delete(args[1])) {
                    String msgKey = args[1].equalsIgnoreCase("rtpzone") ? "rtpzone-deleted" : "cuboid-deleted";
                    player.sendMessage(ColorUtil.text(plugin.messages().get(msgKey).replace("{name}", args[1])));
                } else player.sendMessage(ColorUtil.text(plugin.messages().get("cuboid-not-found")));
            }
            default -> player.sendMessage(ColorUtil.text("&7/cuboid <create|list|delete>"));
        }
    }

    private void balanceCmd(Player player, String[] args) {
        if (!plugin.cmdEnabled("balance") && !plugin.cmdEnabled("bal")) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("disabled")));
            return;
        }
        org.bukkit.OfflinePlayer target = player;
        if (args.length >= 1) {
            target = org.bukkit.Bukkit.getOfflinePlayer(args[0]);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                player.sendMessage(ColorUtil.text(plugin.messages().get("player-offline")));
                return;
            }
        }
        double bal = plugin.economy().get(target);
        String fmt = plugin.economy().format(bal);
        String name = target.getName() != null ? target.getName() : "?";
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("bal-self").replace("{amount}", fmt)));
        } else {
            player.sendMessage(ColorUtil.text(plugin.messages().get("bal-other")
                    .replace("{player}", name).replace("{amount}", fmt)));
        }
    }

    private void baltopCmd(Player player) {
        player.sendMessage(ColorUtil.text(plugin.messages().get("baltop-header")));
        // online players sorted by balance (Vault has no global top without DB)
        java.util.List<org.bukkit.entity.Player> list = new java.util.ArrayList<>(org.bukkit.Bukkit.getOnlinePlayers());
        list.sort((a, b) -> Double.compare(plugin.economy().get(b), plugin.economy().get(a)));
        int i = 1;
        for (org.bukkit.entity.Player p : list) {
            if (i > 10) break;
            player.sendMessage(ColorUtil.text(plugin.messages().get("baltop-line")
                    .replace("{rank}", String.valueOf(i++))
                    .replace("{player}", p.getName())
                    .replace("{amount}", plugin.economy().format(plugin.economy().get(p)))));
        }
        if (list.isEmpty()) player.sendMessage(ColorUtil.text(plugin.messages().get("baltop-empty")));
    }

    private void payCmd(Player player, String[] args) {
        if (args.length == 0) {
            if (plugin.menus().supports(player)) plugin.menus().openPayTargetDialog(player);
            else player.sendMessage(ColorUtil.text(plugin.messages().get("usage-pay")));
            return;
        }
        org.bukkit.entity.Player target = org.bukkit.Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("player-offline")));
            return;
        }
        if (args.length == 1 && plugin.menus().supports(player)) {
            plugin.menus().openPayDialog(player, target);
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("usage-pay")));
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("pay-self")));
            return;
        }
        if (!plugin.settings().get(target.getUniqueId(), me.snownw.core.data.SettingsStore.Toggle.ALLOW_PAYMENTS)) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("pay-disabled-target")));
            return;
        }
        double amount;
        try {
            amount = me.snownw.core.util.MoneyFormat.parse(args[1]);
        } catch (Exception e) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("eco-invalid-amount")));
            return;
        }
        if (amount <= 0) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("eco-invalid-amount")));
            return;
        }
        if (plugin.settings().get(player.getUniqueId(), me.snownw.core.data.SettingsStore.Toggle.PAY_CONFIRMATIONS) && plugin.menus().supports(player)) {
            plugin.menus().openPayConfirmation(player, target, amount);
            return;
        }
        if (!plugin.economy().withdraw(player, amount)) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("pay-broke")));
            return;
        }
        if (!plugin.economy().deposit(target, amount)) {
            plugin.economy().deposit(player, amount);
            player.sendMessage(ColorUtil.text("&cÖdeme gerçekleştirilemedi; paran iade edildi."));
            return;
        }
        String fmt = plugin.economy().format(amount);
        player.sendMessage(ColorUtil.text(plugin.messages().get("pay-sent")
                .replace("{player}", target.getName()).replace("{amount}", fmt)));
        if (plugin.settings().get(target.getUniqueId(), me.snownw.core.data.SettingsStore.Toggle.PAY_ALERTS)) {
            target.sendMessage(ColorUtil.text(plugin.messages().get("pay-received")
                    .replace("{player}", player.getName()).replace("{amount}", fmt)));
        }
    }

    private void msgCmd(Player player, String[] args) {
        if (plugin.offenses() != null && plugin.offenses().isMuted(player.getUniqueId())) {
            player.sendMessage(ColorUtil.text("&cSohbet kullanımın kısıtlı. Özel mesaj gönderemezsin."));
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("usage-msg")));
            return;
        }
        if (!plugin.settings().get(player.getUniqueId(), me.snownw.core.data.SettingsStore.Toggle.PRIVATE_MESSAGES)) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("msg-disabled-self")));
            return;
        }
        org.bukkit.entity.Player target = org.bukkit.Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("player-offline")));
            return;
        }
        if (!plugin.settings().get(target.getUniqueId(), me.snownw.core.data.SettingsStore.Toggle.PRIVATE_MESSAGES)) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("msg-disabled-target")));
            return;
        }
        if (!plugin.socialAudienceAllows(target.getUniqueId(), player.getUniqueId(), me.snownw.core.data.SettingsStore.Toggle.PRIVATE_MESSAGES)) {
            player.sendMessage(ColorUtil.text("&cBu oyuncu özel mesajlarını seçtiği kişilerle sınırlamış."));
            return;
        }
        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        // YOU-target: message  /  sender-YOU: message
        player.sendMessage(ColorUtil.text(plugin.messages().get("msg-out")
                .replace("{player}", target.getName()).replace("{message}", message)));
        target.sendMessage(ColorUtil.text(plugin.messages().get("msg-in")
                .replace("{player}", player.getName()).replace("{message}", message)));
    }

    private boolean admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("snownwcore.admin")) {
            sender.sendMessage(ColorUtil.text(plugin.messages().get("no-permission")));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(ColorUtil.text("&e/snownwcore <reload|enable|disable|eco|wand>"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "wand" -> {
                sender.getServer().getPlayerExact(sender.getName());
                if (sender instanceof Player pl) {
                    pl.getInventory().addItem(plugin.cuboids().wand());
                    pl.sendMessage(ColorUtil.text(plugin.messages().get("wand-given")));
                } else sender.sendMessage("Players only");
            }
            case "reload" -> {
                plugin.reloadAll();
                sender.sendMessage(ColorUtil.text(plugin.messages().getPrefixed("reload")));
            }
            case "disable" -> cmdToggleList(sender, false, args);
            case "enable" -> cmdToggleList(sender, true, args);
            case "eco" -> ecoAdmin(sender, args);
            default -> sender.sendMessage(ColorUtil.text("&e/snownwcore <reload|enable|disable|eco|wand>"));
        }
        return true;
    }


    /** /snownwcore disable [cmd]  |  /snownwcore enable [cmd] */
    private void cmdToggleList(CommandSender sender, boolean enabling, String[] args) {
        var toggles = plugin.commandToggles();
        if (args.length >= 2) {
            String cmd = args[1].toLowerCase(Locale.ROOT);
            boolean currently = toggles.isEnabled(cmd);
            if (enabling) {
                if (currently) {
                    sender.sendMessage(ColorUtil.text(plugin.messages().get("cmd-already-on").replace("{cmd}", cmd)));
                } else {
                    toggles.setEnabled(cmd, true);
                    sender.sendMessage(ColorUtil.text(plugin.messages().get("cmd-enabled").replace("{cmd}", cmd)));
                }
            } else {
                if (!currently) {
                    sender.sendMessage(ColorUtil.text(plugin.messages().get("cmd-already-off").replace("{cmd}", cmd)));
                } else {
                    toggles.setEnabled(cmd, false);
                    sender.sendMessage(ColorUtil.text(plugin.messages().get("cmd-disabled").replace("{cmd}", cmd)));
                }
            }
            return;
        }
        java.util.List<String> list = enabling ? toggles.disabledList() : toggles.enabledList();
        if (list.isEmpty()) {
            sender.sendMessage(ColorUtil.text(enabling
                    ? plugin.messages().get("cmd-none-disabled")
                    : plugin.messages().get("cmd-none-enabled")));
            return;
        }
        if (sender instanceof Player player && plugin.menus().supports(player)) {
            plugin.menus().openCommandToggle(player, enabling);
            return;
        }
        String header = enabling
                ? plugin.messages().get("cmd-enable-header")
                : plugin.messages().get("cmd-disable-header");
        sender.sendMessage(ColorUtil.text(header));
        for (String c : list) {
            String line = (enabling ? plugin.messages().get("cmd-enable-line")
                    : plugin.messages().get("cmd-disable-line")).replace("{cmd}", c);
            sender.sendMessage(ColorUtil.text(line));
        }
        sender.sendMessage(ColorUtil.text(plugin.messages().get("cmd-usage-hint")
                .replace("{mode}", enabling ? "enable" : "disable")));
    }

    private void ecoAdmin(CommandSender sender, String[] args) {
        // /snownwcore eco give <player|*> <amount>
        if (args.length < 4) {
            sender.sendMessage(ColorUtil.text("&e/snownwcore eco <give|take|set|bal> <player|*> <amount>"));
            sender.sendMessage(ColorUtil.text("&7Amounts: &f1k 1m 1b 1t 1q &7(e.g. 50b)"));
            return;
        }
        String op = args[1].toLowerCase(Locale.ROOT);
        String targetName = args[2];
        double amount;
        try {
            amount = me.snownw.core.util.MoneyFormat.parse(args[3]);
        } catch (Exception e) {
            sender.sendMessage(ColorUtil.text(plugin.messages().get("eco-invalid-amount")));
            return;
        }
        if (amount < 0) {
            sender.sendMessage(ColorUtil.text(plugin.messages().get("eco-invalid-amount")));
            return;
        }
        String fmt = plugin.economy().format(amount);
        if (targetName.equals("*")) {
            int n = 0;
            for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                applyEco(op, p, amount);
                n++;
            }
            for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                p.sendMessage(ColorUtil.text(plugin.messages().get("eco-received").replace("{amount}", fmt)));
            }
            sender.sendMessage(ColorUtil.text(plugin.messages().get("eco-give-all-admin")
                    .replace("{amount}", fmt).replace("{count}", String.valueOf(n))));
            return;
        }
        org.bukkit.OfflinePlayer off = org.bukkit.Bukkit.getOfflinePlayer(targetName);
        if (!off.hasPlayedBefore() && !off.isOnline()) {
            sender.sendMessage(ColorUtil.text(plugin.messages().get("player-offline")));
            return;
        }
        applyEco(op, off, amount);
        sender.sendMessage(ColorUtil.text(plugin.messages().get("eco-ok")
                .replace("{op}", op)
                .replace("{player}", targetName)
                .replace("{amount}", fmt)));
        if (off.isOnline() && off.getPlayer() != null) {
            off.getPlayer().sendMessage(ColorUtil.text(plugin.messages().get("eco-received")
                    .replace("{amount}", fmt)));
        }
    }

    private void applyEco(String op, org.bukkit.OfflinePlayer off, double amount) {
        switch (op) {
            case "give", "add" -> plugin.economy().deposit(off, amount);
            case "take", "remove" -> plugin.economy().withdraw(off, amount);
            case "set" -> plugin.economy().set(off, amount);
            default -> {}
        }
    }


    private boolean home(Player player, String[] args) {
        List<HomeStore.Home> list = plugin.homes().list(player.getUniqueId());
        if (args.length == 0) {
            // open homes GUI (dialog or classic) – not English error
            if (plugin.menus().supports(player)) plugin.menus().openHomes(player, 0);
            else plugin.classic().openHomes(player);
            return true;
        }
        // /home 20 or /home name
        HomeStore.Home home = null;
        try {
            int slot = Integer.parseInt(args[0]);
            home = plugin.homes().getSlot(player.getUniqueId(), slot);
        } catch (NumberFormatException e) {
            home = plugin.homes().getByName(player.getUniqueId(), args[0]);
        }
        if (home == null) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("home-missing")));
            return true;
        }
        plugin.teleports().teleport(player, home.location(), "#" + home.slot() + " " + home.name());
        return true;
    }

    private boolean setHome(Player player, String[] args) {
        int limit = plugin.homes().homeLimit(player);
        int slot;
        String display = null;
        if (args.length == 0) {
            slot = -1;
            for (int s = 1; s <= limit; s++) {
                if (!plugin.homes().hasSlot(player.getUniqueId(), s)) { slot = s; break; }
            }
            if (slot < 0) {
                player.sendMessage(ColorUtil.text(plugin.messages().get("home-limit").replace("{n}", "?")));
                return true;
            }
        } else {
            try {
                slot = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                // name → first free slot with that display name
                slot = -1;
                for (int s = 1; s <= limit; s++) {
                    if (!plugin.homes().hasSlot(player.getUniqueId(), s)) { slot = s; break; }
                }
                display = args[0];
                if (slot < 0) {
                    player.sendMessage(ColorUtil.text(plugin.messages().get("home-limit").replace("{n}", "?")));
                    return true;
                }
            }
        }
        if (slot < 1 || slot > limit) {
            player.sendMessage(ColorUtil.text(plugin.getConfig().getString("messages.home-locked")));
            return true;
        }
        String nm = display != null ? display : ("home" + slot);
        plugin.homes().setSlot(player.getUniqueId(), slot, player.getLocation(), nm, limit);
        String setMsg = plugin.messages().get("home-set", java.util.Map.of(
                "name", nm,
                "slot", String.valueOf(slot)));
        // fallback if placeholders left
        setMsg = setMsg.replace("{name}", nm).replace("{slot}", String.valueOf(slot));
        player.sendMessage(ColorUtil.text(setMsg));
        plugin.sounds().play(player, "success");
        return true;
    }

    private boolean delHome(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("usage-delhome")));
            return true;
        }
        HomeStore.Home home = null;
        try {
            int slot = Integer.parseInt(args[0]);
            home = plugin.homes().getSlot(player.getUniqueId(), slot);
            if (home != null) plugin.homes().deleteSlot(player.getUniqueId(), slot);
        } catch (NumberFormatException e) {
            home = plugin.homes().getByName(player.getUniqueId(), args[0]);
            if (home != null) plugin.homes().deleteSlot(player.getUniqueId(), home.slot());
        }
        if (home != null) {
            player.sendMessage(ColorUtil.text(ColorUtil.replace(
                    plugin.getConfig().getString("messages.home-deleted"), "name", "#" + home.slot())));
            plugin.sounds().play(player, "home-delete");
        } else {
            player.sendMessage(ColorUtil.text(plugin.messages().get("home-missing")));
        }
        return true;
    }

    private boolean leaderboard(Player player, String[] args) {
        if (args.length == 0) {
            plugin.menus().openLeaderboardMenu(player);
            return true;
        }
        StatsStore.Type type = switch (args[0].toLowerCase(Locale.ROOT)) {
            case "deaths", "death" -> StatsStore.Type.DEATHS;
            case "blocks", "block" -> StatsStore.Type.BLOCKS;
            case "mobs", "mob" -> StatsStore.Type.MOBS;
            case "playtime", "time" -> StatsStore.Type.PLAYTIME;
            default -> StatsStore.Type.KILLS;
        };
        // Chat fallback + dialog
        List<StatsStore.Entry> top = plugin.stats().top(type, 10);
        player.sendMessage(ColorUtil.text("&6=== Leaderboard: " + type.name() + " ==="));
        int i = 1;
        for (StatsStore.Entry e : top) {
            String val = type == StatsStore.Type.PLAYTIME
                    ? StatsStore.formatPlaytime(e.value()) : String.valueOf(e.value());
            player.sendMessage(ColorUtil.text("&e#" + (i++) + " &f" + e.name() + " &7- &a" + val));
        }
        if (plugin.menus().supports(player)) {
            plugin.menus().openLeaderboard(player, type);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return tab(sender, command.getName().toLowerCase(Locale.ROOT), args);
    }

    private List<String> tab(CommandSender sender, String name, String[] args) {
        String partial = args.length == 0 ? "" : args[args.length - 1];
        if (args.length <= 1) {
            if (name.equals("home") || name.equals("delhome") || name.equals("sethome")) {
                if (!(sender instanceof Player p)) return List.of();
                List<String> homes = new ArrayList<>();
                for (var h : plugin.homes().list(p.getUniqueId())) {
                    homes.add(String.valueOf(h.slot()));
                    if (h.name() != null && !homes.contains(h.name())) homes.add(h.name());
                }
                return filter(homes, partial);
            }
            if (name.equals("leaderboard") || name.equals("lb") || name.equals("leaderboards")) {
                return filter(List.of("kills", "deaths", "blocks", "mobs", "playtime"), partial);
            }
            if (name.equals("bal") || name.equals("balance") || name.equals("money")) {
                return filter(org.bukkit.Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName).collect(Collectors.toList()), partial);
            }
            if (name.equals("tpa") || name.equals("tpahere") || name.equals("stats")
                    || name.equals("pay") || name.equals("msg") || name.equals("message")
                    || name.equals("tell") || name.equals("whisper") || name.equals("friends")) {
                List<String> names = org.bukkit.Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName).collect(Collectors.toList());
                if (sender instanceof Player self) names.remove(self.getName());
                return filter(names, partial);
            }
            if (name.equals("snownwcore") || name.equals("vc")) {
                return filter(List.of("reload", "enable", "disable", "eco", "wand"), partial);
            }
            if (name.equals("rtp")) {
                return filter(List.of("dünya", "nether", "end"), partial);
            }
            if (name.equals("spawnstash")) {
                return filter(List.of("1", "2", "3", "4", "5"), partial);
            }
            if (name.equals("ping")) {
                return filter(org.bukkit.Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), partial);
            }
            if (name.equals("gmc") || name.equals("gms") || name.equals("gmsp")) return List.of();
            if (name.equals("amethysttool") || name.equals("ametisttool") || name.equals("ametistesya")) {
                if (args.length == 1) return filter(List.of("give"), partial);
                if (args.length == 2) return filter(org.bukkit.Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), partial);
                if (args.length == 3) return filter(List.of("DRILL", "SHOVEL", "BUCKET", "SHARD_BOOSTER"), partial);
            }
        }
        if (name.equals("offense")) {
            if (args.length == 1) {
                return filter(org.bukkit.Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), partial);
            }
            if (args.length == 2) return filter(new ArrayList<>(plugin.offenses().keys()), partial);
        }
        if (name.equals("team") || name.equals("takim") || name.equals("takım")) {
            if (args.length == 1) return filter(List.of("create", "invite", "accept", "join", "kick", "leave", "disband", "info", "chat", "home", "sethome", "delhome"), partial);
            if (args.length == 2 && (args[0].equalsIgnoreCase("invite") || args[0].equalsIgnoreCase("kick"))) {
                return filter(org.bukkit.Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), partial);
            }
        }
        if (name.equals("fakeplayer")) return List.of();
        if (name.equals("alan") && args.length >= 1) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (args.length == 1) return filter(List.of("oluştur","olustur","create","hedef","target","hologram","holo","liste","list","sil","delete"), partial);
            if (args.length == 2 && (sub.equals("oluştur") || sub.equals("olustur") || sub.equals("create"))) {
                return filter(List.of("NORMAL", "AFK", "RTPZONE", "DUNYA", "NETHER", "END", "STASH"), partial);
            }
            if (args.length == 3 && (sub.equals("oluştur") || sub.equals("olustur") || sub.equals("create")) && args[1].equalsIgnoreCase("stash")) {
                return filter(List.of("1","2","3","4","5"), partial);
            }
            if (args.length == 2 && (sub.equals("hedef") || sub.equals("target"))) {
                return filter(plugin.areas().all().stream()
                        .filter(a -> a.type() == me.snownw.core.service.AreaService.Type.AFK || a.type() == me.snownw.core.service.AreaService.Type.RTPZONE)
                        .map(me.snownw.core.service.AreaService.Area::name).collect(Collectors.toList()), partial);
            }
            if (args.length == 3 && (sub.equals("hedef") || sub.equals("target"))) {
                return filter(plugin.areas().all().stream()
                        .filter(a -> !a.name().equalsIgnoreCase(args[1]))
                        .map(me.snownw.core.service.AreaService.Area::name).collect(Collectors.toList()), partial);
            }
            if (args.length == 2 && (sub.equals("hologram") || sub.equals("holo") || sub.equals("sil") || sub.equals("delete"))) {
                return filter(plugin.areas().all().stream().map(me.snownw.core.service.AreaService.Area::name).collect(Collectors.toList()), partial);
            }
        }
        if (args.length == 2 && (name.equals("snownwcore") || name.equals("vc"))) {
            if (args[0].equalsIgnoreCase("eco")) {
                return filter(List.of("give", "take", "set"), partial);
            }
            if (args[0].equalsIgnoreCase("disable")) {
                return filter(plugin.commandToggles().enabledList(), partial);
            }
            if (args[0].equalsIgnoreCase("enable")) {
                return filter(plugin.commandToggles().disabledList(), partial);
            }
        }
        if (args.length == 3 && (name.equals("snownwcore") || name.equals("vc"))
                && args[0].equalsIgnoreCase("eco")) {
            List<String> names = org.bukkit.Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName).collect(Collectors.toList());
            names.add(0, "*");
            return filter(names, partial);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) if (o.toLowerCase(Locale.ROOT).startsWith(p)) out.add(o);
        return out;
    }
    private void shardManager(Player player, String[] args) {
        if (!player.hasPermission("snownwcore.admin") && !player.hasPermission("snownwcore.command.shardmanager")) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("no-permission")));
            return;
        }
        if (args.length == 0) {
            player.sendMessage(ColorUtil.text("&7/shardmanager <give|take|set|giveall|reset> ..."));
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "give" -> {
                if (args.length < 3) { player.sendMessage(ColorUtil.text("&7/shardmanager give <player> <amount>")); return; }
                org.bukkit.OfflinePlayer off = org.bukkit.Bukkit.getOfflinePlayer(args[1]);
                long amt;
                try { amt = Long.parseLong(args[2]); } catch (Exception e) {
                    player.sendMessage(ColorUtil.text(plugin.messages().get("eco-invalid-amount"))); return; }
                plugin.shards().add(off.getUniqueId(), amt);
                player.sendMessage(ColorUtil.text(plugin.messages().get("shard-admin-give")
                        .replace("{player}", args[1]).replace("{amount}", String.valueOf(amt))));
            }
            case "take" -> {
                if (args.length < 3) { player.sendMessage(ColorUtil.text("&7/shardmanager take <player> <amount>")); return; }
                org.bukkit.OfflinePlayer off = org.bukkit.Bukkit.getOfflinePlayer(args[1]);
                long amt;
                try { amt = Long.parseLong(args[2]); } catch (Exception e) {
                    player.sendMessage(ColorUtil.text(plugin.messages().get("eco-invalid-amount"))); return; }
                plugin.shards().take(off.getUniqueId(), amt);
                player.sendMessage(ColorUtil.text(plugin.messages().get("shard-admin-take")
                        .replace("{player}", args[1]).replace("{amount}", String.valueOf(amt))));
            }
            case "set" -> {
                if (args.length < 3) { player.sendMessage(ColorUtil.text("&7/shardmanager set <player> <amount>")); return; }
                org.bukkit.OfflinePlayer off = org.bukkit.Bukkit.getOfflinePlayer(args[1]);
                long amt;
                try { amt = Long.parseLong(args[2]); } catch (Exception e) {
                    player.sendMessage(ColorUtil.text(plugin.messages().get("eco-invalid-amount"))); return; }
                plugin.shards().set(off.getUniqueId(), amt);
                player.sendMessage(ColorUtil.text(plugin.messages().get("shard-admin-set")
                        .replace("{player}", args[1]).replace("{amount}", String.valueOf(amt))));
            }
            case "giveall" -> {
                if (args.length < 2) { player.sendMessage(ColorUtil.text("&7/shardmanager giveall <amount>")); return; }
                long amt;
                try { amt = Long.parseLong(args[1]); } catch (Exception e) {
                    player.sendMessage(ColorUtil.text(plugin.messages().get("eco-invalid-amount"))); return; }
                for (Player online : org.bukkit.Bukkit.getOnlinePlayers()) plugin.shards().add(online.getUniqueId(), amt);
                player.sendMessage(ColorUtil.text(plugin.messages().get("shard-admin-giveall")
                        .replace("{amount}", String.valueOf(amt))));
            }
            case "reset" -> {
                if (args.length < 2) { player.sendMessage(ColorUtil.text("&7/shardmanager reset <player>")); return; }
                org.bukkit.OfflinePlayer off = org.bukkit.Bukkit.getOfflinePlayer(args[1]);
                plugin.shards().set(off.getUniqueId(), 0);
                player.sendMessage(ColorUtil.text(plugin.messages().get("shard-admin-reset").replace("{player}", args[1])));
            }
            default -> player.sendMessage(ColorUtil.text("&7/shardmanager <give|take|set|giveall|reset>"));
        }
    }

}
