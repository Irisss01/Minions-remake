package dev.minions;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/** All menus. The minion menu copies Hypixel's 6-row layout. */
public final class Gui implements Listener {
    public static final int[] STORAGE = {21, 22, 23, 24, 25, 30, 31, 32, 33, 34, 39, 40, 41, 42, 43};
    public static final int[] GRID = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
    public static final int[] TIER_SLOTS = {11, 12, 13, 14, 15, 21, 22, 23, 30, 31, 32, 33};
    public static final int TORCH = 3, HEAD = 4, NEXT = 5,
            FUEL = 10, SKIN = 19, SHIP = 28, UP1 = 37, UP2 = 46,
            COLLECT = 48, STATS = 50, PICKUP = 53,
            BACK = 48, CLOSE = 49, PREV = 45, NEXTPAGE = 53;

    enum Kind { MAIN, TYPE, EXTRAS, LAYOUT, MINION }

    static final class Holder implements InventoryHolder {
        final Kind kind; final MinionType type; final Minion minion; int page; Inventory inv;
        Holder(Kind k, MinionType t, Minion m) { kind = k; type = t; minion = m; }
        @Override public Inventory getInventory() { return inv; }
    }

    private final MinionsPlugin plugin;
    private final Map<UUID, Holder> open = new HashMap<>();
    private final Map<UUID, Long> pickupConfirm = new HashMap<>();

    public Gui(MinionsPlugin plugin) { this.plugin = plugin; }

    private static ItemStack filler() { return Items.named(Material.BLACK_STAINED_GLASS_PANE, " "); }

    private Inventory make(Holder h, String title) {
        Inventory inv = Bukkit.createInventory(h, 54, title);
        h.inv = inv;
        for (int i = 0; i < 54; i++) inv.setItem(i, filler());
        return inv;
    }

    private void navigation(Inventory inv, boolean back) {
        if (back) inv.setItem(BACK, Items.named(Material.ARROW, "§aGo Back", "§7To previous menu"));
        inv.setItem(CLOSE, Items.named(Material.BARRIER, "§cClose"));
    }

    private ItemStack asMenuItem(ItemStack icon, String name, String... lore) {
        ItemStack it = icon.clone();
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(name);
        im.setLore(new ArrayList<>(Arrays.asList(lore)));
        im.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        it.setItemMeta(im);
        return it;
    }

    // ------------------------------------------------------------- menus
    public void openMain(Player p) {
        Holder h = new Holder(Kind.MAIN, null, null);
        Inventory inv = make(h, "§8Minions");
        MinionType[] all = MinionType.values();
        for (int i = 0; i < all.length && i < GRID.length; i++) {
            MinionType t = all[i];
            inv.setItem(GRID[i], asMenuItem(Items.icon(t, MinionType.MAX_TIER), "§a" + t.display + " Minion",
                    "§7Category: §e" + t.cat.label,
                    "§7Collects: §f" + Items.pretty(t.resource),
                    "§7Base speed: §a" + Items.num(t.interval(1)) + "s §7(tier I)",
                    "", "§eClick to view tiers!"));
        }
        inv.setItem(48, Items.named(Material.CRAFTING_TABLE, "§aFuel, Upgrades & Items",
                "§7Fuels, upgrades, storage chests", "§7and enchanted resources.", "", "§eClick to view!"));
        inv.setItem(CLOSE, Items.named(Material.BARRIER, "§cClose"));
        inv.setItem(50, Items.named(Material.NAME_TAG, "§aMinion Slots",
                "§7Placed: §e" + plugin.manager.owned(p.getUniqueId()) + "§7/§e" + plugin.manager.slotLimit(p),
                "§7Unique minions crafted: §b" + plugin.manager.uniqueCount(p.getUniqueId()),
                "", "§7Craft more §eunique §7minions/tiers", "§7to unlock more slots."));
        p.openInventory(inv);
        open.remove(p.getUniqueId());
    }

    private String[] recipeLore(MinionType t, int tier) {
        if (tier == 1) return new String[]{"§7Recipe: " + Items.costText(t, 1), "§7Centre: §f" + Items.pretty(t.tool)};
        if (tier == MinionType.MAX_TIER)
            return new String[]{"§7Cost: " + Items.costText(t, tier), "§7(Upgrade in the minion menu)"};
        return new String[]{"§7Recipe: " + Items.costText(t, tier),
                "§7Centre: §9" + t.display + " Minion " + Items.ROMAN[tier - 1]};
    }

    public void openType(Player p, MinionType t) {
        Holder h = new Holder(Kind.TYPE, t, null);
        Inventory inv = make(h, t.display + " Minion Recipes");
        boolean canTake = p.hasPermission("minions.catalog");
        for (int tier = 1; tier <= MinionType.MAX_TIER; tier++) {
            ItemStack it = Items.minionItem(t, tier, 0);
            List<String> extra = new ArrayList<>(Arrays.asList(recipeLore(t, tier)));
            extra.add("");
            extra.add(canTake ? "§eClick to get this minion!" : "§7Craft it in a crafting table.");
            Items.addLore(it, extra.toArray(new String[0]));
            inv.setItem(TIER_SLOTS[tier - 1], it);
        }
        navigation(inv, true);
        p.openInventory(inv);
        open.remove(p.getUniqueId());
    }

    private List<ItemStack> extras() {
        List<ItemStack> l = new ArrayList<>();
        for (Fuel f : Fuel.values()) {
            ItemStack it = Items.fuelItem(f, 1);
            if (!f.custom) {
                long ms = f.millis();
                Items.addLore(it, "§6Minion Fuel", "§7Speed: §a+" + (int) Math.round(f.boost() * 100) + "%",
                        "§7Lasts: §b" + (ms < 0 ? "Forever" : Items.fmtTime(ms)));
            }
            l.add(it);
        }
        for (Upgrade u : Upgrade.values()) l.add(Items.upgrade(u));
        for (int i = 0; i < Items.CHEST_SLOTS.length; i++) l.add(Items.storageItem(i));
        for (MinionType t : MinionType.values()) l.add(Items.enchanted(t.resource, 1));
        for (MinionType t : MinionType.values()) if (t.enchBlock() != null) l.add(Items.enchantedBlock(t, 1));
        return l;
    }

    public void openExtras(Player p, int page) {
        Holder h = new Holder(Kind.EXTRAS, null, null);
        h.page = page;
        Inventory inv = make(h, "Minion Fuel, Upgrades & Items");
        List<ItemStack> l = extras();
        boolean canTake = p.hasPermission("minions.catalog");
        for (int i = 0; i < GRID.length; i++) {
            int idx = page * GRID.length + i;
            if (idx >= l.size()) break;
            ItemStack it = l.get(idx).clone();
            Items.addLore(it, "", canTake ? "§eClick to get!" : "§7Obtain through gameplay.");
            inv.setItem(GRID[i], it);
        }
        if (page > 0) inv.setItem(PREV, Items.named(Material.ARROW, "§aPrevious Page"));
        if ((page + 1) * GRID.length < l.size()) inv.setItem(NEXTPAGE, Items.named(Material.ARROW, "§aNext Page"));
        navigation(inv, true);
        p.openInventory(inv);
        open.remove(p.getUniqueId());
    }

    public void openLayout(Player p, Minion m) {
        Holder h = new Holder(Kind.LAYOUT, null, m);
        Inventory inv = make(h, "Ideal Layout (Top-down view)");
        int r = m.radius();
        for (int row = 0; row < 5; row++)
            for (int c = 2; c <= 6; c++) {
                boolean inside = Math.abs(row - 2) <= r && Math.abs(c - 4) <= r;
                if (!inside) continue;
                inv.setItem(row * 9 + c, m.type.isCrop()
                        ? Items.named(Material.FARMLAND, "§aFarmland", "§7Crops are planted on farmland.",
                        "§7The layer is one block below", "§7the minion.")
                        : Items.named(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§7Air (minion)",
                        "§7Keep this layer free of blocks.", "§7It is the layer the minion", "§7stands on, one block down."));
            }
        inv.setItem(22, Items.minionItem(m.type, m.tier, m.generated));
        navigation(inv, true);
        p.openInventory(inv);
        open.remove(p.getUniqueId());
    }

    public void openMinion(Player p, Minion m) {
        Holder h = new Holder(Kind.MINION, null, m);
        Inventory inv = make(h, "§8" + m.type.display + " Minion " + Items.ROMAN[m.tier]);
        fill(inv, m, p);
        p.openInventory(inv);
        open.put(p.getUniqueId(), h);
        Sfx.open(p);
    }

    private ItemStack pane(Material m, String name, String... lore) { return Items.named(m, name, lore); }

    private static Material accentOf(MinionType.Cat c) {
        return switch (c) {
            case MINING -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            case FARMING -> Material.GREEN_STAINED_GLASS_PANE;
            case FORAGING -> Material.BROWN_STAINED_GLASS_PANE;
        };
    }

    private static final int[] ACCENT = {0, 1, 2, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 47, 49, 51, 52};

    private static String yesNo(boolean b) { return b ? "§a✔ Yes" : "§c✖ No"; }

    private static String bar(double frac, int len) {
        frac = Math.max(0, Math.min(1, frac));
        int on = (int) Math.round(frac * len);
        StringBuilder sb = new StringBuilder("§8[").append(frac >= 0.9 ? "§c" : frac >= 0.6 ? "§e" : "§a");
        for (int i = 0; i < on; i++) sb.append('|');
        sb.append("§7");
        for (int i = on; i < len; i++) sb.append('|');
        return sb.append("§8]").toString();
    }

    private boolean confirming(Player p, long now) {
        Long t = pickupConfirm.get(p.getUniqueId());
        return t != null && now - t < 5000;
    }

    private void fill(Inventory inv, Minion m, Player p) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < 54; i++) inv.setItem(i, filler());
        ItemStack accent = Items.named(accentOf(m.type.cat), " ");
        for (int i : ACCENT) inv.setItem(i, accent);

        inv.setItem(TORCH, Items.named(Material.REDSTONE_TORCH, "§aIdeal Layout",
                "§7View the ideal layout for", "§7this minion.", "", "§eClick to view!"));

        // ---- info head
        boolean fueled = m.fuelActive(now);
        int stored = m.storedItems(), cap = m.capacity();
        double perHour = m.itemsPerHour(now);
        ItemStack info = Items.minionItem(m.type, m.tier, m.generated);
        List<String> lore = new ArrayList<>();
        lore.add("§7Time Between Actions: §a" + String.format(Locale.US, "%.1f", m.intervalMs(now) / 1000.0) + "s"
                + (m.boost(now) > 0 ? " §8(+" + (int) Math.round(m.boost(now) * 100) + "% speed)" : ""));
        lore.add("§7Max Storage: §e" + cap);
        lore.add("§7Resources Generated: §b" + m.generated);
        lore.add("§7Working Area: §e" + (m.radius() * 2 + 1) + "x" + (m.radius() * 2 + 1));
        lore.add("§7Production: §6~" + (int) Math.round(perHour) + " §7" + Items.pretty(m.productMaterial()) + "/hour");
        lore.add("");
        lore.add("§9Upgrades");
        lore.add("§7Super Compactor 3000: " + yesNo(Upgrade.SUPER_COMPACTOR.worksOn(m.type)));
        lore.add("§7Compactor: " + yesNo(Upgrade.COMPACTOR.worksOn(m.type))
                + "   §7Auto Smelter: " + yesNo(Upgrade.AUTO_SMELTER.worksOn(m.type)));
        lore.add("");
        lore.add("§7Storage: " + bar(cap == 0 ? 0 : stored / (double) cap, 20) + " §f" + stored + "§7/§f" + cap);
        if (m.status == Minion.Status.OK && stored < cap && perHour > 0)
            lore.add("§7Full in: §b" + Items.fmtTime((long) ((cap - stored) / perHour * 3_600_000L)));
        lore.add("");
        lore.add(switch (m.status) {
            case OK -> m.placing ? "§aStatus: Placing a block" : "§aStatus: Breaking a block";
            case FULL -> "§cStatus: Storage full!";
            case NO_SPACE -> "§cStatus: Needs more space!";
        });
        ItemMeta im = info.getItemMeta();
        im.setLore(lore);
        info.setItemMeta(im);
        inv.setItem(HEAD, info);

        // ---- next tier
        if (m.tier >= MinionType.MAX_TIER) {
            inv.setItem(NEXT, Items.named(Material.BARRIER, "§cMax Tier", "§7This minion is already at the", "§7highest tier."));
        } else {
            int nt = m.tier + 1, cost = m.type.cost(nt);
            long have = plugin.manager.have(p, m.type);
            boolean can = have >= cost;
            inv.setItem(NEXT, Items.named(can ? Material.GOLD_INGOT : Material.IRON_INGOT,
                    "§aNext Tier: §9" + m.type.display + " Minion " + Items.ROMAN[nt],
                    "§7Time Between Actions: §a" + Items.num(m.type.interval(m.tier)) + "s §7➜ §a" + Items.num(m.type.interval(nt)) + "s",
                    "§7Max Storage: §e" + m.type.storage(m.tier) + " §7➜ §e" + m.type.storage(nt),
                    "", "§7Cost: " + Items.costText(m.type, nt),
                    "§7Total: §e" + cost + " " + Items.pretty(m.type.resource) + " §7(you have " + (can ? "§a" : "§c") + have + "§7)",
                    "§8Enchanted items count as 160 each.", "",
                    can ? "§eClick to upgrade!" : "§cYou don't have the materials!"));
        }

        // ---- storage
        for (int i = 0; i < STORAGE.length; i++) {
            if (i < m.slots()) inv.setItem(STORAGE[i], m.storage[i]);
            else inv.setItem(STORAGE[i], Items.named(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "§cLocked!",
                    "§7Upgrade your minion's tier", "§7to unlock this slot."));
        }

        // ---- fuel
        if (fueled) {
            boolean forever = m.fuelExpires == -1;
            long left = forever ? -1 : m.fuelExpires - now;
            long total = m.fuel.millis();
            List<String> fl = new ArrayList<>();
            fl.add("§7Speed: §a+" + (int) Math.round(m.fuel.boost() * 100) + "%" + (m.fuel.daytime ? " §8(daytime only)" : ""));
            fl.add("§7Time left: §b" + (forever ? "Forever" : Items.fmtTime(left)));
            if (!forever && total > 0) fl.add(bar(left / (double) total, 20));
            fl.add("");
            fl.add(forever ? "§eClick to remove!" : "§cFuel is in use!");
            inv.setItem(FUEL, Items.named(m.fuel.material, m.fuel.rarity.substring(0, 2) + "Fuel: " + m.fuel.display, fl.toArray(new String[0])));
        } else {
            inv.setItem(FUEL, pane(Material.LIME_STAINED_GLASS_PANE, "§aFuel",
                    "§7Place a fuel item here to make", "§7your minion work faster.", "",
                    "§7Coal, Block of Coal, Enchanted Bread,", "§7Enchanted Charcoal, Solar Panel, Foul Flesh,",
                    "§7Catalyst, Hamster Wheel, Lava/Magma/", "§7Plasma Bucket...", "",
                    "§eClick with a fuel item!", "§8(or shift-click it from your inventory)"));
        }

        inv.setItem(SKIN, m.skin != null
                ? asMenuItem(m.skin, "§6Minion Skin", "§7Your minion wears this skin.", "", "§eClick to remove!")
                : pane(Material.ORANGE_STAINED_GLASS_PANE, "§6Minion Skin",
                "§7Place a custom head (skin) here", "§7to change how your minion looks.", "",
                "§eClick with a custom head!", "§8(or shift-click it from your inventory)"));
        inv.setItem(SHIP, m.shipper != null ? m.shipper : pane(Material.BLUE_STAINED_GLASS_PANE, "§9Automated Shipping",
                "§7Place a §aBudget Hopper §7or", "§9Enchanted Hopper §7here to sell items",
                "§7automatically when storage is full.",
                Eco.available() ? "" : "§cNo Vault economy found!", "", "§eClick with a hopper!"));
        inv.setItem(UP1, m.upgrades[0] != null ? m.upgrades[0] : upgradePane());
        inv.setItem(UP2, m.upgrades[1] != null ? m.upgrades[1] : upgradePane());

        // ---- buttons
        int stacks = 0;
        for (int i = 0; i < m.slots(); i++) if (m.storage[i] != null) stacks++;
        inv.setItem(COLLECT, Items.named(Material.CHEST, "§aCollect All",
                "§7Collect all items from this", "§7minion's storage" + (m.chestSlots > 0 ? " and chest." : "."),
                "", "§7Items: §e" + stored, "", stored > 0 ? "§eClick to collect!" : "§8Nothing to collect yet."));
        inv.setItem(STATS, Items.named(Material.DIAMOND, "§bMinion Statistics",
                "§7Resources generated: §b" + m.generated,
                "§7Owner: §e" + m.ownerName,
                "§7Storage chest: " + (m.chestSlots > 0 ? "§e" + m.chestSlots + " slots" : "§cnone"),
                "§7Fuel: §f" + (fueled ? m.fuel.display : "None"),
                "§7Speed bonus: §a+" + (int) Math.round(m.boost(now) * 100) + "%"));
        if (confirming(p, now)) {
            inv.setItem(PICKUP, Items.named(Material.TNT, "§c§lCONFIRM PICKUP",
                    "§7Your minion, its items and", "§7upgrades go to your inventory.", "", "§c§lClick again to confirm!"));
        } else {
            inv.setItem(PICKUP, Items.named(Material.BEDROCK, "§cPickup Minion",
                    "§7Pick up this minion.", "§7Its contents are returned to you.", "", "§eClick to pickup!"));
        }
    }

    private ItemStack upgradePane() {
        return pane(Material.YELLOW_STAINED_GLASS_PANE, "§eUpgrade Slot", "§7Place a minion upgrade here:",
                "§7Auto Smelter, Compactor, Expander,", "§7Diamond Spreading, Mithril Infusion...", "",
                "§eClick with an upgrade item!", "§8(or shift-click it from your inventory)");
    }

    public void refreshOpen() {
        for (Iterator<Map.Entry<UUID, Holder>> it = open.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Holder> e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            Holder h = e.getValue();
            if (p == null || !plugin.manager.minions.containsKey(h.minion.id)) { it.remove(); continue; }
            fill(h.inv, h.minion, p);
        }
    }

    // ------------------------------------------------------------ events
    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) {
            open.remove(e.getPlayer().getUniqueId());
            pickupConfirm.remove(e.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        int size = e.getInventory().getSize();
        for (int s : e.getRawSlots()) if (s < size) { e.setCancelled(true); return; }
    }

    private void give(Player p, ItemStack it) {
        if (!p.hasPermission("minions.catalog")) { p.sendMessage("§cCraft this in a crafting table!"); return; }
        Items.giveHeld(p, it);
        Sfx.collect(p, 1);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null) return;
        if (e.getClickedInventory() != e.getInventory()) {           // clicking own inventory
            if (h.kind == Kind.MINION && e.isShiftClick()) {         // shift-click a fuel / upgrade => it goes in its slot
                e.setCancelled(true);
                autoInsert(p, h.minion, e);
                return;
            }
            if (e.isShiftClick() || e.getAction() == InventoryAction.COLLECT_TO_CURSOR || h.kind != Kind.MINION)
                e.setCancelled(true);
            return;
        }
        e.setCancelled(true);
        int s = e.getRawSlot();
        ItemStack clicked = e.getCurrentItem();
        if (h.kind != Kind.MINION && clicked != null && clicked.getType() != Material.BLACK_STAINED_GLASS_PANE) Sfx.click(p);

        switch (h.kind) {
            case MAIN -> {
                MinionType[] all = MinionType.values();
                for (int i = 0; i < all.length && i < GRID.length; i++)
                    if (GRID[i] == s) { openType(p, all[i]); return; }
                if (s == 48) openExtras(p, 0); else if (s == CLOSE) p.closeInventory();
            }
            case TYPE -> {
                for (int t = 0; t < TIER_SLOTS.length; t++) {
                    if (TIER_SLOTS[t] == s) {
                        if (!p.hasPermission("minions.catalog")) {
                            for (String line : recipeLore(h.type, t + 1)) p.sendMessage(line);
                            return;
                        }
                        give(p, Items.minionItem(h.type, t + 1, 0));
                        plugin.manager.markUnique(p.getUniqueId(), h.type, t + 1);
                        p.sendMessage("§aYou received §9" + h.type.display + " Minion " + Items.ROMAN[t + 1] + "§a!");
                        return;
                    }
                }
                if (s == BACK) openMain(p); else if (s == CLOSE) p.closeInventory();
            }
            case EXTRAS -> {
                List<ItemStack> l = extras();
                for (int i = 0; i < GRID.length; i++) {
                    int idx = h.page * GRID.length + i;
                    if (GRID[i] == s && idx < l.size()) {
                        ItemStack it = l.get(idx).clone();
                        if (e.isShiftClick() || it.getMaxStackSize() > 1 && e.isRightClick()) it.setAmount(Math.min(64, it.getMaxStackSize()));
                        give(p, it);
                        return;
                    }
                }
                if (s == PREV && h.page > 0) openExtras(p, h.page - 1);
                else if (s == NEXTPAGE && (h.page + 1) * GRID.length < l.size()) openExtras(p, h.page + 1);
                else if (s == BACK) openMain(p);
                else if (s == CLOSE) p.closeInventory();
            }
            case LAYOUT -> {
                if (s == BACK) openMinion(p, h.minion); else if (s == CLOSE) p.closeInventory();
            }
            case MINION -> clickMinion(e, p, h.minion, s);
        }
    }

    private void takeCursorOne(InventoryClickEvent e) {
        ItemStack cur = e.getCursor();
        if (cur.getAmount() > 1) { cur.setAmount(cur.getAmount() - 1); e.setCursor(cur); } else e.setCursor(null);
    }

    private boolean validUpgradeFor(Minion m, Upgrade u, boolean shipping) {
        return u != null && u.shipping == shipping && (shipping || u.worksOn(m.type));
    }

    /** true (and tells the player) when the item is a real upgrade that just doesn't work on this minion */
    private boolean incompatible(Player p, Minion m, Upgrade u) {
        if (u == null || u.shipping || u.worksOn(m.type)) return false;
        Sfx.error(p);
        p.sendMessage("§cThe §e" + u.name.substring(2) + " §cdoesn't work on a §e" + m.type.display + " Minion§c!");
        return true;
    }

    private void setSkin(Player p, Minion m, ItemStack cur, InventoryClickEvent e) {
        boolean valid = Items.isSkinHead(cur);
        if (m.skin != null && !valid) {                                 // take it off
            if (!Items.isAir(cur)) return;
            Items.give(p, m.skin);
            m.skin = null;
            Sfx.unplug(p);
        } else if (valid) {                                             // put on / swap
            ItemStack one = cur.clone();
            one.setAmount(1);
            takeCursorOne(e);
            if (m.skin != null) Items.give(p, m.skin);
            m.skin = one;
            Sfx.plug(p);
        } else return;
        plugin.manager.applyAppearance(m);
    }

    /** takes one item out of the clicked slot of the player's inventory */
    private void takeOne(InventoryClickEvent e) {
        ItemStack it = e.getCurrentItem();
        if (it == null) return;
        if (it.getAmount() > 1) { it.setAmount(it.getAmount() - 1); e.setCurrentItem(it); } else e.setCurrentItem(null);
    }

    /** shift-click from the player's inventory: fuel -> fuel slot, hopper -> shipping, upgrade -> free upgrade slot */
    private void autoInsert(Player p, Minion m, InventoryClickEvent e) {
        if (!plugin.manager.minions.containsKey(m.id)) { p.closeInventory(); return; }
        ItemStack it = e.getCurrentItem();
        if (Items.isAir(it)) return;
        long now = System.currentTimeMillis();
        Upgrade u = Items.upgradeOf(it);
        Fuel f = Items.fuelOf(it);
        if (incompatible(p, m, u)) return;
        if (u == null && Items.isSkinHead(it)) {
            if (m.skin != null) { Sfx.error(p); p.sendMessage("§cRemove the current skin first!"); return; }
            ItemStack one = it.clone();
            one.setAmount(1);
            m.skin = one;
            takeOne(e);
            plugin.manager.applyAppearance(m);
            Sfx.plug(p);
            fill(e.getInventory(), m, p);
            plugin.manager.saveSoon();
            return;
        }
        if (u != null) {
            ItemStack one = it.clone();
            one.setAmount(1);
            if (u.shipping) {
                if (m.shipper != null) { Sfx.error(p); p.sendMessage("§cThis minion already has a shipping upgrade!"); return; }
                m.shipper = one;
                if (!Eco.available()) p.sendMessage("§eNote: Automated Shipping needs Vault + an economy plugin.");
            } else {
                if (m.hasUpgrade(u)) { Sfx.error(p); p.sendMessage("§cThis minion already has that upgrade!"); return; }
                int free = m.upgrades[0] == null ? 0 : m.upgrades[1] == null ? 1 : -1;
                if (free < 0) { Sfx.error(p); p.sendMessage("§cBoth upgrade slots are in use!"); return; }
                m.upgrades[free] = one;
            }
            takeOne(e);
            Sfx.plug(p);
        } else if (f != null) {
            if (m.fuelActive(now)) { Sfx.error(p); p.sendMessage("§cThis minion's fuel is still in use!"); return; }
            m.fuel = f;
            m.fuelExpires = f.millis() < 0 ? -1 : now + f.millis();
            takeOne(e);
            Sfx.fuel(p);
        } else return;
        fill(e.getInventory(), m, p);
        plugin.manager.saveSoon();
    }

    /** moves everything (storage + chest) to the player; returns the number of stacks moved */
    private int collectAll(Player p, Minion m) {
        int moved = 0;
        for (int i = 0; i < m.slots(); i++) {
            ItemStack st = m.storage[i];
            if (st == null) continue;
            int before = st.getAmount();
            Map<Integer, ItemStack> left = p.getInventory().addItem(st.clone());
            m.storage[i] = left.isEmpty() ? null : left.values().iterator().next();
            if (m.storage[i] == null || m.storage[i].getAmount() < before) moved++;
        }
        Inventory ci = m.chestInv();
        if (ci != null) {
            for (int i = 0; i < Math.min(m.chestSlots, ci.getSize()); i++) {
                ItemStack st = ci.getItem(i);
                if (Items.isAir(st) || Items.isLocked(st)) continue;
                int before = st.getAmount();
                Map<Integer, ItemStack> left = p.getInventory().addItem(st.clone());
                ItemStack rest = left.isEmpty() ? null : left.values().iterator().next();
                ci.setItem(i, rest);
                if (rest == null || rest.getAmount() < before) moved++;
            }
        }
        return moved;
    }

    private void clickMinion(InventoryClickEvent e, Player p, Minion m, int s) {
        if (!plugin.manager.minions.containsKey(m.id)) { p.closeInventory(); return; }
        long now = System.currentTimeMillis();

        for (int i = 0; i < STORAGE.length; i++) {             // storage slots: click = take that stack
            if (STORAGE[i] == s && i < m.slots()) {
                ItemStack st = m.storage[i];
                if (st == null) return;
                Map<Integer, ItemStack> left = p.getInventory().addItem(st.clone());
                if (!left.isEmpty() && left.values().iterator().next().getAmount() == st.getAmount()) {
                    Sfx.error(p);
                    p.sendMessage("§cYour inventory is full!");
                    return;
                }
                m.storage[i] = left.isEmpty() ? null : left.values().iterator().next();
                Sfx.pop(p);
                break;
            }
        }

        switch (s) {
            case TORCH -> { Sfx.click(p); openLayout(p, m); return; }
            case NEXT -> {
                if (!plugin.manager.upgrade(p, m)) Sfx.error(p);
            }
            case FUEL -> {
                ItemStack cur = e.getCursor();
                boolean fueled = m.fuelActive(now);
                if (fueled) {
                    if (m.fuelExpires == -1 && Items.isAir(cur)) {
                        Items.give(p, Items.fuelItem(m.fuel, 1));
                        m.fuel = null; m.fuelExpires = 0;
                        Sfx.unplug(p);
                    } else { Sfx.error(p); p.sendMessage("§cThis minion's fuel is still in use!"); }
                } else {
                    Fuel f = Items.fuelOf(cur);
                    if (f == null) return;
                    m.fuel = f;
                    m.fuelExpires = f.millis() < 0 ? -1 : now + f.millis();
                    takeCursorOne(e);
                    Sfx.fuel(p);
                }
            }
            case SKIN -> setSkin(p, m, e.getCursor(), e);
            case SHIP -> {
                ItemStack cur = e.getCursor();
                if (m.shipper != null && Items.isAir(cur)) {
                    Items.give(p, m.shipper);
                    m.shipper = null;
                    Sfx.unplug(p);
                } else if (m.shipper == null) {
                    Upgrade u = Items.upgradeOf(cur);
                    if (!validUpgradeFor(m, u, true)) return;
                    ItemStack one = cur.clone();
                    one.setAmount(1);
                    m.shipper = one;
                    takeCursorOne(e);
                    Sfx.plug(p);
                    if (!Eco.available()) p.sendMessage("§eNote: Automated Shipping needs Vault + an economy plugin.");
                }
            }
            case UP1, UP2 -> {
                int idx = s == UP1 ? 0 : 1;
                ItemStack cur = e.getCursor();
                ItemStack there = m.upgrades[idx];
                Upgrade u = Items.upgradeOf(cur);
                if (incompatible(p, m, u)) return;
                boolean usable = validUpgradeFor(m, u, false) && (!m.hasUpgrade(u) || Items.upgradeOf(there) == u);
                if (there != null && Items.isAir(cur)) {                 // take it out
                    Items.give(p, there);
                    m.upgrades[idx] = null;
                    Sfx.unplug(p);
                } else if (there == null && validUpgradeFor(m, u, false)) {
                    if (m.hasUpgrade(u)) { Sfx.error(p); p.sendMessage("§cThis minion already has that upgrade!"); return; }
                    ItemStack one = cur.clone();
                    one.setAmount(1);
                    m.upgrades[idx] = one;
                    takeCursorOne(e);
                    Sfx.plug(p);
                } else if (there != null && usable) {                    // swap with the item on the cursor
                    ItemStack one = cur.clone();
                    one.setAmount(1);
                    takeCursorOne(e);
                    Items.give(p, there);
                    m.upgrades[idx] = one;
                    Sfx.plug(p);
                }
            }
            case COLLECT -> {
                int moved = collectAll(p, m);
                if (moved > 0) Sfx.collect(p, moved); else Sfx.error(p);
            }
            case STATS, HEAD -> Sfx.click(p);
            case PICKUP -> {
                if (confirming(p, now)) {
                    pickupConfirm.remove(p.getUniqueId());
                    plugin.manager.pickup(p, m);
                    Sfx.pickup(p);
                    return;
                }
                pickupConfirm.put(p.getUniqueId(), now);
                Sfx.warn(p);
                p.sendMessage("§eClick §cPickup Minion §eagain within 5 seconds to confirm.");
            }
            default -> { }
        }
        fill(e.getInventory(), m, p);
        plugin.manager.saveSoon();
    }
}
