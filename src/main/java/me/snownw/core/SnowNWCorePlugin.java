package me.snownw.core;

import me.snownw.core.command.CoreCommands;
import me.snownw.core.data.FriendsStore;
import me.snownw.core.data.HomeStore;
import me.snownw.core.data.SettingsStore;
import me.snownw.core.data.StatsStore;
import me.snownw.core.dialog.DialogListener;
import me.snownw.core.dialog.DialogMenus;
import me.snownw.core.gui.ClassicMenus;
import me.snownw.core.gui.ClassicMenusListener;
import me.snownw.core.listener.MobHideListener;
import me.snownw.core.listener.StatsListener;
import me.snownw.core.service.SpawnStashService;
import me.snownw.core.service.AmethystToolService;
import me.snownw.core.service.FakePlayerService;
import me.snownw.core.service.OffenseService;
import me.snownw.core.service.TeleportService;
import me.snownw.core.service.TpaService;
import me.snownw.core.service.CommandToggleService;
import me.snownw.core.service.KitService;
import me.snownw.core.service.ShopService;
import me.snownw.core.service.EnderChestService;
import me.snownw.core.service.ShardService;
import me.snownw.core.service.TeamService;
import me.snownw.core.service.CuboidService;
import me.snownw.core.service.AreaService;
import me.snownw.core.service.RtpService;
import me.snownw.core.service.EconomyService;
import me.snownw.core.service.ScoreboardService;
import me.snownw.core.service.TabService;
import me.snownw.core.service.WarpService;
import me.snownw.core.service.WorthService;
import me.snownw.core.service.AuctionService;
import me.snownw.core.service.CombatService;
import me.snownw.core.service.ExtraSystemsService;
import me.snownw.core.listener.ExtraSystemsListener;
import me.snownw.core.listener.CombatListener;
import me.snownw.core.util.Messages;
import me.snownw.core.util.SoundUtil;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.List;

public final class SnowNWCorePlugin extends JavaPlugin {

    private HomeStore homes;
    private FriendsStore friends;
    private SettingsStore settings;
    private TpaService tpa;
    private StatsStore stats;
    private TeleportService teleports;
    private WorthService worth;
    private AuctionService auction;
    private CombatService combat;
    private DialogMenus menus;
    private ScoreboardService scoreboard;
    private TabService tab;
    private WarpService warps;
    private EconomyService economy;
    private EnderChestService enderChest;
    private ShardService shards;
    private TeamService teams;
    private CuboidService cuboids;
    private AreaService areas;
    private me.snownw.core.listener.AreaListener areaListener;
    private RtpService rtp;
    private CommandToggleService commandToggles;
    private KitService kits;
    private ShopService shop;
    private ClassicMenus classic;
    private MobHideListener mobHide;
    private SoundUtil sounds;
    private Messages messages;
    private boolean commandsEnabled = true;
    private ExtraSystemsService extras;
    private SpawnStashService spawnStash;
    private FakePlayerService fakePlayers;
    private OffenseService offenses;
    private AmethystToolService amethystTools;
    private SellService sell;
    private OrderService orders;
    private BountyService bounty;
    private DeathMessageService deathMessages;
    private ClearLagService clearLag;
    private KeyAllService keyAll;
    private ShardShopService shardShop;
    private me.snownw.core.gui.ShopGui shopGui;
    private me.snownw.core.gui.ShardShopGui shardShopGui;
    private me.snownw.core.listener.DoubleJumpListener doubleJump;

    public SpawnStashService spawnStash() { return spawnStash; }
    public FakePlayerService fakePlayers() { return fakePlayers; }
    public OffenseService offenses() { return offenses; }
    public AmethystToolService amethystTools() { return amethystTools; }
    public SellService sell() { return sell; }
    public OrderService orders() { return orders; }
    public BountyService bounty() { return bounty; }
    public DeathMessageService deathMessages() { return deathMessages; }
    public ClearLagService clearLag() { return clearLag; }
    public KeyAllService keyAll() { return keyAll; }
    public ShardShopService shardShop() { return shardShop; }
    public me.snownw.core.gui.ShopGui shopGui() { return shopGui; }
    public me.snownw.core.gui.ShardShopGui shardShopGui() { return shardShopGui; }
    public me.snownw.core.listener.DoubleJumpListener doubleJump() { return doubleJump; }

    public boolean socialAudienceAllows(java.util.UUID owner, java.util.UUID viewer, SettingsStore.Toggle toggle) {
        SettingsStore.Audience audience = settings.getAudience(owner, toggle);
        if (owner.equals(viewer)) return true;
        return switch (audience) {
            case EVERYONE -> true;
            case FRIENDS -> friends.friends(owner).contains(viewer);
            case FOLLOWING -> friends.following(owner).contains(viewer);
            case FRIENDS_AND_FOLLOWING -> friends.friends(owner).contains(viewer) || friends.following(owner).contains(viewer);
            case NOBODY -> false;
        };
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        me.snownw.core.util.ColorUtil.configure(getConfig().getConfigurationSection("colors"));
        saveResource("rtp.yml", false);
        // Only config.yml (default), messages.yml, scoreboard.yml for configuration
        saveResource("messages.yml", false);
        if (!new java.io.File(getDataFolder(), "offenses.yml").exists()) { try { saveResource("offenses.yml", false); } catch (Throwable ignored) {} }
        saveResource("scoreboard.yml", false);
        if (!new java.io.File(getDataFolder(), "shop.yml").exists()) { try { saveResource("shop.yml", false); } catch (Throwable ignored) {} }
        // worth.yml is data/prices (large) – still used by WorthService if present
        if (!new java.io.File(getDataFolder(), "worth.yml").exists()) {
            try { saveResource("worth.yml", false); } catch (Throwable ignored) {}
        }

        messages = new Messages(this);
        homes = new HomeStore(this);
        homes.load();
        friends = new FriendsStore(this);
        friends.load();
        settings = new SettingsStore(this);
        settings.load();
        tpa = new TpaService(this);
        stats = new StatsStore(this);
        stats.load();
        sounds = new SoundUtil(this);
        teleports = new TeleportService(this);
        worth = new WorthService(this);
        auction = new AuctionService(this);
        combat = new CombatService(this);
        combat.start();
        menus = new DialogMenus(this);
        economy = new EconomyService(this);
        enderChest = new EnderChestService(this);
        shards = new ShardService(this);
        teams = new TeamService(this);
        cuboids = new CuboidService(this);
        areas = new AreaService(this);
        rtp = new RtpService(this);
        warps = new WarpService(this);
        commandToggles = new CommandToggleService(this);
        kits = new KitService(this);
        shop = new ShopService(this);
        scoreboard = new ScoreboardService(this);
        scoreboard.start();
        tab = new TabService(this);
        tab.start();
        classic = new ClassicMenus(this);
        mobHide = new MobHideListener(this);
        extras = new ExtraSystemsService(this);
        spawnStash = new SpawnStashService(this);
        fakePlayers = new FakePlayerService(this);
        offenses = new OffenseService(this);
        amethystTools = new AmethystToolService(this);
        sell = new SellService(this);
        orders = new OrderService(this);
        bounty = new BountyService(this);
        deathMessages = new DeathMessageService(this);
        clearLag = new ClearLagService(this);
        keyAll = new KeyAllService(this);
        shardShop = new ShardShopService(this);
        shopGui = new me.snownw.core.gui.ShopGui(this);
        shardShopGui = new me.snownw.core.gui.ShardShopGui(this);

        getServer().getPluginManager().registerEvents(new DialogListener(this), this);
        getServer().getPluginManager().registerEvents(new StatsListener(this), this);
        getServer().getPluginManager().registerEvents(new me.snownw.core.gui.ItemSearchListener(), this);
        getServer().getPluginManager().registerEvents(new ClassicMenusListener(this), this);
        getServer().getPluginManager().registerEvents(mobHide, this);
        getServer().getPluginManager().registerEvents(new me.snownw.core.listener.ChatSocialListener(this), this);
        getServer().getPluginManager().registerEvents(new me.snownw.core.listener.MuteRestrictionListener(this), this);
        getServer().getPluginManager().registerEvents(new me.snownw.core.listener.WorthLoreListener(this), this);
        getServer().getPluginManager().registerEvents(new me.snownw.core.listener.PvpSettingsListener(this), this);
        getServer().getPluginManager().registerEvents(new me.snownw.core.listener.EnderChestListener(this), this);
        getServer().getPluginManager().registerEvents(new me.snownw.core.listener.WandListener(this), this);
        areaListener = new me.snownw.core.listener.AreaListener(this);
        getServer().getPluginManager().registerEvents(areaListener, this);
        getServer().getPluginManager().registerEvents(new me.snownw.core.listener.SpawnStashListener(this), this);
        getServer().getPluginManager().registerEvents(new me.snownw.core.listener.SpawnerListener(this), this);
        getServer().getPluginManager().registerEvents(new me.snownw.core.listener.TeleportMoveListener(this), this);
        getServer().getPluginManager().registerEvents(new CombatListener(this), this);
        getServer().getPluginManager().registerEvents(new ExtraSystemsListener(this), this);
        getServer().getPluginManager().registerEvents(new me.snownw.core.listener.AmethystToolListener(this), this);
        getServer().getPluginManager().registerEvents(new me.snownw.core.gui.ShopGuiListener(this), this);
        doubleJump = new me.snownw.core.listener.DoubleJumpListener(this);
        getServer().getPluginManager().registerEvents(doubleJump, this);
        getServer().getPluginManager().registerEvents(new me.snownw.core.listener.DamageTweakListener(this), this);
        getServer().getPluginManager().registerEvents(new me.snownw.core.listener.RespawnKitListener(this), this);
        getServer().getPluginManager().registerEvents(new me.snownw.core.listener.ChatFormatListener(this), this);
        getServer().getPluginManager().registerEvents(new me.snownw.core.listener.AfkAutoListener(this), this);
        clearLag.start();
        keyAll.start();

        // Paper plugins cannot use plugin.yml getCommand – register in code
        final CoreCommands cmds = new CoreCommands(this);
        registerPaperCmd("menu", List.of("snownw", "snow", "panel", "menü"), cmds);
        registerPaperCmd("home", List.of("ev"), cmds);
        registerPaperCmd("sethome", List.of("evayarla"), cmds);
        registerPaperCmd("delhome", List.of("evsil"), cmds);
        registerPaperCmd("homes", List.of("evler"), cmds);
        registerPaperCmd("leaderboard", List.of("lb", "leaderboards", "siralama", "sıralama"), cmds);
        registerPaperCmd("profilemanager", List.of("pm"), cmds);
        registerPaperCmd("snownwcore", List.of("vc"), cmds);
        registerPaperCmd("worth", List.of("fiyat", "fiyatlar", "deger", "değer"), cmds);
        registerPaperCmd("ah", List.of("auction", "auctionhouse", "aciktirma", "açıkartırma"), cmds);
        registerPaperCmd("itemsearch", List.<String>of(), cmds);
        registerPaperCmd("friends", List.of("arkadaslar", "arkadaşlar"), cmds);
        registerPaperCmd("settings", List.of("ayarlar"), cmds);
        registerPaperCmd("stats", List.of("istatistik", "istatistikler"), cmds);
        registerPaperCmd("tpa", List.<String>of(), cmds);
        registerPaperCmd("tpahere", List.<String>of(), cmds);
        registerPaperCmd("tpaccept", List.of("tpyes"), cmds);
        registerPaperCmd("tpdeny", List.of("tpno"), cmds);
        registerPaperCmd("tpauto", List.<String>of(), cmds);
        registerPaperCmd("tpacancel", List.<String>of(), cmds);
        registerPaperCmd("msg", List.of("message", "tell", "whisper"), cmds);
        registerPaperCmd("pay", List.<String>of(), cmds);
                        registerPaperCmd("bal", List.of("balance", "money", "bakiye", "para"), cmds);
        registerPaperCmd("enderchest", List.of("ec", "echest"), cmds);
        registerPaperCmd("shard", List.<String>of(), cmds);
        registerPaperCmd("rtp", List.<String>of(), cmds);
        registerPaperCmd("rtpqueue", List.of("rtpsira", "rtp-sira"), cmds);
        registerPaperCmd("spawn", List.of(), cmds);
        registerPaperCmd("warp", List.of("warps"), cmds);
        registerPaperCmd("setspawn", List.of(), cmds);
        registerPaperCmd("kit", List.of("kits"), cmds);
        registerPaperCmd("afk", List.of(), cmds);
        registerPaperCmd("setafk", List.of(), cmds);
        registerPaperCmd("freeze", List.of(), cmds);
        registerPaperCmd("unfreeze", List.of(), cmds);
        registerPaperCmd("rank", List.of("rütbe"), cmds);
        registerPaperCmd("report", List.of("şikayet"), cmds);
        registerPaperCmd("helpop", List.of(), cmds);
        registerPaperCmd("rules", List.of("kurallar"), cmds);
        registerPaperCmd("serverinfo", List.of("sunucubilgi"), cmds);
        registerPaperCmd("hide", List.of("gizle"), cmds);
        registerPaperCmd("spawnstash", List.of("stash"), cmds);
        registerPaperCmd("voice", List.of("sesli"), cmds);
        registerPaperCmd("fakeplayer", List.of("sahteoyuncu"), cmds);
        registerPaperCmd("offense", List.of("ceza"), cmds);
        registerPaperCmd("ping", List.of(), cmds);
        registerPaperCmd("gmc", List.of(), cmds);
        registerPaperCmd("gms", List.of(), cmds);
        registerPaperCmd("gmsp", List.of(), cmds);
        registerPaperCmd("amethysttool", List.of("ametisttool", "ametistesya"), cmds);
        registerPaperCmd("team", List.of("takim", "takım"), cmds);
        
        registerPaperCmd("alan", List.of(), cmds);
        registerPaperCmd("alanbaltasi", List.of("alanbaltası"), cmds);
        registerPaperCmd("cuboid", List.of("kup", "küp"), cmds);
        registerPaperCmd("baltop", List.of("zenginler", "enler"), cmds);
        registerPaperCmd("shardmanager", List.of("shardyonetim", "shardyönetim"), cmds);
        registerPaperCmd("sell", List.of("sat"), cmds);
        registerPaperCmd("sellall", List.of("sathepsi", "satall"), cmds);
        registerPaperCmd("order", List.of("siparis", "sipariş", "orders"), cmds);
        registerPaperCmd("bounty", List.of("odul", "ödül", "bounties"), cmds);
        registerPaperCmd("market", List.of("magaza", "mağaza", "shop"), cmds);
        registerPaperCmd("shardshop", List.of("shardmarket", "shardpazari", "shardpazarı"), cmds);
        registerPaperCmd("clearlag", List.of("lagtemizle", "lagtemizlik"), cmds);
        registerPaperCmd("keyall", List.of("anahtarver"), cmds);
        registerPaperCmd("doublejump", List.of("ciftzipla", "çiftzıpla", "dj"), cmds);

        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            homes.save();
            stats.save();
            friends.save();
            settings.save();
            extras.save();
        }, 20L * 300, 20L * 300);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                Class.forName("me.clip.placeholderapi.expansion.PlaceholderExpansion");
                new me.snownw.core.placeholder.SnowNWExpansion(this).register();
                new me.snownw.core.placeholder.RtpZoneExpansion(this).register();
                getLogger().info("PlaceholderAPI hooked (%snownwcore_*%).");
            } catch (ClassNotFoundException e) {
                getLogger().warning("PlaceholderAPI present but API class missing (join-classpath). Install PAPI.");
            } catch (Throwable e) {
                getLogger().warning("PAPI hook failed: " + e.getMessage());
            }
        }

        printBanner();
    }


    private void registerPaperCmd(String name, List<String> aliases, CoreCommands cmds) {
        registerCommand(name, "", aliases, new BasicCommand() {
            @Override
            public void execute(CommandSourceStack stack, String[] args) {
                cmds.dispatch(stack.getSender(), name, args);
            }

            @Override
            public Collection<String> suggest(CommandSourceStack stack, String[] args) {
                return cmds.suggest(stack.getSender(), name, args);
            }
        });
    }

    private void printBanner() {
        String line = "........................................";
        getLogger().info(line);
        getLogger().info(" __     __                                         ____                ");
        getLogger().info(" \\ \\   / /__  _   _  __ _  __ _  ___ _ __          / ___|___  _ __ ___  ");
        getLogger().info("  \\ \\ / / _ \\| | | |/ _` |/ _` |/ _ \\ '__|  _____ | |   / _ \\| '__/ _ \\ ");
        getLogger().info("   \\ V / (_) | |_| | (_| | (_| |  __/ |    |_____|| |__| (_) | | |  __/ ");
        getLogger().info("    \\_/ \\___/ \\__, |\\__,_|\\__, |\\___|_|           \\____\\___/|_|  \\___| ");
        getLogger().info("               |___/      |___/                                       ");
        getLogger().info("  SnowNW Core • 26.1");
        getLogger().info("  Author: SnowNW Team");
        getLogger().info("  Version: " + getDescription().getVersion());
        getLogger().info(line);
        tick("Homes", getConfig().getBoolean("homes.enabled", true) && getConfig().getBoolean("commands.homes", true));
        tick("Friends", getConfig().getBoolean("friends.enabled", true));
        tick("TPA", getConfig().getBoolean("tpa.enabled", true));
        tick("Ayarlar", getConfig().getBoolean("settings.enabled", true));
        tick("Stats", getConfig().getBoolean("stats.enabled", true));
        tick("Sıralamalar", getConfig().getBoolean("leaderboards.enabled", true));
        // Vault plugin vs economy provider (Essentials etc. may register late)
        boolean vaultJar = economy != null && economy.hasVaultPlugin();
        boolean vaultEco = economy != null && economy.hasVault();
        tick("Vault", vaultJar);
        tick("Economy", vaultEco || (economy != null)); // internal always works
        tick("Worth", getConfig().getBoolean("worth.enabled", true));
        tick("MobHide", getConfig().getBoolean("mob-hide.enabled", true));
        tick("PauseButton", getConfig().getBoolean("pause-menu.enabled", true));
        tick("LuckPerms", getServer().getPluginManager().getPlugin("LuckPerms") != null);
        tick("PlaceholderAPI", getServer().getPluginManager().getPlugin("PlaceholderAPI") != null);
        getLogger().info(line);
        getLogger().info("  SnowNW Core aktif • 26.1 Dialog altyapısı");
        getLogger().info(line);
    }

    private void tick(String name, boolean ok) {
        getLogger().info("  " + (ok ? "[OK] " : "[X]  ") + name);
    }

    @Override
    public void onDisable() {
        if (homes != null) homes.save();
        if (friends != null) friends.save();
        if (auction != null) auction.save();
        if (settings != null) settings.save();
        if (stats != null) stats.save();
        if (combat != null) combat.stop();
        if (spawnStash != null) spawnStash.clear();
        if (orders != null) orders.save();
        if (bounty != null) bounty.save();
        if (clearLag != null) clearLag.stop();
        if (keyAll != null) keyAll.stop();
        if (fakePlayers != null) fakePlayers.clearAll();
    }

    public void reloadAll() {
        reloadConfig();
        me.snownw.core.util.ColorUtil.configure(getConfig().getConfigurationSection("colors"));
        messages.reload();
        sounds.reload();
        homes.load();
        stats.load();
        friends.load();
        settings.load();
        worth.reload();
        mobHide.reload();
        if (scoreboard != null) scoreboard.reload();
        if (areas != null) areas.load();
        if (combat != null) combat.start();
    }

    public boolean cmdEnabled(String name) {
        if (!commandsEnabled) return false;
        return commandToggles != null ? commandToggles.isEnabled(name)
                : getConfig().getBoolean("commands." + name, true);
    }

    public boolean commandsEnabled() { return commandsEnabled; }
    public void setCommandsEnabled(boolean v) { this.commandsEnabled = v; }

    public HomeStore homes() { return homes; }
    public FriendsStore friends() { return friends; }
    public SettingsStore settings() { return settings; }
    public TpaService tpa() { return tpa; }
    public StatsStore stats() { return stats; }
    public TeleportService teleports() { return teleports; }
    public WorthService worth() { return worth; }
    public AuctionService auction() { return auction; }
    public CombatService combat() { return combat; }
    public DialogMenus menus() { return menus; }
    public ScoreboardService scoreboard() { return scoreboard; }
    public TabService tab() { return tab; }
    public WarpService warps() { return warps; }
    public EconomyService economy() { return economy; }
    public EnderChestService enderChest() { return enderChest; }
    public ShardService shards() { return shards; }
    public TeamService teams() { return teams; }
    public CuboidService cuboids() { return cuboids; }
    public AreaService areas() { return areas; }
    public me.snownw.core.listener.AreaListener areaListener() { return areaListener; }
    public RtpService rtp() { return rtp; }
    public CommandToggleService commandToggles() { return commandToggles; }
    public ExtraSystemsService extras() { return extras; }
    public KitService kits() { return kits; }
    public ShopService shop() { return shop; }
    public ClassicMenus classic() { return classic; }
    public MobHideListener mobHide() { return mobHide; }
    public SoundUtil sounds() { return sounds; }
    public Messages messages() { return messages; }
}
