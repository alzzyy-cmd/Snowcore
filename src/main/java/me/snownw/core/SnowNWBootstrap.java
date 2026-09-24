package me.snownw.core;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.DialogKeys;
import io.papermc.paper.registry.keys.tags.DialogTagKeys;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.List;

/**
 * ESC "SnowNWSMP" = same main menu as /menu (Economy SMP style 2-column grid).
 */
public final class SnowNWBootstrap implements PluginBootstrap {

    public static final TypedKey<Dialog> PAUSE_DIALOG =
            DialogKeys.create(Key.key("snownwcore", "pause_menu"));

    private static Component t(String s) {
        return Component.text(s).decoration(TextDecoration.ITALIC, false);
    }

    private static ActionButton btn(String label, String tip, String action) {
        return ActionButton.create(
                t(label).color(NamedTextColor.WHITE),
                t(tip).color(NamedTextColor.GRAY),
                120,
                DialogAction.customClick(Key.key("snownwcore", action.toLowerCase()), null)
        );
    }

    @Override
    public void bootstrap(BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(
                RegistryEvents.DIALOG.compose().newHandler(event -> {
                    // Same grid as screenshot right panel
                    List<ActionButton> buttons = List.of(
                            btn("Evler", "Evlerini yönet", "homes/0"),
                            btn("Fiyatlar", "Eşya fiyatları", "worth"),
                            btn("Açık Artırma", "Oyuncu ilanları", "ah"),
                            btn("Arkadaşlar", "Arkadaş sistemi", "friends"),
                            btn("Işınlanma", "Spawn, ev, warp, RTP ve TPA", "teleport/menu"),
                            btn("İstatistikler", "İstatistiklerin", "stats"),
                            btn("Sıralamalar", "En iyi oyuncular", "lb/menu"),
                            btn("Ayarlar", "Oyuncu ayarları", "settings")
                    );
                    event.registry().register(PAUSE_DIALOG, builder -> builder
                            .base(DialogBase.builder(t("SnowNW").color(NamedTextColor.WHITE))
                                    .externalTitle(t("SnowNW").color(NamedTextColor.WHITE))
                                    .canCloseWithEscape(true)
                                    .pause(false)
                                    .afterAction(DialogBase.DialogAfterAction.CLOSE)
                                    .body(List.of(
                                            DialogBody.plainMessage(
                                                    t("SnowNW kontrol merkezi").color(NamedTextColor.GRAY), 280)
                                    ))
                                    .build())
                            .type(DialogType.multiAction(buttons).columns(2).build()));
                })
        );

        context.getLifecycleManager().registerEventHandler(
                LifecycleEvents.TAGS.postFlatten(RegistryKey.DIALOG).newHandler(event -> {
                    event.registrar().addToTag(
                            DialogTagKeys.PAUSE_SCREEN_ADDITIONS,
                            List.of(PAUSE_DIALOG)
                    );
                })
        );
    }
}
