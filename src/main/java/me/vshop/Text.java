package me.vshop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.*;

/** MiniMessage helper. {placeholders} are escaped (safe for player input) except trusted RAW keys. */
public final class Text {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Set<String> RAW = Set.of("rarity_color");

    private Text() {}

    public static Component c(String template) { return c(template, null); }

    public static Component c(String template, Map<String, String> ph) {
        String t = template == null ? "" : template;
        List<TagResolver> resolvers = new ArrayList<>();
        if (ph != null) {
            for (Map.Entry<String, String> e : ph.entrySet()) {
                String k = e.getKey();
                String v = e.getValue() == null ? "" : e.getValue();
                if (RAW.contains(k)) {
                    t = t.replace("{" + k + "}", v);
                } else {
                    t = t.replace("{" + k + "}", "<" + k + ">");
                    resolvers.add(Placeholder.unparsed(k, v));
                }
            }
        }
        return MM.deserialize(t, TagResolver.resolver(resolvers));
    }

    public static String plain(Component c) { return PlainTextComponentSerializer.plainText().serialize(c); }

    public static Map<String, String> map(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    public static String pretty(String s) {
        StringBuilder sb = new StringBuilder();
        for (String part : s.toLowerCase(Locale.ROOT).split("[_ ]+")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    public static String roman(int n) {
        if (n <= 0 || n > 3999) return String.valueOf(n);
        int[] v = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] r = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length; i++) while (n >= v[i]) { n -= v[i]; sb.append(r[i]); }
        return sb.toString();
    }
}
