package me.snownw.core.service;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.util.MoneyFormat;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;
import java.io.IOException;

/**
 * Soft-depends Vault / VaultUnlocked. Falls back to internal balances.yml.
 * Re-hooks delayed so economy providers (Essentials, CMI, etc.) can register.
 */
public final class EconomyService {

    private final SnowNWCorePlugin plugin;
    private Object vault; // net.milkbowl.vault.economy.Economy
    private boolean vaultPluginPresent;
    private YamlConfiguration data;
    private File file;

    public EconomyService(SnowNWCorePlugin plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "balances.yml");
        data = YamlConfiguration.loadConfiguration(file);
        detectVaultPlugin();
        hookVault();
        // Economy providers often enable after soft-depend plugins
        Bukkit.getScheduler().runTaskLater(plugin, this::hookVault, 20L);
        Bukkit.getScheduler().runTaskLater(plugin, this::hookVault, 60L);
        Bukkit.getScheduler().runTaskLater(plugin, this::hookVault, 100L);
    }

    private void detectVaultPlugin() {
        vaultPluginPresent = Bukkit.getPluginManager().getPlugin("Vault") != null
                || Bukkit.getPluginManager().getPlugin("VaultUnlocked") != null;
    }

    public void hookVault() {
        detectVaultPlugin();
        if (vault != null) return;
        try {
            Class<?> econClass = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> reg = Bukkit.getServicesManager().getRegistration(econClass);
            if (reg != null) {
                vault = reg.getProvider();
                plugin.getLogger().info("[OK] Vault economy: " + vault.getClass().getName());
                return;
            }
            // scan all providers of that type
            for (RegisteredServiceProvider<?> rsp : Bukkit.getServicesManager().getRegistrations(econClass)) {
                if (rsp.getProvider() != null) {
                    vault = rsp.getProvider();
                    plugin.getLogger().info("[OK] Vault economy: " + vault.getClass().getName());
                    return;
                }
            }
        } catch (ClassNotFoundException e) {
            // Vault API not on classpath – still fine with internal
        } catch (Throwable t) {
            plugin.getLogger().warning("Vault hook: " + t.getMessage());
        }
        if (vault == null && vaultPluginPresent) {
            plugin.getLogger().info("[..] Vault plugin found – waiting for economy provider (Essentials/CMI/etc).");
        }
    }

    /** Vault jar installed. */
    public boolean hasVaultPlugin() {
        detectVaultPlugin();
        return vaultPluginPresent;
    }

    /** Actual Economy service registered. */
    public boolean hasVault() {
        return vault != null;
    }

    /** Ready for balance ops (vault OR internal). */
    public boolean isReady() {
        return true;
    }

    public double get(OfflinePlayer player) {
        if (vault != null) {
            try {
                Object r = vault.getClass().getMethod("getBalance", OfflinePlayer.class).invoke(vault, player);
                return ((Number) r).doubleValue();
            } catch (Throwable ignored) {}
        }
        return data.getDouble(player.getUniqueId().toString(), 0);
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        if (amount < 0 || Double.isNaN(amount) || Double.isInfinite(amount)) return false;
        if (vault != null) {
            try {
                vault.getClass().getMethod("depositPlayer", OfflinePlayer.class, double.class)
                        .invoke(vault, player, amount);
                return true;
            } catch (Throwable t) {
                plugin.getLogger().warning("Vault deposit: " + t.getMessage());
            }
        }
        data.set(player.getUniqueId().toString(), get(player) + amount);
        save();
        return true;
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        if (amount < 0 || get(player) < amount) return false;
        if (vault != null) {
            try {
                vault.getClass().getMethod("withdrawPlayer", OfflinePlayer.class, double.class)
                        .invoke(vault, player, amount);
                return true;
            } catch (Throwable t) {
                return false;
            }
        }
        data.set(player.getUniqueId().toString(), get(player) - amount);
        save();
        return true;
    }

    public void set(OfflinePlayer player, double amount) {
        double cur = get(player);
        if (amount > cur) deposit(player, amount - cur);
        else if (amount < cur) withdraw(player, cur - amount);
    }

    private void save() {
        try { data.save(file); } catch (IOException e) {
            plugin.getLogger().severe("balances.yml: " + e.getMessage());
        }
    }

    /** Top balances: online players + internal balances.yml entries. */
    public java.util.List<BalanceEntry> topBalances(int limit) {
        java.util.Map<java.util.UUID, Double> map = new java.util.HashMap<>();
        for (String key : data.getKeys(false)) {
            try {
                java.util.UUID id = java.util.UUID.fromString(key);
                double v = data.getDouble(key, 0);
                if (v > 0) map.put(id, v);
            } catch (Exception ignored) {}
        }
        for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            double bal = get(p);
            if (bal > 0) map.put(p.getUniqueId(), bal);
            else if (bal == 0 && map.containsKey(p.getUniqueId())) {
                // keep file value
            }
        }
        // Also scan offline players who played - vault get
        java.util.List<BalanceEntry> list = new java.util.ArrayList<>();
        for (var e : map.entrySet()) {
            double bal = get(org.bukkit.Bukkit.getOfflinePlayer(e.getKey()));
            if (bal <= 0) bal = e.getValue();
            if (bal <= 0) continue;
            org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(e.getKey());
            String name = op.getName() != null ? op.getName() : "?";
            list.add(new BalanceEntry(e.getKey(), name, bal));
        }
        // Ensure all online with vault balance appear even if not in map
        for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            double bal = get(p);
            if (bal <= 0) continue;
            boolean found = false;
            for (BalanceEntry be : list) {
                if (be.uuid().equals(p.getUniqueId())) { found = true; break; }
            }
            if (!found) list.add(new BalanceEntry(p.getUniqueId(), p.getName(), bal));
        }
        list.sort((a, b) -> Double.compare(b.balance(), a.balance()));
        if (list.size() > limit) return new java.util.ArrayList<>(list.subList(0, limit));
        return list;
    }

    public record BalanceEntry(java.util.UUID uuid, String name, double balance) {}

    public String format(double amount) {
        return MoneyFormat.nicest(amount);
    }
}
