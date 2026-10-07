package me.vshop;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;

import java.io.File;
import java.util.*;

/** Loads every shops/*.yml (except armour/books) as an item category, plus shops/books.yml. */
public final class ShopManager {
    private final VShopPlugin plugin;
    private final Map<String, Category> categories = new LinkedHashMap<>();
    private final List<BookCategory> books = new ArrayList<>();
    private final List<ShopEntry> autoBlocks = new ArrayList<>();
    private final List<ShopEntry> autoItems = new ArrayList<>();
    private final Set<String> searchAllCats = new HashSet<>();

    public ShopManager(VShopPlugin plugin) { this.plugin = plugin; }

    public Collection<Category> categories() { return categories.values(); }
    public List<BookCategory> books() { return books; }
    /** Obtainable BLOCKS not listed in a shop file (searched inside the Blocks section). */
    public List<ShopEntry> autoBlocks() { return autoBlocks; }
    /** Every obtainable ITEM not listed in a shop file (searched from the main menu / /shop search). */
    public List<ShopEntry> autoItems() { return autoItems; }
    public boolean searchesAll(String categoryId) { return !autoBlocks.isEmpty() && searchAllCats.contains(categoryId); }

    public Category find(String key) {
        if (key == null) return null;
        String k = key.toLowerCase(Locale.ROOT);
        Category c = categories.get(k);
        if (c != null) return c;
        for (Category cat : categories.values()) if (cat.aliases().contains(k)) return cat;
        return null;
    }

    public void load() {
        categories.clear();
        books.clear();
        searchAllCats.clear();
        loadCategories();
        loadBooks();
        loadAutoBlocks();
        plugin.getLogger().info("Loaded " + categories.size() + " item categories and " + books.size() + " book categories.");
    }

    private void loadCategories() {
        File[] files = new File(plugin.getDataFolder(), "shops").listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return;
        Arrays.sort(files);
        for (File f : files) {
            String id = f.getName().substring(0, f.getName().length() - 4).toLowerCase(Locale.ROOT);
            if (id.equals("armour") || id.equals("books")) continue;
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            List<String> aliases = new ArrayList<>();
            for (String a : y.getStringList("aliases")) aliases.add(a.toLowerCase(Locale.ROOT));
            List<ShopEntry> entries = new ArrayList<>();
            ConfigurationSection items = y.getConfigurationSection("items");
            if (items != null) {
                for (String key : items.getKeys(false)) {
                    ConfigurationSection s = items.getConfigurationSection(key);
                    if (s == null || !s.getBoolean("enabled", true)) continue;
                    Material m = Material.matchMaterial(s.getString("material", key));
                    if (m == null || !m.isItem()) {
                        plugin.getLogger().warning(f.getName() + ": unknown/invalid material for '" + key + "'");
                        continue;
                    }
                    entries.add(ShopEntry.material(id + ":" + key, s.getString("name", Text.pretty(m.name())), m,
                            s.getDouble("price", 1.0), s.getString("rarity", "COMMON"), id));
                }
            }
            if (y.getBoolean("search-all-blocks", false)) searchAllCats.add(id);
            categories.put(id, new Category(id, y.getString("title", "<white>" + Text.pretty(id)),
                    Math.max(1, y.getInt("columns", 3)), y.getInt("button-width", 150),
                    y.getBoolean("search", true), aliases, entries));
        }
    }

    private void loadBooks() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "shops/books.yml"));
        ConfigurationSection cats = y.getConfigurationSection("categories");
        if (cats == null) return;
        for (String cid : cats.getKeys(false)) {
            ConfigurationSection cs = cats.getConfigurationSection(cid);
            if (cs == null) continue;
            List<BookCategory.Node> nodes = new ArrayList<>();
            ConfigurationSection es = cs.getConfigurationSection("entries");
            if (es != null) {
                for (String eid : es.getKeys(false)) {
                    ConfigurationSection s = es.getConfigurationSection(eid);
                    if (s == null || !s.getBoolean("enabled", true)) continue;
                    Enchantment ench = enchant(s.getString("enchantment", eid));
                    if (ench == null) {
                        plugin.getLogger().warning("books.yml: unknown enchantment '" + s.getString("enchantment", eid)
                                + "' (entry " + cid + "/" + eid + ") - skipped. Does your server version have it?");
                        continue;
                    }
                    String rarity = s.getString("rarity", "COMMON");
                    String base = s.getString("name", Text.pretty(eid));
                    String uid = cid + "_" + eid;
                    ConfigurationSection ls = s.getConfigurationSection("levels");
                    if (ls != null) {
                        List<ShopEntry> variants = new ArrayList<>();
                        for (String lk : ls.getKeys(false)) {
                            try {
                                int lvl = Integer.parseInt(lk);
                                variants.add(ShopEntry.book(uid + "_" + lvl, base + " " + Text.roman(lvl), ench, lvl,
                                        ls.getDouble(lk), rarity, "books"));
                            } catch (NumberFormatException ex) {
                                plugin.getLogger().warning("books.yml: bad level '" + lk + "' in " + uid);
                            }
                        }
                        if (!variants.isEmpty()) nodes.add(new BookCategory.Node(eid, base, true, variants));
                    } else {
                        int lvl = s.getInt("level", 1);
                        nodes.add(new BookCategory.Node(eid, base, false,
                                List.of(ShopEntry.book(uid, base, ench, lvl, s.getDouble("price", 100), rarity, "books"))));
                    }
                }
            }
            books.add(new BookCategory(cid, cs.getString("name", Text.pretty(cid)), nodes));
        }
    }

    /** Builds a purchasable entry for every obtainable item so search can find (and sell) anything. */
    private void loadAutoBlocks() {
        autoBlocks.clear();
        autoItems.clear();
        ConfigurationSection c = plugin.getConfig().getConfigurationSection("searchable-items");
        if (c == null || !c.getBoolean("enabled", true)) return;

        Set<Material> configured = new HashSet<>();
        for (Category cat : categories.values())
            for (ShopEntry e : cat.entries()) if (e.material() != null) configured.add(e.material());

        Set<String> excluded = new HashSet<>();
        for (String x : c.getStringList("excluded")) excluded.add(x.toUpperCase(Locale.ROOT));
        List<String> patterns = new ArrayList<>();
        for (String x : c.getStringList("excluded-patterns")) patterns.add(x.toUpperCase(Locale.ROOT));
        ConfigurationSection overrides = c.getConfigurationSection("price-overrides");
        List<Map<?, ?>> rules = c.getMapList("price-rules");
        double defPrice = c.getDouble("default-price", 50);
        String defRarity = c.getString("default-rarity", "COMMON");

        for (Material m : Material.values()) {
            if (m.isLegacy() || m.isAir() || !m.isItem() || configured.contains(m)) continue;
            String n = m.name();
            if (excluded.contains(n)) continue;
            boolean skip = false;
            for (String pat : patterns) if (n.contains(pat)) { skip = true; break; }
            if (skip) continue;

            double price = defPrice;
            String rarity = defRarity;
            if (overrides != null && overrides.contains(n)) {
                price = overrides.getDouble(n);
            } else {
                for (Map<?, ?> r : rules) {
                    Object contains = r.get("contains");
                    if (contains != null && n.contains(String.valueOf(contains).toUpperCase(Locale.ROOT))) {
                        Object pr = r.get("price");
                        if (pr instanceof Number num) price = num.doubleValue();
                        Object ra = r.get("rarity");
                        if (ra != null) rarity = String.valueOf(ra);
                        break;
                    }
                }
            }
            autoItems.add(ShopEntry.material("auto:" + n.toLowerCase(Locale.ROOT), Text.pretty(n), m, price, rarity,
                    m.isBlock() ? "blocks" : "items"));
        }
        autoItems.sort(Comparator.comparing(ShopEntry::name));
        for (ShopEntry e : autoItems) if (e.material().isBlock()) autoBlocks.add(e);
        plugin.getLogger().info("Search can find " + autoItems.size() + " extra obtainable items ("
                + autoBlocks.size() + " of them blocks).");
    }

    private Enchantment enchant(String key) {
        NamespacedKey nk = NamespacedKey.fromString(key.toLowerCase(Locale.ROOT));
        return nk == null ? null : Registry.ENCHANTMENT.get(nk);
    }
}
