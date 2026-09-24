package me.snownw.core.service;

import me.snownw.core.SnowNWCorePlugin;
import me.snownw.core.data.SettingsStore;
import me.snownw.core.util.ColorUtil;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TpaService {

    public record Request(UUID from, UUID to, boolean here, long expiresAt) {}

    private final SnowNWCorePlugin plugin;
    private final Map<UUID, Request> pending = new ConcurrentHashMap<>(); // key = target

    public TpaService(SnowNWCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void send(Player from, Player to, boolean here) {
        if (from.getUniqueId().equals(to.getUniqueId())) {
            from.sendMessage(ColorUtil.text(msg("tpa-self")));
            return;
        }
        SettingsStore.Toggle need = here ? SettingsStore.Toggle.TPA_HERE : SettingsStore.Toggle.TPA_REQUESTS;
        if (!plugin.settings().get(to.getUniqueId(), need) || !isAllowedByPolicy(from, to)) {
            from.sendMessage(ColorUtil.text(msg("tpa-disabled-target")));
            return;
        }
        long exp = System.currentTimeMillis()
                + plugin.getConfig().getLong("tpa.timeout-seconds", 60) * 1000L;
        pending.put(to.getUniqueId(), new Request(from.getUniqueId(), to.getUniqueId(), here, exp));

        from.sendMessage(ColorUtil.text(msg("tpa-sent").replace("{name}", to.getName())));
        if (here) {
            to.sendMessage(ColorUtil.text(msg("tpa-received-here").replace("{name}", from.getName())));
        } else {
            to.sendMessage(ColorUtil.text(msg("tpa-received").replace("{name}", from.getName())));
        }
        Component click = Component.text("[KABUL ET]")
                .decoration(TextDecoration.ITALIC, false)
                .color(NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand("/tpaccept"))
                .hoverEvent(HoverEvent.showText(Component.text("TPA isteğini kabul et")));
        Component rest = Component.text(" yaz veya tıkla · reddetmek için /tpdeny")
                .decoration(TextDecoration.ITALIC, false)
                .color(NamedTextColor.GRAY);
        to.sendMessage(click.append(rest));
        plugin.sounds().play(from, "menu-click");
        plugin.sounds().play(to, "menu-click");

        // tpauto on target
        if (plugin.settings().isTpAuto(to.getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (pending.containsKey(to.getUniqueId())) accept(to);
            }, 5L);
        }
    }

    public void accept(Player target) {
        Request r = pending.remove(target.getUniqueId());
        if (r == null || r.expiresAt() < System.currentTimeMillis()) {
            target.sendMessage(ColorUtil.text(msg("tpa-none")));
            return;
        }
        Player from = Bukkit.getPlayer(r.from());
        if (from == null || !from.isOnline()) {
            target.sendMessage(ColorUtil.text(msg("tpa-offline")));
            return;
        }
        // TPA also uses the common 5-second teleport preparation. The player who
        // will actually be teleported must remain still; moving cancels it.
        // Destination is captured at acceptance time, matching normal /home /warp behaviour.
        Player teleporting = r.here() ? target : from;
        Player destination = r.here() ? from : target;
        from.sendMessage(ColorUtil.text(msg("tpa-accepted")));
        target.sendMessage(ColorUtil.text(msg("tpa-accepted")));
        plugin.teleports().teleport(teleporting, destination.getLocation().clone(),
                "TPA: " + destination.getName());
    }

    public void deny(Player target) {
        Request r = pending.remove(target.getUniqueId());
        if (r == null) {
            target.sendMessage(ColorUtil.text(msg("tpa-none")));
            return;
        }
        Player from = Bukkit.getPlayer(r.from());
        if (from != null) {
            from.sendMessage(ColorUtil.text(msg("tpa-denied-other").replace("{name}", target.getName())));
        }
        target.sendMessage(ColorUtil.text(msg("tpa-denied")));
    }

    public void cancel(Player from) {
        boolean removed = false;
        for (Map.Entry<UUID, Request> e : pending.entrySet()) {
            if (e.getValue().from().equals(from.getUniqueId())) {
                pending.remove(e.getKey());
                removed = true;
                Player t = Bukkit.getPlayer(e.getKey());
                if (t != null) t.sendMessage(ColorUtil.text(msg("tpa-cancelled")));
            }
        }
        from.sendMessage(ColorUtil.text(msg(removed ? "tpa-cancelled" : "tpa-none")));
    }

    public void toggleAuto(Player player) {
        boolean on = plugin.settings().toggleTpAuto(player.getUniqueId());
        String chatMsg = msg(on ? "tpauto-on" : "tpauto-off");
        String barMsg = msg(on ? "tpauto-on-bar" : "tpauto-off-bar");
        if (plugin.getConfig().getBoolean("tpa.tpauto-chat-messages", true)) {
            player.sendMessage(ColorUtil.text(chatMsg));
        }
        if (plugin.getConfig().getBoolean("tpa.tpauto-actionbar-messages", true)) {
            player.sendActionBar(ColorUtil.text(barMsg));
        }
        // Beacon activate / deactivate style
        String soundName = plugin.getConfig().getString(
                on ? "tpa.tpauto-sound-on" : "tpa.tpauto-sound-off",
                on ? "BLOCK_BEACON_ACTIVATE" : "BLOCK_BEACON_DEACTIVATE");
        try {
            player.playSound(player.getLocation(), Sound.valueOf(soundName), 1f, on ? 1.0f : 0.9f);
        } catch (Exception ignored) {
            try {
                player.playSound(player.getLocation(),
                        on ? Sound.BLOCK_BEACON_ACTIVATE : Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1f);
            } catch (Exception ignored2) {}
        }
    }

    private boolean isAllowedByPolicy(Player from, Player to) {
        SettingsStore.TpaPolicy policy = plugin.settings().getTpaPolicy(to.getUniqueId());
        return switch (policy) {
            case EVERYONE -> true;
            case FRIENDS -> plugin.friends().friends(to.getUniqueId()).contains(from.getUniqueId());
            case FOLLOWING -> plugin.friends().isFollowing(to.getUniqueId(), from.getUniqueId());
            case FRIENDS_AND_FOLLOWING -> plugin.friends().friends(to.getUniqueId()).contains(from.getUniqueId())
                    || plugin.friends().isFollowing(to.getUniqueId(), from.getUniqueId());
            case NOBODY -> false;
        };
    }

    private String msg(String key) {
        if (plugin.messages() != null) return plugin.messages().get(key);
        return key;
    }
}
