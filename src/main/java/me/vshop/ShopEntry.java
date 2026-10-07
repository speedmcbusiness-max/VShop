package me.vshop;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.Locale;

/** One purchasable thing: a material, an enchanted book, or a fixed ItemStack (armour chest). */
public final class ShopEntry {
    private final String id, name, rarity, category;
    private final double price;
    private final ItemStack template;     // material / stack entries
    private final Enchantment enchant;    // book entries
    private final int level;
    private final boolean amountSelectable;

    private ShopEntry(String id, String name, double price, String rarity, String category,
                      ItemStack template, Enchantment enchant, int level, boolean amountSelectable) {
        this.id = id; this.name = name; this.price = price; this.rarity = rarity; this.category = category;
        this.template = template; this.enchant = enchant; this.level = level; this.amountSelectable = amountSelectable;
    }

    public static ShopEntry material(String id, String name, Material m, double price, String rarity, String cat) {
        return new ShopEntry(id, name, price, rarity, cat, new ItemStack(m), null, 0, true);
    }

    public static ShopEntry book(String id, String name, Enchantment e, int level, double price, String rarity, String cat) {
        return new ShopEntry(id, name, price, rarity, cat, null, e, level, false);
    }

    public static ShopEntry stack(String id, String name, ItemStack s, double price, String rarity, String cat) {
        return new ShopEntry(id, name, price, rarity, cat, s.clone(), null, 0, false);
    }

    public ItemStack create(int amount) {
        if (enchant != null) {
            ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
            meta.addStoredEnchant(enchant, level, true);
            book.setItemMeta(meta);
            return book;
        }
        ItemStack s = template.clone();
        s.setAmount(Math.max(1, amount));
        return s;
    }

    public String id() { return id; }
    public String name() { return name; }
    public double price() { return price; }
    public String rarity() { return rarity; }
    public String category() { return category; }
    public Material material() { return template == null ? null : template.getType(); }
    public boolean isBook() { return enchant != null; }
    public boolean amountSelectable() { return amountSelectable; }
    public String materialKey() {
        return enchant != null ? "enchanted_book" : template.getType().name().toLowerCase(Locale.ROOT);
    }
}
