package me.snownw.core.dialog;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.data.HomeStore;
import me.snownw.core.data.SettingsStore;
import me.snownw.core.data.StatsStore;
import me.snownw.core.util.ColorUtil;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Locale;
import java.util.UUID;

public final class DialogListener implements Listener {

    private final SnowNWCorePlugin plugin;

    public DialogListener(SnowNWCorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(PlayerCustomClickEvent event) {
        if (!(event.getCommonConnection() instanceof PlayerGameConnection conn)) return;
        String id = event.getIdentifier().asString();
        if (!id.startsWith("snownwcore:")) return;

        Player player = conn.getPlayer();
        if (!plugin.commandsEnabled()) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("disabled")));
            return;
        }

        String action = id.substring("snownwcore:".length()).toLowerCase(Locale.ROOT);
        DialogResponseView view = event.getDialogResponseView();
        DialogMenus menus = plugin.menus();

        if (action.startsWith("kit/claim/")) {
            String kitId = action.substring("kit/claim/".length());
            var kit = plugin.kits().get(kitId);
            if (kit == null || !player.hasPermission(kit.permission())) {
                player.sendMessage(ColorUtil.text(plugin.messages().get("kit-no-permission")));
                menus.openKits(player);
                return;
            }
            long left = plugin.kits().remaining(player, kit);
            if (left > 0) {
                player.sendMessage(ColorUtil.text(plugin.messages().get("kit-cooldown").replace("{sec}", String.valueOf(left))));
                menus.openKits(player);
                return;
            }
            if (plugin.kits().claim(player, kit.id())) {
                player.sendMessage(ColorUtil.text(plugin.messages().get("kit-claimed").replace("{kit}", kit.id())));
            } else {
                player.sendMessage(ColorUtil.text("&cKit alınamadı."));
            }
            menus.openKits(player);
            return;
        }

        if (action.startsWith("rtpqueue/confirm/")) {
            String world = action.substring("rtpqueue/confirm/".length());
            plugin.rtp().joinQueue(player, world);
            closeWaitedDialog(player);
            return;
        }
        if (action.startsWith("rtpqueue/world/")) {
            String world = action.substring("rtpqueue/world/".length());
            menus.openRtpQueueConfirm(player, world);
            return;
        }
        if (action.equals("rtpqueue/menu")) {
            menus.openRtpQueueMenu(player);
            return;
        }
        if (action.startsWith("rtp/world/")) {
            plugin.rtp().rtp(player, action.substring("rtp/world/".length()));
            closeWaitedDialog(player);
            return;
        }
        if (action.equals("rtp/menu")) {
            menus.openRtpMenu(player);
            return;
        }


        if (action.startsWith("cmd/enable/")) {
            String cmd = action.substring("cmd/enable/".length());
            plugin.commandToggles().setEnabled(cmd, true);
            player.sendMessage(ColorUtil.text(plugin.messages().get("cmd-enabled").replace("{cmd}", cmd)));
            plugin.menus().openCommandToggle(player, true);
            return;
        }
        if (action.startsWith("cmd/disable/")) {
            String cmd = action.substring("cmd/disable/".length());
            plugin.commandToggles().setEnabled(cmd, false);
            player.sendMessage(ColorUtil.text(plugin.messages().get("cmd-disabled").replace("{cmd}", cmd)));
            plugin.menus().openCommandToggle(player, false);
            return;
        }
        switch (action) {
            case "main" -> menus.openMain(player);
            case "pay/open" -> menus.openPayTargetDialog(player);
            case "kits" -> menus.openKits(player);
            case "team/menu" -> menus.openTeamMenu(player);
            case "orders" -> menus.openOrders(player);
            case "order/mine" -> menus.openOrderMine(player);
            case "order/create" -> menus.openOrderCreate(player);
            case "bounty" -> menus.openBounties(player);
            case "bounty/place" -> menus.openBountyPlace(player);
            case "market" -> { player.closeDialog(); plugin.shopGui().openCategories(player); }
            case "shardshop" -> { player.closeDialog(); plugin.shardShopGui().open(player, 0); }
            case "rtp/menu", "rtp/go" -> menus.openRtpMenu(player);
            case "teleport/menu" -> menus.openTeleportMenu(player);
            case "teleport/warps" -> menus.openWarpMenu(player);
            case "teleport/spawn" -> {
                var loc = plugin.warps().spawn();
                if (loc == null) player.sendMessage(ColorUtil.text("&cSpawn ayarlı değil."));
                else plugin.teleports().teleport(player, loc, "Spawn");
            }
            case "team/createhint" -> {
                player.sendMessage(ColorUtil.text("&7Takım oluştur: &f/team create <isim>"));
                menus.openTeamMenu(player);
            }
            case "team/invitehint" -> {
                player.sendMessage(ColorUtil.text("&7Invite: &f/team invite <player>"));
                menus.openTeamMenu(player);
            }
            case "team/accept" -> {
                if (plugin.teams().accept(player))
                    player.sendMessage(ColorUtil.text(plugin.messages().get("team-joined")));
                else player.sendMessage(ColorUtil.text(plugin.messages().get("team-no-invite")));
                menus.openTeamMenu(player);
            }
            case "team/info" -> menus.openTeamMenu(player);
            case "team/leave" -> {
                plugin.teams().leave(player);
                player.sendMessage(ColorUtil.text(plugin.messages().get("team-left")));
                menus.openTeamMenu(player);
            }
            case "team/disband" -> {
                var tm = plugin.teams().get(player);
                if (tm != null && tm.leader().equals(player.getUniqueId())) {
                    plugin.teams().disband(tm);
                    player.sendMessage(ColorUtil.text(plugin.messages().get("team-disbanded")));
                }
                menus.openTeamMenu(player);
            }

            case "close" -> menus.openMain(player);
            case "profiles" -> menus.openProfiles(player);
            case "friends" -> menus.openFriends(player);
            case "friends/cyclefilter" -> menus.cycleFriendFilter(player);
            case "friends/followdialog" -> menus.openFollowDialog(player);
            case "friends/search/toggledir" -> {
                menus.toggleFriendsSearchDir(player);
                menus.openFollowDialog(player);
            }
            case "friends/search/go" -> friendsSearchGo(player, view);
            case "friends/follow" -> followFromInput(player, view);
            case "settings" -> menus.openAyarlar(player);
            case "settings/tpa" -> menus.openTpaGizlilikAyarlar(player);
            case "stats" -> plugin.menus().openStatsDialog(player);
            case "stats/add" -> plugin.menus().openStatsAdd(player);
            case "tpa/menu" -> menus.openTpaMenu(player);
            case "tpa/toggledir" -> menus.toggleTpaDir(player);
            case "tpa/input" -> menus.openTpaInput(player, menus.isTpaHere(player));
            case "tpa/to" -> menus.openTpaInput(player, false);
            case "tpa/here" -> menus.openTpaInput(player, true);
            case "tpa/sendto" -> sendTpa(player, view, false);
            case "tpa/sendhere" -> sendTpa(player, view, true);
            case "lb/menu" -> plugin.menus().openLeaderboardMenu(player);
            case "lb/kills" -> plugin.menus().openLeaderboard(player, StatsStore.Type.KILLS);
            case "lb/deaths" -> plugin.menus().openLeaderboard(player, StatsStore.Type.DEATHS);
            case "lb/blocks" -> plugin.menus().openLeaderboard(player, StatsStore.Type.BLOCKS);
            case "lb/mobs" -> plugin.menus().openLeaderboard(player, StatsStore.Type.MOBS);
            case "lb/playtime" -> plugin.menus().openLeaderboard(player, StatsStore.Type.PLAYTIME);
            case "worth" -> menus.openWorth(player);
            case "home/locked" -> {
                player.sendMessage(ColorUtil.text(plugin.messages().get("home-locked")));
                plugin.sounds().play(player, "error");
                menus.openHomes(player, 0);
            }
            case "home/save" -> {
                String nm = view != null ? view.getText("name") : "home";
                int limit = plugin.homes().homeLimit(player);
                int free = -1;
                for (int s = 1; s <= limit; s++) {
                    if (!plugin.homes().hasSlot(player.getUniqueId(), s)) { free = s; break; }
                }
                if (free < 0) {
                    player.sendMessage(ColorUtil.text("&cBoş ev yuvası yok."));
                } else {
                    setSlotHome(player, free, nm);
                }
            }
            case "home/setdialog" -> menus.openSetNameDialog(player);
            default -> handle(player, action, view);
        }
    }

    private void handle(Player player, String action, DialogResponseView view) {
        DialogMenus menus = plugin.menus();

        
        if (action.startsWith("teleport/warp/")) {
            String name = action.substring("teleport/warp/".length());
            var loc = plugin.warps().warp(name);
            if (loc == null) player.sendMessage(ColorUtil.text("&cBu warp bulunamadı."));
            else plugin.teleports().teleport(player, loc, name);
            return;
        }
        if (action.startsWith("homes/")) {
            menus.openHomes(player, Integer.parseInt(action.substring(6)));
            return;
        }
        if (action.startsWith("home/setslot/")) {
            int slot = Integer.parseInt(action.substring("home/setslot/".length()));
            setSlotHome(player, slot, null);
            return;
        }
        if (action.startsWith("home/manage/")) {
            menus.openHomeManage(player, Integer.parseInt(action.substring("home/manage/".length())));
            return;
        }
        if (action.startsWith("home/tp/")) {
            tpSlot(player, Integer.parseInt(action.substring("home/tp/".length())));
            return;
        }
        if (action.startsWith("home/delete/")) {
            int slot = Integer.parseInt(action.substring("home/delete/".length()));
            HomeStore.Home h = plugin.homes().getSlot(player.getUniqueId(), slot);
            String nm = h != null ? h.name() : ("#" + slot);
            plugin.homes().deleteSlot(player.getUniqueId(), slot);
            player.sendMessage(ColorUtil.text(plugin.messages().get("home-deleted").replace("{name}", nm)));
            plugin.menus().openHomes(player, 0);
            return;
        }
        if (action.startsWith("home/overwrite/")) {
            int slot = Integer.parseInt(action.substring("home/overwrite/".length()));
            setSlotHome(player, slot, null);
            return;
        }
        if (action.startsWith("home/renamedialog/")) {
            menus.openRenameDialog(player, Integer.parseInt(action.substring("home/renamedialog/".length())));
            return;
        }
        if (action.startsWith("home/rename/")) {
            int slot = Integer.parseInt(action.substring("home/rename/".length()));
            String neu = view != null ? view.getText("name") : null;
            if (neu == null || !neu.toLowerCase(Locale.ROOT).matches("[a-z0-9_-]{1,16}")) {
                player.sendMessage(ColorUtil.text(plugin.messages().get("invalid-name")));
                return;
            }
            if (plugin.homes().renameSlot(player.getUniqueId(), slot, neu)) {
                player.sendMessage(ColorUtil.text(ColorUtil.replace(
                        plugin.getConfig().getString("messages.home-renamed"), "name", neu.toLowerCase())));
            }
            plugin.menus().openHomes(player, 0);
            return;
        }
        if (action.equals("itemsearch/menu") || action.equals("itemsearch/home")) {
            String ctx = action.endsWith("home") ? "home" : "menu";
            menus.openItemSearchInput(player, ctx);
            return;
        }
        if (action.startsWith("itemsearch/input/")) {
            menus.openItemSearchInput(player, action.substring("itemsearch/input/".length()));
            return;
        }
        if (action.startsWith("itemsearch/do/")) {
            String ctx = action.substring("itemsearch/do/".length());
            String q = view != null ? view.getText("query") : "";
            menus.openItemSearch(player, q, ctx);
            return;
        }
        if (action.startsWith("itemsearch/pick/")) {
            // itemsearch/pick/<ctx>/<MATERIAL>
            String rest = action.substring("itemsearch/pick/".length());
            int slash = rest.indexOf('/');
            if (slash > 0) {
                String ctx = rest.substring(0, slash);
                String mat = rest.substring(slash + 1);
                if (ctx.equals("home")) {
                    plugin.getConfig().set("home-icon", mat);
                } else {
                    plugin.getConfig().set("menu-icon", mat);
                }
                plugin.saveConfig();
                player.sendMessage(ColorUtil.text("&aIcon set to &f" + mat));
                if (ctx.equals("home")) plugin.menus().openHomes(player, 0);
                else menus.openMain(player);
            }
            return;
        }
        if (action.equals("ah")) { menus.openAuction(player); return; }
        if (action.equals("ah/list")) { menus.openAuctionListDialog(player); return; }
        if (action.equals("ah/list-confirm")) {
            try {
                String raw = view != null ? view.getText("price") : "";
                double price = Double.parseDouble(raw.replace(',', '.'));
                if (!plugin.auction().create(player, price)) player.sendMessage(ColorUtil.text("&cİlan oluşturulamadı. Elinde eşya olduğundan ve limitini aşmadığından emin ol."));
                else player.sendMessage(ColorUtil.text("&aİlan oluşturuldu."));
            } catch (Exception e) { player.sendMessage(ColorUtil.text("&cGeçerli bir fiyat gir.")); }
            menus.openAuction(player);
            return;
        }
        if (action.equals("ah/mine")) { menus.openAuctionMine(player); return; }

        // ====== SIPARİŞLER ======
        if (action.equals("order/create-confirm")) {
            String matRaw = view != null ? view.getText("material") : "";
            String amountRaw = view != null ? view.getText("amount") : "";
            String priceRaw = view != null ? view.getText("price") : "";
            String err;
            try {
                org.bukkit.Material mat = org.bukkit.Material.matchMaterial(matRaw == null ? "" : matRaw.trim().toUpperCase(Locale.ROOT));
                if (mat == null || !mat.isItem()) { player.sendMessage(ColorUtil.text(plugin.messages().get("order-invalid-material"))); menus.openOrderCreate(player); return; }
                int amount = Integer.parseInt(amountRaw == null ? "" : amountRaw.trim());
                double price = Double.parseDouble((priceRaw == null ? "" : priceRaw.trim()).replace(',', '.'));
                err = plugin.orders().create(player, mat, amount, price);
            } catch (NumberFormatException ex) {
                err = plugin.messages().get("order-invalid-amount");
            }
            if (err != null) player.sendMessage(ColorUtil.text(err));
            menus.openOrders(player);
            return;
        }
        if (action.startsWith("order/fill/")) {
            try {
                int id = Integer.parseInt(action.substring("order/fill/".length()));
                String err = plugin.orders().fill(player, id);
                if (err != null) player.sendMessage(ColorUtil.text(err));
            } catch (NumberFormatException ignored) {}
            menus.openOrders(player);
            return;
        }
        if (action.startsWith("order/cancel/")) {
            try {
                int id = Integer.parseInt(action.substring("order/cancel/".length()));
                String err = plugin.orders().cancel(player, id);
                if (err != null) player.sendMessage(ColorUtil.text(err));
            } catch (NumberFormatException ignored) {}
            menus.openOrderMine(player);
            return;
        }

        // ====== KAFA ÖDÜLLERİ ======
        if (action.equals("bounty/place-confirm")) {
            String nameRaw = view != null ? view.getText("name") : "";
            String priceRaw = view != null ? view.getText("price") : "";
            Player target = Bukkit.getPlayerExact(nameRaw == null ? "" : nameRaw.trim());
            if (target == null) {
                player.sendMessage(ColorUtil.text(plugin.messages().get("player-offline")));
                menus.openBountyPlace(player);
                return;
            }
            try {
                double price = Double.parseDouble((priceRaw == null ? "" : priceRaw.trim()).replace(',', '.'));
                String err = plugin.bounty().place(player, target, price);
                if (err != null) player.sendMessage(ColorUtil.text(err));
            } catch (NumberFormatException ex) {
                player.sendMessage(ColorUtil.text(plugin.messages().get("pay-money-invalid")));
            }
            menus.openBounties(player);
            return;
        }

        if (action.startsWith("ah/view/")) { try { menus.openAuctionView(player, Long.parseLong(action.substring(8))); } catch (Exception ignored) {} return; }
        if (action.startsWith("ah/buy/")) {
            try { long id = Long.parseLong(action.substring(7)); if (plugin.auction().buy(player, id)) player.sendMessage(ColorUtil.text("&aSatın alma tamamlandı.")); else player.sendMessage(ColorUtil.text("&cSatın alma başarısız. Bakiye veya envanterini kontrol et.")); } catch (Exception ignored) {}
            menus.openAuction(player); return;
        }
        if (action.startsWith("ah/cancel/")) {
            try { long id = Long.parseLong(action.substring(10)); if (plugin.auction().cancel(player, id)) player.sendMessage(ColorUtil.text("&aİlan iptal edildi ve eşya geri verildi.")); else player.sendMessage(ColorUtil.text("&cİlan iptal edilemedi.")); } catch (Exception ignored) {}
            menus.openAuction(player); return;
        }

        if (action.equals("pay/target")) {
            String raw = view == null ? null : view.getText("name");
            if (raw == null || raw.isBlank()) {
                player.sendMessage(ColorUtil.text("&cOyuncu adı girmelisin."));
                menus.openPayTargetDialog(player);
                return;
            }
            Player target = Bukkit.getPlayerExact(raw.trim());
            if (target == null) {
                player.sendMessage(ColorUtil.text(plugin.messages().get("player-offline")));
                menus.openPayTargetDialog(player);
                return;
            }
            menus.openPayDialog(player, target);
            return;
        }
        if (action.startsWith("pay/confirm/")) {
            String[] p = action.split("/", 4);
            if (p.length >= 4) {
                try {
                    UUID id = UUID.fromString(p[2]);
                    double amount = Double.parseDouble(p[3]);
                    sendPaymentDirect(player, Bukkit.getPlayer(id), amount);
                } catch (Exception e) {
                    player.sendMessage(ColorUtil.text(plugin.messages().get("eco-invalid-amount")));
                }
            }
            return;
        }
        if (action.startsWith("pay/send/")) {
            String[] p = action.split("/");
            if (p.length >= 4) {
                try {
                    UUID id = UUID.fromString(p[2]);
                    Player target = Bukkit.getPlayer(id);
                    double amount = me.snownw.core.util.MoneyFormat.parse(p[3]);
                    sendPayment(player, target, amount);
                } catch (Exception e) {
                    player.sendMessage(ColorUtil.text(plugin.messages().get("eco-invalid-amount")));
                }
            }
            return;
        }
        if (action.startsWith("pay/custom/")) {
            try { menus.openPayCustomDialog(player, UUID.fromString(action.substring("pay/custom/".length()))); }
            catch (Exception ignored) {}
            return;
        }
        if (action.startsWith("pay/custom-send/")) {
            try {
                UUID id = UUID.fromString(action.substring("pay/custom-send/".length()));
                String raw = view == null ? null : view.getText("amount");
                double amount = me.snownw.core.util.MoneyFormat.parse(raw == null ? "" : raw.trim());
                sendPayment(player, Bukkit.getPlayer(id), amount);
            } catch (Exception e) {
                player.sendMessage(ColorUtil.text(plugin.messages().get("eco-invalid-amount")));
            }
            return;
        }
        if (action.startsWith("pay/back/")) {
            try {
                Player target = Bukkit.getPlayer(UUID.fromString(action.substring("pay/back/".length())));
                if (target != null) menus.openPayDialog(player, target);
            } catch (Exception ignored) {}
            return;
        }
        if (action.equals("settings/scoreboard-style")) {
            SettingsStore.ScoreboardMode mode = plugin.settings().toggleScoreboardMode(player.getUniqueId());
            player.sendMessage(ColorUtil.text("&7Skor tablosu biçimi: " + (mode == SettingsStore.ScoreboardMode.MODERN ? "&aModern" : "&dKlasik")));
            plugin.scoreboard().forceUpdate(player);
            menus.openAyarlarCategory(player, "scoreboard");
            return;
        }

        if (action.startsWith("settings/page/")) {
            try {
                int page = Integer.parseInt(action.substring("settings/page/".length()));
                menus.openAyarlarPage(player, page);
            } catch (Exception ignored) {}
            return;
        }
        if (action.startsWith("settings/audience/")) {
            String[] parts = action.split("/");
            if (parts.length >= 4) {
                try {
                    SettingsStore.Toggle toggle = SettingsStore.Toggle.valueOf(parts[2].toUpperCase(Locale.ROOT));
                    SettingsStore.Audience audience = SettingsStore.Audience.valueOf(parts[3].toUpperCase(Locale.ROOT));
                    plugin.settings().setAudience(player.getUniqueId(), toggle, audience);
                    menus.openAudienceSettings(player, toggle);
                } catch (Exception ignored) {}
            } else if (parts.length >= 3) {
                try {
                    SettingsStore.Toggle toggle = SettingsStore.Toggle.valueOf(parts[2].toUpperCase(Locale.ROOT));
                    menus.openAudienceSettings(player, toggle);
                } catch (Exception ignored) {}
            }
            return;
        }
        if (action.startsWith("settings/tpa-policy/")) {
            try {
                SettingsStore.TpaPolicy policy = SettingsStore.TpaPolicy.valueOf(action.substring("settings/tpa-policy/".length()).toUpperCase(Locale.ROOT));
                plugin.settings().setTpaPolicy(player.getUniqueId(), policy);
                player.sendMessage(ColorUtil.text("&aTPA gizlilik ayarı: &f" + policy.name()));
                menus.openTpaGizlilikAyarlar(player);
            } catch (Exception ignored) {}
            return;
        }
        if (action.startsWith("settings/cat/")) {
            menus.openAyarlarCategory(player, action.substring("settings/cat/".length()));
            return;
        }
        if (action.startsWith("settings/toggle/")) {
            try {
                SettingsStore.Toggle t = SettingsStore.Toggle.valueOf(action.substring("settings/toggle/".length()).toUpperCase(Locale.ROOT));
                boolean on = plugin.settings().toggle(player.getUniqueId(), t);
                player.sendMessage(ColorUtil.text("&7" + t.label() + ": " + (on ? "&aON" : "&cOFF")));
                if (t == SettingsStore.Toggle.HIDE_MOBS) {
                    plugin.mobHide().apply(player);
                }
                if (t == SettingsStore.Toggle.WORTH_LORE) {
                    new me.snownw.core.listener.WorthLoreListener(plugin).refresh(player);
                }
                // force scoreboard rebuild for SHOW_* / SCOREBOARD toggles
                if (t == SettingsStore.Toggle.NAMETAG_INFO) {
                    if (plugin.settings().get(player.getUniqueId(), SettingsStore.Toggle.SCOREBOARD)) plugin.scoreboard().forceUpdate(player);
                    else plugin.scoreboard().clear(player);
                }
                if (t.category().equalsIgnoreCase("Skor tablosu") || t == SettingsStore.Toggle.SCOREBOARD) {
                    if (plugin.scoreboard() != null) {
                        if (t == SettingsStore.Toggle.SCOREBOARD && !plugin.settings().get(player.getUniqueId(), t)) {
                            plugin.scoreboard().clear(player);
                        } else {
                            plugin.scoreboard().forceUpdate(player);
                        }
                    }
                }
                String cat = switch (t.category().toLowerCase(Locale.ROOT)) {
                    case "skor tablosu" -> "scoreboard";
                    case "ekonomi" -> "economy";
                    case "bildirimler" -> "notifications";
                    case "görünüm" -> "visuals";
                    case "gizlilik" -> "privacy";
                    case "pvp" -> "pvp";
                    case "sohbet" -> "chat";
                    default -> "general";
                };
                plugin.menus().openAyarlarCategory(player, cat);
            } catch (Exception ignored) {}
            return;
        }
        if (action.startsWith("friends/dir/")) {
            try {
                UUID id = UUID.fromString(action.substring("friends/dir/".length()));
                menus.toggleFriendsSearchDir(player);
                menus.openFriendView(player, id);
            } catch (Exception ignored) {}
            return;
        }
        if (action.startsWith("friends/view/")) {
            try {
                menus.openFriendView(player, UUID.fromString(action.substring("friends/view/".length())));
            } catch (Exception ignored) {}
            return;
        }
        if (action.startsWith("friends/unfollow/")) {
            try {
                UUID id = UUID.fromString(action.substring("friends/unfollow/".length()));
                plugin.friends().unfollow(player.getUniqueId(), id);
                org.bukkit.entity.Player unf = org.bukkit.Bukkit.getPlayer(id);
                if (unf != null && plugin.settings().get(unf.getUniqueId(), SettingsStore.Toggle.FRIEND_ALERTS)) {
                    unf.sendMessage(ColorUtil.text(plugin.messages().get("friends-unfollowed-you")
                            .replace("{player}", player.getName())));
                }
                player.sendMessage(ColorUtil.text(plugin.messages().get("friends-unfollowed").replace("{name}", "")));
                menus.openFriends(player);
            } catch (Exception ignored) {}
            return;
        }
        if (action.startsWith("friends/dofollow/")) {
            try {
                UUID id = UUID.fromString(action.substring("friends/dofollow/".length()));
                if (id.equals(player.getUniqueId())) {
                    player.sendMessage(ColorUtil.text(plugin.messages().get("friends-self")));
                    return;
                }
                plugin.friends().follow(player.getUniqueId(), id);
                org.bukkit.entity.Player fol = org.bukkit.Bukkit.getPlayer(id);
                if (fol != null && plugin.settings().get(fol.getUniqueId(), SettingsStore.Toggle.FRIEND_ALERTS)) {
                    fol.sendMessage(ColorUtil.text(plugin.messages().get("friends-followed-you")
                            .replace("{player}", player.getName())));
                }
                player.sendMessage(ColorUtil.text("&aTakip ediliyor."));
                menus.openFriends(player);
            } catch (Exception ignored) {}
            return;
        }
        if (action.startsWith("friends/pay/")) {
            try {
                UUID id = UUID.fromString(action.substring("friends/pay/".length()));
                org.bukkit.OfflinePlayer off = Bukkit.getOfflinePlayer(id);
                String nm = off.getName() != null ? off.getName() : "?";
                Player target = Bukkit.getPlayer(id);
                if (target == null) {
                    player.sendMessage(ColorUtil.text(plugin.messages().get("player-offline")));
                    menus.openFriendView(player, id);
                    return;
                }
                menus.openPayDialog(player, target);
            } catch (Exception ignored) {}
            return;
        }
        if (action.startsWith("friends/settings/")) {
            try {
                UUID id = UUID.fromString(action.substring("friends/settings/".length()));
                menus.openFriendAyarlar(player, id);
            } catch (Exception ignored) {}
            return;
        }
        if (action.startsWith("friends/tpa/")) {
            try {
                UUID id = UUID.fromString(action.substring("friends/tpa/".length()));
                Player target = Bukkit.getPlayer(id);
                if (target == null) {
                    player.sendMessage(ColorUtil.text(plugin.messages().get("player-offline")));
                    plugin.menus().openFriendView(player, id);
                    return;
                }
                boolean here = plugin.menus().isTpaHere(player);
                plugin.tpa().send(player, target, here);
                plugin.menus().openFriendView(player, id);
            } catch (Exception ignored) {}
            return;
        }
        if (action.startsWith("friends/stats/")) {
            try {
                UUID id = UUID.fromString(action.substring("friends/stats/".length()));
                OfflineStats(player, id);
            } catch (Exception ignored) {}
            return;
        }
        
        if (action.startsWith("stats/view/")) {
            try {
                UUID id = UUID.fromString(action.substring("stats/view/".length()));
                plugin.menus().openStatsView(player, id);
            } catch (Exception ignored) {}
            return;
        }
        if (action.equals("stats/lookup")) {
            String name = view != null ? view.getText("name") : null;
            if (name == null || name.isBlank()) {
                player.sendMessage(ColorUtil.text(plugin.messages().get("invalid-name")));
                plugin.menus().openStatsAdd(player);
                return;
            }
            org.bukkit.OfflinePlayer off = org.bukkit.Bukkit.getOfflinePlayer(name);
            boolean known = off.isOnline() || off.hasPlayedBefore()
                    || (off.getUniqueId() != null && plugin.stats().hasData(off.getUniqueId()));
            if (!known) {
                player.sendMessage(ColorUtil.text(plugin.messages().get("stats-not-found").replace("{player}", name)));
                plugin.menus().openStatsAdd(player);
                return;
            }
            player.sendMessage(ColorUtil.text(plugin.messages().get("stats-showing").replace("{player}",
                    off.getName() != null ? off.getName() : name)));
            plugin.menus().openStatsView(player, off.getUniqueId());
            return;
        }
        if (action.equals("lb/money") || action.equals("lb/earned")) {
            plugin.menus().openParaLeaderboard(player);
            return;
        }
        if (action.equals("lb/placed")) {
            plugin.menus().openLeaderboard(player, StatsStore.Type.PLACED);
            return;
        }
        if (action.equals("lb/shards")) {
            plugin.menus().openShardLeaderboard(player);
            return;
        }

        
        if (action.equals("profile/search")) {
            plugin.menus().openProfileSearch(player);
            return;
        }
        if (action.equals("profile/lookup")) {
            String name = view != null ? view.getText("name") : null;
            if (name == null || name.isBlank()) {
                player.sendMessage(ColorUtil.text(plugin.messages().get("enter-player-name")));
                plugin.menus().openProfileSearch(player);
                return;
            }
            org.bukkit.OfflinePlayer off = org.bukkit.Bukkit.getOfflinePlayer(name);
            if (!off.hasPlayedBefore() && !off.isOnline()) {
                player.sendMessage(ColorUtil.text(plugin.messages().get("profile-not-found").replace("{player}", name)));
                plugin.menus().openProfiles(player);
                return;
            }
            plugin.menus().openProfile(player, off.getUniqueId());
            return;
        }
        if (action.startsWith("profile/homes/")) {
            try {
                java.util.UUID id = java.util.UUID.fromString(action.substring("profile/homes/".length()));
                plugin.menus().openPlayerHomes(player, id);
            } catch (Exception ignored) {}
            return;
        }
        if (action.startsWith("profile/inv/")) {
            try {
                java.util.UUID id = java.util.UUID.fromString(action.substring("profile/inv/".length()));
                org.bukkit.OfflinePlayer off = org.bukkit.Bukkit.getOfflinePlayer(id);
                if (off.isOnline() && off.getPlayer() != null) {
                    player.openInventory(off.getPlayer().getInventory());
                } else {
                    player.sendMessage(ColorUtil.text(plugin.messages().get("profile-offline-inv")));
                    plugin.menus().openProfile(player, id);
                }
            } catch (Exception ignored) {}
            return;
        }
        if (action.startsWith("profile/ec/")) {
            try {
                java.util.UUID id = java.util.UUID.fromString(action.substring("profile/ec/".length()));
                org.bukkit.OfflinePlayer off = org.bukkit.Bukkit.getOfflinePlayer(id);
                if (off.isOnline() && off.getPlayer() != null) {
                    plugin.enderChest().openFor(player, id);
                } else {
                    player.sendMessage(ColorUtil.text(plugin.messages().get("profile-offline-ec")));
                    plugin.menus().openProfile(player, id);
                }
            } catch (Exception ignored) {}
            return;
        }
        if (action.startsWith("profile/wipe/")) {
            try {
                java.util.UUID id = java.util.UUID.fromString(action.substring("profile/wipe/".length()));
                // full wipe: homes, stats, balance, extra ender
                for (var h : new java.util.ArrayList<>(plugin.homes().list(id))) {
                    plugin.homes().deleteSlot(id, h.slot());
                }
                plugin.stats().wipe(id);
                org.bukkit.OfflinePlayer off = org.bukkit.Bukkit.getOfflinePlayer(id);
                if (plugin.economy() != null) plugin.economy().set(off, 0);
                plugin.enderChest().wipe(id);
                if (off.isOnline() && off.getPlayer() != null) {
                    off.getPlayer().getEnderChest().clear();
                    off.getPlayer().getInventory().clear();
                }
                player.sendMessage(ColorUtil.text(plugin.messages().get("profile-wiped")));
                plugin.menus().openProfile(player, id);
            } catch (Exception ignored) {}
            return;
        }

        if (action.startsWith("profile/")) {
            String rest = action.substring("profile/".length());
            if (rest.contains("/")) return;
            try {
                plugin.menus().openProfile(player, UUID.fromString(rest));
            } catch (Exception ignored) {}
            return;
        }
        if (action.startsWith("admin/tp/")) {
            adminTp(player, action.substring("admin/tp/".length()));
            return;
        }
        if (action.startsWith("admin/del/")) {
            adminDel(player, action.substring("admin/del/".length()));
        }
    }


    private void friendsSearchGo(Player player, DialogResponseView view) {
        String name = view != null ? view.getText("name") : null;
        if (name == null || name.isBlank()) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("enter-player-name")));
            plugin.menus().openFollowDialog(player);
            return;
        }
        Player online = Bukkit.getPlayerExact(name);
        org.bukkit.OfflinePlayer off = Bukkit.getOfflinePlayer(name);
        if (online == null && !off.hasPlayedBefore()) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("player-offline")));
            plugin.menus().openFollowDialog(player);
            return;
        }
        UUID id = online != null ? online.getUniqueId() : off.getUniqueId();
        plugin.menus().openFriendView(player, id);
    }
    private void OfflineStats(Player viewer, UUID id) {
        String name = Bukkit.getOfflinePlayer(id).getName();
        if (name == null) name = "?";
        long kills = plugin.stats().get(id, StatsStore.Type.KILLS);
        long deaths = plugin.stats().get(id, StatsStore.Type.DEATHS);
        viewer.sendMessage(ColorUtil.text("&6" + name + " &7Kills: &f" + kills + " &7Ölümler: &f" + deaths));
    }

    private void followFromInput(Player player, DialogResponseView view) {
        String name = view != null ? view.getText("name") : null;
        if (name == null || name.isBlank()) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("enter-player-name")));
            plugin.menus().openFollowDialog(player);
            return;
        }
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("player-offline")));
            plugin.menus().openFriends(player);
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("friends-self")));
            plugin.menus().openFriends(player);
            return;
        }
        plugin.friends().follow(player.getUniqueId(), target.getUniqueId());
        if (plugin.settings().get(target.getUniqueId(), SettingsStore.Toggle.FRIEND_ALERTS)) {
            target.sendMessage(ColorUtil.text(plugin.messages().get("friends-followed-you")
                    .replace("{player}", player.getName())));
        }
        player.sendMessage(ColorUtil.text("&aTakip ediliyor: &f" + target.getName()));
        plugin.menus().openFriends(player);
    }

    /** afterAction NONE olan menuler yeni dialog gelmezse ekranda asili kalir; bu yuzden kapatilir. */
    private void closeWaitedDialog(Player player) {
        try { player.closeDialog(); } catch (Throwable ignored) {}
    }

    private void sendTpa(Player player, DialogResponseView view, boolean here) {
        String name = view != null ? view.getText("name") : null;
        if (name == null || name.isBlank()) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("enter-player-name")));
            plugin.menus().openTpaInput(player, here);
            return;
        }
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("player-offline")));
            plugin.menus().openTpaInput(player, here);
            return;
        }
        plugin.tpa().send(player, target, here);
        // reopen main tpa menu after send so client leaves WAIT state
        plugin.menus().openTpaMenu(player);
    }

    private String getInput(io.papermc.paper.dialog.DialogResponseView view, String key) {
        try {
            return view.getText(key);
        } catch (Throwable t) {
            try {
                Object o = view.getClass().getMethod("getText", String.class).invoke(view, key);
                return o == null ? null : o.toString();
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    private void setSlotHome(Player player, int slot, String displayName) {
        int limit = plugin.homes().homeLimit(player);
        if (slot < 1 || slot > limit) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("home-locked").replace("{n}", String.valueOf(slot))));
            return;
        }
        if (!plugin.homes().setSlot(player.getUniqueId(), slot, player.getLocation(), displayName, limit)) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("home-limit").replace("{n}", String.valueOf(slot))));
            return;
        }
        player.sendMessage(ColorUtil.text(plugin.messages().get("home-set").replace("{slot}", String.valueOf(slot))));
        plugin.menus().openHomes(player, 0);
    }


    private void tpSlot(Player player, int slot) {
        HomeStore.Home home = plugin.homes().getSlot(player.getUniqueId(), slot);
        if (home == null) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("home-missing")));
            return;
        }
        plugin.teleports().teleport(player, home.location(), "#" + slot + " " + home.name());
    }

    private void adminTp(Player admin, String rest) {
        if (!admin.hasPermission("snownwcore.admin")) return;
        int s = rest.indexOf('/');
        if (s < 0) return;
        try {
            UUID id = UUID.fromString(rest.substring(0, s));
            int slot = Integer.parseInt(rest.substring(s + 1));
            HomeStore.Home home = plugin.homes().getSlot(id, slot);
            if (home == null) return;
            // ProfileManager: no delay for staff
            plugin.teleports().teleportNow(admin, home.location(), "#" + slot);
        } catch (Exception ignored) {}
    }

    private void adminDel(Player admin, String rest) {
        if (!admin.hasPermission("snownwcore.admin")) return;
        int s = rest.indexOf('/');
        if (s < 0) return;
        try {
            UUID id = UUID.fromString(rest.substring(0, s));
            int slot = Integer.parseInt(rest.substring(s + 1));
            if (plugin.homes().deleteSlot(id, slot)) {
                admin.sendMessage(ColorUtil.text(ColorUtil.replace(
                        plugin.getConfig().getString("messages.home-deleted"), "name", "#" + slot)));
            }
            plugin.menus().openPlayerHomes(admin, id);
        } catch (Exception ignored) {}
    }
    private void sendPayment(Player sender, Player target, double amount) {
        if (target == null) {
            sender.sendMessage(ColorUtil.text(plugin.messages().get("player-offline")));
            return;
        }
        if (plugin.settings().get(sender.getUniqueId(), SettingsStore.Toggle.PAY_CONFIRMATIONS)) {
            plugin.menus().openPayConfirmation(sender, target, amount);
            return;
        }
        sendPaymentDirect(sender, target, amount);
    }

    private void sendPaymentDirect(Player sender, Player target, double amount) {
        if (target == null) {
            sender.sendMessage(ColorUtil.text(plugin.messages().get("player-offline")));
            return;
        }
        if (target.getUniqueId().equals(sender.getUniqueId())) {
            sender.sendMessage(ColorUtil.text(plugin.messages().get("pay-self")));
            return;
        }
        if (amount <= 0 || Double.isNaN(amount) || Double.isInfinite(amount)) {
            sender.sendMessage(ColorUtil.text(plugin.messages().get("eco-invalid-amount")));
            return;
        }
        if (!plugin.settings().get(target.getUniqueId(), SettingsStore.Toggle.ALLOW_PAYMENTS)) {
            sender.sendMessage(ColorUtil.text(plugin.messages().get("pay-disabled-target")));
            return;
        }
        if (!plugin.economy().withdraw(sender, amount)) {
            sender.sendMessage(ColorUtil.text(plugin.messages().get("pay-broke")));
            return;
        }
        if (!plugin.economy().deposit(target, amount)) {
            plugin.economy().deposit(sender, amount);
            sender.sendMessage(ColorUtil.text("&cÖdeme gerçekleştirilemedi; paran iade edildi."));
            return;
        }
        String fmt = plugin.economy().format(amount);
        sender.sendMessage(ColorUtil.text(plugin.messages().get("pay-sent")
                .replace("{player}", target.getName()).replace("{amount}", fmt)));
        if (plugin.settings().get(target.getUniqueId(), SettingsStore.Toggle.PAY_ALERTS)) {
            target.sendMessage(ColorUtil.text(plugin.messages().get("pay-received")
                    .replace("{player}", sender.getName()).replace("{amount}", fmt)));
        }
        plugin.menus().openPayDialog(sender, target);
    }

}
