package me.vshop;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class VShopPlugin extends JavaPlugin {
    private Economy economy;
    private YamlConfiguration messages, dialogCfg, armourCfg;
    private ShopManager shops;
    private ArmourShop armour;
    private Dialogs dialogs;
    private PurchaseService purchases;
    private DiscordNotifier discord;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        for (String f : List.of("messages.yml", "dialogs.yml", "shops/blocks.yml", "shops/ores.yml", "shops/farm.yml",
                "shops/redstone.yml", "shops/books.yml", "shops/armour.yml")) {
            if (!new File(getDataFolder(), f).exists()) saveResource(f, false);
        }
        shops = new ShopManager(this);
        armour = new ArmourShop(this);
        dialogs = new Dialogs(this);
        discord = new DiscordNotifier(this);
        purchases = new PurchaseService(this);
        reloadAll();

        getServer().getPluginManager().registerEvents(armour, this);
        ShopCommand cmd = new ShopCommand(this);
        for (String n : List.of("vshop", "shop", "editarmour")) {
            PluginCommand pc = getCommand(n);
            if (pc != null) { pc.setExecutor(cmd); pc.setTabCompleter(cmd); }
        }
        if (economy() == null) getLogger().warning("No Vault economy provider found yet - purchases will fail until one loads. Run /vshop status to re-check.");
        else getLogger().info("Using economy: " + economy().getName());
    }

    public void reloadAll() {
        reloadConfig();
        messages = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
        dialogCfg = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "dialogs.yml"));
        armourCfg = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "shops/armour.yml"));
        shops.load();
        armour.load();
    }

    public Economy economy() {
        if (economy == null) {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) economy = rsp.getProvider();
        }
        return economy;
    }

    public ShopManager shops() { return shops; }
    public ArmourShop armour() { return armour; }
    public Dialogs dialogs() { return dialogs; }
    public PurchaseService purchases() { return purchases; }
    public DiscordNotifier discord() { return discord; }
    public YamlConfiguration dialogCfg() { return dialogCfg; }
    public YamlConfiguration armourCfg() { return armourCfg; }

    public void send(CommandSender to, String key) { send(to, key, null); }

    public void send(CommandSender to, String key, Map<String, String> ph) {
        String m = messages.getString(key);
        if (m == null) m = "<red>Missing message: " + key;
        to.sendMessage(Text.c(messages.getString("prefix", "") + m, ph));
    }

    public net.kyori.adventure.text.Component msg(String key, Map<String, String> ph) {
        String m = messages.getString(key, "<red>Missing message: " + key);
        return Text.c(m, ph);
    }

    /** Message above the hotbar (action bar). */
    public void actionbar(Player p, String key, Map<String, String> ph) {
        if (getConfig().getBoolean("notifications.actionbar", true)) p.sendActionBar(msg(key, ph));
    }

    /** Chat line (with prefix) + action bar, each toggleable in config.yml -> notifications. */
    public void notify(Player p, String chatKey, String barKey, Map<String, String> ph) {
        if (getConfig().getBoolean("notifications.chat", true)) send(p, chatKey, ph);
        actionbar(p, barKey, ph);
    }

    public String money(double v) {
        String fmt = getConfig().getString("currency.format", "#,##0.##");
        String num = new DecimalFormat(fmt, DecimalFormatSymbols.getInstance(Locale.US)).format(v);
        return getConfig().getString("currency.symbol", "$") + num + getConfig().getString("currency.suffix", "");
    }

    public String rarityColor(String rarity) {
        return getConfig().getString("rarities." + String.valueOf(rarity).toUpperCase(Locale.ROOT), "<white>");
    }

    public Map<String, String> ph(ShopEntry e, int amount) {
        Map<String, String> m = new HashMap<>();
        m.put("name", e.name());
        m.put("price", money(e.price()));
        m.put("amount", String.valueOf(amount));
        m.put("total", money(e.price() * amount));
        m.put("category", e.category());
        m.put("rarity", e.rarity());
        m.put("rarity_color", rarityColor(e.rarity()));
        m.put("material", e.materialKey());
        return m;
    }

    public void sound(Player p, String key) {
        String path = "sounds." + key;
        String s = getConfig().getString(path + ".sound", "");
        if (s.isBlank()) return;
        p.playSound(p.getLocation(), s, (float) getConfig().getDouble(path + ".volume", 1.0),
                (float) getConfig().getDouble(path + ".pitch", 1.0));
    }

    /** Shows the exception to staff (vshop.reload) in-game so they don't need the console to debug. */
    public void adminDebug(CommandSender to, Throwable t) {
        if (to.hasPermission("vshop.reload"))
            to.sendMessage(Text.c("<dark_gray>[debug] <gray>{err}", Text.map("err", String.valueOf(t))));
    }

    public void sync(Runnable r) {
        if (Bukkit.isPrimaryThread()) r.run();
        else Bukkit.getScheduler().runTask(this, r);
    }
}
