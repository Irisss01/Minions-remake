package dev.minions;

import org.bukkit.Material;

import java.util.EnumMap;
import java.util.Map;

public enum Upgrade {
    AUTO_SMELTER("smelter", "§aMinion Auto Smelter", Material.FURNACE, false, 0, 0, "§aUncommon",
            "§7Automatically smelts the items", "§7your minion collects.", "",
            "§8Cobblestone→Stone, Sand→Glass,", "§8Logs→Charcoal, Potato→Baked Potato,", "§8Clay Ball→Brick."),
    COMPACTOR("compactor", "§aCompactor", Material.DROPPER, false, 0, 0, "§aUncommon",
            "§7Compacts 160 of the same item", "§7into 1 Enchanted version."),
    SUPER_COMPACTOR("super_compactor", "§9Super Compactor 3000", Material.CRAFTER, false, 0, 0, "§9Rare",
            "§7Compacts 160 of the same item", "§7into 1 Enchanted version, and 160", "§7Enchanted into 1 Enchanted Block", "§7(Lapis, Coal, Iron, Gold, Diamond,", "§7Redstone, Emerald, Quartz)."),
    DIAMOND_SPREADING("spreading", "§9Diamond Spreading", Material.DIAMOND, false, 0, 0, "§9Rare",
            "§7Gives your minion a §a10% §7chance", "§7to also produce §bDiamond§7 on every", "§7action it harvests."),
    FLINT_SHOVEL("flint_shovel", "§aFlint Shovel", Material.IRON_SHOVEL, false, 0, 0, "§aUncommon",
            "§7Makes a §eGravel §7minion produce", "§7§fFlint §7instead of gravel."),
    EXPANDER("expander", "§aMinion Expander", Material.DISPENSER, false, 0, 0, "§aUncommon",
            "§7Increases the working area", "§7of your minion from §e5x5 §7to §e7x7§7."),
    MITHRIL_INFUSION("mithril_infusion", "§9Mithril Infusion", Material.PRISMARINE_CRYSTALS, false, 0, 0.10, "§9Rare",
            "§7Makes your minion work §a10% §7faster.", "§7Stacks with fuel."),
    FREE_WILL("free_will", "§9Free Will", Material.GHAST_TEAR, false, 0, 0.10, "§9Rare",
            "§7Makes your minion work §a10% §7faster.", "§7Stacks with fuel."),
    BUDGET_HOPPER("budget_hopper", "§aBudget Hopper", Material.HOPPER, true, 0.5, 0, "§aUncommon",
            "§7Automated Shipping: sells your", "§7minion's items when storage is full", "§7for §a50% §7of their value."),
    ENCHANTED_HOPPER("enchanted_hopper", "§9Enchanted Hopper", Material.HOPPER, true, 0.9, 0, "§9Rare",
            "§7Automated Shipping: sells your", "§7minion's items when storage is full", "§7for §a90% §7of their value.");

    public final String id, name, rarity;
    public final Material icon;
    public final boolean shipping;
    public final double rate;      // shipping sell rate
    public final double speed;     // +% speed (0.10 = 10%)
    public final String[] lore;

    Upgrade(String id, String name, Material icon, boolean shipping, double rate, double speed, String rarity, String... lore) {
        this.id = id; this.name = name; this.icon = icon; this.shipping = shipping; this.rate = rate;
        this.speed = speed; this.rarity = rarity; this.lore = lore;
    }

    public static final Map<Material, Material> SMELT = new EnumMap<>(Material.class);
    static {
        SMELT.put(Material.COBBLESTONE, Material.STONE);
        SMELT.put(Material.SAND, Material.GLASS);
        SMELT.put(Material.POTATO, Material.BAKED_POTATO);
        SMELT.put(Material.CLAY_BALL, Material.BRICK);
        for (Material m : new Material[]{Material.OAK_LOG, Material.BIRCH_LOG, Material.SPRUCE_LOG,
                Material.JUNGLE_LOG, Material.ACACIA_LOG, Material.DARK_OAK_LOG})
            SMELT.put(m, Material.CHARCOAL);
    }

    /** Hypixel's "Upgrades: Yes / No" per minion (e.g. Auto Smelter does nothing for a Lapis minion). */
    public boolean worksOn(MinionType t) {
        return switch (this) {
            case AUTO_SMELTER -> SMELT.containsKey(t.resource);
            case FLINT_SHOVEL -> t == MinionType.GRAVEL;
            case DIAMOND_SPREADING -> t.cat == MinionType.Cat.MINING;
            default -> true;
        };
    }

    public static Upgrade byId(String id) {
        for (Upgrade u : values()) if (u.id.equals(id)) return u;
        return null;
    }
}
