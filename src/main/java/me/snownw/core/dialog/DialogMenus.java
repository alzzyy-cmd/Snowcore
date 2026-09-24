package me.snownw.core.dialog;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.data.FriendsStore;
import me.snownw.core.data.HomeStore;
import me.snownw.core.data.SettingsStore;
import me.snownw.core.data.StatsStore;
import me.snownw.core.service.AuctionService;
import me.snownw.core.util.ColorUtil;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** All dialog UIs from feature guide – no italic text. */
public final class DialogMenus {

    /** last show time – avoid double show same tick flash */
    private final java.util.Map<java.util.UUID, Boolean> tpaHereMode = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, Long> lastDialogAt = new java.util.concurrent.ConcurrentHashMap<>();


    private final SnowNWCorePlugin plugin;
    private final Map<UUID, FriendsStore.Filter> friendFilter = new HashMap<>();

    public DialogMenus(SnowNWCorePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean supports(Player player) {
        int min = plugin.getConfig().getInt("minimum-protocol", 772);
        try {
            // Paper'in protocol metodunu reflection ile cagir; yoksa engelleme
            java.lang.reflect.Method m = player.getClass().getMethod("getProtocolVersion");
            Object v = m.invoke(player);
            return v instanceof Number num && num.intValue() >= min;
        } catch (Throwable t) {
            return true;
        }
    }

    // ===================== MAIN =====================
    public void openMain(Player player) {
        if (!supports(player)) {
            player.sendMessage(ColorUtil.text(plugin.getConfig().getString("unsupported-message")));
            return;
        }
        // Stable 2-col layout – never swap buttons; Profiller only for staff
        List<ActionButton> buttons = new ArrayList<>();
        if (plugin.cmdEnabled("homes")) buttons.add(btn("\u2302", cat("homes") + "Evler", "Evlerini yönet ve ışınlan", "homes/0"));
        if (plugin.cmdEnabled("worth")) buttons.add(btn("\u2756", cat("progress") + "Fiyatlar", "Eşya satış değerleri", "worth"));
        if (plugin.getConfig().getBoolean("kits.enabled", true) && !plugin.kits().available(player).isEmpty()) buttons.add(btn("\u271a", cat("economy") + "Kitler", "Yetkine sahip olduğun kitleri al", "kits"));
        if (plugin.cmdEnabled("ah") && plugin.getConfig().getBoolean("auction.enabled", true)) buttons.add(btn("\u2691", cat("economy") + "Açık Artırma", "Oyuncu ilanlarını görüntüle", "ah"));
        if (plugin.cmdEnabled("friends")) buttons.add(btn("\u2764", cat("social") + "Arkadaşlar", "Arkadaş ve takip listesi", "friends"));
        if (plugin.cmdEnabled("pay")) buttons.add(btn("\u27a4", cat("economy") + "Ödeme", "Bir oyuncuya para gönder", "pay/open"));
        if (plugin.cmdEnabled("rtp") || plugin.getConfig().getBoolean("spawn.enabled", true) || plugin.cmdEnabled("tpa") || !plugin.warps().names().isEmpty())
            buttons.add(btn("\u2726", cat("teleport") + "Işınlanma", "Spawn, ev, warp, RTP ve TPA", "teleport/menu"));
        if (plugin.cmdEnabled("stats")) buttons.add(btn("\u2605", cat("progress") + "İstatistikler", "Kendi istatistiklerin", "stats"));
        if (plugin.cmdEnabled("leaderboard")) buttons.add(btn("\u265b", cat("progress") + "Sıralamalar", "En iyi oyuncular", "lb/menu"));
        if (plugin.cmdEnabled("settings")) buttons.add(btn("\u2699", cat("neutral") + "Ayarlar", "Oyuncu ayarları", "settings"));
        if (player.hasPermission("snownwcore.admin.profilemanager")
                || player.hasPermission("snownwcore.commands.profilemanager")
                || player.hasPermission("snownwcore.admin")) {
            if (plugin.cmdEnabled("profilemanager")) {
                buttons.add(btn("\u26a0", cat("admin") + "Profiller", "Profil yöneticisi", "profiles"));
            }
        }

        String title = plugin.getConfig().getString("title", "&fSnowNWCore");
        // Item icon (nether star etc.) above buttons – not emoji
        show(player, build(title, List.of(
                item(menuIcon(), title, plugin.getConfig().getString("subtitle", "&7SnowNW kontrol merkezi"))
        ), buttons, cols("main-columns", 2)));
    }


    // ===================== KİTLER =====================
    public void openKits(Player player) {
        if (!plugin.getConfig().getBoolean("kits.enabled", true)) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("disabled")));
            return;
        }
        List<ActionButton> buttons = new ArrayList<>();
        for (var kit : plugin.kits().available(player)) {
            long left = plugin.kits().remaining(player, kit);
            String state = left > 0 ? "&cBekleme: " + left + " sn" : "&aHazır";
            buttons.add(btn(kit.displayName() + " " + state, "Kiti almak için tıkla", "kit/claim/" + kit.id()));
        }
        if (buttons.isEmpty()) {
            buttons.add(btn("&7Kullanılabilir kit yok", "Bu hesabın hiçbir kit yetkisi yok", "main"));
        }
        buttons.add(backBtn("main"));
        show(player, build("&bKitler", List.of(item(Material.CHEST, "&bKitler", "&7Yetkine sahip olduğun kitleri burada alabilirsin.")), buttons, cols("list-columns", 2)));
    }

    // ===================== AUCTION HOUSE =====================
    public void openAuction(Player player) {
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(btn("&aİlan Ver", "Elindeki eşyayı satışa çıkar", "ah/list"));
        buttons.add(btn("&eİlanlarım", "Kendi ilanlarını yönet", "ah/mine"));
        int shown = 0;
        for (AuctionService.Listing l : plugin.auction().all()) {
            if (shown++ >= 18) break;
            buttons.add(btn("&f#" + l.id() + " · " + l.item().getType().name(), "Satıcı: " + l.sellerName() + " · Fiyat: " + plugin.economy().format(l.price()), "ah/view/" + l.id()));
        }
        buttons.add(backBtn("main"));
        show(player, build("&6Açık Artırma", List.of(item(Material.GOLD_INGOT, "&6Açık Artırma", "&7Oyuncuların satış ilanları")), buttons, cols("list-columns", 2)));
    }

    public void openAuctionListDialog(Player player) {
        Dialog dialog = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(ColorUtil.text("&aİlan Ver"))
                        .canCloseWithEscape(true).afterAction(DialogBase.DialogAfterAction.NONE)
                        .body(List.of(item(Material.CHEST, "&aİlan Ver", "&7Ana elindeki eşyayı satışa çıkar.")))
                        .inputs(List.of(DialogInput.text("price", Component.text("Fiyat"))
                                .maxLength(16).width(220).build())).build())
                .type(DialogType.confirmation(
                        btn("&aİlanı Oluştur", "Fiyatı kaydet", "ah/list-confirm"),
                        btn("&7İptal", "Geri", "ah")
                )));
        show(player, dialog);
    }

    public void openAuctionView(Player player, long id) {
        AuctionService.Listing l = plugin.auction().get(id);
        if (l == null) { player.sendMessage(ColorUtil.text("&cBu ilan artık mevcut değil.")); openAuction(player); return; }
        List<ActionButton> buttons = new ArrayList<>();
        if (l.seller().equals(player.getUniqueId())) buttons.add(btn("&cİlanı İptal Et", "Eşyayı geri al", "ah/cancel/" + id));
        else buttons.add(btn("&aSatın Al", "Fiyat: " + plugin.economy().format(l.price()), "ah/buy/" + id));
        buttons.add(backBtn("ah"));
        show(player, build("&6İlan #" + id, List.of(item(l.item().getType(), "&f" + l.item().getType().name(), "&7Satıcı: " + l.sellerName() + "\n&7Fiyat: &a" + plugin.economy().format(l.price()) + "\n&7Miktar: " + l.item().getAmount())), buttons, cols("detail-columns", 1)));
    }

    public void openAuctionMine(Player player) {
        List<ActionButton> buttons = new ArrayList<>();
        for (AuctionService.Listing l : plugin.auction().mine(player.getUniqueId())) buttons.add(btn("&f#" + l.id() + " · " + l.item().getType().name(), "Fiyat: " + plugin.economy().format(l.price()), "ah/view/" + l.id()));
        buttons.add(backBtn("ah"));
        show(player, build("&eİlanlarım", List.of(item(Material.BARREL, "&eİlanlarım", "&7Satışta olan eşyaların")), buttons, cols("list-columns", 2)));
    }


    // ===================== WORTH (DIALOG) =====================
    public void openWorth(Player player) {
        List<Map.Entry<Material, Double>> entries = new ArrayList<>(plugin.worth().allPrices().entrySet());
        entries.removeIf(e -> e.getKey() == null || e.getKey().isAir() || !e.getKey().isItem());
        entries.sort(Map.Entry.<Material, Double>comparingByValue().reversed());
        List<DialogBody> body = new ArrayList<>();
        body.add(item(Material.CAULDRON, "&eEşya Değerleri", "&7Eşyaların satış değerleri"));
        int shown = 0;
        for (Map.Entry<Material, Double> e : entries) {
            if (shown++ >= 30) break;
            body.add(item(e.getKey(), "&f" + prettyMaterial(e.getKey()), "&7Değer: &a" + plugin.economy().format(e.getValue())));
        }
        show(player, build("&eEşya Değerleri", body, List.of(backBtn("main")), cols("list-columns", 3)));
    }
    // ===================== HOMES (screenshot style: 4-col grid, scroll via Show More) =====================
    /** Paged homes entry point used by /homes, /home and the dialog listener. */
    public void openHomes(Player player, int page) { openEvler(player, page); }

    public void openEvler(Player player, int page) {
        if (!supports(player)) { plugin.classic().openEvler(player); return; }
        final int max = plugin.getConfig().getInt("homes.max-homes", plugin.getConfig().getInt("max-homes", 90));
        int perPage = Math.max(8, plugin.getConfig().getInt("homes-per-page", 16));
        final int limit = plugin.homes().homeLimit(player);
        final int showCount = Math.min(max, Math.max(perPage, (page + 1) * perPage));

        List<ActionButton> buttons = new ArrayList<>();
        for (int slot = 1; slot <= showCount; slot++) {
            if (slot > limit) {
                buttons.add(smallBtn("\u26d4", "&cKilitli", "Yuva için izin gerekli (snownwcore.home." + slot + ")", "home/locked"));
            } else {
                HomeStore.Home h = plugin.homes().getSlot(player.getUniqueId(), slot);
                if (h != null) {
                    String label = h.name() != null && !h.name().isBlank() ? h.name() : ("Ev #" + slot);
                    // show custom name from /sethome <name>
                    buttons.add(smallBtn("\u2302", "&a" + label, "Yuva #" + slot + " · Yönet", "home/manage/" + slot));
                } else {
                    // Gray Yeni Ev
                    buttons.add(smallBtn("\u2795", "&7Yeni Ev", "Boş yuvayı burada ayarla " + slot, "home/setslot/" + slot));
                }
            }
        }
        if (showCount < max) {
            buttons.add(smallBtn("\u25bc", "&fDaha Fazla Göster", "Sonraki ev yuvalarını göster", "homes/" + (page + 1)));
        }
        buttons.add(smallBtn("\u00ab", "&8Geri", "Ana menüye dön", "main"));

        final List<ActionButton> fButtons = buttons;
        // 4 columns like screenshot – dialog stays compact, scroll with more buttons
        show(player, Dialog.create(b -> b.empty()
                .base(DialogBase.builder(ColorUtil.text("&fEvler"))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .body(List.of(item(Material.RED_BED, "&fEvler", "&7Evlerini ayarla ve ışınlan")))
                        .build())
                .type(DialogType.multiAction(fButtons).columns(cols("home-columns", 4)).build())));
    }

    public void openHomeManage(Player player, int slot) {
        if (!supports(player)) return;
        HomeStore.Home home = plugin.homes().getSlot(player.getUniqueId(), slot);
        if (home == null) {
            player.sendMessage(ColorUtil.text(plugin.getConfig().getString("messages.home-missing")));
            openEvler(player, 0);
            return;
        }
        String label = "#" + slot + " " + home.name();
        List<ActionButton> buttons = List.of(
                btn("&fIşınlanma", "Ev yuvasına ışınlan #" + slot, "home/tp/" + slot),
                btn("&fYeniden Adlandır", "Yeniden adlandır", "home/renamedialog/" + slot),
                btn("&fÜzerine Yaz", "Buradaki konumu # olarak kaydet" + slot, "home/overwrite/" + slot),
                btn("&cSil", "Yuvayı sil #" + slot, "home/delete/" + slot),
                backBtn("homes/0")
        );
        String hLabel = home.name() != null ? home.name() : ("Ev #" + slot);
        show(player, build("&a" + hLabel, List.of(
                item(Material.RED_BED, "&a" + hLabel, "&7Slot #" + slot + "\n&7Teleport · Yeniden Adlandır · Sil")
        ), buttons, cols("detail-columns", 1)));
    }

    public void openSetNameDialog(Player player) {
        Dialog dialog = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(ColorUtil.text("&eYeni Ev"))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .body(List.of(item(Material.RED_BED, "&eYeni Ev", "&7Bir isim gir")))
                        .inputs(List.of(DialogInput.text("name", Component.text("Ev adı"))
                                .initial("home").maxLength(16).width(220).build()))
                        .build())
                .type(DialogType.confirmation(
                        btn("&aKaydet", "Kaydet", "home/save"),
                        btn("&7İptal", "İptal", "homes/0")
                )));
        show(player, dialog);
    }

    public void openRenameDialog(Player player, int slot) {
        HomeStore.Home home = plugin.homes().getSlot(player.getUniqueId(), slot);
        String oldName = home != null ? home.name() : ("home" + slot);
        Dialog dialog = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(ColorUtil.text("&eYeniden Adlandır"))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .body(List.of(item(Material.NAME_TAG, "&eYeniden Adlandır", "&7" + oldName)))
                        .inputs(List.of(DialogInput.text("name", Component.text("Yeni ad"))
                                .initial(oldName).maxLength(16).width(220).build()))
                        .build())
                .type(DialogType.confirmation(
                        btn("&aYeniden Adlandır", "Kaydet", "home/rename/" + slot),
                        btn("&7İptal", "İptal", "main")
                )));
        show(player, dialog);
    }

    // ===================== FRIENDS =====================
    public void cycleFriendFilter(Player player) {
        FriendsStore.Filter cur = friendFilter.getOrDefault(player.getUniqueId(), FriendsStore.Filter.ALL);
        FriendsStore.Filter[] vals = FriendsStore.Filter.values();
        int next = (cur.ordinal() + 1) % vals.length;
        friendFilter.put(player.getUniqueId(), vals[next]);
        openFriends(player);
    }

    public void openFriends(Player player) {
        if (!supports(player)) { plugin.classic().openFriends(player); return; }
        FriendsStore.Filter filter = friendFilter.getOrDefault(player.getUniqueId(), FriendsStore.Filter.ALL);
        var following = plugin.friends().following(player.getUniqueId());
        int followCount = following.size();
        int friendCount = plugin.friends().friends(player.getUniqueId()).size();

        List<ActionButton> buttons = new ArrayList<>();
        String filterLabel = switch (filter) {
            case ALL -> "Tümü";
            case FRIENDS -> "Arkadaşlar";
            case FOLLOWING -> "Takip ettiklerim";
            case FOLLOWERS -> "Takipçiler";
        };
        buttons.add(btn("&fFilter: &7" + filterLabel, "Değiştirmek için tıkla · Tümü / Arkadaşlar / Takip ettiklerim / Takipçiler", "friends/cyclefilter"));
        buttons.add(btn("&fAra", "Oyuncu ara", "friends/followdialog"));
        buttons.add(btn("&a+ Follow", "Oyuncuyu takip et", "friends/followdialog"));

        for (java.util.UUID id : plugin.friends().filtered(player.getUniqueId(), filter)) {
            String name = org.bukkit.Bukkit.getOfflinePlayer(id).getName();
            if (name == null) name = id.toString().substring(0, 8);
            buttons.add(btn("&f" + name, "Seçenekler", "friends/view/" + id));
        }

        buttons.add(btn("&fGeri", "Ana Menü", "main"));
        String title = "&fArkadaşlar " + friendCount + " arkadaş / " + followCount + " takip";
        show(player, build(title, List.of(
                item(Material.SPYGLASS, "&fArkadaşlar", "&7" + friendCount + " arkadaş / " + followCount + " takip")
        ), buttons, cols("list-columns", 2)));
    }


    public void openFollowDialog(Player player) {
        boolean here = tpaHereMode.getOrDefault(player.getUniqueId(), false);
        String dirLabel = here
                ? "&fYön: &eOyuncuyu yanıma getir"
                : "&fYön: &eOyuncuya ışınlan";
        Dialog dialog = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(ColorUtil.text("&fOyuncu Ara"))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .body(List.of(
                                item(Material.SPYGLASS, "&fOyuncu Ara", "&7Aşağıya bir isim gir"),
                                DialogBody.plainMessage(ColorUtil.text("&7Yönü değiştirip ara"), 280)
                        ))
                        .inputs(List.of(DialogInput.text("name", Component.text("Oyuncu Adı"))
                                .maxLength(16).width(220).build()))
                        .build())
                .type(DialogType.multiAction(List.of(
                        btn(dirLabel, "Yönü değiştirmek için tıkla", "friends/search/toggledir"),
                        btn("&aAra", "Oyuncuyu bul", "friends/search/go"),
                        backBtn("friends")
                )).columns(1).build()));
        show(player, dialog);
    }


    public void openFriendView(Player player, UUID targetId) {
        if (!supports(player)) return;
        String name = org.bukkit.Bukkit.getOfflinePlayer(targetId).getName();
        if (name == null) name = "?";
        boolean following = plugin.friends().isFollowing(player.getUniqueId(), targetId);
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(btn("&fİstatistikleri Gör", "İstatistikler", "friends/stats/" + targetId));
        buttons.add(btn("&fÖdeme Gönder", "Ödeme gönder", "friends/pay/" + targetId));
        boolean here = tpaHereMode.getOrDefault(player.getUniqueId(), false);
        String tpLabel = here ? "&fOyuncuyu Yanına Getir" : "&fOyuncuya Işınlan";
        boolean friend = plugin.friends().friends(player.getUniqueId()).contains(targetId);
        boolean followed = plugin.friends().isFollowing(player.getUniqueId(), targetId);
        String tpaDesc = friend ? "Arkadaşın · TPA gönderebilirsin" : (followed ? "Takip ediyorsun · TPA gönderebilirsin" : "TPA erişimi karşı tarafın ayarlarına bağlı");
        buttons.add(btn(tpLabel, tpaDesc, "friends/tpa/" + targetId));
        buttons.add(btn("&eYönü Değiştir", "Işınlanma yönünü değiştir", "friends/dir/" + targetId));
        buttons.add(btn("&fAyarlar", "Arkadaş ayarları", "friends/settings/" + targetId));
        if (following) {
            buttons.add(btn("&cTakibi Bırak", "Takibi bırak", "friends/unfollow/" + targetId));
        } else {
            buttons.add(btn("&aFollow", "Oyuncuyu takip et", "friends/dofollow/" + targetId));
        }
        buttons.add(backBtn("friends"));
        show(player, build("&f" + name, List.of(
                item(Material.PLAYER_HEAD, "&f" + name, "&7Friend options")
        ), buttons, cols("detail-columns", 1)));
    }

    public void openFriendAyarlar(Player player, UUID targetId) {
        String name = org.bukkit.Bukkit.getOfflinePlayer(targetId).getName();
        if (name == null) name = "?";
        // Personal friend prefs (stored in settings store keys)
        List<ActionButton> buttons = List.of(
                btn("&fAktivite: &aAçık", "Toggle", "friends/set/" + targetId + "/activity"),
                btn("&fİşlemler: &aAçık", "Toggle", "friends/set/" + targetId + "/tx"),
                btn("&fCan Message: &aOn", "Toggle", "friends/set/" + targetId + "/msg"),
                btn("&fCan Gönder TP: &aOn", "Toggle", "friends/set/" + targetId + "/tpa"),
                btn("&fAuto-Accept TPs: &cOff", "Toggle", "friends/set/" + targetId + "/tpauto"),
                btn("&fCan Pay You: &aOn", "Toggle", "friends/set/" + targetId + "/pay"),
                btn("&7Geri", "Geri", "friends/view/" + targetId)
        );
        show(player, build("&f" + name, List.of(
                item(Material.COMPARATOR, "&fFriend Ayarlar", "&7Per-friend options")
        ), buttons, cols("detail-columns", 1)));
    }


    public void openTeamHome(Player player) {
        var team = plugin.teams().get(player);
        if (team == null) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("team-none")));
            openEvler(player, 0);
            return;
        }
        List<ActionButton> buttons = new ArrayList<>();
        if (team.home() != null) {
            buttons.add(btn("&aTeleport", "Takım evine git", "team/home/tp"));
            if (team.leader().equals(player.getUniqueId())) {
                buttons.add(btn("&cSil", "Sil team home", "team/home/del"));
            }
        } else if (team.leader().equals(player.getUniqueId())) {
            buttons.add(btn("&aTakım Evini Ayarla", "Buraya ayarla", "team/home/set"));
        } else {
            buttons.add(btn("&7No team home", "-", "homes/0"));
        }
        buttons.add(backBtn("homes/0"));
        show(player, build("&6Takım Evi", List.of(
                item(Material.RED_BANNER, "&6Takım Evi", "&7" + team.name())
        ), buttons, cols("detail-columns", 1)));
    }


    public void showRtpStarted(Player player) {
        // no-op – dialog already CLOSE; do not open another dialog
    }

    // ===================== TEAM =====================
    public void openTeamMenu(Player player) {
        if (!supports(player)) {
            // classic chat fallback
            var tm = plugin.teams().get(player);
            if (tm == null) player.sendMessage(ColorUtil.text(plugin.messages().get("team-none")));
            else player.sendMessage(ColorUtil.text(plugin.messages().get("team-info")
                    .replace("{team}", tm.name()).replace("{members}", String.valueOf(tm.members().size()))));
            return;
        }
        var tm = plugin.teams().get(player);
        List<ActionButton> buttons = new ArrayList<>();
        if (tm == null) {
            buttons.add(btn("&aTakım Oluştur", "Sohbette /team create <isim> yaz", "team/createhint"));
            buttons.add(btn("&eDaveti Kabul Et", "Bekleyen davete katıl", "team/accept"));
            buttons.add(backBtn("main"));
            show(player, build("&fTakım", List.of(
                    item(Material.SHIELD, "&fTakım", "&7Bir takımda değilsin")
            ), buttons, cols("detail-columns", 1)));
            return;
        }
        boolean leader = tm.leader().equals(player.getUniqueId());
        buttons.add(btn("&fTakım: &a" + tm.name(), "Üye sayısı: " + tm.members().size(), "team/info"));
        buttons.add(btn("&eOyuncuyu Davet Et", "/team invite <player>", "team/invitehint"));
        if (tm.home() != null) {
            buttons.add(btn("&aTakım Evine Işınlan", "Takım evine git", "team/home/tp"));
        } else if (leader) {
            buttons.add(btn("&aTakım Evini Ayarla", "Buraya ayarla", "team/home/set"));
        } else {
            buttons.add(btn("&7Takım Evi Yok", "-", "team/menu"));
        }
        if (leader && tm.home() != null) {
            buttons.add(btn("&cSil Team Home", "Evi kaldır", "team/home/del"));
        }
        if (leader) {
            buttons.add(btn("&cTakımı Dağıt", "Sil team", "team/disband"));
        } else {
            buttons.add(btn("&cTakımdan Ayrıl", "Ayrıl", "team/leave"));
        }
        buttons.add(backBtn("main"));
        show(player, build("&fTakım · " + tm.name(), List.of(
                item(Material.SHIELD, "&f" + tm.name(), "&7Üye sayısı: " + tm.members().size())
        ), buttons, cols("detail-columns", 1)));
    }

    // ===================== TELEPORT CENTER =====================
    public void openTeleportMenu(Player player) {
        if (!supports(player)) { player.sendMessage(ColorUtil.text("&7/spawn, /warp <isim>, /rtp, /tpa")); return; }
        List<ActionButton> buttons = new ArrayList<>();
        if (plugin.getConfig().getBoolean("spawn.enabled", true)) buttons.add(btn("&bSpawn", "Sunucu spawnına ışınlan", "teleport/spawn"));
        if (plugin.cmdEnabled("homes")) buttons.add(btn("&fEvler", "Evlerini yönet ve ışınlan", "homes/0"));
        buttons.add(btn("&aRTP", "Güvenli rastgele ışınlanma", "rtp/menu"));
        if (plugin.cmdEnabled("tpa")) buttons.add(btn("&fTPA", "Oyuncuya ışınlanma isteği", "tpa/menu"));
        if (!plugin.warps().names().isEmpty()) buttons.add(btn("&eWarplar", "Sunucu warpları", "teleport/warps"));
        buttons.add(backBtn("main"));
        show(player, build("&bIşınlanma Merkezi", List.of(item(Material.ENDER_PEARL, "&bIşınlanma", "&7Spawn, ev, warp, RTP ve TPA")), buttons, cols("list-columns", 2)));
    }

    public void openWarpMenu(Player player) {
        List<ActionButton> buttons = new ArrayList<>();
        for (String name : plugin.warps().names()) {
            buttons.add(btn("&e" + name, "Warp'a ışınlan", "teleport/warp/" + name.toLowerCase(java.util.Locale.ROOT)));
        }
        if (buttons.isEmpty()) buttons.add(btn("&7Warp yok", "Henüz warp oluşturulmadı", "teleport/menu"));
        buttons.add(backBtn("teleport/menu"));
        show(player, build("&eWarplar", List.of(item(Material.ENDER_EYE, "&eWarplar", "&7Bir warp seç")), buttons, cols("list-columns", 2)));
    }

    public void openRtpQueueMenu(Player player) {
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(btn("&aDünya", "Dünya RTP sırasına katıl", "rtpqueue/world/world"));
        buttons.add(btn("&cNether", "Nether RTP sırasına katıl", "rtpqueue/world/world_nether"));
        buttons.add(backBtn("teleport/menu"));
        show(player, build("&bRTP Sırası", List.of(
                item(Material.COMPASS, "&bRTP Sırası", "&7RTP sırasına hangi dünyada girmek istiyorsun?")
        ), buttons, cols("list-columns", 2)));
    }

    public void openRtpQueueConfirm(Player player, String worldName) {
        String display = "world_nether".equalsIgnoreCase(worldName) ? "Nether" : "Dünya";
        Dialog dialog = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(ColorUtil.text("&bRTP Sırası"))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .body(List.of(
                                item(Material.COMPASS, "&bRTP Sırası", "&fRTP sırasına &b" + display + " &fdünyasında girmek istediğinizden emin misiniz?"),
                                DialogBody.plainMessage(ColorUtil.text("&7Şu an sırada: &f" + plugin.rtp().queueSize(worldName) + " &7oyuncu"))
                        ))
                        .build())
                .type(DialogType.confirmation(
                        btn("&aEvet", "RTP sırasına katıl", "rtpqueue/confirm/" + worldName),
                        btn("&cHayır", "Vazgeç", "rtpqueue/menu")
                )));
        show(player, dialog);
    }

    public void openRtpMenu(Player player) {
        List<ActionButton> buttons = new ArrayList<>();
        var cfg = plugin.rtp().config();
        var worlds = cfg.getConfigurationSection("WORLD-SETTINGS");
        if (worlds != null) {
            for (String world : worlds.getKeys(false)) {
                if (Bukkit.getWorld(world) == null) continue;
                Material icon = Bukkit.getWorld(world).getEnvironment() == org.bukkit.World.Environment.NETHER ? Material.NETHERRACK
                        : Bukkit.getWorld(world).getEnvironment() == org.bukkit.World.Environment.THE_END ? Material.END_STONE : Material.GRASS_BLOCK;
                String label = switch (Bukkit.getWorld(world).getEnvironment()) {
                    case NETHER -> "&cNether";
                    case THE_END -> "&eEnd";
                    default -> "&aDünya";
                };
                String description = switch (Bukkit.getWorld(world).getEnvironment()) {
                    case NETHER -> "Güvenli Nether bölgesine RTP";
                    case THE_END -> "Güvenli End bölgesine RTP";
                    default -> "Güvenli dünyaya RTP";
                };
                buttons.add(btn(label, description, "rtp/world/" + world));
            }
        }
        if (buttons.isEmpty()) buttons.add(btn("&cRTP dünyası yok", "RTP ayarlarını kontrol et", "close"));
        show(player, build("&aGüvenli RTP", List.of(item(Material.COMPASS, "&aGüvenli RTP", "&7Sıvı, tehlikeli zemin ve Nether çatısı reddedilir")), buttons, cols("list-columns", 2)));
    }

    // ===================== TPA =====================
    public void openTpaMenu(Player player) {
        if (!supports(player)) { player.sendMessage(ColorUtil.text("&7/tpa <player> | /tpahere <player>")); return; }
        List<ActionButton> buttons = List.of(
                btn("&fOyuncuya Işınlan", "Oyuncuya ışınlan", "tpa/to"),
                btn("&fOyuncuyu Yanına Getir", "Oyuncu sana ışınlansın", "tpa/here"),
                backBtn("main")
        );
        show(player, build("&fIşınlanma İsteği", List.of(
                item(Material.ENDER_PEARL, "&fIşınlanma İsteği", "&7Yön seç")
        ), buttons, cols("detail-columns", 1)));
    }

    public void toggleTpaDir(Player player) {
        openTpaMenu(player);
    }

    public boolean isTpaHere(Player player) {
        return tpaHereMode.getOrDefault(player.getUniqueId(), false);
    }

    /** Arkadaşlar search dialog direction (same map as tpaHereMode). */
    public void toggleFriendsSearchDir(Player player) {
        boolean now = !tpaHereMode.getOrDefault(player.getUniqueId(), false);
        tpaHereMode.put(player.getUniqueId(), now);
    }


    public void openTpaInput(Player player, boolean here) {
        tpaHereMode.put(player.getUniqueId(), here);
        String title = here ? "&fOyuncuyu Yanına Getir" : "&fOyuncuya Işınlan";
        String action = here ? "tpa/sendhere" : "tpa/sendto";
        Dialog dialog = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(ColorUtil.text(title))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .body(List.of(item(Material.ENDER_PEARL, title, "&7Oyuncu adını gir")))
                        .inputs(List.of(DialogInput.text("name", Component.text("Oyuncu Adı"))
                                .maxLength(16).width(220).build()))
                        .build())
                .type(DialogType.confirmation(
                        btn("&aİstek Gönder", "Gönder", action),
                        btn("&7İptal", "İptal", "main")
                )));
        show(player, dialog);
    }

    // ===================== PAY =====================
    public void openPayTargetDialog(Player player) {
        Dialog dialog = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(ColorUtil.text("&d&lÖdeme Gönder"))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .body(List.of(
                                item(Material.GOLD_INGOT, "&d&lÖdeme Gönder", "&7Ödeme yapacağın oyuncunun adını gir."),
                                DialogBody.plainMessage(ColorUtil.text("&7Örnek: &fSamir-Spider"), 280)
                        ))
                        .inputs(List.of(DialogInput.text("name", Component.text("Oyuncu Adı"))
                                .maxLength(16).width(220).build()))
                        .build())
                .type(DialogType.confirmation(
                        btn("&aDevam", "Oyuncuyu bul", "pay/target"),
                        btn("&7İptal", "Kapat", "main")
                )));
        show(player, dialog);
    }

    public void openPayDialog(Player player, Player target) {
        if (target == null || !target.isOnline()) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("player-offline")));
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("pay-self")));
            return;
        }
        String id = target.getUniqueId().toString();
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(btn("&a$ 1", "1 para gönder", "pay/send/" + id + "/1"));
        buttons.add(btn("&a$ 2", "2 para gönder", "pay/send/" + id + "/2"));
        buttons.add(btn("&a$ 29", "29 para gönder", "pay/send/" + id + "/29"));
        buttons.add(btn("&a$ 100", "100 para gönder", "pay/send/" + id + "/100"));
        buttons.add(btn("&a$ 150", "150 para gönder", "pay/send/" + id + "/150"));
        buttons.add(btn("&a$ 200", "200 para gönder", "pay/send/" + id + "/200"));
        buttons.add(btn("&a$ 1K", "1000 para gönder", "pay/send/" + id + "/1000"));
        buttons.add(btn("&fÖzel Miktar", "Kendi miktarını gir", "pay/custom/" + id));
        buttons.add(backBtn("friends"));
        show(player, build("&d&lÖde · &f" + target.getName(), List.of(
                item(Material.GOLD_INGOT, "&f" + target.getName(), "&7Bir ödeme miktarı seç."),
                DialogBody.plainMessage(ColorUtil.text("&7Bakiye: &a$ " + plugin.economy().format(plugin.economy().get(player))), 280)
        ), buttons, cols("list-columns", 2)));
    }

    public void openPayConfirmation(Player player, Player target, double amount) {
        if (target == null || !target.isOnline()) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("player-offline")));
            return;
        }
        String id = target.getUniqueId().toString();
        String encoded = Double.toString(amount);
        List<ActionButton> buttons = List.of(
                btn("&aOnayla", "Ödemeyi gönder", "pay/confirm/" + id + "/" + encoded),
                btn("&cİptal", "Miktar seçimine dön", "pay/back/" + id)
        );
        show(player, build("&d&lÖdemeyi Onayla", List.of(
                item(Material.GOLD_INGOT, "&f" + target.getName(), "&7Gönderilecek: &a$ " + plugin.economy().format(amount)),
                DialogBody.plainMessage(ColorUtil.text("&7Bu ödemeyi göndermek istediğine emin misin?"), 300)
        ), buttons, cols("detail-columns", 1)));
    }

    public void openPayCustomDialog(Player player, UUID targetId) {
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("player-offline")));
            return;
        }
        Dialog dialog = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(ColorUtil.text("&d&lÖzel Miktar · &f" + target.getName()))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .body(List.of(DialogBody.plainMessage(ColorUtil.text("&7Miktarı gir. Örn: &f250 &7veya &f1k"), 300)))
                        .inputs(List.of(DialogInput.text("amount", Component.text("Miktar"))
                                .maxLength(24).width(220).build()))
                        .build())
                .type(DialogType.confirmation(
                        btn("&aÖde", "Ödemeyi gönder", "pay/custom-send/" + targetId),
                        btn("&7Geri", "Miktar seçimi", "pay/back/" + targetId)
                )));
        show(player, dialog);
    }

    // ===================== SETTINGS =====================
    public void openAyarlar(Player player) {
        if (!supports(player)) { plugin.classic().openAyarlar(player); return; }
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(btn("&b💬 Sohbet", "Sohbet, özel mesaj ve sosyal görünürlük", "settings/cat/chat"));
        buttons.add(btn("&e🔔 Bildirimler", "Bildirim tercihleri", "settings/cat/notifications"));
        buttons.add(btn("&c⚔ PvP", "PvP efektleri ve savaş görünümü", "settings/cat/pvp"));
        buttons.add(btn("&d👁 Görünüm", "Görsel tercihler", "settings/cat/visuals"));
        buttons.add(btn("&6▣ Skor Tablosu", "Skor tablosu ve oyuncu bilgileri", "settings/cat/scoreboard"));
        buttons.add(btn("&a🔒 Gizlilik", "TPA ve ödeme izinleri", "settings/cat/privacy"));
        buttons.add(btn("&f⚙ Genel", "Genel oyun tercihleri", "settings/cat/general"));
        buttons.add(btn("&9👥 Arkadaşlar", "Arkadaş ve takip sistemi", "friends"));
        buttons.add(btn("&7Sonraki Sayfa →", "Ek ayarlar", "settings/page/2"));
        buttons.add(btn("&7← Ana Menü", "Ana Menü", "main"));
        show(player, build("&b&lAyarlar", List.of(
                item(Material.COMPARATOR, "&b&lAyarlar", "&7SnowNW deneyimini kişiselleştir")
        ), buttons, cols("list-columns", 2)));
    }

    public void openAyarlarPage(Player player, int page) {
        if (page <= 1) { openAyarlar(player); return; }
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(btn("&6💰 Ekonomi", "Ödeme ve ekonomi bildirimleri", "settings/cat/economy"));
        buttons.add(btn("&9👥 Arkadaşlar", "Arkadaş ve takip ayarları", "friends"));
        buttons.add(btn("&d☾ Gece Görüşü", "Gece görüşü tercihi", "settings/cat/visuals"));
        buttons.add(btn("&bTAB", "Oyuncu listesi görünümü", "settings/cat/general"));
        buttons.add(btn("&7← Önceki Sayfa", "Ayarlar", "settings/page/1"));
        buttons.add(btn("&7Ana Menü", "Ana Menü", "main"));
        show(player, build("&b&lAyarlar &7· 2/2", List.of(
                item(Material.BOOK, "&bAyarlar", "&7Diğer SnowNW ayarları")
        ), buttons, cols("list-columns", 2)));
    }

    public void openTpaGizlilikAyarlar(Player player) {
        SettingsStore.TpaPolicy policy = plugin.settings().getTpaPolicy(player.getUniqueId());
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(btn(policy == SettingsStore.TpaPolicy.EVERYONE ? "&a✓ Herkes" : "&fHerkes", "Tüm oyuncular", "settings/tpa-policy/EVERYONE"));
        buttons.add(btn(policy == SettingsStore.TpaPolicy.FRIENDS ? "&a✓ Sadece arkadaşlar" : "&fSadece arkadaşlar", "Karşılıklı arkadaşlar", "settings/tpa-policy/FRIENDS"));
        buttons.add(btn(policy == SettingsStore.TpaPolicy.FOLLOWING ? "&a✓ Takip ettiklerim" : "&fTakip ettiklerim", "Takip ettiğin oyuncular", "settings/tpa-policy/FOLLOWING"));
        buttons.add(btn(policy == SettingsStore.TpaPolicy.FRIENDS_AND_FOLLOWING ? "&a✓ Arkadaş + takip" : "&fArkadaş + takip", "İki grup da TPA gönderebilir", "settings/tpa-policy/FRIENDS_AND_FOLLOWING"));
        buttons.add(btn(policy == SettingsStore.TpaPolicy.NOBODY ? "&a✓ Hiç kimse" : "&fHiç kimse", "TPA isteklerini kapat", "settings/tpa-policy/NOBODY"));
        buttons.add(btn("&7← Geri", "Ayarlar", "settings/cat/privacy"));
        show(player, build("&a&lTPA Gizliliği", List.of(
                item(Material.ENDER_PEARL, "&aTPA Gizliliği", "&7Seçim: &f" + tpaPolicyName(policy))
        ), buttons, cols("detail-columns", 1)));
    }

    private String audienceName(SettingsStore.Audience a) {
        return switch (a) {
            case EVERYONE -> "Herkes";
            case FRIENDS -> "Arkadaşlar";
            case FOLLOWING -> "Takip ettiklerim";
            case FRIENDS_AND_FOLLOWING -> "Arkadaşlar + takip";
            case NOBODY -> "Hiç kimse";
        };
    }

    private String audienceButton(SettingsStore.Toggle toggle, Player player) {
        SettingsStore.Audience a = plugin.settings().getAudience(player.getUniqueId(), toggle);
        return "&f" + toggle.label() + ": " + "&e" + audienceName(a);
    }

    private String tpaPolicyName(SettingsStore.TpaPolicy p) {
        return switch (p) {
            case EVERYONE -> "Herkes";
            case FRIENDS -> "Sadece arkadaşlar";
            case FOLLOWING -> "Takip ettiklerim";
            case FRIENDS_AND_FOLLOWING -> "Arkadaş + takip";
            case NOBODY -> "Hiç kimse";
        };
    }

    public void openAyarlarCategory(Player player, String category) {
        if (!supports(player)) { plugin.classic().openAyarlarCat(player, category); return; }
        String key = category.toLowerCase(java.util.Locale.ROOT);
        List<ActionButton> buttons = new ArrayList<>();
        String title;
        Material icon;
        switch (key) {
            case "chat" -> {
                title = "&b&lSohbet"; icon = Material.PAPER;
                boolean pub = plugin.settings().get(player.getUniqueId(), SettingsStore.Toggle.PUBLIC_CHAT);
                buttons.add(btn("&fGenel sohbet: " + (pub ? "&aAÇIK" : "&cKAPALI"), "Sunucu sohbetini görüntüle/gönder", "settings/toggle/public_chat"));
                buttons.add(btn(audienceButton(SettingsStore.Toggle.PRIVATE_MESSAGES, player), "Özel mesaj alabilenleri seç", "settings/audience/PRIVATE_MESSAGES"));
                buttons.add(btn(audienceButton(SettingsStore.Toggle.DEATH_MESSAGES, player), "Ölüm mesajlarını kimler görsün", "settings/audience/DEATH_MESSAGES"));
                buttons.add(btn(audienceButton(SettingsStore.Toggle.JOIN_LEAVE, player), "Giriş/çıkış mesajlarını kimler görsün", "settings/audience/JOIN_LEAVE"));
                buttons.add(btn("&7← Geri", "Ayarlar", "settings"));
            }
            case "notifications" -> {
                title = "&e&lBildirimler"; icon = Material.BELL;
                addToggle(buttons, player, SettingsStore.Toggle.PAY_ALERTS);
                addToggle(buttons, player, SettingsStore.Toggle.FRIEND_ALERTS);
                addToggle(buttons, player, SettingsStore.Toggle.TPA_ALERTS);
                buttons.add(btn("&7← Geri", "Ayarlar", "settings"));
            }
            case "pvp" -> {
                title = "&c&lPvP"; icon = Material.IRON_SWORD;
                addToggle(buttons, player, SettingsStore.Toggle.FAST_CRYSTAL);
                addToggle(buttons, player, SettingsStore.Toggle.TOTEM_PARTICLES);
                addToggle(buttons, player, SettingsStore.Toggle.EXPLOSION_PARTICLES);
                addToggle(buttons, player, SettingsStore.Toggle.EXPLOSION_SOUNDS);
                buttons.add(btn("&7← Geri", "Ayarlar", "settings"));
            }
            case "visuals" -> {
                title = "&d&lGörünüm"; icon = Material.SPYGLASS;
                addToggle(buttons, player, SettingsStore.Toggle.NIGHT_VISION);
                addToggle(buttons, player, SettingsStore.Toggle.WORTH_LORE);
                addToggle(buttons, player, SettingsStore.Toggle.HIDE_MOBS);
                addToggle(buttons, player, SettingsStore.Toggle.NAMETAG_INFO);
                buttons.add(btn("&7← Geri", "Ayarlar", "settings"));
            }
            case "privacy" -> {
                title = "&a&lGizlilik"; icon = Material.ENDER_EYE;
                buttons.add(btn("&aTPA İzinleri", "Kimler sana TPA gönderebilir", "settings/tpa"));
                addToggle(buttons, player, SettingsStore.Toggle.TPA_REQUESTS);
                addToggle(buttons, player, SettingsStore.Toggle.TPA_HERE);
                addToggle(buttons, player, SettingsStore.Toggle.ALLOW_PAYMENTS);
                buttons.add(btn("&7← Geri", "Ayarlar", "settings"));
            }
            case "scoreboard" -> {
                title = "&6&lSkor Tablosu"; icon = Material.PAINTING;
                addToggle(buttons, player, SettingsStore.Toggle.SCOREBOARD);
                addToggle(buttons, player, SettingsStore.Toggle.SHOW_MONEY);
                addToggle(buttons, player, SettingsStore.Toggle.SHOW_SHARDS);
                addToggle(buttons, player, SettingsStore.Toggle.SHOW_KILLS);
                addToggle(buttons, player, SettingsStore.Toggle.SHOW_DEATHS);
                addToggle(buttons, player, SettingsStore.Toggle.SHOW_PLAYTIME);
                addToggle(buttons, player, SettingsStore.Toggle.SHOW_PING);
                SettingsStore.ScoreboardMode mode = plugin.settings().getScoreboardMode(player.getUniqueId());
                buttons.add(btn("&fSkor Tablosu: " + (mode == SettingsStore.ScoreboardMode.MODERN ? "&aModern" : "&dKlasik"),
                        "Modern veya klasik görünüm", "settings/scoreboard-style"));
                buttons.add(btn("&7Oyuncu adı: &aDaima açık", "Klasik görünümde isim kapatılamaz", "settings/cat/scoreboard"));
                buttons.add(btn("&7← Geri", "Ayarlar", "settings"));
            }
            case "economy" -> {
                title = "&6&lEkonomi"; icon = Material.GOLD_INGOT;
                addToggle(buttons, player, SettingsStore.Toggle.PAY_ALERTS);
                addToggle(buttons, player, SettingsStore.Toggle.PAY_CONFIRMATIONS);
                buttons.add(btn("&7← Geri", "Ayarlar", "settings/page/2"));
            }
            default -> {
                title = "&f&lGenel"; icon = Material.COMPARATOR;
                addToggle(buttons, player, SettingsStore.Toggle.SOUNDS);
                buttons.add(btn("&7← Geri", "Ayarlar", "settings"));
            }
        }
        show(player, build(title, List.of(item(icon, title, "&7Seçenekleri kişiselleştir")), buttons, cols("detail-columns", 1)));
    }

    private void addToggle(List<ActionButton> buttons, Player player, SettingsStore.Toggle toggle) {
        boolean on = plugin.settings().get(player.getUniqueId(), toggle);
        buttons.add(btn("&f" + toggle.label() + ": " + (on ? "&aAÇIK" : "&cKAPALI"), "Ayarı değiştir", "settings/toggle/" + toggle.name().toLowerCase(java.util.Locale.ROOT)));
    }

    public void openAudienceSettings(Player player, SettingsStore.Toggle toggle) {
        SettingsStore.Audience current = plugin.settings().getAudience(player.getUniqueId(), toggle);
        List<ActionButton> buttons = new ArrayList<>();
        for (SettingsStore.Audience a : SettingsStore.Audience.values()) {
            buttons.add(btn(current == a ? "&a✓ " + audienceName(a) : "&f" + audienceName(a), "Bu grubu seç", "settings/audience/" + toggle.name() + "/" + a.name()));
        }
        buttons.add(btn("&7← Geri", "Sohbet", "settings/cat/chat"));
        show(player, build("&b&l" + toggle.label(), List.of(item(Material.PLAYER_HEAD, "&b" + toggle.label(), "&7Kimler görebilir?")), buttons, cols("detail-columns", 1)));
    }

    // ===================== LEADERBOARDS =====================
    public void openLeaderboardMenu(Player player) {
        if (!supports(player)) return;
        List<ActionButton> buttons = List.of(
                btn("&fPara", "En yüksek bakiyeler", "lb/money"),
                btn("&fShard", "En yüksek shard", "lb/shards"),
                btn("&fKills", "Oyuncu öldürmeleri", "lb/kills"),
                btn("&fÖlümler", "Ölümler", "lb/deaths"),
                btn("&fPlaytime", "Oynama süresi", "lb/playtime"),
                btn("&fYerleştirilen Bloklar", "Kırılan bloklar", "lb/placed"),
                btn("&fKırılan Bloklar", "Kırılan bloklar", "lb/blocks"),
                btn("&fÖldürülen Yaratıklar", "Öldürülen yaratıklar", "lb/mobs"),
                backBtn("main")
        );
        show(player, build("&fSıralamalar", List.of(
                item(Material.GOLD_INGOT, "&fSıralamalar", "&7Kategori seç")
        ), buttons, cols("list-columns", 2)));
    }

    public void openParaLeaderboard(Player player) {
        if (!supports(player)) return;
        var top = plugin.economy().topBalances(10);
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (var e : top) {
            sb.append("&e#").append(i++).append(" ").append(e.name())
                    .append(" - ").append(plugin.economy().format(e.balance())).append("\n");
        }
        if (top.isEmpty()) sb.append("&7Henüz veri yok. Para kazan veya al.");
        List<DialogBody> body = new ArrayList<>();
        body.add(item(Material.GOLD_INGOT, "&fEn İyi 10 - Para", "&7En yüksek bakiyeler"));
        int hi = 1;
        for (var e : top) {
            int r = hi++;
            // order: #rank  then head  then score (screenshot style)
            body.add(DialogBody.plainMessage(ColorUtil.text("&6#" + r), 40));
            body.add(rankHead(e.uuid(), e.name()));
            body.add(DialogBody.plainMessage(ColorUtil.text("&f" + e.name() + " &7- &e" + plugin.economy().format(e.balance())), 200));
        }
        if (top.isEmpty()) body.add(DialogBody.plainMessage(ColorUtil.text("&7Henüz veri yok."), 300));
        show(player, build("&fEn İyi 10 - Para", body, List.of(btn("&fGeri", "Sıralamalar", "lb/menu")), 1));
    }

    public void openLeaderboard(Player player, StatsStore.Type type) {
        if (!supports(player)) return;
        var top = plugin.stats().top(type, 10);
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (var e : top) {
            String name = e.name() != null ? e.name() : org.bukkit.Bukkit.getOfflinePlayer(e.uuid()).getName();
            if (name == null) name = "?";
            String val = type == StatsStore.Type.PLAYTIME
                    ? StatsStore.formatPlaytime(e.value())
                    : String.valueOf(e.value());
            sb.append("&e#").append(i++).append(" ").append(name).append(" - ").append(val).append("\n");
        }
        String title = switch (type) {
            case KILLS -> "&fEn İyi 10 - Öldürme";
            case DEATHS -> "&fTop 10 - Ölümler";
            case BLOCKS -> "&fEn İyi 10 - Kırılan Blok";
            case PLACED -> "&fEn İyi 10 - Yerleştirilen Blok";
            case MOBS -> "&fEn İyi 10 - Yaratık Öldürme";
            case PLAYTIME -> "&fEn İyi 10 - Oyun Süresi";
        };
        List<DialogBody> body = new ArrayList<>();
        body.add(item(Material.ITEM_FRAME, title, "&7En iyi 10"));
        int rank = 1;
        for (var e : top) {
            String val = type == StatsStore.Type.PLAYTIME
                    ? StatsStore.formatPlaytime(e.value()) : String.valueOf(e.value());
            String nm = e.name() != null ? e.name() : "?";
            body.add(DialogBody.plainMessage(ColorUtil.text("&6#" + rank), 40));
            body.add(rankHead(e.uuid(), nm));
            body.add(DialogBody.plainMessage(ColorUtil.text("&f" + nm + " &7- &e" + val), 200));
            rank++;
        }
        if (top.isEmpty()) body.add(DialogBody.plainMessage(ColorUtil.text("&7Henüz veri yok."), 300));
        show(player, build(title, body, List.of(btn("&fGeri", "Sıralamalar", "lb/menu")), 1));
    }



    public void openStatsDialog(Player player) {
        if (!supports(player)) {
            plugin.classic().openİstatistikler(player, player.getUniqueId());
            return;
        }
        List<ActionButton> buttons = new ArrayList<>();
        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            buttons.add(btn("&f" + p.getName(), "İstatistikleri görüntüle", "stats/view/" + p.getUniqueId()));
        }
        buttons.add(btn("&f+ Oyuncu Ekle", "Çevrim dışı oyuncu ara", "stats/add"));
        buttons.add(btn("&fGeri", "Ana Menü", "main"));
        show(player, build("&fİstatistikler", List.of(
                item(Material.BOOK, "&fİstatistikler", "&7İstatistiklerini görmek için oyuncuya tıkla")
        ), buttons, cols("list-columns", 2)));
    }

    public void openStatsView(Player viewer, java.util.UUID target) {
        if (!supports(viewer)) {
            plugin.classic().openİstatistikler(viewer, target);
            return;
        }
        String name = org.bukkit.Bukkit.getOfflinePlayer(target).getName();
        if (name == null) name = "?";
        long kills = plugin.stats().get(target, StatsStore.Type.KILLS);
        long deaths = plugin.stats().get(target, StatsStore.Type.DEATHS);
        long blocks = plugin.stats().get(target, StatsStore.Type.BLOCKS);
        long placed = plugin.stats().get(target, StatsStore.Type.PLACED);
        long mobs = plugin.stats().get(target, StatsStore.Type.MOBS);
        String play = StatsStore.formatPlaytime(plugin.stats().get(target, StatsStore.Type.PLAYTIME));
        String money = plugin.economy() == null ? "0" : plugin.economy().format(plugin.economy().get(org.bukkit.Bukkit.getOfflinePlayer(target)));
        String shard = plugin.shards() == null ? "0" : String.valueOf(plugin.shards().get(target));
        String body = "&f" + name
                + "\n\n&7Para: &a$ " + money
                + "\n\n&7Shard: &d★ " + shard
                + "\n\n&7Öldürme: &f" + kills
                + "\n\n&7Ölme: &f" + deaths
                + "\n\n&7Oynama: &f" + play
                + "\n\n&7Kırılan Bloklar: &f" + blocks
                + "\n\n&7Yerleştirilen Bloklar: &f" + placed
                + "\n\n&7Öldürülen Yaratıklar: &f" + mobs;
        List<ActionButton> backs = new ArrayList<>();
        if (viewer.hasPermission("snownwcore.admin.profilemanager")
                || viewer.hasPermission("snownwcore.admin")) {
            backs.add(backBtn("profiles"));
        }
        backs.add(btn("&fİstatistikler", "İstatistikler", "stats"));
        backs.add(btn("&8Ana Menü", "Ana Menü", "main"));
        show(viewer, build("&fİstatistikler", List.of(
                DialogBody.plainMessage(ColorUtil.text(body), 320)
        ), backs, 1));
    }


    public void openStatsAdd(Player player) {
        Dialog dialog = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(ColorUtil.text("&fOyuncu Ekle"))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .body(List.of(DialogBody.plainMessage(
                                ColorUtil.text("&7İstatistiklerini görmek için isim gir"), 300)))
                        .inputs(List.of(DialogInput.text("name", Component.text("Oyuncu Adı"))
                                .maxLength(16).width(220).build()))
                        .build())
                .type(DialogType.confirmation(
                        btn("&aİstatistikleri Gör", "Ara", "stats/lookup"),
                        backBtn("stats")
                )));
        show(player, dialog);
    }



    public void openCommandToggle(Player player, boolean enabling) {
        var toggles = plugin.commandToggles();
        java.util.List<String> list = enabling ? toggles.disabledList() : toggles.enabledList();
        List<ActionButton> buttons = new ArrayList<>();
        for (String c : list) {
            if (enabling) {
                buttons.add(btn("&a" + c, "Bu komutu aç", "cmd/enable/" + c));
            } else {
                buttons.add(btn("&c" + c, "Bu komutu kapat", "cmd/disable/" + c));
            }
        }
        buttons.add(btn("&7Kapat", "Kapat", "close"));
        String title = enabling ? "&aKomutları Aç" : "&cKomutları Kapat";
        String body = enabling
                ? "&7Komutu açmak için tıkla"
                : "&7Sunucuda komutu kapatmak için tıkla";
        show(player, build(title, List.of(
                item(Material.COMMAND_BLOCK, title, body)
        ), buttons, cols("list-columns", 2)));
    }

    // ===================== PROFILE MANAGER =====================
    public void openProfiller(Player player) {
        if (!supports(player)) return;
        if (!player.hasPermission("snownwcore.admin.profilemanager")
                && !player.hasPermission("snownwcore.commands.profilemanager")
                && !player.hasPermission("snownwcore.admin")) {
            player.sendMessage(ColorUtil.text(plugin.messages().get("no-permission")));
            return;
        }
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(btn("&eOyuncu Ara", "İsimle ara", "profile/search"));
        for (Player online : Bukkit.getOnlinePlayers()) {
            buttons.add(btn("&f" + online.getName(), "Çevrim içi", "profile/" + online.getUniqueId()));
        }
        // offline with stats data
        for (String key : plugin.stats().knownPlayers()) {
            try {
                java.util.UUID id = java.util.UUID.fromString(key);
                if (Bukkit.getPlayer(id) != null) continue;
                String name = plugin.stats().nameOf(id);
                if (name == null) continue;
                buttons.add(btn("&7" + name, "Çevrim dışı", "profile/" + id));
            } catch (Exception ignored) {}
        }
        buttons.add(backBtn("main"));
        show(player, build("&cProfil yöneticisi", List.of(
                item(Material.PLAYER_HEAD, "&cProfil yöneticisi", "&7Oyuncu seç veya ara")
        ), buttons, cols("list-columns", 2)));
    }

    public void openProfileSearch(Player admin) {
        Dialog dialog = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(ColorUtil.text("&cOyuncu Ara"))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .body(List.of(item(Material.NAME_TAG, "&eAra", "&7Oyuncu adını gir")))
                        .inputs(List.of(DialogInput.text("name", Component.text("Oyuncu Adı"))
                                .maxLength(16).width(220).build()))
                        .build())
                .type(DialogType.confirmation(
                        btn("&aAra", "Bul", "profile/lookup"),
                        backBtn("profiles")
                )));
        show(admin, dialog);
    }

    public void openProfile(Player admin, java.util.UUID targetId) {
        if (!admin.hasPermission("snownwcore.admin.profilemanager")
                && !admin.hasPermission("snownwcore.commands.profilemanager")
                && !admin.hasPermission("snownwcore.admin")) return;
        org.bukkit.OfflinePlayer off = Bukkit.getOfflinePlayer(targetId);
        String name = off.getName() != null ? off.getName() : "?";
        boolean online = off.isOnline();
        double bal = plugin.economy() != null ? plugin.economy().get(off) : 0;
        int homes = plugin.homes().list(targetId).size();
        String body = "&7Durum: " + (online ? "&aOnline" : "&cOffline")
                + "\n&7Bakiye: &f" + (plugin.economy() != null ? plugin.economy().format(bal) : "0")
                + "\n&7Evler: &f" + homes;
        List<ActionButton> buttons = List.of(
                btn("&fEvleri Gör", "Evler", "profile/homes/" + targetId),
                btn("&fEnvanteri Gör", "Inventory", "profile/inv/" + targetId),
                btn("&fView Ender Chest", "Ender sandığı", "profile/ec/" + targetId),
                btn("&fİstatistikleri Gör", "İstatistikler", "stats/view/" + targetId),
                btn("&cOyuncu Verilerini Sil", "Ev verilerini temizle", "profile/wipe/" + targetId),
                backBtn("profiles")
        );
        show(admin, build("&f" + name, List.of(
                playerHead(targetId, name, "&f" + name, body)
        ), buttons, cols("list-columns", 2)));
        admin.sendMessage(ColorUtil.text(plugin.messages().get("profile-showing").replace("{player}", name)));
    }

    /** Admin view of another player's homes (profile/homes/ actions). */
    public void openPlayerHomes(Player admin, UUID targetId) { openPlayerEvler(admin, targetId); }

    public void openPlayerEvler(Player admin, UUID targetId) {
        if (!admin.hasPermission("snownwcore.admin") && !admin.hasPermission("snownwcore.commands.profilemanager")
                && !admin.hasPermission("snownwcore.admin.profilemanager")) return;
        Player target = Bukkit.getPlayer(targetId);
        String name = target != null ? target.getName()
                : String.valueOf(Bukkit.getOfflinePlayer(targetId).getName());
        if (name == null) name = "?";
        List<HomeStore.Home> list = plugin.homes().list(targetId);
        List<ActionButton> buttons = new ArrayList<>();
        for (HomeStore.Home h : list) {
            buttons.add(btn("&aTP #" + h.slot() + " &f" + h.name(), "Işınlan", "admin/tp/" + targetId + "/" + h.slot()));
            buttons.add(btn("&cDEL #" + h.slot(), "Sil", "admin/del/" + targetId + "/" + h.slot()));
        }
        if (list.isEmpty()) buttons.add(btn("&7Ev yok", "-", "profiles"));
        buttons.add(backBtn("profiles"));
        show(admin, build("&c" + name, List.of(
                item(Material.PLAYER_HEAD, "&c" + name, "&7Evler: " + list.size())
        ), buttons, cols("list-columns", 2)));
    }


    // ===================== ITEM SEARCH (dialog) =====================
    public void openItemSearch(Player player, String query, String context) {
        if (!supports(player)) return;
        final String q = query == null ? "" : query.trim().toUpperCase(java.util.Locale.ROOT);
        final String ctx = context == null ? "menu" : context;

        List<Material> mats = new ArrayList<>();
        for (Material m : Material.values()) {
            if (!m.isItem() || m.isAir() || m.name().contains("LEGACY")) continue;
            if (q.isEmpty() || m.name().contains(q) || m.name().replace("_", " ").contains(q.replace(" ", "_"))) mats.add(m);
            if (mats.size() >= 48) break;
        }

        List<ActionButton> buttons = new ArrayList<>();
        List<DialogBody> body = new ArrayList<>();
        body.add(item(Material.COMPASS, "&eEşya Seç", "&7Arama aşağıdaki listeyi filtreler"));
        int shown = 0;
        for (Material m : mats) {
            String label = m.name().replace('_', ' ');
            if (label.length() > 18) label = label.substring(0, 18);
            buttons.add(smallBtn("&f" + label, m.name(), "itemsearch/pick/" + ctx + "/" + m.name()));
            if (shown < 12) {
                body.add(DialogBody.item(new ItemStack(m)).description(
                        DialogBody.plainMessage(ColorUtil.text("&f" + label), 120)
                ).showDecorations(true).showTooltip(true).width(16).height(16).build());
                shown++;
            }
        }
        if (buttons.isEmpty()) buttons.add(btn("&7No results", "Try another word", "itemsearch/" + ctx));
        buttons.add(btn("&eAra", "Filtre yaz", "itemsearch/input/" + ctx));
        buttons.add(btn("&8Geri", "Geri", ctx.equals("home") ? "homes/0" : "main"));

        final List<ActionButton> fButtons = buttons;
        String title = q.isEmpty() ? "&fEşya Seç" : ("&fEşya Seç &7- " + q);
        show(player, Dialog.create(b -> b.empty()
                .base(DialogBase.builder(ColorUtil.text(title))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .body(body)
                        .build())
                .type(DialogType.multiAction(fButtons).columns(cols("home-columns", 4)).build())));
        // no click sound on open (client already plays)
    }

    public void openItemSearchInput(Player player, String context) {
        final String ctx = context == null ? "menu" : context;
        Dialog dialog = Dialog.create(b -> b.empty()
                .base(DialogBase.builder(ColorUtil.text("&eEşya Arama"))
                        .canCloseWithEscape(true)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .body(List.of(item(Material.COMPASS, "&eAra", "&7Eşya adını gir (ör. DIAMOND)")))
                        .inputs(List.of(DialogInput.text("query", Component.text("Ara"))
                                .maxLength(32).width(250).build()))
                        .build())
                .type(DialogType.confirmation(
                        btn("&aAra", "Bul items", "itemsearch/do/" + ctx),
                        btn("&7İptal", "İptal", "main")
                )));
        show(player, dialog);
    }

    // ===================== helpers =====================
    // ===================== RENK TEMASI + YARDIMCILAR =====================
    /** dialog-theme.* anahtarlarini okur; tum menuler tek palet kullanir. */
    private String theme(String key, String def) {
        return plugin.getConfig().getString("dialog-theme." + key, def);
    }

    /** Legacy renk kodlarini temizler (header satirinda cift renk olusmasin). */
    private static String stripLegacy(String s) {
        if (s == null) return "";
        return s.replaceAll("(?i)[&§][0-9a-fk-orx]", "").trim();
    }

    /** /profilemanager komutunun ve dinleyicinin bekledigi isim. */
    public void openProfiles(Player player) { openProfiller(player); }

    /** DIAMOND_SWORD -> "Diamond Sword" */
    private static String prettyMaterial(Material mat) {
        if (mat == null) return "?";
        StringBuilder sb = new StringBuilder();
        for (String part : mat.name().toLowerCase(java.util.Locale.ROOT).split("_")) {
            if (part.isEmpty()) continue;
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    private Material menuIcon() {

        try {
            return Material.valueOf(plugin.getConfig().getString("menu-icon", "NETHER_STAR")
                    .toUpperCase(java.util.Locale.ROOT));
        } catch (Exception e) {
            return Material.NETHER_STAR;
        }
    }

    private Dialog build(String title, List<DialogBody> body, List<ActionButton> buttons, int columns) {
        // Renk temasi: her menude ayni basli seridi + ayirici
        List<DialogBody> themed = new ArrayList<>();
        themed.add(DialogBody.plainMessage(ColorUtil.text(
                theme("header", "&b") + "◆ &f" + stripLegacy(title)), 300));
        themed.add(DialogBody.plainMessage(ColorUtil.text(
                theme("separator", "&8") + theme("divider", "---------------")), 300));
        themed.addAll(body);
        return Dialog.create(b -> b.empty()
                .base(DialogBase.builder(ColorUtil.text(title))
                        .canCloseWithEscape(true)
                        // WAIT keeps overlay until new dialog arrives – no empty world flash
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .body(themed)
                        .build())
                .type(DialogType.multiAction(buttons).columns(Math.max(1, columns)).build()));
    }

    /** Dialogu oyuncunun kendi entity scheduler'ında açar; Paper + Folia uyumludur. */
    public void show(Player player, Dialog dialog) {
        if (player == null || dialog == null || !player.isOnline()) return;
        lastDialogAt.put(player.getUniqueId(), System.nanoTime());
        player.getScheduler().run(plugin, task -> {
            try {
                if (player.isOnline()) player.showDialog(dialog);
            } catch (Throwable ex) {
                // Gercek sebebi gormek icin TAM stack trace bas (tek satir degil)
                plugin.getLogger().log(java.util.logging.Level.WARNING,
                        "Dialog açılamadı (" + player.getName() + "):", ex);
                // Yedek: dialog calismiyorsa klasik envanter menusune dus
                try {
                    plugin.classic().openHomes(player);
                } catch (Throwable ignored) {
                    player.sendMessage(ColorUtil.text("&cMenü açılamadı. Sunucu konsolunu kontrol edin."));
                }
            }
        }, () -> {});
    }


    private ActionButton smallBtn(String label, String tooltip, String action) {
        return smallBtn(null, label, tooltip, action);
    }

    /** Ev yuvasi izgarasi icin kucuk simgeli buton. */
    private ActionButton smallBtn(String glyph, String label, String tooltip, String action) {
        String text = (glyph == null ? "" : icon(glyph)) + label;
        String key = action == null ? "close" : action.toLowerCase(java.util.Locale.ROOT);
        return ActionButton.create(
                ColorUtil.text(text),
                ColorUtil.text(theme("tooltip", "&7") + tooltip),
                Math.max(40, plugin.getConfig().getInt("home-button-width", 100)),
                DialogAction.customClick(Key.key("snownwcore", key), null));
    }

    private ActionButton btn(String label, String tooltip, String action) {
        return btn(null, label, tooltip, action);
    }

    /** Simgeli buton (glyph null ise simge eklenmez). */
    private ActionButton btn(String glyph, String label, String tooltip, String action) {
        String text = (glyph == null ? "" : icon(glyph)) + label;
        String key = action == null ? "close" : action.toLowerCase(java.util.Locale.ROOT);
        return ActionButton.create(
                ColorUtil.text(text),
                ColorUtil.text(theme("tooltip", "&7") + tooltip),
                Math.max(40, plugin.getConfig().getInt("button-width", 150)),
                DialogAction.customClick(Key.key("snownwcore", key), null));
    }

    /** Tum menulerde tek tip geri butonu. */
    private ActionButton backBtn(String action) {
        return btn("\u00ab", "&8Geri", "Bir önceki menüye dön", action);
    }

    /** Kolon sayisi: dialog-theme.<key>. */
    private int cols(String key, int def) {
        return Math.max(1, plugin.getConfig().getInt("dialog-theme." + key, def));
    }

    /** Kategori rengi: dialog-theme.categories.<key>. */
    private String cat(String key) {
        String def = switch (key) {
            case "homes" -> "&a";
            case "teleport" -> "&b";
            case "economy" -> "&6";
            case "social" -> "&d";
            case "progress" -> "&e";
            case "admin" -> "&c";
            default -> "&7";
        };
        return theme("categories." + key, def);
    }

    /** Simge onekleri (dialog-theme.icons ile kapatilir). */
    private String icon(String glyph) {
        return plugin.getConfig().getBoolean("dialog-theme.icons", true) ? glyph + " " : "";
    }

    private DialogBody rankHead(java.util.UUID uuid, String name) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        try {
            org.bukkit.inventory.meta.SkullMeta sm = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
            if (sm != null) {
                sm.setOwningPlayer(org.bukkit.Bukkit.getOfflinePlayer(uuid));
                head.setItemMeta(sm);
            }
        } catch (Throwable ignored) {}
        return DialogBody.item(head)
                .description(DialogBody.plainMessage(ColorUtil.text(" "), 8))
                .showDecorations(true)
                .showTooltip(true)
                .width(16)
                .height(16)
                .build();
    }

    private DialogBody playerHead(java.util.UUID uuid, String name, String title, String desc) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        try {
            org.bukkit.inventory.meta.SkullMeta sm = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
            if (sm != null) {
                sm.setOwningPlayer(org.bukkit.Bukkit.getOfflinePlayer(uuid));
                head.setItemMeta(sm);
            }
        } catch (Throwable ignored) {}
        return DialogBody.item(head)
                .description(DialogBody.plainMessage(ColorUtil.text(title + "\n" + desc), 250))
                .showDecorations(true)
                .showTooltip(true)
                .width(32)
                .height(32)
                .build();
    }

    private DialogBody item(Material mat, String title, String desc) {
        ItemStack stack = new ItemStack(mat);
        return DialogBody.item(stack)
                .description(DialogBody.plainMessage(ColorUtil.text(title + "\n" + desc), 250))
                .showDecorations(true)
                .showTooltip(true)
                .width(32)
                .height(32)
                .build();
    }
}
