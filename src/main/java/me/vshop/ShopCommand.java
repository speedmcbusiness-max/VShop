package me.vshop;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.Consumer;

public final class ShopCommand implements CommandExecutor, TabCompleter {
    private final VShopPlugin plugin;

    public ShopCommand(VShopPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (cmd.getName().equalsIgnoreCase("editarmour")) { editArmour(s); return true; }
        if (a.length == 0) { open(s, p -> plugin.dialogs().openMain(p)); return true; }

        String sub = a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                if (!s.hasPermission("vshop.reload")) plugin.send(s, "no-permission");
                else { plugin.reloadAll(); plugin.send(s, "reloaded"); }
            }
            case "status" -> status(s);
            case "editarmour" -> editArmour(s);
            case "armour", "armor" -> open(s, p -> plugin.armour().open(p, false));
            case "books", "book" -> open(s, p -> plugin.dialogs().openBooks(p));
            case "search" -> {
                String q = String.join(" ", Arrays.copyOfRange(a, 1, a.length));
                open(s, p -> plugin.dialogs().openSearch(p, q));
            }
            default -> {
                Category c = plugin.shops().find(sub);
                if (c == null) plugin.send(s, "unknown-category", Text.map("input", sub));
                else open(s, p -> plugin.dialogs().openCategory(p, c, null));
            }
        }
        return true;
    }

    /** /vshop status - quick self-check when "players can't buy". */
    private void status(CommandSender s) {
        if (!s.hasPermission("vshop.reload")) { plugin.send(s, "no-permission"); return; }
        var eco = plugin.economy();
        s.sendMessage(Text.c("<gold>VShop status"));
        s.sendMessage(Text.c("<gray>Economy: " + (eco == null ? "<red>NONE FOUND (players cannot buy!)" : "<green>" + eco.getName())));
        if (eco != null && s instanceof Player p)
            s.sendMessage(Text.c("<gray>Your balance: <white>" + plugin.money(eco.getBalance(p))));
        int items = plugin.shops().categories().stream().mapToInt(c -> c.entries().size()).sum();
        s.sendMessage(Text.c("<gray>Listed items: <white>" + items + " <gray>| searchable extras: <white>"
                + plugin.shops().autoItems().size() + " <gray>| book categories: <white>" + plugin.shops().books().size()));
    }

    private void editArmour(CommandSender s) {
        if (!s.hasPermission("vshop.editarmour")) { plugin.send(s, "no-permission"); return; }
        if (!(s instanceof Player p)) { plugin.send(s, "players-only"); return; }
        plugin.armour().open(p, true);
        plugin.send(p, "armour-edit-help");
    }

    private void open(CommandSender s, Consumer<Player> action) {
        if (!(s instanceof Player p)) { plugin.send(s, "players-only"); return; }
        if (!p.hasPermission("vshop.use")) { plugin.send(p, "no-permission"); return; }
        action.accept(p);
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        if (cmd.getName().equalsIgnoreCase("editarmour") || a.length != 1) return List.of();
        List<String> opts = new ArrayList<>(List.of("armour", "books", "search"));
        plugin.shops().categories().forEach(c -> { opts.add(c.id()); opts.addAll(c.aliases()); });
        if (s.hasPermission("vshop.reload")) { opts.add("reload"); opts.add("status"); }
        if (s.hasPermission("vshop.editarmour")) opts.add("editarmour");
        String pre = a[0].toLowerCase(Locale.ROOT);
        return opts.stream().filter(o -> o.startsWith(pre)).sorted().toList();
    }
}
