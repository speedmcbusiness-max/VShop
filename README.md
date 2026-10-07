# VShop - Dialog Shop for Paper

Requires: **Paper 1.21.11+** (Dialog API; Lunge/spear enchant needs 1.21.11), **Java 21**, **Vault** + any economy plugin.

## Build
    mvn package          # -> target/VShop-1.0.0.jar  (drop in /plugins)

## Commands
| Command | What |
|---|---|
| `/shop` `/vshop` | main dialog |
| `/shop blocks|ore|farm|redstone|armour|books` (also `/vshop ...`) | open a section |
| `/shop search <text>` | global search |
| `/shop reload` `/vshop reload` | reload all files (`vshop.reload`) |
| `/vshop editarmour`, `/editarmour`, `/vshop:editarmour` | edit armour chest (`vshop.editarmour`) |

## Editing the armour shop (in-game)
Run `/vshop editarmour`, then either hold an item and click a slot, or shift-click an item from your own inventory.
A price dialog opens. Left-click an item = change price, right-click = remove. Saved in `armour-items.yml`.

## Files (all in plugins/VShop/)
- `config.yml` - currency, rarities, sounds, sell command, Discord webhook
- `messages.yml` - chat messages
- `dialogs.yml` - every dialog's text, labels, widths, columns, main-menu buttons
- `shops/*.yml` - items & prices (add a new .yml = new category), `books.yml`, `armour.yml`

## Discord
Create a webhook in your channel, paste into `discord.webhook-url`, set `enabled: true`. Embed, fields and
colours are fully editable. (A webhook posts like a bot; no bot token needed.)
