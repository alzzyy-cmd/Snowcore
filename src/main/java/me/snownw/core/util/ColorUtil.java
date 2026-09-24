package me.snownw.core.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import java.util.HashMap;
import java.util.Map;

public final class ColorUtil {
    // &#RRGGBB ve &x&F&F&0&0&0&0 hex biçimlerini de destekleyen serileştirici
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    private static final java.util.regex.Pattern LEGACY_PATTERN =
            java.util.regex.Pattern.compile("(?i)[&§][0-9A-FK-ORX]|(?i)&#([0-9A-F]{6})|&x(&[0-9A-F]){6}");
    private static final Map<Character, Character> REMAP = new HashMap<>();

    private ColorUtil() {}

    public static void configure(org.bukkit.configuration.ConfigurationSection section) {
        REMAP.clear();
        if (section == null || !section.getBoolean("enabled", true)) return;
        org.bukkit.configuration.ConfigurationSection map = section.getConfigurationSection("legacy-map");
        if (map == null) return;
        for (String key : map.getKeys(false)) {
            if (key.length() != 1) continue;
            String value = map.getString(key, key);
            if (value != null && value.length() == 1) REMAP.put(Character.toLowerCase(key.charAt(0)), Character.toLowerCase(value.charAt(0)));
        }
    }

    /** &#RRGGBB hex + & renk kodlarını Component'e çevirir. Asla italik yapmaz. */
    public static Component text(String s) {
        if (s == null || s.isEmpty()) return Component.empty();
        String clean = s.replace("&o", "").replace("§o", "")
                .replace("&O", "").replace("§O", "");
        StringBuilder out = new StringBuilder(clean.length());
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < clean.length()) {
                char code = Character.toLowerCase(clean.charAt(i + 1));
                Character mapped = REMAP.get(code);
                out.append(c).append(mapped == null ? clean.charAt(i + 1) : mapped);
                i++;
            } else out.append(c);
        }
        clean = out.toString();
        return LEGACY.deserialize(clean).decoration(TextDecoration.ITALIC, false);
    }

    /** Renk kodlarını silen düz metin (tabela gibi String isteyen yerler için). */
    public static String strip(String s) {
        if (s == null) return "";
        return LEGACY_PATTERN.matcher(s).replaceAll("");
    }

    /** Legacy & metni Sprite → § biçime çevirir (tabela satırları). */
    public static String legacySection(String s) {
        if (s == null) return "";
        return s.replace('&', '§');
    }

    public static String replace(String s, String k, String v) {
        return s == null ? "" : s.replace("{" + k + "}", v == null ? "" : v);
    }
}
