package me.vshop;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;

/** Posts a purchase embed to a Discord webhook. Everything is configurable in config.yml -> discord. */
public final class DiscordNotifier {
    private final VShopPlugin plugin;
    private final HttpClient http = HttpClient.newHttpClient();

    public DiscordNotifier(VShopPlugin plugin) { this.plugin = plugin; }

    public void send(Player p, ShopEntry e, int amount, double total) {
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("discord");
        if (c == null || !c.getBoolean("enabled", false)) return;
        String url = c.getString("webhook-url", "");
        if (url.isBlank() || total < c.getDouble("min-total", 0)) return;

        Map<String, String> ph = new HashMap<>();
        ph.put("player", p.getName());
        ph.put("uuid", p.getUniqueId().toString());
        ph.put("item", e.name());
        ph.put("amount", String.valueOf(amount));
        ph.put("price", plugin.money(e.price()));
        ph.put("total", plugin.money(total));
        ph.put("category", e.category());
        ph.put("rarity", e.rarity());
        ph.put("material", e.materialKey());
        ph.put("server", plugin.getConfig().getString("server-name", "My Server"));

        List<String> top = new ArrayList<>();
        top.add("\"username\":" + q(rep(c.getString("username", "VShop"), ph)));
        String avatar = c.getString("avatar-url", "");
        if (!avatar.isBlank()) top.add("\"avatar_url\":" + q(rep(avatar, ph)));
        String content = c.getString("content", "");
        if (!content.isBlank()) top.add("\"content\":" + q(rep(content, ph)));

        ConfigurationSection em = c.getConfigurationSection("embed");
        if (em != null && em.getBoolean("enabled", true)) {
            List<String> e2 = new ArrayList<>();
            e2.add("\"title\":" + q(rep(em.getString("title", ""), ph)));
            e2.add("\"description\":" + q(rep(em.getString("description", ""), ph)));
            e2.add("\"color\":" + color(em.getString("color", "#57F287")));
            if (em.getBoolean("timestamp", true)) e2.add("\"timestamp\":" + q(Instant.now().toString()));
            String thumb = em.getString("thumbnail", "");
            if (!thumb.isBlank()) e2.add("\"thumbnail\":{\"url\":" + q(rep(thumb, ph)) + "}");
            String footer = em.getString("footer", "");
            if (!footer.isBlank()) e2.add("\"footer\":{\"text\":" + q(rep(footer, ph)) + "}");
            List<String> fields = new ArrayList<>();
            for (Map<?, ?> f : em.getMapList("fields")) {
                fields.add("{\"name\":" + q(rep(String.valueOf(f.get("name")), ph))
                        + ",\"value\":" + q(rep(String.valueOf(f.get("value")), ph))
                        + ",\"inline\":" + Boolean.parseBoolean(String.valueOf(f.get("inline"))) + "}");
            }
            if (!fields.isEmpty()) e2.add("\"fields\":[" + String.join(",", fields) + "]");
            top.add("\"embeds\":[{" + String.join(",", e2) + "}]");
        }

        String json = "{" + String.join(",", top) + "}";
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
        http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept(r -> {
            if (r.statusCode() >= 300) plugin.getLogger().warning("Discord webhook returned " + r.statusCode() + ": " + r.body());
        }).exceptionally(ex -> {
            plugin.getLogger().warning("Discord webhook failed: " + ex.getMessage());
            return null;
        });
    }

    private static String rep(String s, Map<String, String> ph) {
        for (Map.Entry<String, String> e : ph.entrySet()) s = s.replace("{" + e.getKey() + "}", e.getValue());
        return s;
    }

    private static int color(String hex) {
        try { return Integer.parseInt(hex.replace("#", ""), 16); } catch (NumberFormatException ex) { return 0x57F287; }
    }

    private static String q(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> { if (ch < 0x20) sb.append(String.format("\\u%04x", (int) ch)); else sb.append(ch); }
            }
        }
        return sb.append('"').toString();
    }
}
