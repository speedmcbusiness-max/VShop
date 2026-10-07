package me.vshop;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.*;

/** Every dialog in the shop. All text/labels/columns come from dialogs.yml (and shops/*.yml titles). */
public final class Dialogs {
    private static final ClickCallback.Options OPTS = ClickCallback.Options.builder()
            .uses(ClickCallback.UNLIMITED_USES).lifetime(Duration.ofHours(1)).build();

    private final VShopPlugin plugin;

    public Dialogs(VShopPlugin plugin) { this.plugin = plugin; }

    // ------------------------------------------------------------------ helpers

    private ConfigurationSection d() { return plugin.dialogCfg(); }

    private ConfigurationSection sec(String path) {
        ConfigurationSection s = d().getConfigurationSection(path);
        return s != null ? s : d().createSection(path);
    }

    private String common(String key, String def) { return d().getString("common." + key, def); }

    private int width(ConfigurationSection s) { return s.getInt("button-width", d().getInt("common.button-width", 150)); }

    private ActionButton btn(String label, int width, Map<String, String> ph, DialogActionCallback cb) {
        ActionButton.Builder b = ActionButton.builder(Text.c(label, ph)).width(width);
        if (cb != null) b.action(DialogAction.customClick(cb, OPTS));
        return b.build();
    }

    private DialogActionCallback run(Runnable r) {
        return (view, audience) -> plugin.sync(() -> guard(audience, r));
    }

    /** A crash inside a click must never be silent: log it and tell the player. */
    private void guard(net.kyori.adventure.audience.Audience who, Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Error while handling a shop dialog click", t);
            who.sendMessage(plugin.msg("error-generic", null));
            if (who instanceof org.bukkit.command.CommandSender cs) plugin.adminDebug(cs, t);
        }
    }

    private ActionButton closeBtn(ConfigurationSection s) {
        return btn(s.getString("close-label", common("close-label", "<red>Close")), width(s), null, null);
    }

    private ActionButton backBtn(int w, Runnable back) {
        return btn(common("back-label", "<yellow>« Back"), w, null, run(back));
    }

    private List<DialogBody> body(List<String> lines, Map<String, String> ph) {
        List<DialogBody> out = new ArrayList<>();
        for (String l : lines) out.add(DialogBody.plainMessage(Text.c(l, ph)));
        return out;
    }

    private DialogInput searchInput(String initial) {
        int max = Math.max(1, d().getInt("common.search-max-length", 32));
        String init = initial == null ? "" : initial;
        if (init.length() > max) init = init.substring(0, max);       // Paper rejects initial > maxLength
        return DialogInput.text("search", Text.c(common("search-input-label", "Search")))
                .width(d().getInt("common.search-input-width", 250))
                .initial(init)
                .maxLength(max)
                .build();
    }

    private ActionButton searchBtn(int w, java.util.function.Consumer<String> onSearch) {
        return btn(common("search-label", "<aqua>Search"), w, null, (view, aud) -> {
            String q = view.getText("search");
            plugin.sync(() -> onSearch.accept(q));
        });
    }

    /**
     * pause(false) is REQUIRED: Minecraft rejects dialogs that pause the game (the default) when their
     * after-action is NONE - which the Amount dialog uses. That rejection crashed every item click.
     */
    private Dialog make(Component title, List<DialogBody> body, List<DialogInput> inputs, DialogType type,
                        DialogBase.DialogAfterAction after) {
        DialogBase base = DialogBase.builder(title).canCloseWithEscape(true).pause(false)
                .body(body).inputs(inputs).afterAction(after).build();
        return Dialog.create(f -> f.empty().base(base).type(type));
    }

    private void showMulti(Player p, Component title, List<DialogBody> body, List<DialogInput> inputs,
                           List<ActionButton> buttons, ActionButton exit, int columns,
                           DialogBase.DialogAfterAction after) {
        if (buttons.isEmpty()) {                                   // Paper rejects an empty action list
            buttons = new ArrayList<>(buttons);
            buttons.add(btn(common("close-label", "<red>Close"), 150, null, null));
        }
        DialogType type = DialogType.multiAction(buttons, exit, Math.max(1, columns));
        Dialog dialog;
        try {
            dialog = make(title, body, inputs, type, after);
        } catch (RuntimeException ex) {                       // never let a bad after-action break a shop click
            plugin.getLogger().warning("Dialog rejected with after-action " + after + " (" + ex + ") - retrying with CLOSE");
            dialog = make(title, body, inputs, type, DialogBase.DialogAfterAction.CLOSE);
        }
        p.showDialog(dialog);
    }

    private void showConfirm(Player p, Component title, List<DialogBody> body, List<DialogInput> inputs,
                             ActionButton yes, ActionButton no) {
        p.showDialog(make(title, body, inputs, DialogType.confirmation(yes, no), DialogBase.DialogAfterAction.CLOSE));
    }

    private List<ShopEntry> filter(List<ShopEntry> in, String q) {
        if (q == null || q.isBlank()) return in;
        String[] terms = q.toLowerCase(Locale.ROOT).trim().split("\\s+");
        List<ShopEntry> out = new ArrayList<>();
        outer:
        for (ShopEntry e : in) {
            String hay = (e.name() + " " + e.materialKey()).toLowerCase(Locale.ROOT);
            for (String t : terms) if (!hay.contains(t)) continue outer;
            out.add(e);
        }
        return out;
    }

    private void addEntryButtons(Player p, List<ActionButton> out, List<ShopEntry> list, int w, Runnable back) {
        int max = d().getInt("common.max-results", 60);
        String label = common("entry-label", "{rarity_color}{name} <dark_gray>| <green>{price}");
        for (ShopEntry e : list.subList(0, Math.min(max, list.size()))) {
            out.add(btn(label, w, plugin.ph(e, 1), run(() -> select(p, e, back))));
        }
    }

    private void select(Player p, ShopEntry e, Runnable back) {
        if (e.amountSelectable()) openAmount(p, e, 1, back);
        else openConfirm(p, e, 1, back);
    }

    private List<String> noResultsBody(String query) {
        return List.of(common("no-results", "<red>Nothing found for <white>{query}"));
    }

    // ------------------------------------------------------------------ main menu

    public void openMain(Player p) {
        ConfigurationSection s = sec("main");
        int w = width(s);
        List<ActionButton> buttons = new ArrayList<>();
        boolean search = false;
        final boolean bar = s.getBoolean("search-bar", true);

        for (Map<?, ?> m : s.getMapList("buttons")) {
            String type = String.valueOf(m.get("type")).toLowerCase(Locale.ROOT);
            Object lo = m.get("label");
            String label = lo == null ? type : String.valueOf(lo);
            switch (type) {
                case "search" -> {
                    search = true;
                    String sl = lo == null ? common("search-label", "<aqua>Search") : label;
                    buttons.add(btn(sl, w, null, (view, aud) -> {
                        String q = view.getText("search");
                        plugin.sync(() -> openSearch(p, q));
                    }));
                }
                case "category" -> {
                    Category c = plugin.shops().find(String.valueOf(m.get("id")));
                    if (c == null) plugin.getLogger().warning("dialogs.yml main button: unknown category " + m.get("id"));
                    else buttons.add(btn(label, w, null, (view, aud) -> {
                        String q = typed(view, bar, s);
                        plugin.sync(() -> { if (q != null) openSearch(p, q); else openCategory(p, c, null); });
                    }));
                }
                case "armour" -> buttons.add(btn(label, w, null, (view, aud) -> {
                    String q = typed(view, bar, s);
                    plugin.sync(() -> { if (q != null) openSearch(p, q); else plugin.armour().open(p, false); });
                }));
                case "books" -> buttons.add(btn(label, w, null, (view, aud) -> {
                    String q = typed(view, bar, s);
                    plugin.sync(() -> { if (q != null) openSearch(p, q); else openBooks(p); });
                }));
                case "sell" -> buttons.add(btn(label, w, null,
                        run(() -> p.performCommand(plugin.getConfig().getString("sell-command", "sell")))));
                case "command" -> buttons.add(btn(label, w, null,
                        run(() -> p.performCommand(String.valueOf(m.get("command"))))));
                default -> plugin.getLogger().warning("dialogs.yml main: unknown button type " + type);
            }
        }
        if (buttons.isEmpty()) { plugin.getLogger().warning("dialogs.yml main.buttons is empty"); return; }

        List<DialogInput> inputs = (bar || search) ? List.of(searchInput("")) : List.of();
        showMulti(p, Text.c(s.getString("title", "Shop")), body(s.getStringList("body"), null), inputs,
                buttons, closeBtn(s), s.getInt("columns", 2), DialogBase.DialogAfterAction.CLOSE);
    }

    /** Text typed in the main search bar (null if empty/disabled). Typing then clicking a shop button = global search. */
    private String typed(DialogResponseView view, boolean bar, ConfigurationSection s) {
        if (!(bar || search_btn_present(s)) || !s.getBoolean("search-on-any-button", true)) return null;
        String q = view.getText("search");
        return q == null || q.isBlank() ? null : q.trim();
    }

    private boolean search_btn_present(ConfigurationSection s) {
        for (Map<?, ?> m : s.getMapList("buttons"))
            if ("search".equalsIgnoreCase(String.valueOf(m.get("type")))) return true;
        return false;
    }

    // ------------------------------------------------------------------ item categories + search

    public void openCategory(Player p, Category c, String query) {
        int w = c.buttonWidth();
        List<ShopEntry> pool = c.entries();
        if (query != null && !query.isBlank() && plugin.shops().searchesAll(c.id())) {
            pool = new ArrayList<>();
            for (ShopEntry e : c.entries()) if (e.material() != null && e.material().isBlock()) pool.add(e);
            pool.addAll(plugin.shops().autoBlocks());      // blocks ONLY in this section
        }
        List<ShopEntry> list = filter(pool, query);
        List<ActionButton> btns = new ArrayList<>();
        if (c.search()) btns.add(searchBtn(w, q -> openCategory(p, c, q)));
        addEntryButtons(p, btns, list, w, () -> openCategory(p, c, query));
        if (d().getBoolean("common.show-back-button", true)) btns.add(backBtn(w, () -> openMain(p)));

        Component title = Text.c(c.title());
        List<String> lines = new ArrayList<>(sec("category").getStringList("body"));
        Map<String, String> qph = Text.map("query", query == null ? "" : query);
        if (query != null && !query.isBlank()) {
            title = title.append(Text.c(common("results-suffix", " <gray>- {query}"), qph));
            if (list.isEmpty()) lines = new ArrayList<>(noResultsBody(query));
        }
        List<DialogInput> inputs = c.search() ? List.of(searchInput(query)) : List.of();
        showMulti(p, title, body(lines, qph), inputs, btns, closeBtn(sec("category")), c.columns(),
                DialogBase.DialogAfterAction.CLOSE);
    }

    public void openSearch(Player p, String query) {
        if (query == null || query.isBlank()) {
            plugin.send(p, "search-empty");
            openMain(p);
            return;
        }
        ConfigurationSection s = sec("search");
        int w = width(s);
        List<ShopEntry> all = new ArrayList<>();
        plugin.shops().categories().forEach(c -> all.addAll(c.entries()));
        all.addAll(plugin.shops().autoItems());
        for (BookCategory bc : plugin.shops().books())
            for (BookCategory.Node n : bc.nodes()) all.addAll(n.variants());
        List<ShopEntry> list = filter(all, query);
        Map<String, String> qph = Text.map("query", query);

        List<ActionButton> btns = new ArrayList<>();
        btns.add(searchBtn(w, q -> openSearch(p, q)));
        addEntryButtons(p, btns, list, w, () -> openSearch(p, query));
        btns.add(backBtn(w, () -> openMain(p)));

        List<String> lines = list.isEmpty() ? noResultsBody(query) : s.getStringList("body");
        showMulti(p, Text.c(s.getString("title", "Search: {query}"), qph), body(lines, qph),
                List.of(searchInput(query)), btns, closeBtn(s), s.getInt("columns", 3),
                DialogBase.DialogAfterAction.CLOSE);
    }

    // ------------------------------------------------------------------ books

    public void openBooks(Player p) {
        ConfigurationSection s = sec("books");
        int w = width(s);
        List<ActionButton> btns = new ArrayList<>();
        for (BookCategory bc : plugin.shops().books())
            btns.add(btn(bc.name(), w, null, run(() -> openBookCategory(p, bc))));
        if (s.getBoolean("show-back-button", true)) btns.add(backBtn(w, () -> openMain(p)));
        if (btns.isEmpty()) return;
        showMulti(p, Text.c(s.getString("title", "Books")), body(s.getStringList("body"), null), List.of(),
                btns, closeBtn(s), s.getInt("columns", 2), DialogBase.DialogAfterAction.CLOSE);
    }

    public void openBookCategory(Player p, BookCategory bc) {
        ConfigurationSection s = sec("books");
        int w = s.getInt("entry-width", width(s));
        List<ActionButton> btns = new ArrayList<>();
        Runnable back = () -> openBookCategory(p, bc);
        String entryLabel = common("entry-label", "{name} <dark_gray>| <green>{price}");
        for (BookCategory.Node n : bc.nodes()) {
            if (n.group()) {
                btns.add(btn(s.getString("group-label", "{name} <gray>»"), w, Text.map("name", n.name()),
                        run(() -> openBookGroup(p, bc, n))));
            } else {
                ShopEntry e = n.variants().get(0);
                btns.add(btn(entryLabel, w, plugin.ph(e, 1), run(() -> select(p, e, back))));
            }
        }
        btns.add(backBtn(w, () -> openBooks(p)));
        showMulti(p, Text.c(s.getString("category-title", "{name}"), Text.map("name", bc.name())),
                body(s.getStringList("category-body"), null), List.of(), btns, closeBtn(s),
                s.getInt("entry-columns", 2), DialogBase.DialogAfterAction.CLOSE);
    }

    public void openBookGroup(Player p, BookCategory bc, BookCategory.Node n) {
        ConfigurationSection s = sec("books");
        int w = s.getInt("entry-width", width(s));
        Runnable back = () -> openBookGroup(p, bc, n);
        List<ActionButton> btns = new ArrayList<>();
        for (ShopEntry e : n.variants())
            btns.add(btn(common("entry-label", "{name} <dark_gray>| <green>{price}"), w, plugin.ph(e, 1),
                    run(() -> select(p, e, back))));
        btns.add(backBtn(w, () -> openBookCategory(p, bc)));
        showMulti(p, Text.c(s.getString("category-title", "{name}"), Text.map("name", n.name())),
                body(s.getStringList("category-body"), null), List.of(), btns, closeBtn(s),
                s.getInt("entry-columns", 2), DialogBase.DialogAfterAction.CLOSE);
    }

    // ------------------------------------------------------------------ amount + confirm

    public void openAmount(Player p, ShopEntry e, int requested, Runnable back) {
        ConfigurationSection s = sec("amount");
        int max = plugin.getConfig().getInt("max-amount", 2304);
        int amount = Math.max(1, Math.min(requested, max));
        int w = s.getInt("button-width", 60);
        List<Integer> steps = s.getIntegerList("steps");
        if (steps.isEmpty()) steps = List.of(1, 5, 16, 32, 64);

        List<ActionButton> btns = new ArrayList<>();
        for (int step : steps)
            btns.add(btn(s.getString("negative-format", "<red>-{step}"), w, Text.map("step", String.valueOf(step)),
                    run(() -> openAmount(p, e, amount - step, back))));
        for (int step : steps)
            btns.add(btn(s.getString("positive-format", "<green>+{step}"), w, Text.map("step", String.valueOf(step)),
                    run(() -> openAmount(p, e, amount + step, back))));
        btns.add(btn(s.getString("ok-label", "<green>Okay"), w, null, run(() -> openConfirm(p, e, amount, back))));
        btns.add(btn(s.getString("cancel-label", "<red>Cancel"), w, null, run(back)));

        showMulti(p, Text.c(s.getString("title", "Amount"), plugin.ph(e, amount)),
                body(s.getStringList("body"), plugin.ph(e, amount)), List.of(), btns, null, steps.size(),
                DialogBase.DialogAfterAction.NONE);
    }

    public void openConfirm(Player p, ShopEntry e, int amount, Runnable back) {
        ConfigurationSection s = sec("confirm");
        int w = s.getInt("button-width", 120);
        Map<String, String> ph = plugin.ph(e, amount);
        ActionButton yes = btn(s.getString("confirm-label", "<green>Confirm"), w, null, run(() -> {
            plugin.purchases().buy(p, e, amount);
            if (plugin.getConfig().getBoolean("reopen-after-purchase", false)) back.run();
        }));
        ActionButton no = btn(s.getString("cancel-label", "<red>Cancel"), w, null, run(back));
        showConfirm(p, Text.c(s.getString("title", "Confirm"), ph), body(s.getStringList("body"), ph), List.of(), yes, no);
    }

    // ------------------------------------------------------------------ armour price editor (admin)

    public void openPriceEditor(Player p, int slot, ShopEntry e) {
        ConfigurationSection s = sec("price-editor");
        int w = s.getInt("button-width", 120);
        Map<String, String> ph = plugin.ph(e, 1);
        DialogInput input = DialogInput.text("price", Text.c(s.getString("input-label", "Price")))
                .width(200).initial(new java.math.BigDecimal(e.price()).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()).maxLength(16).build();

        ActionButton save = btn(s.getString("save-label", "<green>Save"), w, null, (view, aud) -> {
            String txt = view.getText("price");
            plugin.sync(() -> {
                double price;
                try {
                    price = Double.parseDouble(txt == null ? "" : txt.trim().replace(",", "."));
                    if (Double.isNaN(price) || Double.isInfinite(price) || price < 0) throw new NumberFormatException();
                } catch (NumberFormatException ex) {
                    plugin.send(p, "armour-price-invalid");
                    openPriceEditor(p, slot, e);
                    return;
                }
                plugin.armour().setPrice(slot, price);
                plugin.send(p, "armour-price-set", Text.map("price", plugin.money(price)));
                plugin.armour().open(p, true);
            });
        });
        ActionButton cancel = btn(s.getString("cancel-label", "<red>Cancel"), w, null,
                run(() -> plugin.armour().open(p, true)));
        showConfirm(p, Text.c(s.getString("title", "Set price"), ph), body(s.getStringList("body"), ph),
                List.of(input), save, cancel);
    }
}
