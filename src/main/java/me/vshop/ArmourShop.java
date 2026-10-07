package me.vshop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

/** 53 item slots (0-52) + slot 53 = close button. Admins edit it in-game with /vshop editarmour. */
public final class ArmourShop implements Listener {
    private static final int SIZE = 54, ITEM_SLOTS = 53, CLOSE_SLOT = 53;

    public record Slot(ItemStack item, double price) {}

    public static final class Holder implements InventoryHolder {
        final boolean edit;
        Inventory inv;
        Holder(boolean edit) { this.edit = edit; }
        @Override public Inventory getInventory() { return inv; }
    }

    private final VShopPlugin plugin;
    private final File dataFile;
    private final TreeMap<Integer, Slot> slots = new TreeMap<>();

    public ArmourShop(VShopPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "armour-items.yml");
    }

    // ------------------------------------------------------------------ data

    public void load() {
        slots.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection s = y.getConfigurationSection("slots");
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            try {
                int i = Integer.parseInt(k);
                ItemStack it = s.getItemStack(k + ".item");
                if (it != null && i >= 0 && i < ITEM_SLOTS) slots.put(i, new Slot(it, s.getDouble(k + ".price")));
            } catch (NumberFormatException ignored) { }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        slots.forEach((i, sl) -> {
            y.set("slots." + i + ".item", sl.item());
            y.set("slots." + i + ".price", sl.price());
        });
        try { y.save(dataFile); } catch (IOException e) { plugin.getLogger().warning("Could not save armour-items.yml: " + e.getMessage()); }
    }

    public void setPrice(int slot, double price) {
        Slot old = slots.get(slot);
        if (old == null) return;
        slots.put(slot, new Slot(old.item(), price));
        save();
    }

    private ShopEntry entryFor(int slot) {
        Slot s = slots.get(slot);
        if (s == null) return null;
        ItemMeta meta = s.item().getItemMeta();
        String name = meta != null && meta.hasDisplayName() ? Text.plain(meta.displayName())
                : Text.pretty(s.item().getType().name());
        return ShopEntry.stack("armour_" + slot, name, s.item(), s.price(),
                plugin.armourCfg().getString("rarity", "RARE"), "armour");
    }

    // ------------------------------------------------------------------ gui

    public void open(Player p, boolean edit) {
        ConfigurationSection c = plugin.armourCfg();
        Holder h = new Holder(edit);
        Inventory inv = Bukkit.createInventory(h, SIZE,
                Text.c(edit ? c.getString("edit-title", "Armour Shop (Editing)") : c.getString("title", "Armour Shop")));
        h.inv = inv;
        render(inv, edit);
        p.openInventory(inv);
    }

    private void render(Inventory inv, boolean edit) {
        ConfigurationSection c = plugin.armourCfg();
        inv.clear();
        Material filler = Material.matchMaterial(c.getString("filler", "AIR"));
        for (int i = 0; i < ITEM_SLOTS; i++) {
            Slot s = slots.get(i);
            if (s != null) inv.setItem(i, display(i, s, edit));
            else if (!edit && filler != null && !filler.isAir()) {
                ItemStack f = new ItemStack(filler);
                ItemMeta m = f.getItemMeta();
                m.setHideTooltip(true);
                f.setItemMeta(m);
                inv.setItem(i, f);
            }
        }
        Material cm = Material.matchMaterial(c.getString("close-item.material", "BARRIER"));
        ItemStack close = new ItemStack(cm == null ? Material.BARRIER : cm);
        ItemMeta m = close.getItemMeta();
        m.displayName(Text.c(c.getString("close-item.name", "<red>Close")).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        close.setItemMeta(m);
        inv.setItem(CLOSE_SLOT, close);
    }

    private ItemStack display(int slot, Slot s, boolean edit) {
        ItemStack it = s.item().clone();
        ItemMeta meta = it.getItemMeta();
        List<Component> lore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        var ph = plugin.ph(entryFor(slot), 1);
        for (String line : plugin.armourCfg().getStringList(edit ? "edit-lore" : "price-lore"))
            lore.add(Text.c(line, ph).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        meta.lore(lore);
        it.setItemMeta(meta);
        return it;
    }

    private void later(Runnable r) { Bukkit.getScheduler().runTask(plugin, r); }

    // ------------------------------------------------------------------ events

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder h)) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR) { e.setCancelled(true); return; }
        Inventory clicked = e.getClickedInventory();
        if (clicked == null) return;

        if (!clicked.equals(top)) {                      // player's own inventory
            if (!h.edit) { e.setCancelled(true); return; }
            if (e.isShiftClick()) {                      // shift-click = add to first free slot
                e.setCancelled(true);
                ItemStack it = e.getCurrentItem();
                if (it == null || it.getType().isAir()) return;
                for (int i = 0; i < ITEM_SLOTS; i++) {
                    if (!slots.containsKey(i)) { addItem(p, top, i, it); return; }
                }
            }
            return;
        }

        e.setCancelled(true);
        int slot = e.getSlot();
        if (slot == CLOSE_SLOT) { later(p::closeInventory); return; }
        if (slot >= ITEM_SLOTS) return;

        if (h.edit) {
            ItemStack cursor = e.getCursor();
            boolean has = cursor != null && !cursor.getType().isAir();
            if (has) addItem(p, top, slot, cursor);
            else if (slots.containsKey(slot)) {
                if (e.isRightClick()) { slots.remove(slot); save(); render(top, true); }
                else promptPrice(p, slot);
            }
        } else if (slots.containsKey(slot)) {
            ShopEntry entry = entryFor(slot);
            later(() -> {
                p.closeInventory();
                plugin.dialogs().openConfirm(p, entry, 1, () -> open(p, false));
            });
        }
    }

    private void addItem(Player p, Inventory top, int slot, ItemStack source) {
        ItemStack copy = source.clone();
        copy.setAmount(1);
        Slot old = slots.get(slot);
        double price = old != null ? old.price() : plugin.armourCfg().getDouble("default-price", 1000.0);
        slots.put(slot, new Slot(copy, price));
        save();
        render(top, true);
        promptPrice(p, slot);
    }

    private void promptPrice(Player p, int slot) {
        ShopEntry entry = entryFor(slot);
        later(() -> {
            p.closeInventory();
            plugin.dialogs().openPriceEditor(p, slot, entry);
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder)) return;
        for (int raw : e.getRawSlots()) if (raw < top.getSize()) { e.setCancelled(true); return; }
    }
}
