package me.vshop;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public final class PurchaseService {
    private final VShopPlugin plugin;

    public PurchaseService(VShopPlugin plugin) { this.plugin = plugin; }

    public void buy(Player p, ShopEntry e, int amount) {
        try {
            doBuy(p, e, amount);
        } catch (Throwable t) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Purchase crashed for " + p.getName() + " (" + e.name() + " x" + amount + ")", t);
            plugin.send(p, "error-generic");
            plugin.adminDebug(p, t);
        }
    }

    private void doBuy(Player p, ShopEntry e, int amount) {
        Economy eco = plugin.economy();
        if (eco == null) {
            plugin.getLogger().warning("Purchase blocked: no Vault economy provider is registered "
                    + "(install Vault + an economy plugin such as EssentialsX).");
            plugin.notify(p, "no-economy", "actionbar-no-economy", plugin.ph(e, amount));
            plugin.sound(p, "error");
            return;
        }
        Map<String, String> ph = plugin.ph(e, amount);

        // 1) read the balance and calculate the total
        double total = Math.round(e.price() * amount * 100.0) / 100.0;
        double balance = eco.getBalance(p);
        if (plugin.getConfig().getBoolean("debug", false))
            plugin.getLogger().info("[debug] " + p.getName() + " buys " + amount + "x " + e.name() + " total=" + total
                    + " balance=" + balance + " economy=" + eco.getName());

        // 2) not enough money -> stop here, nothing is taken, no item is given
        if (balance + 1e-9 < total) {
            ph.put("balance", plugin.money(balance));
            ph.put("missing", plugin.money(total - balance));
            plugin.notify(p, "not-enough-money", "actionbar-not-enough", ph);
            plugin.sound(p, "error");
            return;
        }
        boolean deny = "DENY".equalsIgnoreCase(plugin.getConfig().getString("full-inventory", "DROP"));
        if (deny && !canFit(p, e.create(1), amount)) {
            plugin.notify(p, "inventory-full", "actionbar-inventory-full", ph);
            plugin.sound(p, "error");
            return;
        }

        // 3) take the money (balance - price). If the economy refuses, no item is given.
        EconomyResponse r = eco.withdrawPlayer(p, total);
        if (!r.transactionSuccess()) {
            ph.put("error", String.valueOf(r.errorMessage));
            plugin.notify(p, "purchase-failed", "actionbar-purchase-failed", ph);
            plugin.sound(p, "error");
            return;
        }

        // 4) give the item. If delivery fails for ANY reason, the money is refunded.
        try {
            Map<Integer, ItemStack> left = p.getInventory().addItem(e.create(amount));
            left.values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
        } catch (Throwable t) {
            eco.depositPlayer(p, total);
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Could not give " + amount + "x " + e.name() + " to " + p.getName() + " - refunded " + total, t);
            plugin.send(p, "purchase-refunded");
            plugin.adminDebug(p, t);
            return;
        }

        // 5) the purchase is DONE. Messages / log / Discord must never undo or block it.
        try {
            ph.put("balance", plugin.money(eco.getBalance(p)));
            plugin.notify(p, "purchased", "actionbar-purchased", ph);
            plugin.sound(p, "purchase");
        } catch (Throwable t) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Purchase went through but the notification failed", t);
        }
        try { logToFile(p, e, amount, total); } catch (Throwable t) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "purchases.log failed", t);
        }
        try { plugin.discord().send(p, e, amount, total); } catch (Throwable t) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Discord notification failed", t);
        }
    }

    private boolean canFit(Player p, ItemStack proto, int amount) {
        int max = proto.getMaxStackSize();
        long free = 0;
        for (ItemStack s : p.getInventory().getStorageContents()) {
            if (s == null || s.getType().isAir()) free += max;
            else if (s.isSimilar(proto)) free += Math.max(0, max - s.getAmount());
        }
        return free >= amount;
    }

    private void logToFile(Player p, ShopEntry e, int amount, double total) {
        if (!plugin.getConfig().getBoolean("log-to-file", true)) return;
        String line = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " "
                + p.getName() + " (" + p.getUniqueId() + ") bought " + amount + "x " + e.name()
                + " [" + e.category() + "] for " + total + System.lineSeparator();
        try {
            Files.writeString(plugin.getDataFolder().toPath().resolve("purchases.log"), line,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not write purchases.log: " + ex.getMessage());
        }
    }
}
